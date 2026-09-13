package pe.firmador.escritorio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.firmador.DatosCertificado;
import pe.firmador.FirmaException;
import pe.firmador.LectorCertificado;

/**
 * Lee las identidades del llavero de macOS a traves del almacen {@code KeychainStore} que el
 * propio JDK expone. No hace falta ninguna dependencia nativa.
 *
 * <p>Se ofrecen solo las identidades que el usuario puede usar de verdad, y eso descarta dos
 * grupos por motivos distintos:</p>
 *
 * <ul>
 *   <li><strong>Las que no sirven para firmar documentos.</strong> El llavero guarda juntos
 *       certificados de usos muy distintos: los de Apple Developer, por ejemplo, son de firma de
 *       codigo y una firma hecha con ellos no vale como firma de un documento. El criterio de
 *       aptitud lo decide {@link LectorCertificado#sirveParaFirmarDocumentos}, y los vencidos
 *       tampoco se ofrecen porque lo que se firme con ellos no validara.</li>
 *   <li><strong>Las del llavero del sistema</strong> ({@code /Library/Keychains/System.keychain}).
 *       El {@code KeychainStore} de Java muestra juntos todos los llaveros de busqueda, pero
 *       pedir la clave privada de una identidad que vive alli hace que macOS exija usuario y
 *       contrasena de <em>administrador</em>, algo que esta aplicacion no pide jamas. Se
 *       identifican cruzando las huellas SHA-1 con las del llavero del usuario, y las que son
 *       aptas se cuentan aparte ({@link #aptasEnElLlaveroDelSistema()}) para poder explicarle al
 *       usuario por que no ve su certificado y que puede hacer.</li>
 * </ul>
 *
 * <p>Listar es una operacion inofensiva: se leen certificados, nunca claves privadas, asi que
 * abrir el modal no provoca ningun dialogo del sistema. La autorizacion, si macOS la pide,
 * aparece al firmar, y trae el boton «Permitir siempre».</p>
 *
 * <p>Esta clase no guarda estado mutable compartido; se usa desde el hilo de la interfaz.</p>
 */
public final class LlaveroIdentidadesMacOs implements IdentidadesDelLlavero {

    private static final Logger log = LoggerFactory.getLogger(LlaveroIdentidadesMacOs.class);

    private static final String TIPO_ALMACEN = "KeychainStore";
    private static final String PROVEEDOR = "Apple";
    private static final String EJECUTABLE = "/usr/bin/security";
    private static final String ALGORITMO_HUELLA = "SHA-1";

    private static final Pattern HUELLA = Pattern.compile("SHA-1 hash: ([0-9A-Fa-f]{40})");
    private static final int ESPERA_SEGUNDOS = 10;

    private final boolean disponible;
    private final Clock reloj;

    private int aptasDelSistema;

    /**
     * Comprueba una sola vez si el equipo puede leer el llavero.
     *
     * @param reloj fuente de la fecha con la que se descartan los certificados vencidos
     */
    public LlaveroIdentidadesMacOs(Clock reloj) {
        this.reloj = Objects.requireNonNull(reloj, "reloj es obligatorio");
        this.disponible = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac")
                && Files.isExecutable(Path.of(EJECUTABLE));
    }

    @Override
    public boolean disponible() {
        return disponible;
    }

    @Override
    public List<IdentidadLlavero> disponibles() {
        return List.copyOf(utilizables().values());
    }

    @Override
    public Optional<IdentidadLlavero> buscar(String alias) {
        Objects.requireNonNull(alias, "alias es obligatorio");
        return todasLasUtilizables().stream()
                .map(Utilizable::identidad)
                .filter(identidad -> identidad.alias().equals(alias))
                .findFirst();
    }

    /**
     * Identidades utilizables agrupadas por certificado: la clave del mapa es la huella, de modo
     * que un mismo certificado guardado varias veces en el llavero se ofrece una sola vez. Al
     * usuario no le dice nada elegir entre «su nombre», «su nombre 1» y «su nombre 2»: es el
     * mismo certificado repetido.
     */
    private Map<String, IdentidadLlavero> utilizables() {
        Map<String, IdentidadLlavero> porCertificado = new LinkedHashMap<>();
        for (Utilizable utilizable : todasLasUtilizables()) {
            porCertificado.putIfAbsent(utilizable.huella(), utilizable.identidad());
        }
        return porCertificado;
    }

    private List<Utilizable> todasLasUtilizables() {
        aptasDelSistema = 0;
        if (!disponible) {
            return List.of();
        }
        Set<String> delUsuario = huellasDelLlaveroDeInicio();
        if (delUsuario.isEmpty()) {
            log.warn("No se pudieron leer los certificados del llavero de inicio de sesión");
            return List.of();
        }
        try {
            return leerIdentidades(delUsuario);
        } catch (IOException | GeneralSecurityFallo e) {
            log.warn("No se pudo leer el llavero de macOS: {}", e.getMessage());
            return List.of();
        }
    }

    /** Una identidad del llavero junto con la huella del certificado que la identifica. */
    private record Utilizable(String huella, IdentidadLlavero identidad) {
    }

    @Override
    public int aptasEnElLlaveroDelSistema() {
        return aptasDelSistema;
    }

    /** Fallo al abrir el almacen del llavero; agrupa las excepciones de {@code java.security}. */
    private static final class GeneralSecurityFallo extends Exception {

        private static final long serialVersionUID = 1L;

        GeneralSecurityFallo(Throwable causa) {
            super(causa.getMessage(), causa);
        }
    }

    private List<Utilizable> leerIdentidades(Set<String> delUsuario)
            throws IOException, GeneralSecurityFallo {
        KeyStore llavero = abrir();
        List<Utilizable> identidades = new ArrayList<>();
        try {
            for (String alias : Collections.list(llavero.aliases())) {
                if (!llavero.isKeyEntry(alias)) {
                    continue;
                }
                agregarSiSirve(identidades, llavero, alias, delUsuario);
            }
        } catch (KeyStoreException e) {
            throw new GeneralSecurityFallo(e);
        }
        return List.copyOf(identidades);
    }

    private void agregarSiSirve(List<Utilizable> identidades, KeyStore llavero, String alias,
                                Set<String> delUsuario) throws KeyStoreException {
        // getCertificate solo lee la parte publica: no despierta ningun dialogo del sistema.
        if (!(llavero.getCertificate(alias) instanceof X509Certificate certificado)) {
            return;
        }
        if (!LectorCertificado.sirveParaFirmarDocumentos(certificado)) {
            log.debug("Se omite «{}»: su certificado no sirve para firmar documentos", alias);
            return;
        }
        DatosCertificado datos;
        try {
            datos = LectorCertificado.datosDe(certificado);
        } catch (FirmaException e) {
            // Un certificado sin CN no sirve para el sello; no es motivo para vaciar la lista.
            log.debug("Se omite una identidad del llavero sin nombre común", e);
            return;
        }
        if (!datos.vigencia(reloj).permiteFirmar()) {
            log.debug("Se omite «{}»: su certificado está vencido", alias);
            return;
        }
        String huella = huellaDe(certificado);
        if (!delUsuario.contains(huella)) {
            // Sirve para firmar, pero esta en el llavero del sistema: usarla pediria permisos de
            // administrador. Se cuenta para poder explicarselo al usuario.
            aptasDelSistema++;
            return;
        }
        identidades.add(new Utilizable(huella, new IdentidadLlavero(alias, datos)));
    }

    private KeyStore abrir() throws IOException, GeneralSecurityFallo {
        try {
            KeyStore llavero = KeyStore.getInstance(TIPO_ALMACEN, PROVEEDOR);
            llavero.load(null, null);
            return llavero;
        } catch (KeyStoreException | NoSuchProviderException | NoSuchAlgorithmException
                 | CertificateException e) {
            throw new GeneralSecurityFallo(e);
        }
    }

    private String huellaDe(X509Certificate certificado) throws KeyStoreException {
        try {
            byte[] resumen = MessageDigest.getInstance(ALGORITMO_HUELLA).digest(certificado.getEncoded());
            StringBuilder hexadecimal = new StringBuilder(resumen.length * 2);
            for (byte octeto : resumen) {
                hexadecimal.append(String.format(Locale.ROOT, "%02X", octeto));
            }
            return hexadecimal.toString();
        } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
            throw new KeyStoreException("No se pudo calcular la huella del certificado", e);
        }
    }

    /**
     * Huellas SHA-1 de los certificados del llavero de inicio de sesion, preguntando al propio
     * {@code security} cual es ese llavero en vez de suponer su ruta.
     */
    private Set<String> huellasDelLlaveroDeInicio() {
        String llaveroDelUsuario = llaveroPredeterminadoDelUsuario();
        if (llaveroDelUsuario.isBlank()) {
            return Set.of();
        }
        String salida = ejecutar(List.of(EJECUTABLE, "find-certificate", "-a", "-Z", llaveroDelUsuario));
        Set<String> huellas = new HashSet<>();
        Matcher encontrado = HUELLA.matcher(salida);
        while (encontrado.find()) {
            huellas.add(encontrado.group(1).toUpperCase(Locale.ROOT));
        }
        return huellas;
    }

    private String llaveroPredeterminadoDelUsuario() {
        String salida = ejecutar(List.of(EJECUTABLE, "default-keychain", "-d", "user")).trim();
        return salida.replace("\"", "").trim();
    }

    /** Ejecuta un comando corto de solo lectura y devuelve su salida; vacio si algo falla. */
    private String ejecutar(List<String> comando) {
        ProcessBuilder constructor = new ProcessBuilder(comando);
        constructor.redirectError(ProcessBuilder.Redirect.DISCARD);
        try {
            Process proceso = constructor.start();
            proceso.getOutputStream().close();
            if (!proceso.waitFor(ESPERA_SEGUNDOS, TimeUnit.SECONDS)) {
                proceso.destroyForcibly();
                log.warn("El comando security no respondió en {} segundos", ESPERA_SEGUNDOS);
                return "";
            }
            return new String(proceso.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("No se pudo ejecutar {}: {}", EJECUTABLE, e.getMessage());
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }
}
