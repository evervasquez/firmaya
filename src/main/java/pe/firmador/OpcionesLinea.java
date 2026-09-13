package pe.firmador;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Argumentos de la linea de comandos ya interpretados, sin resolver todavia contra el
 * documento: la pagina puede ser {@link LectorPdf#PAGINA_ULTIMA} y las coordenadas pueden
 * venir sin fijar.
 *
 * @param pdf           documento a firmar
 * @param certificado   almacen PKCS#12
 * @param indiceMotivo  indice dentro de {@link MotivosReFirma}
 * @param pagina        pagina empezando en uno, o {@link LectorPdf#PAGINA_ULTIMA}
 * @param x             puntos desde el borde izquierdo, o {@link #COORDENADA_AUTOMATICA}
 * @param y             puntos desde el borde superior, o {@link #COORDENADA_AUTOMATICA}
 * @param nivel         nivel PAdES pedido
 */
public record OpcionesLinea(Path pdf, Path certificado, int indiceMotivo, int pagina, float x, float y,
                            NivelFirma nivel) {

    /** Marca de "calcular la coordenada por mi". Se usa NaN para no confundirla con un 0 legitimo. */
    public static final float COORDENADA_AUTOMATICA = Float.NaN;

    /** Opcion sin valor que cambia el nivel de firma a PAdES-LT. */
    public static final String OPCION_LT = "--lt";

    /** Opcion con valor que cambia el servidor de sellado de tiempo usado por {@link #OPCION_LT}. */
    public static final String OPCION_TSA = "--tsa";

    private static final String OPCION_MOTIVO = "--motivo";
    private static final String OPCION_PAGINA = "--página";
    private static final String OPCION_X = "--x";
    private static final String OPCION_Y = "--y";

    /**
     * Opciones equivalentes a las de siempre, con nivel PAdES-B.
     *
     * @param pdf          documento a firmar
     * @param certificado  almacen PKCS#12
     * @param indiceMotivo indice dentro de {@link MotivosReFirma}
     * @param pagina       pagina empezando en uno, o {@link LectorPdf#PAGINA_ULTIMA}
     * @param x            puntos desde el borde izquierdo, o {@link #COORDENADA_AUTOMATICA}
     * @param y            puntos desde el borde superior, o {@link #COORDENADA_AUTOMATICA}
     */
    public OpcionesLinea(Path pdf, Path certificado, int indiceMotivo, int pagina, float x, float y) {
        this(pdf, certificado, indiceMotivo, pagina, x, y, NivelFirma.porDefecto());
    }

    public OpcionesLinea {
        Objects.requireNonNull(pdf, "pdf es obligatorio");
        Objects.requireNonNull(certificado, "certificado es obligatorio");
        Objects.requireNonNull(nivel, "nivel es obligatorio");
        if (pagina < LectorPdf.PAGINA_ULTIMA) {
            throw new FirmaException("Página inválida: " + pagina);
        }
    }

    /**
     * Interpreta los argumentos de firma.
     *
     * @param argumentos argumentos tal como llegan a {@code main}
     * @return las opciones interpretadas
     * @throws FirmaException si faltan argumentos obligatorios, sobra alguno o un valor no es numerico
     */
    public static OpcionesLinea parsear(String[] argumentos) {
        Objects.requireNonNull(argumentos, "argumentos es obligatorio");
        if (argumentos.length < 2) {
            throw new FirmaException("Faltan argumentos: se esperan el PDF y el certificado .p12");
        }

        Path pdf = Path.of(argumentos[0]);
        Path certificado = Path.of(argumentos[1]);
        int indiceMotivo = MotivosReFirma.INDICE_POR_DEFECTO;
        int pagina = LectorPdf.PAGINA_ULTIMA;
        float x = COORDENADA_AUTOMATICA;
        float y = COORDENADA_AUTOMATICA;
        boolean largoPlazo = false;
        String tsa = NivelFirma.TSA_POR_DEFECTO;

        for (int i = 2; i < argumentos.length; i++) {
            String opcion = argumentos[i];
            if (OPCION_LT.equals(opcion)) {
                largoPlazo = true;
                continue;
            }
            String valor = valorDe(argumentos, i);
            i++;
            switch (opcion) {
                case OPCION_MOTIVO -> indiceMotivo = entero(opcion, valor);
                case OPCION_PAGINA -> pagina = entero(opcion, valor);
                case OPCION_X -> x = decimal(opcion, valor);
                case OPCION_Y -> y = decimal(opcion, valor);
                case OPCION_TSA -> tsa = valor;
                default -> throw new FirmaException("Opción desconocida: " + opcion);
            }
        }
        NivelFirma nivel = largoPlazo ? new NivelFirma.LargoPlazo(tsa) : NivelFirma.porDefecto();
        return new OpcionesLinea(pdf, certificado, indiceMotivo, pagina, x, y, nivel);
    }

    private static String valorDe(String[] argumentos, int posicionDeLaOpcion) {
        if (posicionDeLaOpcion + 1 >= argumentos.length) {
            throw new FirmaException("La opción " + argumentos[posicionDeLaOpcion] + " necesita un valor");
        }
        return argumentos[posicionDeLaOpcion + 1];
    }

    private static int entero(String opcion, String valor) {
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            throw new FirmaException("El valor de " + opcion + " debe ser un entero, se recibió: " + valor, e);
        }
    }

    private static float decimal(String opcion, String valor) {
        try {
            float numero = Float.parseFloat(valor);
            if (!Float.isFinite(numero)) {
                throw new FirmaException("El valor de " + opcion + " debe ser finito, se recibió: " + valor);
            }
            return numero;
        } catch (NumberFormatException e) {
            throw new FirmaException("El valor de " + opcion + " debe ser un número, se recibió: " + valor, e);
        }
    }
}
