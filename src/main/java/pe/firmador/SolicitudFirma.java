package pe.firmador;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Todo lo que hace falta para firmar un documento, ya resuelto y validado.
 *
 * <p>Las coordenadas se miden desde la esquina superior izquierda de la pagina, que es
 * el sistema que usa DSS para la firma visible en PAdES.</p>
 *
 * @param pdfOrigen   documento a firmar
 * @param origen      de donde sale la clave del firmante: un archivo .p12 o el llavero
 * @param destino     archivo a escribir; no debe existir
 * @param motivo      texto del motivo, ya traducido desde el indice de ReFirma
 * @param pagina      pagina donde va el sello, empezando en uno
 * @param x           distancia en puntos desde el borde izquierdo
 * @param y           distancia en puntos desde el borde superior
 * @param nivel       nivel PAdES con el que se firma
 */
public record SolicitudFirma(Path pdfOrigen, OrigenClave origen, Path destino,
                             String motivo, int pagina, float x, float y, NivelFirma nivel) {

    /** Sufijo que ReFirma agrega al nombre del archivo firmado ({@code SignedPdfSufix=[R]}). */
    public static final String SUFIJO_FIRMADO = "[R]";

    private static final String EXTENSION_PDF = ".pdf";

    /** Reconoce el sufijo de firma ya presente, con o sin numero: {@code [R]}, {@code [R2]}… */
    private static final Pattern SUFIJO_YA_PUESTO = Pattern.compile("\\[R(\\d*)]$");

    /**
     * Calcula el nombre del archivo firmado con la convencion de ReFirma:
     * {@code documento.pdf} pasa a ser {@code documento[R].pdf}, en la misma carpeta.
     *
     * <p>Cuando se vuelve a firmar un documento ya firmado, el sufijo no se repite: se numera.
     * Asi {@code documento[R].pdf} pasa a {@code documento[R2].pdf} y luego a
     * {@code documento[R3].pdf}, en vez de encadenar {@code documento[R][R].pdf}. El numero dice
     * cuantas veces paso por el firmador, y el nombre no crece sin control.</p>
     *
     * @param original ruta del documento sin firmar
     * @return la ruta del documento firmado
     */
    public static Path rutaFirmada(Path original) {
        Objects.requireNonNull(original, "original es obligatorio");
        Path carpeta = original.toAbsolutePath().getParent();
        if (carpeta == null) {
            throw new FirmaException("No se pudo determinar la carpeta de salida para: " + original);
        }
        return rutaFirmadaEn(original, carpeta);
    }

    /**
     * Calcula el nombre del archivo firmado dentro de otra carpeta.
     *
     * <p>El nombre sale siempre del documento de origen, asi que {@code acta.pdf} produce
     * {@code acta[R].pdf} tambien en la carpeta elegida. Si ahi ya hay un archivo con ese
     * nombre, se sigue numerando —{@code acta[R2].pdf}, {@code acta[R3].pdf}— hasta dar con uno
     * libre: <strong>nunca se sobrescribe un documento firmado</strong>, que es la regla que no
     * se puede romper. El numero indica, por tanto, la vuelta del firmador y no necesariamente
     * cuantas firmas lleva el PDF: las firmas que tiene se leen del propio documento y se
     * muestran en el panel.</p>
     *
     * @param original ruta del documento sin firmar
     * @param carpeta  carpeta donde guardar el resultado
     * @return la primera ruta libre dentro de esa carpeta
     */
    public static Path rutaFirmadaEn(Path original, Path carpeta) {
        Objects.requireNonNull(original, "original es obligatorio");
        Objects.requireNonNull(carpeta, "carpeta es obligatoria");

        String nombre = original.getFileName().toString();
        String base = nombre.toLowerCase(Locale.ROOT).endsWith(EXTENSION_PDF)
                ? nombre.substring(0, nombre.length() - EXTENSION_PDF.length())
                : nombre;

        String candidato = siguienteSufijo(base);
        Path destino = carpeta.resolve(candidato + EXTENSION_PDF);
        while (Files.exists(destino)) {
            candidato = siguienteSufijo(candidato);
            destino = carpeta.resolve(candidato + EXTENSION_PDF);
        }
        return destino;
    }

    private static String siguienteSufijo(String base) {
        Matcher yaFirmado = SUFIJO_YA_PUESTO.matcher(base);
        if (!yaFirmado.find()) {
            return base + SUFIJO_FIRMADO;
        }
        String numero = yaFirmado.group(1);
        int vuelta = numero.isEmpty() ? 1 : Integer.parseInt(numero);
        return base.substring(0, yaFirmado.start()) + "[R" + (vuelta + 1) + "]";
    }

    /**
     * Solicitud con nivel PAdES-B, que es el comportamiento comprobado contra el validador
     * oficial. El sello mide siempre lo mismo: {@link SelloVisual#ANCHO_PT} x
     * {@link SelloVisual#ALTO_PT} pt.
     *
     * @param pdfOrigen   documento a firmar
     * @param origen      de donde sale la clave del firmante
     * @param destino     archivo a escribir; no debe existir
     * @param motivo      texto del motivo
     * @param pagina      pagina donde va el sello, empezando en uno
     * @param x           distancia en puntos desde el borde izquierdo
     * @param y           distancia en puntos desde el borde superior
     */
    public SolicitudFirma(Path pdfOrigen, OrigenClave origen, Path destino,
                          String motivo, int pagina, float x, float y) {
        this(pdfOrigen, origen, destino, motivo, pagina, x, y, NivelFirma.porDefecto());
    }

    public SolicitudFirma {
        Objects.requireNonNull(pdfOrigen, "pdfOrigen es obligatorio");
        Objects.requireNonNull(origen, "origen es obligatorio");
        Objects.requireNonNull(destino, "destino es obligatorio");
        Objects.requireNonNull(motivo, "motivo es obligatorio");
        Objects.requireNonNull(nivel, "nivel es obligatorio");

        if (!Files.isRegularFile(pdfOrigen)) {
            throw new FirmaException("No existe el PDF: " + pdfOrigen);
        }
        if (origen instanceof OrigenClave.Archivo archivo && !Files.isRegularFile(archivo.ruta())) {
            throw new FirmaException("No existe el certificado: " + archivo.ruta());
        }
        if (Files.exists(destino)) {
            throw new FirmaException("El archivo de salida ya existe y no se sobrescribe: " + destino);
        }
        if (pagina < 1) {
            throw new IllegalArgumentException("La página empieza en uno, se recibió: " + pagina);
        }
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("Coordenadas del sello no finitas: x=" + x + ", y=" + y);
        }
    }
}
