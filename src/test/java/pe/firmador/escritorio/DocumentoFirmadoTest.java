package pe.firmador.escritorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pe.firmador.FirmaException;

/**
 * Lo que importa aqui es que el usuario llegue al archivo: que la ruta que ve sea exactamente la
 * que se abre, y que los nombres que genera la aplicacion —con corchetes, y muchas veces con
 * espacios— lleguen intactos al sistema.
 */
class DocumentoFirmadoTest {

    /** Abridor de juego: no abre nada, solo anota con que ruta lo llamaron. */
    private static final class AbridorAnotador implements AbridorDeArchivos {

        private final List<String> mostrados = new ArrayList<>();
        private final List<String> abiertos = new ArrayList<>();

        @Override
        public void mostrarEnElGestor(Path archivo) {
            mostrados.add(archivo.toString());
        }

        @Override
        public void abrir(Path archivo) {
            abiertos.add(archivo.toString());
        }
    }

    @Test
    void abreExactamenteLaRutaQueEnsenaAlUsuario(@TempDir Path carpeta) throws Exception {
        Path firmado = Files.createFile(carpeta.resolve("acta[R].pdf"));
        DocumentoFirmado documento = new DocumentoFirmado(firmado);
        AbridorAnotador abridor = new AbridorAnotador();

        documento.mostrarEnElGestor(abridor);
        documento.abrir(abridor);

        assertThat(abridor.mostrados).containsExactly(documento.rutaVisible());
        assertThat(abridor.abiertos).containsExactly(documento.rutaVisible());
    }

    @Test
    void conservaLosCorchetesYLosEspaciosDelNombre(@TempDir Path carpeta) throws Exception {
        Path conEspacios = Files.createDirectory(carpeta.resolve("mis documentos"));
        Path firmado = Files.createFile(conEspacios.resolve("acta de conformidad[R2].pdf"));
        DocumentoFirmado documento = new DocumentoFirmado(firmado);
        AbridorAnotador abridor = new AbridorAnotador();

        documento.abrir(abridor);

        assertThat(documento.rutaVisible())
                .contains("mis documentos")
                .endsWith("acta de conformidad[R2].pdf");
        assertThat(abridor.abiertos).containsExactly(documento.rutaVisible());
    }

    @Test
    void siempreEnsenaLaRutaCompletaAunqueLeLleguenRelativa() {
        DocumentoFirmado documento = new DocumentoFirmado(Path.of("acta[R].pdf"));

        assertThat(documento.rutaVisible()).isEqualTo(Path.of("acta[R].pdf").toAbsolutePath().toString());
    }

    @Test
    void aviseEnVezDeFallarCuandoElArchivoYaNoEsta(@TempDir Path carpeta) throws Exception {
        Path firmado = Files.createFile(carpeta.resolve("acta[R].pdf"));
        DocumentoFirmado documento = new DocumentoFirmado(firmado);
        Files.delete(firmado);
        AbridorAnotador abridor = new AbridorAnotador();

        assertThatThrownBy(() -> documento.mostrarEnElGestor(abridor))
                .isInstanceOf(FirmaException.class)
                .hasMessageContaining("ya no está en")
                .hasMessageContaining("acta[R].pdf");
        assertThat(documento.existe()).isFalse();
        assertThat(abridor.mostrados).isEmpty();
    }
}
