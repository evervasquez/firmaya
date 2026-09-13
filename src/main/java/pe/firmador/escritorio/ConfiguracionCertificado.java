package pe.firmador.escritorio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.firmador.DatosCertificado;
import pe.firmador.FirmaException;
import pe.firmador.OrigenClave;
import pe.firmador.ServicioFirmaPades;

/**
 * El certificado como <em>configuracion</em> de la aplicacion, no como dato que se escribe en
 * cada sesion: se elige una vez y a partir de ahi la aplicacion firma sin volver a preguntar
 * nada.
 *
 * <p>Junta las tres piezas que hacen falta para eso: lo que se recuerda entre sesiones
 * ({@link PreferenciasUsuario}), donde vive la contrasena cuando el certificado es un archivo
 * ({@link AlmacenContrasenas}) y que identidades ofrece el llavero
 * ({@link IdentidadesDelLlavero}). Cada una esta detras de su interfaz, de modo que las pruebas
 * no tocan ni el llavero ni el disco del usuario.</p>
 *
 * <p>Esta clase no es segura para varios hilos; se usa desde el hilo de la interfaz.</p>
 */
public final class ConfiguracionCertificado {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionCertificado.class);

    private final PreferenciasUsuario preferencias;
    private final AlmacenContrasenas contrasenas;
    private final IdentidadesDelLlavero identidades;
    private final ServicioFirmaPades servicio;

    /**
     * @param preferencias donde se recuerda el certificado configurado
     * @param contrasenas  almacen de contrasenas del sistema
     * @param identidades  identidades disponibles en el llavero
     * @param reloj        fuente de la hora que necesita el servicio de firma
     */
    public ConfiguracionCertificado(PreferenciasUsuario preferencias, AlmacenContrasenas contrasenas,
                                    IdentidadesDelLlavero identidades, Clock reloj) {
        this.preferencias = Objects.requireNonNull(preferencias, "preferencias es obligatorio");
        this.contrasenas = Objects.requireNonNull(contrasenas, "contrasenas es obligatorio");
        this.identidades = Objects.requireNonNull(identidades, "identidades es obligatorio");
        this.servicio = new ServicioFirmaPades(Objects.requireNonNull(reloj, "reloj es obligatorio"));
    }

    /**
     * Resuelve con que certificado arranca la aplicacion, sin pedirle nada al usuario.
     *
     * @return el estado en que quedo: sin configurar, listo para firmar, o con un problema que
     *         hay que explicarle
     */
    public EstadoCertificado cargar() {
        Optional<OrigenClave> configurado = preferencias.origenConfigurado();
        if (configurado.isEmpty()) {
            return new EstadoCertificado.SinConfigurar();
        }
        OrigenClave origen = configurado.get();
        if (origen instanceof OrigenClave.Llavero llavero) {
            return cargarDelLlavero(llavero);
        }
        return cargarDeArchivo((OrigenClave.Archivo) origen);
    }

    /**
     * Abre un .p12 con la contrasena escrita para comprobar que de verdad funciona.
     *
     * @param ruta       archivo .p12
     * @param contrasena contrasena escrita; el llamador es quien la limpia
     * @return los datos leidos del certificado
     * @throws FirmaException si el archivo no existe, la contrasena no lo abre o no tiene clave
     */
    public DatosCertificado comprobarArchivo(Path ruta, char[] contrasena) {
        Objects.requireNonNull(ruta, "ruta es obligatoria");
        Objects.requireNonNull(contrasena, "contraseña es obligatoria");
        if (!Files.isRegularFile(ruta)) {
            throw new FirmaException("No se encuentra el archivo del certificado: " + ruta);
        }
        return servicio.datosDe(ruta, contrasena);
    }

    /**
     * Deja configurado un certificado de archivo ya comprobado.
     *
     * @param ruta       archivo .p12
     * @param datos      datos leidos al comprobarlo
     * @param contrasena contrasena comprobada; el llamador es quien la limpia
     * @param recordar   si la contrasena debe quedar guardada en el llavero del sistema
     * @return el estado resultante, siempre listo para firmar
     * @throws FirmaException si se pidio recordar la contrasena y el llavero no la acepto
     */
    public EstadoCertificado.Listo guardarArchivo(Path ruta, DatosCertificado datos,
                                                  char[] contrasena, boolean recordar) {
        Objects.requireNonNull(ruta, "ruta es obligatoria");
        Objects.requireNonNull(datos, "datos es obligatorio");
        Objects.requireNonNull(contrasena, "contraseña es obligatoria");

        Path archivo = ruta.toAbsolutePath().normalize();
        OrigenClave origen = new OrigenClave.Archivo(archivo);
        olvidarContrasenaDelAnterior(origen);
        String cuenta = AlmacenContrasenas.cuentaPara(archivo);
        if (recordar) {
            contrasenas.guardar(cuenta, contrasena);
        } else {
            contrasenas.olvidar(cuenta);
        }
        return dejarConfigurado(origen, datos);
    }

    /**
     * Deja configurada una identidad del llavero. No hay contrasena que guardar: la clave
     * privada no sale del llavero.
     *
     * @param identidad identidad elegida por el usuario
     * @return el estado resultante, siempre listo para firmar
     */
    public EstadoCertificado.Listo guardarDelLlavero(IdentidadLlavero identidad) {
        Objects.requireNonNull(identidad, "identidad es obligatoria");
        OrigenClave origen = new OrigenClave.Llavero(identidad.alias());
        olvidarContrasenaDelAnterior(origen);
        return dejarConfigurado(origen, identidad.datos());
    }

    /**
     * Contrasena guardada de un certificado de archivo.
     *
     * @param ruta archivo .p12
     * @return la contrasena, que el llamador debe limpiar, o vacio si no hay ninguna guardada
     */
    public Optional<char[]> contrasenaDe(Path ruta) {
        Objects.requireNonNull(ruta, "ruta es obligatoria");
        return contrasenas.recuperar(AlmacenContrasenas.cuentaPara(ruta));
    }

    /**
     * @return los certificados ya configurados alguna vez, el mas reciente primero
     */
    public List<CertificadoRecordado> conocidos() {
        return preferencias.certificadosConocidos();
    }

    /**
     * @return las identidades del llavero de inicio de sesion que se pueden usar para firmar
     */
    public List<IdentidadLlavero> identidadesDelLlavero() {
        return identidades.disponibles();
    }

    /**
     * @return cuantas identidades aptas para firmar se quedaron fuera por estar en el llavero del
     *         sistema, que esta aplicacion no usa para no pedir permisos de administrador
     */
    public int identidadesEnElLlaveroDelSistema() {
        return identidades.aptasEnElLlaveroDelSistema();
    }

    /**
     * @return {@code true} si este equipo puede guardar la contrasena en el llavero
     */
    public boolean puedeRecordarContrasenas() {
        return contrasenas.disponible();
    }

    private EstadoCertificado.Listo dejarConfigurado(OrigenClave origen, DatosCertificado datos) {
        preferencias.recordarOrigen(origen, datos.titular());
        preferencias.guardar();
        return new EstadoCertificado.Listo(origen, datos);
    }

    /**
     * Al cambiar de certificado se borra la contrasena del anterior: la aplicacion guarda como
     * mucho la del certificado configurado, nunca un rastro de los previos.
     */
    private void olvidarContrasenaDelAnterior(OrigenClave nuevo) {
        preferencias.origenConfigurado()
                .filter(anterior -> !anterior.equals(nuevo))
                .filter(OrigenClave.Archivo.class::isInstance)
                .map(anterior -> AlmacenContrasenas.cuentaPara(((OrigenClave.Archivo) anterior).ruta()))
                .ifPresent(contrasenas::olvidar);
    }

    private EstadoCertificado cargarDelLlavero(OrigenClave.Llavero llavero) {
        return identidades.buscar(llavero.alias())
                .<EstadoCertificado>map(identidad ->
                        new EstadoCertificado.Listo(llavero, identidad.datos()))
                .orElseGet(() -> new EstadoCertificado.Problema(preferencias.titularCertificado(),
                        "La identidad «" + llavero.alias() + "» ya no está en su llavero de inicio "
                                + "de sesión. Vuelva a configurar el certificado."));
    }

    private EstadoCertificado cargarDeArchivo(OrigenClave.Archivo archivo) {
        String titular = preferencias.titularCertificado();
        if (!Files.isRegularFile(archivo.ruta())) {
            return new EstadoCertificado.Problema(titular,
                    "El archivo del certificado ya no está en " + archivo.ruta()
                            + ". Si lo movió o lo renombró, vuelva a cargarlo.");
        }
        Optional<char[]> guardada = contrasenaDe(archivo.ruta());
        if (guardada.isEmpty()) {
            return new EstadoCertificado.Problema(titular,
                    "La contraseña de este certificado no está guardada en el Llavero de macOS. "
                            + "Cárguelo otra vez y deje marcada la casilla para recordarla.");
        }
        char[] contrasena = guardada.get();
        try {
            return new EstadoCertificado.Listo(archivo, servicio.datosDe(archivo.ruta(), contrasena));
        } catch (FirmaException e) {
            log.warn("El certificado configurado no se pudo abrir con la contraseña guardada");
            log.debug("Detalle del fallo al abrir el certificado", e);
            return new EstadoCertificado.Problema(titular,
                    "La contraseña guardada ya no abre este certificado. Si lo renovó, cárguelo "
                            + "de nuevo con su contraseña actual.");
        } finally {
            Arrays.fill(contrasena, '\0');
        }
    }
}
