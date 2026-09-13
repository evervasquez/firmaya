package pe.firmador.escritorio;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pe.firmador.DestinoFirma;
import pe.firmador.MotivosReFirma;
import pe.firmador.NivelFirma;

class PreferenciasUsuarioTest {

    /** Nodo propio para no tocar las preferencias reales del usuario. */
    private static final String NODO_DE_PRUEBA = "pe/firmador/pruebas";

    private PreferenciasUsuario preferencias;

    @BeforeEach
    void limpiarNodo() throws Exception {
        Preferences nodo = Preferences.userRoot().node(NODO_DE_PRUEBA);
        nodo.clear();
        preferencias = new PreferenciasUsuario(nodo);
    }

    @Test
    void noRecuerdaNadaLaPrimeraVez() {
        assertThat(preferencias.ultimaCarpeta()).isEmpty();
        assertThat(preferencias.ultimoCertificado()).isEmpty();
        assertThat(preferencias.ultimoRecuadro()).isEmpty();
        assertThat(preferencias.ultimoMotivo()).isEqualTo(MotivosReFirma.INDICE_POR_DEFECTO);
        assertThat(preferencias.urlSelloTiempo()).isEqualTo(NivelFirma.TSA_POR_DEFECTO);
    }

    @Test
    void recuerdaLaColocacionDelSelloEntreSesiones() {
        preferencias.recordarRecuadro(new Recuadro(45.5, 660.25));

        Recuadro recuperado = preferencias.ultimoRecuadro().orElseThrow();

        assertThat(recuperado.x()).isEqualTo(45.5);
        assertThat(recuperado.y()).isEqualTo(660.25);
    }

    @Test
    void normalizaElTamanoDeUnRecuadroGuardadoPorUnaVersionAnterior() {
        // Las versiones viejas dejaban elegir el tamaño y lo guardaban; ahora es fijo.
        Preferences nodo = Preferences.userRoot().node(NODO_DE_PRUEBA);
        nodo.putDouble("sello.x", 45.5);
        nodo.putDouble("sello.y", 660.25);
        nodo.putDouble("sello.ancho", 463.2);

        Recuadro recuperado = preferencias.ultimoRecuadro().orElseThrow();

        assertThat(recuperado.ancho()).isEqualTo(Recuadro.ANCHO);
        assertThat(recuperado.alto()).isEqualTo(Recuadro.ALTO);
    }

    @Test
    void recuerdaLaRutaDelCertificadoPeroNuncaLaContrasena(@TempDir Path carpeta) throws Exception {
        Path certificado = Files.createFile(carpeta.resolve("firma.p12"));

        preferencias.recordarCertificado(certificado);

        assertThat(preferencias.ultimoCertificado()).contains(certificado.toAbsolutePath());
        assertThat(clavesGuardadas()).noneMatch(clave -> clave.toLowerCase().contains("contraseña")
                || clave.toLowerCase().contains("password"));
    }

    @Test
    void olvidaElCertificadoCuandoElArchivoYaNoExiste(@TempDir Path carpeta) throws Exception {
        Path certificado = Files.createFile(carpeta.resolve("firma.p12"));
        preferencias.recordarCertificado(certificado);
        Files.delete(certificado);

        assertThat(preferencias.ultimoCertificado()).isEmpty();
    }

    @Test
    void descartaUnMotivoGuardadoFueraDeRango() {
        Preferences.userRoot().node(NODO_DE_PRUEBA).putInt("motivo.indice", 99);

        assertThat(preferencias.ultimoMotivo()).isEqualTo(MotivosReFirma.INDICE_POR_DEFECTO);
    }

    @Test
    void recuerdaLaCarpetaDeTrabajo(@TempDir Path carpeta) {
        preferencias.recordarCarpeta(carpeta);

        assertThat(preferencias.ultimaCarpeta()).contains(carpeta.toAbsolutePath());
    }

    @Test
    void guardaLosFirmadosJuntoAlOriginalMientrasNoSeElijaOtraCosa() {
        assertThat(preferencias.destinoDeLosFirmados())
                .isInstanceOf(DestinoFirma.JuntoAlOriginal.class);
    }

    @Test
    void recuerdaLaCarpetaDeDestinoAlReiniciarLaAplicacion(@TempDir Path firmados) {
        preferencias.recordarDestino(new DestinoFirma.EnCarpeta(firmados));
        preferencias.guardar();

        // Otra instancia sobre el mismo almacén es lo que ocurre al volver a abrir la aplicación.
        PreferenciasUsuario trasReiniciar =
                new PreferenciasUsuario(Preferences.userRoot().node(NODO_DE_PRUEBA));

        assertThat(trasReiniciar.destinoDeLosFirmados())
                .isEqualTo(new DestinoFirma.EnCarpeta(firmados));
    }

    @Test
    void vuelveAJuntoAlOriginalSiLaCarpetaGuardadaYaNoExiste(@TempDir Path carpeta) throws Exception {
        Path firmados = Files.createDirectory(carpeta.resolve("firmados"));
        preferencias.recordarDestino(new DestinoFirma.EnCarpeta(firmados));
        Files.delete(firmados);

        assertThat(preferencias.destinoDeLosFirmados())
                .isInstanceOf(DestinoFirma.JuntoAlOriginal.class);
    }

    @Test
    void volverAJuntoAlOriginalBorraLaCarpetaRecordada(@TempDir Path firmados) {
        preferencias.recordarDestino(new DestinoFirma.EnCarpeta(firmados));

        preferencias.recordarDestino(new DestinoFirma.JuntoAlOriginal());

        assertThat(preferencias.destinoDeLosFirmados())
                .isInstanceOf(DestinoFirma.JuntoAlOriginal.class);
    }

    private String[] clavesGuardadas() throws Exception {
        return Preferences.userRoot().node(NODO_DE_PRUEBA).keys();
    }
}
