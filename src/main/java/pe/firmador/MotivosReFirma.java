package pe.firmador;

import java.util.List;

/**
 * Motivos de firma que ofrece ReFirma PDF 1.6, en el mismo orden que su archivo
 * {@code default.properties} (clave {@code Reasons}).
 *
 * <p>El orden importa: el indice que el usuario pasa por linea de comandos es el mismo
 * {@code ReasonId} que usa ReFirma, de modo que un documento firmado aqui lleve el texto
 * exacto que llevaria firmado alla.</p>
 */
public final class MotivosReFirma {

    /** Motivo que ReFirma trae seleccionado de fabrica ({@code ReasonId=0}). */
    public static final int INDICE_POR_DEFECTO = 0;

    private static final List<String> MOTIVOS = List.of(
            "Soy el autor del documento",
            "En señal de conformidad",
            "Doy V° B°",
            "Por encargo",
            "Doy fe");

    private MotivosReFirma() {
    }

    /**
     * Devuelve todos los motivos disponibles, en orden de indice.
     *
     * @return lista inmutable, nunca vacia
     */
    public static List<String> todos() {
        return MOTIVOS;
    }

    /**
     * Traduce un indice de motivo a su texto.
     *
     * @param indice posicion en la lista, empezando en cero
     * @return el texto del motivo
     * @throws FirmaException si el indice esta fuera de rango
     */
    public static String porIndice(int indice) {
        if (indice < 0 || indice >= MOTIVOS.size()) {
            throw new FirmaException(
                    "Motivo fuera de rango: " + indice + ". Valores válidos: 0.." + (MOTIVOS.size() - 1));
        }
        return MOTIVOS.get(indice);
    }
}
