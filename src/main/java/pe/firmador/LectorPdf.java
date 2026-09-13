package pe.firmador;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/**
 * Lectura de la geometria del PDF a firmar, necesaria para colocar el sello sin tener
 * que pedirle al usuario las coordenadas.
 *
 * <p>Solo lee: nunca modifica el documento original.</p>
 */
public final class LectorPdf {

    /** Valor de pagina que significa "la última", como hace ReFirma con {@code StampOnFirstPage=false}. */
    public static final int PAGINA_ULTIMA = 0;

    private LectorPdf() {
    }

    /**
     * Tamano y numero de la pagina donde se va a estampar el sello.
     *
     * @param numeroPagina  pagina resuelta, empezando en uno
     * @param totalPaginas  paginas del documento
     * @param anchoPt       ancho de la pagina en puntos
     * @param altoPt        alto de la pagina en puntos
     */
    public record Geometria(int numeroPagina, int totalPaginas, float anchoPt, float altoPt) {

        public Geometria {
            if (numeroPagina < 1 || numeroPagina > totalPaginas) {
                throw new FirmaException(
                        "Página " + numeroPagina + " fuera de rango: el documento tiene " + totalPaginas);
            }
        }
    }

    /**
     * Resuelve la pagina pedida y devuelve sus dimensiones.
     *
     * @param pdf             ruta del documento
     * @param paginaSolicitada pagina empezando en uno, o {@link #PAGINA_ULTIMA} para la ultima
     * @return la geometria de la pagina elegida
     * @throws FirmaException si el archivo no existe, no es un PDF legible o la pagina no existe
     */
    public static Geometria geometria(Path pdf, int paginaSolicitada) {
        Objects.requireNonNull(pdf, "pdf es obligatorio");
        if (!Files.isRegularFile(pdf)) {
            throw new FirmaException("No existe el PDF: " + pdf);
        }
        try (PDDocument documento = Loader.loadPDF(pdf.toFile())) {
            int total = documento.getNumberOfPages();
            if (total < 1) {
                throw new FirmaException("El PDF no tiene páginas: " + pdf);
            }
            int numeroPagina = paginaSolicitada == PAGINA_ULTIMA ? total : paginaSolicitada;
            if (numeroPagina < 1 || numeroPagina > total) {
                throw new FirmaException(
                        "Página " + numeroPagina + " fuera de rango: " + pdf + " tiene " + total + " páginas");
            }
            PDPage pagina = documento.getPage(numeroPagina - 1);
            PDRectangle caja = pagina.getMediaBox();
            return new Geometria(numeroPagina, total, caja.getWidth(), caja.getHeight());
        } catch (IOException e) {
            throw new FirmaException("No se pudo leer el PDF: " + pdf, e);
        }
    }
}
