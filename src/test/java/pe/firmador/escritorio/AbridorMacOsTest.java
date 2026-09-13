package pe.firmador.escritorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import pe.firmador.FirmaException;

/**
 * La orden que se le pasa al sistema no pasa por ningún shell: la ruta va entera en su propio
 * argumento. Estas pruebas fijan eso, que es lo que hace que «acta[R].pdf» y las carpetas con
 * espacios funcionen sin comillas ni escapes.
 */
class AbridorMacOsTest {

    private static final Path CON_CORCHETES_Y_ESPACIOS =
            Path.of("/Users/alguien/mis documentos/acta de conformidad[R2].pdf");

    @Test
    void pasaLaRutaCompletaComoUnUnicoArgumentoAlMostrarla() {
        var orden = AbridorMacOs.ordenPara(AbridorMacOs.Accion.MOSTRAR, CON_CORCHETES_Y_ESPACIOS);

        assertThat(orden).containsExactly("/usr/bin/open", "-R",
                "/Users/alguien/mis documentos/acta de conformidad[R2].pdf");
    }

    @Test
    void pasaLaRutaCompletaComoUnUnicoArgumentoAlAbrirla() {
        var orden = AbridorMacOs.ordenPara(AbridorMacOs.Accion.ABRIR, CON_CORCHETES_Y_ESPACIOS);

        assertThat(orden).containsExactly("/usr/bin/open",
                "/Users/alguien/mis documentos/acta de conformidad[R2].pdf");
    }

    @Test
    void noEscapaNiEntrecomillaLaRuta() {
        var orden = AbridorMacOs.ordenPara(AbridorMacOs.Accion.ABRIR, CON_CORCHETES_Y_ESPACIOS);

        assertThat(orden.get(orden.size() - 1))
                .doesNotContain("\\")
                .doesNotContain("\"")
                .doesNotContain("'");
    }

    @Test
    void usaLaRutaAbsolutaAunqueLeDenUnaRelativa() {
        var orden = AbridorMacOs.ordenPara(AbridorMacOs.Accion.ABRIR, Path.of("acta[R].pdf"));

        assertThat(orden.get(1)).isEqualTo(Path.of("acta[R].pdf").toAbsolutePath().toString());
    }

    @Test
    void aviseCuandoElArchivoYaNoExiste() {
        assertThatThrownBy(() -> new AbridorMacOs().abrir(Path.of("/tmp/no-existe-jamas[R].pdf")))
                .isInstanceOf(FirmaException.class)
                .hasMessageContaining("ya no está en");
    }
}
