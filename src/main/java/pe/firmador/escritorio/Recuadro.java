package pe.firmador.escritorio;

import java.util.Locale;

import pe.firmador.SelloVisual;

/**
 * Donde va el sello dentro de una pagina, en puntos PDF.
 *
 * <p>El origen es la esquina superior izquierda de la pagina, que es el sistema que usa DSS
 * para la firma visible en PAdES. Guardar la posicion en puntos y no en pixeles de pantalla
 * permite cambiar el zoom sin recalcular nada.</p>
 *
 * <p><strong>El sello mide siempre lo mismo</strong>: {@value SelloVisual#ANCHO_PT} x
 * {@value SelloVisual#ALTO_PT} pt, el tamano exacto de ReFirma. Por eso aqui solo hay posicion:
 * un recuadro con otras medidas seria un estado que la aplicacion no debe poder representar,
 * porque estirar el sello descoloca su texto. Lo unico que el usuario elige es donde ponerlo,
 * igual que en ReFirma.</p>
 *
 * <p><strong>Tampoco guarda en que pagina esta</strong>: acompana al usuario mientras navega y
 * la firma acaba en la pagina que tenga delante, como la ubicacion «Actual» de ReFirma.</p>
 *
 * @param x distancia desde el borde izquierdo de la pagina
 * @param y distancia desde el borde superior de la pagina
 */
public record Recuadro(double x, double y) {

    /** Ancho del sello en puntos PDF; no se puede cambiar. */
    public static final double ANCHO = SelloVisual.ANCHO_PT;

    /** Alto del sello en puntos PDF; no se puede cambiar. */
    public static final double ALTO = SelloVisual.ALTO_PT;

    /**
     * @throws IllegalArgumentException si la posicion no es un numero utilizable
     */
    public Recuadro {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("La posición del sello no es un número: " + x + ", " + y);
        }
    }

    /**
     * @return el ancho del sello, siempre el mismo
     */
    public double ancho() {
        return ANCHO;
    }

    /**
     * @return el alto del sello, siempre el mismo
     */
    public double alto() {
        return ALTO;
    }

    /**
     * Coloca el sello centrado en un punto, que es lo que espera quien hace clic sobre la pagina.
     *
     * @param centroX punto en el eje horizontal, en puntos PDF
     * @param centroY punto en el eje vertical, en puntos PDF
     * @return el recuadro con su centro en ese punto
     */
    public static Recuadro centradoEn(double centroX, double centroY) {
        return new Recuadro(centroX - ANCHO / 2, centroY - ALTO / 2);
    }

    /**
     * Recuadro abajo a la izquierda de la pagina, que es donde ReFirma deja el sello por defecto.
     *
     * @param altoPt alto de la pagina en puntos
     * @param margen separacion respecto de los bordes
     * @return el recuadro colocado
     */
    public static Recuadro abajoIzquierda(double altoPt, double margen) {
        return new Recuadro(margen, Math.max(0, altoPt - ALTO - margen));
    }

    /**
     * Devuelve el mismo sello empujado dentro de los limites de la pagina.
     *
     * <p>Solo se mueve: el tamano no se toca nunca. En una pagina mas estrecha que el propio
     * sello queda pegado al borde superior izquierdo, que es lo unico sensato sin deformarlo.</p>
     *
     * @param anchoPagina ancho de la pagina en puntos
     * @param altoPagina  alto de la pagina en puntos
     * @return un recuadro dentro de la pagina
     */
    public Recuadro dentroDe(double anchoPagina, double altoPagina) {
        if (!(anchoPagina > 0) || !(altoPagina > 0)) {
            return this;
        }
        double xUtil = Math.min(Math.max(0, x), Math.max(0, anchoPagina - ANCHO));
        double yUtil = Math.min(Math.max(0, y), Math.max(0, altoPagina - ALTO));
        return new Recuadro(xUtil, yUtil);
    }

    /**
     * @return descripcion breve para la interfaz, con una decimal
     */
    public String descripcion() {
        return String.format(Locale.ROOT, "%.1f x %.1f pt en (%.1f, %.1f)", ANCHO, ALTO, x, y);
    }
}
