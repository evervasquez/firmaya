package pe.firmador.escritorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pe.firmador.ArtefactosDePrueba;
import pe.firmador.DatosCertificado;
import pe.firmador.FirmaException;
import pe.firmador.OrigenClave;

/**
 * Comprueba el comportamiento que ve el usuario: que al abrir la aplicacion el certificado ya
 * configurado se cargue solo, y que cuando algo falla se explique en vez de estallar.
 *
 * <p>Ni el llavero ni las preferencias reales del equipo se tocan: el almacen de contrasenas y
 * las identidades son dobles en memoria, y las preferencias van a un nodo propio de prueba.</p>
 */
class ConfiguracionCertificadoTest {

    private static final String NODO_DE_PRUEBA = "pe/firmador/pruebas-configuracion";
    private static final Instant MOMENTO = Instant.parse("2026-09-11T12:00:00Z");
    private static final String TITULAR = "PEREZ GARCIA JUAN CARLOS";
    private static final int DIAS_DE_VIGENCIA = 365;

    private final char[] contrasena = "secreta".toCharArray();

    private AlmacenEnMemoria almacen;
    private IdentidadesFijas identidades;
    private PreferenciasUsuario preferencias;
    private ConfiguracionCertificado configuracion;

    @BeforeEach
    void prepararEntorno() throws Exception {
        Preferences nodo = Preferences.userRoot().node(NODO_DE_PRUEBA);
        nodo.clear();
        almacen = new AlmacenEnMemoria();
        identidades = new IdentidadesFijas();
        preferencias = new PreferenciasUsuario(nodo);
        configuracion = new ConfiguracionCertificado(preferencias, almacen, identidades, reloj());
    }

    @Test
    void laPrimeraVezNoHayCertificadoConfigurado() {
        assertThat(configuracion.cargar()).isInstanceOf(EstadoCertificado.SinConfigurar.class);
    }

    @Test
    void alAbrirDeNuevoCargaElCertificadoSinPedirNada(@TempDir Path carpeta) throws Exception {
        Path p12 = certificadoDePrueba(carpeta, DIAS_DE_VIGENCIA);
        configurar(p12, true);

        EstadoCertificado estado = configuracion.cargar();

        assertThat(estado).isInstanceOf(EstadoCertificado.Listo.class);
        EstadoCertificado.Listo listo = (EstadoCertificado.Listo) estado;
        assertThat(listo.datos().titular()).isEqualTo(TITULAR);
        assertThat(listo.datos().documentoLegible()).isEqualTo("DNI 12345678");
        assertThat(listo.origen()).isEqualTo(new OrigenClave.Archivo(p12));
    }

    @Test
    void explicaQueElArchivoYaNoEstaEnEsaRuta(@TempDir Path carpeta) throws Exception {
        Path p12 = certificadoDePrueba(carpeta, DIAS_DE_VIGENCIA);
        configurar(p12, true);
        Files.delete(p12);

        EstadoCertificado estado = configuracion.cargar();

        assertThat(estado).isInstanceOf(EstadoCertificado.Problema.class);
        assertThat(((EstadoCertificado.Problema) estado).mensaje())
                .contains("ya no está en")
                .contains("vuelva a cargarlo");
    }

    @Test
    void explicaQueLaContrasenaNoQuedoGuardadaEnElLlavero(@TempDir Path carpeta) throws Exception {
        Path p12 = certificadoDePrueba(carpeta, DIAS_DE_VIGENCIA);
        configurar(p12, false);

        EstadoCertificado estado = configuracion.cargar();

        assertThat(estado).isInstanceOf(EstadoCertificado.Problema.class);
        assertThat(((EstadoCertificado.Problema) estado).mensaje()).contains("no está guardada");
        assertThat(almacen.cuantasGuardadas()).isZero();
    }

    @Test
    void explicaQueLaContrasenaGuardadaYaNoAbreElCertificado(@TempDir Path carpeta) throws Exception {
        Path p12 = certificadoDePrueba(carpeta, DIAS_DE_VIGENCIA);
        configurar(p12, true);
        almacen.cambiarPorUnaEquivocada(AlmacenContrasenas.cuentaPara(p12));

        EstadoCertificado estado = configuracion.cargar();

        assertThat(estado).isInstanceOf(EstadoCertificado.Problema.class);
        assertThat(((EstadoCertificado.Problema) estado).mensaje()).contains("ya no abre");
        assertThat(((EstadoCertificado.Problema) estado).titular()).isEqualTo(TITULAR);
    }

    @Test
    void rechazaLaContrasenaEquivocadaSinGuardarNada(@TempDir Path carpeta) throws Exception {
        Path p12 = certificadoDePrueba(carpeta, DIAS_DE_VIGENCIA);

        assertThatThrownBy(() -> configuracion.comprobarArchivo(p12, "otra".toCharArray()))
                .isInstanceOf(FirmaException.class);
        assertThat(almacen.cuantasGuardadas()).isZero();
        assertThat(configuracion.cargar()).isInstanceOf(EstadoCertificado.SinConfigurar.class);
    }

    @Test
    void avisaCuandoElCertificadoConfiguradoEstaVencido(@TempDir Path carpeta) throws Exception {
        // Vigencia de un solo dia a partir de ayer: al momento de la prueba ya vencio.
        Path p12 = certificadoDePrueba(carpeta, 1);
        DatosCertificado datos = configuracion.comprobarArchivo(p12, contrasena);
        configuracion.guardarArchivo(p12, datos, contrasena, true);

        EstadoCertificado.Listo listo = (EstadoCertificado.Listo) configuracion.cargar();

        assertThat(listo.datos().vigencia(reloj())).isEqualTo(DatosCertificado.Vigencia.VENCIDO);
        assertThat(listo.datos().vigencia(reloj()).permiteFirmar()).isFalse();
    }

    @Test
    void cargaLaIdentidadDelLlaveroSinContrasenaNinguna() {
        IdentidadLlavero identidad = new IdentidadLlavero("firma del titular", datosVigentes());
        identidades.agregar(identidad);
        configuracion.guardarDelLlavero(identidad);

        EstadoCertificado estado = configuracion.cargar();

        assertThat(estado).isInstanceOf(EstadoCertificado.Listo.class);
        assertThat(((EstadoCertificado.Listo) estado).origen())
                .isEqualTo(new OrigenClave.Llavero("firma del titular"));
        assertThat(almacen.cuantasGuardadas()).isZero();
    }

    @Test
    void explicaQueLaIdentidadDesaparecioDelLlavero() {
        IdentidadLlavero identidad = new IdentidadLlavero("firma del titular", datosVigentes());
        identidades.agregar(identidad);
        configuracion.guardarDelLlavero(identidad);
        identidades.quitarTodas();

        EstadoCertificado estado = configuracion.cargar();

        assertThat(estado).isInstanceOf(EstadoCertificado.Problema.class);
        assertThat(((EstadoCertificado.Problema) estado).mensaje()).contains("ya no está en su llavero");
    }

    @Test
    void alCambiarDeCertificadoOlvidaLaContrasenaDelAnterior(@TempDir Path carpeta) throws Exception {
        Path viejo = certificadoDePrueba(carpeta, DIAS_DE_VIGENCIA, "viejo.p12");
        Path nuevo = certificadoDePrueba(carpeta, DIAS_DE_VIGENCIA, "nuevo.p12");
        configurar(viejo, true);

        configurar(nuevo, true);

        assertThat(almacen.recuperar(AlmacenContrasenas.cuentaPara(viejo))).isEmpty();
        assertThat(almacen.recuperar(AlmacenContrasenas.cuentaPara(nuevo))).isPresent();
        assertThat(almacen.cuantasGuardadas()).isEqualTo(1);
    }

    @Test
    void recuerdaLosCertificadosYaUsadosParaNoTenerQueBuscarlosOtraVez(@TempDir Path carpeta)
            throws Exception {
        Path primero = certificadoDePrueba(carpeta, DIAS_DE_VIGENCIA, "primero.p12");
        Path segundo = certificadoDePrueba(carpeta, DIAS_DE_VIGENCIA, "segundo.p12");
        configurar(primero, true);

        configurar(segundo, true);

        assertThat(configuracion.conocidos()).hasSize(2);
        assertThat(configuracion.conocidos().get(0).origen())
                .isEqualTo(new OrigenClave.Archivo(segundo));
        assertThat(configuracion.conocidos().get(0).titular()).isEqualTo(TITULAR);
    }

    @Test
    void nuncaEscribeLaContrasenaEnLasPreferencias(@TempDir Path carpeta) throws Exception {
        Path p12 = certificadoDePrueba(carpeta, DIAS_DE_VIGENCIA);
        configurar(p12, true);

        Preferences nodo = Preferences.userRoot().node(NODO_DE_PRUEBA);
        for (String clave : nodo.keys()) {
            assertThat(nodo.get(clave, "")).doesNotContain(new String(contrasena));
        }
    }

    private void configurar(Path p12, boolean recordar) {
        DatosCertificado datos = configuracion.comprobarArchivo(p12, contrasena);
        configuracion.guardarArchivo(p12, datos, contrasena, recordar);
    }

    private Path certificadoDePrueba(Path carpeta, int diasDeVigencia) throws Exception {
        return certificadoDePrueba(carpeta, diasDeVigencia, "firma.p12");
    }

    private Path certificadoDePrueba(Path carpeta, int diasDeVigencia, String nombre) throws Exception {
        return ArtefactosDePrueba.crearCertificado(carpeta.resolve(nombre), TITULAR, "DNI-12345678",
                contrasena, MOMENTO, diasDeVigencia);
    }

    private DatosCertificado datosVigentes() {
        return new DatosCertificado(TITULAR, "DNI-12345678", "FirmEasy SubCA",
                MOMENTO.minus(30, ChronoUnit.DAYS), MOMENTO.plus(300, ChronoUnit.DAYS));
    }

    private Clock reloj() {
        return Clock.fixed(MOMENTO, ZoneOffset.UTC);
    }
}
