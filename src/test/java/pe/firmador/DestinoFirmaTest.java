package pe.firmador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DestinoFirmaTest {

    @Test
    void juntoAlOriginalGuardaEnLaMismaCarpeta(@TempDir Path carpeta) throws Exception {
        Path original = Files.createFile(carpeta.resolve("acta.pdf"));

        Path destino = new DestinoFirma.JuntoAlOriginal().rutaPara(original);

        assertThat(destino.getParent()).isEqualTo(carpeta);
        assertThat(destino.getFileName()).hasToString("acta[R].pdf");
    }

    @Test
    void enCarpetaGuardaEnLaCarpetaElegidaConservandoElNombre(@TempDir Path carpeta)
            throws Exception {
        Path original = Files.createFile(carpeta.resolve("acta.pdf"));
        Path firmados = Files.createDirectory(carpeta.resolve("firmados"));

        Path destino = new DestinoFirma.EnCarpeta(firmados).rutaPara(original);

        assertThat(destino.getParent()).isEqualTo(firmados);
        assertThat(destino.getFileName()).hasToString("acta[R].pdf");
    }

    @Test
    void nuncaSobrescribeLoQueYaHayEnLaCarpetaDeDestino(@TempDir Path carpeta) throws Exception {
        Path original = Files.createFile(carpeta.resolve("acta.pdf"));
        Path firmados = Files.createDirectory(carpeta.resolve("firmados"));
        Files.createFile(firmados.resolve("acta[R].pdf"));
        Files.createFile(firmados.resolve("acta[R2].pdf"));

        Path destino = new DestinoFirma.EnCarpeta(firmados).rutaPara(original);

        assertThat(destino.getFileName()).hasToString("acta[R3].pdf");
        assertThat(Files.exists(destino)).isFalse();
    }

    @Test
    void tampocoSobrescribeCuandoSeGuardaJuntoAlOriginal(@TempDir Path carpeta) throws Exception {
        Path original = Files.createFile(carpeta.resolve("acta.pdf"));
        Files.createFile(carpeta.resolve("acta[R].pdf"));

        Path destino = new DestinoFirma.JuntoAlOriginal().rutaPara(original);

        assertThat(destino.getFileName()).hasToString("acta[R2].pdf");
    }

    @Test
    void explicaQueLaCarpetaGuardadaYaNoExiste(@TempDir Path carpeta) throws Exception {
        Path original = Files.createFile(carpeta.resolve("acta.pdf"));
        Path borrada = carpeta.resolve("ya-no-esta");

        assertThatThrownBy(() -> new DestinoFirma.EnCarpeta(borrada).rutaPara(original))
                .isInstanceOf(FirmaException.class)
                .hasMessageContaining("ya no existe")
                .hasMessageContaining("Elija otra carpeta");
    }

    @Test
    void explicaQueElDestinoNoEsUnaCarpeta(@TempDir Path carpeta) throws Exception {
        Path original = Files.createFile(carpeta.resolve("acta.pdf"));
        Path archivoSuelto = Files.createFile(carpeta.resolve("esto-es-un-archivo"));

        assertThatThrownBy(() -> new DestinoFirma.EnCarpeta(archivoSuelto).rutaPara(original))
                .isInstanceOf(FirmaException.class)
                .hasMessageContaining("no es una carpeta");
    }

    @Test
    void explicaQueMacOsNoDaPermisoDeEscritura(@TempDir Path carpeta) throws Exception {
        Path original = Files.createFile(carpeta.resolve("acta.pdf"));
        Path protegida = Files.createDirectory(carpeta.resolve("protegida"));
        Files.setPosixFilePermissions(protegida, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            assertThatThrownBy(() -> new DestinoFirma.EnCarpeta(protegida).rutaPara(original))
                    .isInstanceOf(FirmaException.class)
                    .hasMessageContaining("permiso de escritura")
                    .hasMessageContaining("Elija otra");
        } finally {
            Files.setPosixFilePermissions(protegida, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test
    void guardaLaCarpetaEnFormaAbsoluta() {
        DestinoFirma.EnCarpeta destino = new DestinoFirma.EnCarpeta(Path.of("firmados"));

        assertThat(destino.carpeta().isAbsolute()).isTrue();
        assertThat(destino.descripcion()).endsWith("firmados");
    }
}
