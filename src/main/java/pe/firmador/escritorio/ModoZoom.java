package pe.firmador.escritorio;

import java.util.Locale;

/**
 * Con que aumento se dibuja la pagina.
 *
 * <p>Puede ser un porcentaje fijo, elegido por el usuario, o un ajuste que se recalcula con el
 * tamano de la ventana: que la pagina quepa entera o que encaje a lo ancho. Al abrir un
 * documento se ajusta a la ventana, porque una hoja recortada por los bordes no deja ver donde
 * va a ir la firma.</p>
 */
public sealed interface ModoZoom {

    /** Por debajo de esto la pagina es ilegible; por encima, el dibujo se come la memoria. */
    double ESCALA_MINIMA = 0.1;

    /** Tope de aumento: mas que esto no aporta nada y cuesta mucho dibujarlo. */
    double ESCALA_MAXIMA = 4.0;

    /**
     * Calcula el aumento con el que dibujar la pagina.
     *
     * @param anchoPt         ancho de la pagina en puntos
     * @param altoPt          alto de la pagina en puntos
     * @param anchoDisponible ancho util del visor en pixeles, ya sin los margenes
     * @param altoDisponible  alto util del visor en pixeles, ya sin los margenes
     * @return pixeles por punto, siempre dentro de los limites razonables
     */
    double escalaPara(double anchoPt, double altoPt, double anchoDisponible, double altoDisponible);

    /**
     * @return como se llama en el desplegable
     */
    String etiqueta();

    /**
     * @return {@code true} si hay que recalcularlo cuando cambia el tamano de la ventana
     */
    boolean dependeDeLaVentana();

    /** La pagina entera cabe en el visor. Es el modo con el que se abre un documento. */
    record AjustarALaVentana() implements ModoZoom {

        @Override
        public double escalaPara(double anchoPt, double altoPt, double anchoDisponible,
                                 double altoDisponible) {
            return acotar(Math.min(anchoDisponible / anchoPt, altoDisponible / altoPt));
        }

        @Override
        public String etiqueta() {
            return "Ajustar a la ventana";
        }

        @Override
        public boolean dependeDeLaVentana() {
            return true;
        }
    }

    /** La pagina encaja a lo ancho; se desplaza verticalmente para leerla. */
    record AjustarAlAncho() implements ModoZoom {

        @Override
        public double escalaPara(double anchoPt, double altoPt, double anchoDisponible,
                                 double altoDisponible) {
            return acotar(anchoDisponible / anchoPt);
        }

        @Override
        public String etiqueta() {
            return "Ajustar al ancho";
        }

        @Override
        public boolean dependeDeLaVentana() {
            return true;
        }
    }

    /**
     * Un porcentaje fijo elegido por el usuario.
     *
     * @param escala pixeles por punto
     */
    record Fijo(double escala) implements ModoZoom {

        /** @throws IllegalArgumentException si la escala no es utilizable */
        public Fijo {
            if (!(escala > 0) || !Double.isFinite(escala)) {
                throw new IllegalArgumentException("El zoom debe ser mayor que cero: " + escala);
            }
        }

        @Override
        public double escalaPara(double anchoPt, double altoPt, double anchoDisponible,
                                 double altoDisponible) {
            return acotar(escala);
        }

        @Override
        public String etiqueta() {
            return String.format(Locale.ROOT, "%d %%", Math.round(escala * 100));
        }

        @Override
        public boolean dependeDeLaVentana() {
            return false;
        }
    }

    /** Deja la escala dentro de lo razonable, pase lo que pase con el tamano de la ventana. */
    private static double acotar(double escala) {
        if (!Double.isFinite(escala) || escala <= 0) {
            return ESCALA_MINIMA;
        }
        return Math.min(ESCALA_MAXIMA, Math.max(ESCALA_MINIMA, escala));
    }
}
