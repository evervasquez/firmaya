package pe.firmador;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SolicitudFirmaTest {

    @Test
    void agregaElSufijoDeReFirmaAntesDeLaExtension() {
        Path firmada = SolicitudFirma.rutaFirmada(Path.of("/tmp", "contrato.pdf"));

        assertThat(firmada.getFileName()).hasToString("contrato[R].pdf");
    }

    @Test
    void dejaElArchivoFirmadoJuntoAlOriginal() {
        Path original = Path.of("/tmp", "carpeta", "contrato.pdf");

        Path firmada = SolicitudFirma.rutaFirmada(original);

        assertThat(firmada.getParent()).isEqualTo(original.toAbsolutePath().getParent());
    }

    @Test
    void aceptaExtensionEnMayusculas() {
        Path firmada = SolicitudFirma.rutaFirmada(Path.of("/tmp", "CONTRATO.PDF"));

        assertThat(firmada.getFileName()).hasToString("CONTRATO[R].pdf");
    }

    @Test
    void agregaLaExtensionSiElOriginalNoLaTiene() {
        Path firmada = SolicitudFirma.rutaFirmada(Path.of("/tmp", "contrato"));

        assertThat(firmada.getFileName()).hasToString("contrato[R].pdf");
    }

    @Test
    void numeraElSufijoAlVolverAFirmarUnDocumentoYaFirmado() {
        Path segunda = SolicitudFirma.rutaFirmada(Path.of("/tmp", "contrato[R].pdf"));

        assertThat(segunda.getFileName()).hasToString("contrato[R2].pdf");
    }

    @Test
    void sigueNumerandoEnLasFirmasSucesivas() {
        Path tercera = SolicitudFirma.rutaFirmada(Path.of("/tmp", "contrato[R2].pdf"));
        Path cuarta = SolicitudFirma.rutaFirmada(Path.of("/tmp", "contrato[R3].pdf"));

        assertThat(tercera.getFileName()).hasToString("contrato[R3].pdf");
        assertThat(cuarta.getFileName()).hasToString("contrato[R4].pdf");
    }

    @Test
    void noConfundeUnCorcheteCualquieraConElSufijoDeFirma() {
        Path firmada = SolicitudFirma.rutaFirmada(Path.of("/tmp", "informe [Revisado].pdf"));

        assertThat(firmada.getFileName()).hasToString("informe [Revisado][R].pdf");
    }
}
