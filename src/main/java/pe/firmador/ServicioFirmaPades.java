package pe.firmador;

import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;

import javax.security.auth.DestroyFailedException;

import eu.europa.esig.dss.alert.ExceptionOnStatusAlert;
import eu.europa.esig.dss.alert.LogOnStatusAlert;
import eu.europa.esig.dss.alert.exception.AlertException;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.ImageScaling;
import eu.europa.esig.dss.enumerations.MimeTypeEnum;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.model.FileDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.SignatureFieldParameters;
import eu.europa.esig.dss.pades.SignatureImageParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.service.crl.OnlineCRLSource;
import eu.europa.esig.dss.service.http.commons.CommonsDataLoader;
import eu.europa.esig.dss.service.http.commons.OCSPDataLoader;
import eu.europa.esig.dss.service.http.commons.TimestampDataLoader;
import eu.europa.esig.dss.service.ocsp.OnlineOCSPSource;
import eu.europa.esig.dss.service.tsp.OnlineTSPSource;
import eu.europa.esig.dss.spi.DSSASN1Utils;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.spi.validation.TrustAnchorVerifier;
import eu.europa.esig.dss.spi.x509.aia.DefaultAIASource;
import eu.europa.esig.dss.token.AppleSignatureToken;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.Pkcs12SignatureToken;
import eu.europa.esig.dss.token.SignatureTokenConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Firma un PDF en PAdES-B con un certificado PKCS#12, reproduciendo lo que hace
 * ReFirma PDF 1.6 de RENIEC: {@code SubFilter = ETSI.CAdES.detached}, resumen SHA-256,
 * sin sello de tiempo y con el sello visible del escudo.
 *
 * <p>El nivel por defecto es <em>B</em> a proposito: es el unico comprobado contra el
 * validador oficial de Firma Peru, y el servicio de sellado de tiempo de RENIEC requiere
 * convenio institucional, que ReFirma sin convenio tampoco usa.</p>
 *
 * <p>Bajo pedido explicito ({@link NivelFirma.LargoPlazo}) produce PAdES-BASELINE-LT, que
 * agrega sello de tiempo y deja incrustadas las respuestas OCSP y las CRL para que el
 * documento siga validando despues de que el certificado venza.</p>
 *
 * <p>Esta clase no guarda estado mutable y es segura para varios hilos.</p>
 */
public final class ServicioFirmaPades {

    private static final Logger log = LoggerFactory.getLogger(ServicioFirmaPades.class);

    /** ReFirma expresa la hora de firma en UTC; el sello visible la muestra con sufijo {@code +0000}. */
    private static final TimeZone ZONA_DE_FIRMA = TimeZone.getTimeZone("UTC");

    private final Clock reloj;
    private final SelloVisual selloVisual;

    /**
     * @param reloj fuente de la hora de firma; se inyecta para que las pruebas sean deterministas.
     *              ReFirma sincroniza contra {@code horanacional.indecopi.gob.pe} antes de firmar:
     *              cuando se agregue esa consulta NTP, ira detras de esta misma interfaz
     *              (un {@code Clock} que devuelva la hora del servidor).
     */
    public ServicioFirmaPades(Clock reloj) {
        this.reloj = Objects.requireNonNull(reloj, "reloj es obligatorio");
        this.selloVisual = new SelloVisual();
    }

    /**
     * Firma el documento y escribe el resultado en {@link SolicitudFirma#destino()}.
     *
     * @param solicitud  parametros ya validados
     * @param contrasena contrasena del almacen PKCS#12; el llamador es responsable de
     *                   limpiarla, este metodo no la conserva ni la registra
     * @return la ruta del archivo firmado
     * @throws FirmaException si el certificado no se puede abrir, no tiene clave privada
     *                        o la firma no se puede escribir
     */
    public Path firmar(SolicitudFirma solicitud, char[] contrasena) {
        Objects.requireNonNull(solicitud, "solicitud es obligatoria");
        Objects.requireNonNull(contrasena, "contraseña es obligatoria");
        Path certificado = archivoDe(solicitud.origen());

        KeyStore.PasswordProtection proteccion = new KeyStore.PasswordProtection(contrasena);
        try (Pkcs12SignatureToken token = abrirToken(certificado, proteccion)) {
            DSSPrivateKeyEntry clave = primeraClave(token, certificado);
            return firmarCon(token, clave, solicitud);
        } finally {
            destruir(proteccion);
        }
    }

    /**
     * Firma con una identidad del llavero de inicio de sesion de macOS. No hay contrasena que
     * manejar: la clave privada no sale del llavero, lo custodia el sistema.
     *
     * <p>Se pide al llavero <strong>solo</strong> la identidad elegida, nunca la lista completa:
     * tocar una identidad del llavero del sistema haria que macOS pidiera credenciales de
     * administrador, y esta aplicacion no las pide jamas.</p>
     *
     * @param solicitud parametros ya validados, con origen {@link OrigenClave.Llavero}
     * @return la ruta del archivo firmado
     * @throws FirmaException si el llavero no tiene esa identidad o no se pudo usar su clave
     */
    public Path firmarConLlavero(SolicitudFirma solicitud) {
        Objects.requireNonNull(solicitud, "solicitud es obligatoria");
        String alias = aliasDe(solicitud.origen());

        try (AppleSignatureToken token = new AppleSignatureToken()) {
            DSSPrivateKeyEntry clave = claveDelLlavero(token, alias);
            return firmarCon(token, clave, solicitud);
        }
    }

    private Path archivoDe(OrigenClave origen) {
        if (origen instanceof OrigenClave.Archivo archivo) {
            return archivo.ruta();
        }
        throw new FirmaException("Esta solicitud no se firma con un archivo .p12 sino con "
                + origen.descripcion() + "; use firmarConLlavero");
    }

    private String aliasDe(OrigenClave origen) {
        if (origen instanceof OrigenClave.Llavero llavero) {
            return llavero.alias();
        }
        throw new FirmaException("Esta solicitud no se firma con el llavero sino con "
                + origen.descripcion() + "; use firmar");
    }

    private DSSPrivateKeyEntry claveDelLlavero(AppleSignatureToken token, String alias) {
        DSSPrivateKeyEntry clave;
        try {
            clave = token.getKey(alias);
        } catch (DSSException e) {
            throw new FirmaException("El llavero de macOS no permitió usar la identidad «"
                    + alias + "»", e);
        }
        if (clave == null) {
            throw new FirmaException("El llavero de macOS ya no tiene la identidad «" + alias
                    + "». Vuelva a configurar el certificado.");
        }
        return clave;
    }

    /**
     * Abre el certificado y lee quien es su titular, con que documento, quien lo emitio y hasta
     * cuando vale. Sirve para comprobar la contrasena al configurarlo y para mostrar la ficha.
     *
     * @param certificado almacen PKCS#12
     * @param contrasena  contrasena del almacen; el llamador es responsable de limpiarla
     * @return los datos del certificado
     * @throws FirmaException si el certificado no se puede abrir, la contrasena es incorrecta
     *                        o el certificado no tiene nombre comun
     */
    public DatosCertificado datosDe(Path certificado, char[] contrasena) {
        Objects.requireNonNull(certificado, "certificado es obligatorio");
        Objects.requireNonNull(contrasena, "contraseña es obligatoria");

        KeyStore.PasswordProtection proteccion = new KeyStore.PasswordProtection(contrasena);
        try (Pkcs12SignatureToken token = abrirToken(certificado, proteccion)) {
            return LectorCertificado.datosDe(
                    primeraClave(token, certificado).getCertificate().getCertificate());
        } finally {
            destruir(proteccion);
        }
    }

    private Pkcs12SignatureToken abrirToken(Path certificado, KeyStore.PasswordProtection proteccion) {
        try {
            return new Pkcs12SignatureToken(certificado.toFile(), proteccion);
        } catch (IOException | DSSException e) {
            // Una contrasena incorrecta llega hasta aqui como IOException envolviendo una
            // UnrecoverableKeyException. No se registra ni se incluye la contrasena.
            throw new FirmaException("No se pudo abrir el certificado " + certificado
                    + " (contraseña incorrecta o archivo dañado)", e);
        }
    }

    private DSSPrivateKeyEntry primeraClave(Pkcs12SignatureToken token, Path certificado) {
        List<DSSPrivateKeyEntry> claves = token.getKeys();
        if (claves.isEmpty()) {
            throw new FirmaException("El certificado no contiene ningúna clave privada: " + certificado);
        }
        if (claves.size() > 1) {
            log.warn("El certificado tiene {} claves; se usa la primera", claves.size());
        }
        return claves.get(0);
    }

    private Path firmarCon(SignatureTokenConnection token, DSSPrivateKeyEntry clave,
                           SolicitudFirma solicitud) {
        CertificateToken certificado = clave.getCertificate();
        String titular = titularDe(certificado);
        // El diccionario /M del PDF guarda la hora al segundo: truncamos para que la fecha
        // del sello visible y la del diccionario coincidan exactamente.
        Instant fecha = reloj.instant().truncatedTo(ChronoUnit.SECONDS);

        PAdESSignatureParameters parametros = construirParametros(clave, solicitud, titular, fecha);
        DSSDocument original = new FileDocument(solicitud.pdfOrigen().toFile());
        PAdESService servicio = construirServicio(solicitud.nivel());

        ToBeSigned porFirmar = servicio.getDataToSign(original, parametros);
        SignatureValue valor = token.sign(porFirmar, DigestAlgorithm.SHA256, clave);
        DSSDocument firmado = incrustarFirma(servicio, original, parametros, valor, solicitud.nivel());

        try {
            firmado.save(solicitud.destino().toString());
        } catch (IOException e) {
            throw new FirmaException("No se pudo escribir el documento firmado: " + solicitud.destino(), e);
        }
        log.debug("Documento firmado en {}", solicitud.destino());
        return solicitud.destino();
    }

    /**
     * Incrusta la firma en el documento. Para el nivel LT este paso sale a la red a pedir
     * el sello de tiempo, las respuestas OCSP y las CRL; sus fallos se traducen aqui a un
     * mensaje que el usuario pueda entender.
     */
    private DSSDocument incrustarFirma(PAdESService servicio, DSSDocument original,
                                       PAdESSignatureParameters parametros, SignatureValue valor,
                                       NivelFirma nivel) {
        try {
            return servicio.signDocument(original, parametros, valor);
        } catch (AlertException | DSSException e) {
            throw traducirFallo(nivel, e);
        }
    }

    private FirmaException traducirFallo(NivelFirma nivel, RuntimeException causa) {
        // El proyecto compila contra Java 17: el switch con patrones aun es preview ahi,
        // asi que la jerarquia sellada se consume con instanceof con patron.
        if (nivel instanceof NivelFirma.LargoPlazo largoPlazo) {
            return new FirmaException(
                    "No se pudo completar la firma de largo plazo (PAdES-LT). Se necesita conexión a "
                            + "internet para obtener el sello de tiempo de " + largoPlazo.urlSelloTiempo()
                            + " y la prueba de revocación (OCSP/CRL) de la entidad certificadora. "
                            + "Detalle: " + causa.getMessage(), causa);
        }
        return new FirmaException("No se pudo generar la firma: " + causa.getMessage(), causa);
    }

    /**
     * Arma el servicio de firma segun el nivel pedido.
     *
     * <p>Para el nivel basico se usa un verificador vacio, exactamente como hasta ahora:
     * es la configuracion que dio "válido" en el validador oficial de Firma Peru y no se
     * toca. El nivel LT necesita fuentes en linea porque debe incrustar la prueba de
     * revocacion dentro del PDF.</p>
     */
    private PAdESService construirServicio(NivelFirma nivel) {
        if (nivel instanceof NivelFirma.LargoPlazo largoPlazo) {
            PAdESService servicio = new PAdESService(verificadorEnLinea());
            servicio.setTspSource(new OnlineTSPSource(largoPlazo.urlSelloTiempo(), new TimestampDataLoader()));
            return servicio;
        }
        return new PAdESService(new CommonCertificateVerifier());
    }

    private CertificateVerifier verificadorEnLinea() {
        CommonCertificateVerifier verificador = new CommonCertificateVerifier();
        verificador.setAIASource(new DefaultAIASource(new CommonsDataLoader()));
        verificador.setOcspSource(new OnlineOCSPSource(new OCSPDataLoader()));
        verificador.setCrlSource(new OnlineCRLSource(new CommonsDataLoader()));
        // Sin lista de confianza cargada, la cadena del firmante es "no confiable" para DSS.
        // Igual queremos la prueba de revocacion: es justamente lo que da valor al nivel LT.
        verificador.setCheckRevocationForUntrustedChains(true);
        TrustAnchorVerifier anclas = TrustAnchorVerifier.createEmptyTrustAnchorVerifier();
        anclas.setAcceptRevocationUntrustedCertificateChains(true);
        anclas.setAcceptTimestampUntrustedCertificateChains(true);
        verificador.setTrustAnchorVerifier(anclas);
        // Si no llega la prueba de revocacion, el LT no sirve para nada: mejor fallar y decirlo.
        verificador.setAlertOnMissingRevocationData(new ExceptionOnStatusAlert());
        // El certificado puede estar vencido al re-firmar un documento antiguo; eso no impide
        // construir el LT, solo se deja constancia en el registro.
        verificador.setAlertOnExpiredCertificate(new LogOnStatusAlert());
        return verificador;
    }

    private PAdESSignatureParameters construirParametros(DSSPrivateKeyEntry clave, SolicitudFirma solicitud,
                                                         String titular, Instant fecha) {
        PAdESSignatureParameters parametros = new PAdESSignatureParameters();
        parametros.setSignatureLevel(solicitud.nivel().nivelDss());
        parametros.setDigestAlgorithm(DigestAlgorithm.SHA256);
        parametros.setSigningCertificate(clave.getCertificate());
        parametros.setCertificateChain(clave.getCertificateChain());
        parametros.setReason(solicitud.motivo());
        parametros.setSignerName(titular);
        parametros.setSigningTimeZone(ZONA_DE_FIRMA);
        // DSS trabaja con java.util.Date en su API; es el unico punto donde se convierte.
        parametros.bLevel().setSigningDate(Date.from(fecha));
        parametros.setImageParameters(construirSello(solicitud, titular, fecha));
        return parametros;
    }

    private SignatureImageParameters construirSello(SolicitudFirma solicitud, String titular, Instant fecha) {
        SignatureFieldParameters campo = new SignatureFieldParameters();
        campo.setPage(solicitud.pagina());
        campo.setOriginX(solicitud.x());
        campo.setOriginY(solicitud.y());
        campo.setWidth(SelloVisual.ANCHO_PT);
        campo.setHeight(SelloVisual.ALTO_PT);

        byte[] png = selloVisual.generar(new TextoSello(titular, solicitud.motivo(), fecha));

        SignatureImageParameters imagen = new SignatureImageParameters();
        imagen.setImage(new InMemoryDocument(png, "sello.png", MimeTypeEnum.PNG));
        imagen.setFieldParameters(campo);
        // El PNG ya viene con el tamano exacto del recuadro: asi ocupa 192 x 61,5 pt clavados,
        // como el sello de ReFirma, y su contenido no se escala nunca.
        imagen.setImageScaling(ImageScaling.STRETCH);
        return imagen;
    }

    private String titularDe(CertificateToken certificado) {
        String nombreComun = DSSASN1Utils.getSubjectCommonName(certificado);
        if (nombreComun == null || nombreComun.isBlank()) {
            throw new FirmaException("El certificado del firmante no tiene nombre comun (CN)");
        }
        return nombreComun;
    }

    private void destruir(KeyStore.PasswordProtection proteccion) {
        try {
            proteccion.destroy();
        } catch (DestroyFailedException e) {
            // La implementacion por defecto de PasswordProtection si limpia el arreglo;
            // si alguna JVM no lo hiciera, el llamador igual limpia su propia copia.
            log.debug("No se pudo destruir la protección de contraseña", e);
        }
    }
}
