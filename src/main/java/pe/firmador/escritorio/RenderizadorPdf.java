package pe.firmador.escritorio;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import javax.imageio.ImageIO;

import javafx.scene.image.Image;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.firmador.FirmaException;

/**
 * Convierte las paginas de un PDF en imagenes para mostrarlas en pantalla.
 *
 * <p>Abre el documento una sola vez y lo mantiene abierto mientras dure la vista previa,
 * en modo de solo lectura: nunca escribe sobre el original.</p>
 *
 * <p><strong>No es segura para varios hilos.</strong> {@link PDDocument} no lo es, asi que
 * todas las llamadas deben venir de un unico hilo; la aplicacion usa un ejecutor de un solo
 * hilo para eso.</p>
 */
public final class RenderizadorPdf implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RenderizadorPdf.class);

    private final Path ruta;
    private final PDDocument documento;

    /**
     * Abre el documento.
     *
     * @param pdf ruta del archivo
     * @throws FirmaException si el archivo no existe o no es un PDF legible
     */
    public RenderizadorPdf(Path pdf) {
        this.ruta = Objects.requireNonNull(pdf, "pdf es obligatorio");
        if (!Files.isRegularFile(pdf)) {
            throw new FirmaException("No existe el PDF: " + pdf);
        }
        try {
            this.documento = Loader.loadPDF(pdf.toFile());
        } catch (IOException e) {
            throw new FirmaException("No se pudo abrir el PDF: " + pdf, e);
        }
        if (documento.getNumberOfPages() < 1) {
            cerrar();
            throw new FirmaException("El PDF no tiene páginas: " + pdf);
        }
    }

    /**
     * @return numero de paginas del documento, siempre mayor que cero
     */
    public int totalPaginas() {
        return documento.getNumberOfPages();
    }

    /**
     * Tamano de una pagina en puntos, tal como lo declara su {@code MediaBox}.
     *
     * @param pagina pagina empezando en uno
     * @return el rectangulo de la pagina
     * @throws FirmaException si la pagina no existe
     */
    public PDRectangle tamano(int pagina) {
        return paginaDe(pagina).getMediaBox();
    }

    /**
     * Giro declarado de una pagina, en grados.
     *
     * @param pagina pagina empezando en uno
     * @return 0, 90, 180 o 270
     * @throws FirmaException si la pagina no existe
     */
    public int rotacion(int pagina) {
        return paginaDe(pagina).getRotation();
    }

    /**
     * Dibuja una pagina.
     *
     * @param pagina pagina empezando en uno
     * @param escala pixeles por punto; 1 equivale a 72 dpi
     * @return la imagen de la pagina
     * @throws FirmaException si la pagina no existe o no se puede dibujar
     */
    public Image renderizar(int pagina, double escala) {
        if (!(escala > 0) || !Double.isFinite(escala)) {
            throw new IllegalArgumentException("La escala debe ser positiva, se recibió: " + escala);
        }
        paginaDe(pagina);
        try {
            BufferedImage mapa = new PDFRenderer(documento)
                    .renderImage(pagina - 1, (float) escala, ImageType.RGB);
            return new Image(new ByteArrayInputStream(aPng(mapa)));
        } catch (IOException e) {
            throw new FirmaException("No se pudo dibujar la página " + pagina + " de " + ruta, e);
        }
    }

    @Override
    public void close() {
        cerrar();
    }

    private PDPage paginaDe(int pagina) {
        int total = documento.getNumberOfPages();
        if (pagina < 1 || pagina > total) {
            throw new FirmaException(
                    "Página " + pagina + " fuera de rango: el documento tiene " + total);
        }
        return documento.getPage(pagina - 1);
    }

    private byte[] aPng(BufferedImage mapa) throws IOException {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            if (!ImageIO.write(mapa, "png", salida)) {
                throw new FirmaException("Esta JVM no tiene codificador PNG disponible");
            }
            return salida.toByteArray();
        }
    }

    private void cerrar() {
        try {
            documento.close();
        } catch (IOException e) {
            // El documento se abrio solo para lectura: un fallo al cerrarlo no puede perder
            // datos ni afecta al usuario, asi que se registra y no se propaga.
            log.debug("No se pudo cerrar el PDF {}", ruta, e);
        }
    }
}
