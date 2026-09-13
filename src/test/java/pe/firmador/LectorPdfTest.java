package pe.firmador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LectorPdfTest {

    private static final int PAGINAS = 3;
    private static final float ALTO_A4_PT = 841.89f;

    @TempDir
    Path carpeta;

    private Path pdf;

    @BeforeEach
    void prepararDocumento() throws Exception {
        pdf = ArtefactosDePrueba.crearPdf(carpeta.resolve("documento.pdf"), PAGINAS);
    }

    @Test
    void tomaLaUltimaPaginaCuandoNoSeIndicaNinguna() {
        LectorPdf.Geometria geometria = LectorPdf.geometria(pdf, LectorPdf.PAGINA_ULTIMA);

        assertThat(geometria.numeroPagina()).isEqualTo(PAGINAS);
    }

    @Test
    void informaElTotalDePaginas() {
        LectorPdf.Geometria geometria = LectorPdf.geometria(pdf, LectorPdf.PAGINA_ULTIMA);

        assertThat(geometria.totalPaginas()).isEqualTo(PAGINAS);
    }

    @Test
    void devuelveElAltoDeLaPaginaEnPuntos() {
        LectorPdf.Geometria geometria = LectorPdf.geometria(pdf, 1);

        assertThat(geometria.altoPt()).isCloseTo(ALTO_A4_PT, org.assertj.core.data.Offset.offset(0.01f));
    }

    @Test
    void rechazaUnaPaginaQueNoExiste() {
        assertThatThrownBy(() -> LectorPdf.geometria(pdf, PAGINAS + 1))
                .isInstanceOf(FirmaException.class)
                .hasMessageContaining("fuera de rango");
    }

    @Test
    void rechazaUnArchivoQueNoExiste() {
        Path inexistente = carpeta.resolve("no-esta.pdf");

        assertThatThrownBy(() -> LectorPdf.geometria(inexistente, 1))
                .isInstanceOf(FirmaException.class)
                .hasMessageContaining("No existe el PDF");
    }
}
