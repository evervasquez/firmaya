package pe.firmador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Prueba de extremo a extremo con un certificado de juego: firma un PDF y vuelve a leerlo
 * para comprobar que quedo una firma PAdES-B con el mismo perfil que produce ReFirma 1.6.
 */
class FirmaPadesTest {

    private static final Instant INSTANTE_FIJO = Instant.parse("2024-02-22T02:40:20Z");
    private static final String TITULAR = "PEREZ GARCIA JUAN CARLOS";
    private static final String MOTIVO = "En señal de conformidad";
    private static final int PAGINAS = 2;

    @TempDir
    Path carpeta;

    private Path pdf;
    private Path certificado;
    private char[] contrasena;

    @BeforeEach
    void prepararArtefactos() throws Exception {
        contrasena = "clave-de-juego".toCharArray();
        pdf = ArtefactosDePrueba.crearPdf(carpeta.resolve("documento.pdf"), PAGINAS);
        certificado = ArtefactosDePrueba.crearCertificado(
                carpeta.resolve("firmante.p12"), TITULAR, contrasena, INSTANTE_FIJO);
    }

    @Test
    void produceUnaFirmaPadesBConSubFilterEtsiCadesDetached() {
        DatosFirma firma = firmarYLeer();

        assertThat(firma.subFiltro()).isEqualTo("ETSI.CAdES.detached");
        assertThat(firma.nivel()).isEqualTo("PAdES-BASELINE-B");
        assertThat(firma.filtro()).isEqualTo("Adobe.PPKLite");
    }

    @Test
    void firmaConSha256YQuedaIntegra() {
        DatosFirma firma = firmarYLeer();

        assertThat(firma.algoritmo()).containsIgnoringCase("SHA256");
        assertThat(firma.integra()).isTrue();
    }

    @Test
    void guardaElTitularElMotivoYLaFechaDelReloj() {
        DatosFirma firma = firmarYLeer();

        assertThat(firma.titular()).isEqualTo(TITULAR);
        assertThat(firma.motivo()).isEqualTo(MOTIVO);
        assertThat(firma.fecha()).isEqualTo(INSTANTE_FIJO);
    }

    @Test
    void escribeUnArchivoNuevoYNoTocaElOriginal() throws Exception {
        byte[] originalAntes = Files.readAllBytes(pdf);

        Path firmado = firmar();

        assertThat(firmado.getFileName()).hasToString("documento[R].pdf");
        assertThat(Files.readAllBytes(pdf)).isEqualTo(originalAntes);
    }

    @Test
    void firmaDeVerdadEnLaCarpetaDeDestinoElegida() throws Exception {
        Path firmados = Files.createDirectory(carpeta.resolve("firmados"));
        DestinoFirma destino = new DestinoFirma.EnCarpeta(firmados);
        LectorPdf.Geometria geometria = LectorPdf.geometria(pdf, LectorPdf.PAGINA_ULTIMA);
        SolicitudFirma solicitud = new SolicitudFirma(pdf, new OrigenClave.Archivo(certificado),
                destino.rutaPara(pdf), MOTIVO, geometria.numeroPagina(), 20f, 20f);
        char[] copia = Arrays.copyOf(contrasena, contrasena.length);

        Path firmado;
        try {
            firmado = servicio().firmar(solicitud, copia);
        } finally {
            Arrays.fill(copia, '\0');
        }

        assertThat(firmado.getParent()).isEqualTo(firmados);
        assertThat(firmado.getFileName()).hasToString("documento[R].pdf");
        assertThat(new VerificadorPdf().verificar(firmado)).hasSize(1);
        assertThat(Files.exists(carpeta.resolve("documento[R].pdf")))
                .as("no deja copias junto al original")
                .isFalse();
    }

    @Test
    void nuncaPisaUnaFirmaAnterior() throws Exception {
        // Si el nombre de siempre está ocupado, se pasa al siguiente en vez de sobrescribir.
        Path ocupado = carpeta.resolve("documento[R].pdf");
        Files.createFile(ocupado);
        byte[] contenidoDelOcupado = Files.readAllBytes(ocupado);

        Path firmado = firmar();

        assertThat(firmado.getFileName()).hasToString("documento[R2].pdf");
        assertThat(Files.readAllBytes(ocupado)).isEqualTo(contenidoDelOcupado);
    }

    @Test
    void laSolicitudSigueRechazandoUnDestinoYaOcupado() throws Exception {
        // Red de seguridad del modelo: aunque el cálculo del nombre falle, no se sobrescribe.
        Path ocupado = Files.createFile(carpeta.resolve("documento[R].pdf"));
        LectorPdf.Geometria geometria = LectorPdf.geometria(pdf, LectorPdf.PAGINA_ULTIMA);

        assertThatThrownBy(() -> new SolicitudFirma(pdf, new OrigenClave.Archivo(certificado),
                ocupado, MOTIVO, geometria.numeroPagina(), 20f, 20f))
                .isInstanceOf(FirmaException.class)
                .hasMessageContaining("ya existe");
    }

    @Test
    void diceQueNoPudoEscribirCuandoLaCarpetaNoDejaGuardar() throws Exception {
        // Es lo que pasa si macOS deniega el acceso a la carpeta (Documentos, Escritorio…):
        // el fallo tiene que llegar al usuario con la ruta, no quedarse en el registro.
        Path protegida = Files.createDirectory(carpeta.resolve("sin-permiso"));
        Path documento = ArtefactosDePrueba.crearPdf(protegida.resolve("acta.pdf"), 1);
        Files.setPosixFilePermissions(protegida, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            LectorPdf.Geometria geometria = LectorPdf.geometria(documento, LectorPdf.PAGINA_ULTIMA);
            SolicitudFirma solicitud = new SolicitudFirma(documento,
                    new OrigenClave.Archivo(certificado), SolicitudFirma.rutaFirmada(documento),
                    MOTIVO, geometria.numeroPagina(), 20f, 20f);
            char[] copia = Arrays.copyOf(contrasena, contrasena.length);

            assertThatThrownBy(() -> servicio().firmar(solicitud, copia))
                    .isInstanceOf(FirmaException.class)
                    .hasMessageContaining("No se pudo escribir el documento firmado")
                    .hasMessageContaining("acta[R].pdf");
        } finally {
            // Sin esto, @TempDir no puede limpiar la carpeta al terminar.
            Files.setPosixFilePermissions(protegida, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test
    void rechazaLaContrasenaIncorrectaSinExponerla() {
        SolicitudFirma solicitud = solicitud();
        char[] equivocada = "no-es-la-clave".toCharArray();

        assertThatThrownBy(() -> servicio().firmar(solicitud, equivocada))
                .isInstanceOf(FirmaException.class)
                .hasMessageContaining("contraseña incorrecta")
                .hasMessageNotContaining("no-es-la-clave");
    }

    @Test
    void laSegundaFirmaSeAgregaSinInvalidarLaPrimera() {
        // Es el caso de ReFirma: se firma, se abre el resultado y se vuelve a firmar en otra
        // página. La segunda firma debe ser una revisión incremental del PDF, de modo que lo
        // que cubría la primera quede intacto.
        Path primeraVuelta = firmar();

        Path segundaVuelta = firmarDeNuevo(primeraVuelta);

        List<DatosFirma> firmas = new VerificadorPdf().verificar(segundaVuelta);
        assertThat(firmas).hasSize(2);
        assertThat(firmas).allSatisfy(firma -> {
            assertThat(firma.integra()).isTrue();
            assertThat(firma.titular()).isEqualTo(TITULAR);
            assertThat(firma.nivel()).isEqualTo("PAdES-BASELINE-B");
        });
    }

    @Test
    void elDocumentoRefirmadoNoEncadenaElSufijo() {
        Path primeraVuelta = firmar();

        Path segundaVuelta = firmarDeNuevo(primeraVuelta);

        assertThat(primeraVuelta.getFileName()).hasToString("documento[R].pdf");
        assertThat(segundaVuelta.getFileName()).hasToString("documento[R2].pdf");
    }

    @Test
    void laPrimeraFirmaSigueSiendoValidaEnElArchivoOriginal() throws Exception {
        Path primeraVuelta = firmar();
        byte[] antesDeRefirmar = Files.readAllBytes(primeraVuelta);

        firmarDeNuevo(primeraVuelta);

        // Re-firmar escribe un archivo nuevo: el que ya estaba firmado no se toca.
        assertThat(Files.readAllBytes(primeraVuelta)).isEqualTo(antesDeRefirmar);
        assertThat(new VerificadorPdf().verificar(primeraVuelta)).hasSize(1);
    }

    /** Firma un documento que ya tiene firma, colocando el sello en la otra página. */
    private Path firmarDeNuevo(Path yaFirmado) {
        LectorPdf.Geometria geometria = LectorPdf.geometria(yaFirmado, 1);
        SolicitudFirma solicitud = new SolicitudFirma(yaFirmado, new OrigenClave.Archivo(certificado),
                SolicitudFirma.rutaFirmada(yaFirmado), MOTIVO,
                geometria.numeroPagina(), 250f, geometria.altoPt() - SelloVisual.ALTO_PT - 20f);
        char[] copia = Arrays.copyOf(contrasena, contrasena.length);
        try {
            return servicio().firmar(solicitud, copia);
        } finally {
            Arrays.fill(copia, '\0');
        }
    }

    private DatosFirma firmarYLeer() {
        Path firmado = firmar();
        List<DatosFirma> firmas = new VerificadorPdf().verificar(firmado);
        assertThat(firmas).hasSize(1);
        return firmas.get(0);
    }

    private Path firmar() {
        char[] copia = Arrays.copyOf(contrasena, contrasena.length);
        try {
            return servicio().firmar(solicitud(), copia);
        } finally {
            Arrays.fill(copia, '\0');
        }
    }

    private SolicitudFirma solicitud() {
        LectorPdf.Geometria geometria = LectorPdf.geometria(pdf, LectorPdf.PAGINA_ULTIMA);
        return new SolicitudFirma(pdf, new OrigenClave.Archivo(certificado),
                SolicitudFirma.rutaFirmada(pdf), MOTIVO,
                geometria.numeroPagina(), 20f, geometria.altoPt() - SelloVisual.ALTO_PT - 20f);
    }

    private ServicioFirmaPades servicio() {
        return new ServicioFirmaPades(Clock.fixed(INSTANTE_FIJO, ZoneOffset.UTC));
    }
}
