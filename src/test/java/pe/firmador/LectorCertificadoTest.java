package pe.firmador;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LectorCertificadoTest {

    private static final Instant MOMENTO = Instant.parse("2026-09-11T12:00:00Z");
    private static final int DIAS = 365;

    @Test
    void leeTitularDocumentoEmisorYVigenciaDelCertificado(@TempDir Path carpeta) throws Exception {
        char[] contrasena = "secreta".toCharArray();
        Path p12 = ArtefactosDePrueba.crearCertificado(carpeta.resolve("firma.p12"),
                "PEREZ GARCIA JUAN CARLOS", "DNI-12345678", contrasena, MOMENTO, DIAS);

        DatosCertificado datos = LectorCertificado.datosDe(certificadoDe(p12, contrasena));

        assertThat(datos.titular()).isEqualTo("PEREZ GARCIA JUAN CARLOS");
        assertThat(datos.documento()).isEqualTo("DNI-12345678");
        assertThat(datos.documentoLegible()).isEqualTo("DNI 12345678");
        // El certificado de prueba es autofirmado: el emisor es el propio titular.
        assertThat(datos.emisor()).isEqualTo("PEREZ GARCIA JUAN CARLOS");
        assertThat(datos.vigenteHasta()).isAfter(MOMENTO);
    }

    @Test
    void dejaElDocumentoVacioCuandoElCertificadoNoLoTrae(@TempDir Path carpeta) throws Exception {
        char[] contrasena = "secreta".toCharArray();
        Path p12 = ArtefactosDePrueba.crearCertificado(carpeta.resolve("sin-documento.p12"),
                "EMPRESA SIN DOCUMENTO", contrasena, MOMENTO);

        DatosCertificado datos = LectorCertificado.datosDe(certificadoDe(p12, contrasena));

        assertThat(datos.documento()).isEmpty();
        assertThat(datos.documentoLegible()).isEmpty();
    }

    @Test
    void aceptaElCertificadoDePersonaQueFirmaDocumentos(@TempDir Path carpeta) throws Exception {
        char[] contrasena = "secreta".toCharArray();
        Path p12 = ArtefactosDePrueba.crearCertificado(carpeta.resolve("persona.p12"),
                "PEREZ GARCIA JUAN CARLOS", "DNI-12345678", contrasena, MOMENTO, DIAS,
                ArtefactosDePrueba.Uso.FIRMA_DE_DOCUMENTOS);

        assertThat(LectorCertificado.sirveParaFirmarDocumentos(certificadoDe(p12, contrasena)))
                .isTrue();
    }

    @Test
    void rechazaElCertificadoDeFirmaDeAplicaciones(@TempDir Path carpeta) throws Exception {
        // Es el caso de los certificados de Apple Developer: firman apps, no documentos.
        char[] contrasena = "secreta".toCharArray();
        Path p12 = ArtefactosDePrueba.crearCertificado(carpeta.resolve("codigo.p12"),
                "Apple Development: Pedro Ever Vasquez", "", contrasena, MOMENTO, DIAS,
                ArtefactosDePrueba.Uso.FIRMA_DE_CODIGO);

        assertThat(LectorCertificado.sirveParaFirmarDocumentos(certificadoDe(p12, contrasena)))
                .isFalse();
    }

    @Test
    void aceptaElCertificadoQueNoDeclaraParaQueSirve(@TempDir Path carpeta) throws Exception {
        // Sin la extension keyUsage, X.509 no restringe el uso: no hay motivo para ocultarlo.
        char[] contrasena = "secreta".toCharArray();
        Path p12 = ArtefactosDePrueba.crearCertificado(carpeta.resolve("sin-usos.p12"),
                "TITULAR SIN EXTENSIONES", "", contrasena, MOMENTO, DIAS,
                ArtefactosDePrueba.Uso.SIN_DECLARAR);

        assertThat(LectorCertificado.sirveParaFirmarDocumentos(certificadoDe(p12, contrasena)))
                .isTrue();
    }

    private X509Certificate certificadoDe(Path p12, char[] contrasena) throws Exception {
        KeyStore almacen = KeyStore.getInstance("PKCS12");
        try (InputStream entrada = Files.newInputStream(p12)) {
            almacen.load(entrada, contrasena);
        }
        return (X509Certificate) almacen.getCertificate(almacen.aliases().nextElement());
    }
}
