package pe.firmador;

import java.io.Console;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Punto de entrada de la herramienta.
 *
 * <p>Uso:</p>
 * <pre>
 *   java -jar firmador.jar &lt;pdf&gt; &lt;p12&gt; [--motivo N] [--pagina N] [--x N] [--y N]
 *   java -jar firmador.jar --verificar &lt;pdf&gt;
 * </pre>
 *
 * <p>La contrasena del certificado se pide siempre por consola: no se acepta como argumento
 * ni por variable de entorno, porque ambos quedan registrados (historial del shell, lista de
 * procesos, volcados de entorno).</p>
 */
public final class Firmador {

    private static final Logger log = LoggerFactory.getLogger(Firmador.class);

    /** Salida para el usuario. No es registro: es el resultado que la herramienta reporta. */
    private static final PrintStream SALIDA = System.out;

    private static final String OPCION_VERIFICAR = "--verificar";
    private static final String OPCION_AYUDA = "--ayuda";

    /** Margen por defecto del sello respecto del borde de la pagina, en puntos. */
    private static final float MARGEN_PT = 20f;

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ssZ", Locale.ROOT).withZone(ZoneOffset.UTC);

    private static final int CODIGO_ERROR_CONOCIDO = 1;
    private static final int CODIGO_ERROR_INESPERADO = 2;

    private Firmador() {
    }

    /**
     * Ejecuta la herramienta en modo linea de comandos.
     *
     * @param argumentos argumentos ya sin la opcion de interfaz grafica
     */
    public static void main(String[] argumentos) {
        // Sin esto macOS levanta un icono en el Dock al inicializar AWT para dibujar el sello.
        // Se hace aqui y no en SelloVisual porque la interfaz grafica SI necesita AWT visible.
        System.setProperty("java.awt.headless", "true");
        try {
            ejecutar(argumentos);
        } catch (FirmaException e) {
            SALIDA.flush();
            System.err.println("Error: " + e.getMessage());
            log.debug("Detalle del fallo", e);
            System.exit(CODIGO_ERROR_CONOCIDO);
        } catch (RuntimeException e) {
            // Borde exterior de la aplicacion: aqui no puede escaparse nada sin explicacion.
            SALIDA.flush();
            System.err.println("Error inesperado: " + e);
            log.error("Fallo no contemplado", e);
            System.exit(CODIGO_ERROR_INESPERADO);
        }
    }

    private static void ejecutar(String[] argumentos) {
        if (argumentos.length == 0 || OPCION_AYUDA.equals(argumentos[0]) || "-h".equals(argumentos[0])) {
            imprimirUso();
            return;
        }
        if (OPCION_VERIFICAR.equals(argumentos[0])) {
            if (argumentos.length != 2) {
                throw new FirmaException("Uso: --verificar <pdf>");
            }
            imprimirFirmas(Path.of(argumentos[1]));
            return;
        }
        firmar(OpcionesLinea.parsear(argumentos));
    }

    private static void firmar(OpcionesLinea opciones) {
        LectorPdf.Geometria geometria = LectorPdf.geometria(opciones.pdf(), opciones.pagina());
        SolicitudFirma solicitud = new SolicitudFirma(
                opciones.pdf().toAbsolutePath(),
                new OrigenClave.Archivo(opciones.certificado().toAbsolutePath()),
                SolicitudFirma.rutaFirmada(opciones.pdf()),
                MotivosReFirma.porIndice(opciones.indiceMotivo()),
                geometria.numeroPagina(),
                coordenadaX(opciones),
                coordenadaY(opciones, geometria),
                opciones.nivel());

        char[] contrasena = pedirContrasena();
        Path firmado;
        try {
            firmado = new ServicioFirmaPades(Clock.systemUTC()).firmar(solicitud, contrasena);
        } finally {
            Arrays.fill(contrasena, '\0');
        }

        SALIDA.println("Documento firmado: " + firmado);
        imprimirFirmas(firmado);
    }

    private static float coordenadaX(OpcionesLinea opciones) {
        return Float.isNaN(opciones.x()) ? MARGEN_PT : opciones.x();
    }

    /** Por defecto el sello va abajo a la izquierda; DSS mide la Y desde el borde superior. */
    private static float coordenadaY(OpcionesLinea opciones, LectorPdf.Geometria geometria) {
        if (!Float.isNaN(opciones.y())) {
            return opciones.y();
        }
        float automatica = geometria.altoPt() - SelloVisual.ALTO_PT - MARGEN_PT;
        if (automatica < 0) {
            throw new FirmaException("La página " + geometria.numeroPagina()
                    + " es demasiado pequeña para el sello: mide " + geometria.altoPt() + " pt de alto");
        }
        return automatica;
    }

    private static char[] pedirContrasena() {
        Console consola = System.console();
        if (consola == null) {
            throw new FirmaException("No hay consola interactiva disponible. "
                    + "Ejecute la herramienta desde una terminal: la contraseña no se acepta por argumento.");
        }
        char[] contrasena = consola.readPassword("Contraseña del certificado: ");
        if (contrasena == null || contrasena.length == 0) {
            throw new FirmaException("No se ingresó la contraseña del certificado");
        }
        return contrasena;
    }

    private static void imprimirFirmas(Path pdf) {
        List<DatosFirma> firmas = new VerificadorPdf().verificar(pdf);
        if (firmas.isEmpty()) {
            SALIDA.println("El documento no tiene firmas: " + pdf);
            return;
        }
        int numero = 1;
        for (DatosFirma firma : firmas) {
            SALIDA.println();
            SALIDA.println("Firma " + numero + " de " + firmas.size());
            SALIDA.println("  Titular   : " + firma.titular());
            SALIDA.println("  Motivo    : " + firma.motivo());
            SALIDA.println("  Fecha     : " + FORMATO_FECHA.format(firma.fecha()));
            SALIDA.println("  Nivel     : " + firma.nivel());
            SALIDA.println("  Filter    : " + firma.filtro());
            SALIDA.println("  SubFilter : " + firma.subFiltro());
            SALIDA.println("  Algoritmo : " + firma.algoritmo());
            SALIDA.println("  Integridad: " + (firma.integra() ? "correcta" : "ALTERADA"));
            numero++;
        }
        SALIDA.println();
        SALIDA.println("La cadena de confianza y la vigencia del certificado las decide "
                + "el validador oficial de RENIEC; esta herramienta no las comprueba.");
    }

    private static void imprimirUso() {
        SALIDA.println("Firmaya - firma PAdES-B compatible con ReFirma PDF 1.6 de RENIEC");
        SALIDA.println();
        SALIDA.println("Uso:");
        SALIDA.println("  java -jar firmador.jar                    abre la interfaz gráfica");
        SALIDA.println("  java -jar firmador.jar <pdf> <p12> [--motivo N] [--página N] [--x N] [--y N] "
                + "[" + OpcionesLinea.OPCION_LT + "] [" + OpcionesLinea.OPCION_TSA + " URL]");
        SALIDA.println("  java -jar firmador.jar --verificar <pdf>");
        SALIDA.println();
        SALIDA.println("Opciones:");
        SALIDA.println("  --motivo N   índice del motivo de firma (por defecto "
                + MotivosReFirma.INDICE_POR_DEFECTO + ")");
        SALIDA.println("  --página N   página donde va el sello, empezando en 1 (por defecto la última)");
        SALIDA.println("  --x N        puntos desde el borde izquierdo (por defecto " + MARGEN_PT + ")");
        SALIDA.println("  --y N        puntos desde el borde superior (por defecto, abajo a la izquierda)");
        SALIDA.println("  " + OpcionesLinea.OPCION_LT + "         firma en PAdES-LT: incrusta sello de tiempo y "
                + "prueba de revocación");
        SALIDA.println("  " + OpcionesLinea.OPCION_TSA + " URL   servidor de sellado de tiempo para "
                + OpcionesLinea.OPCION_LT + " (por defecto " + NivelFirma.TSA_POR_DEFECTO + ")");
        SALIDA.println();
        SALIDA.println("Motivos disponibles:");
        int indice = 0;
        for (String motivo : MotivosReFirma.todos()) {
            SALIDA.println("  " + indice + " = " + motivo);
            indice++;
        }
        SALIDA.println();
        SALIDA.println("La contraseña del certificado se pide por consola, nunca como argumento.");
        SALIDA.println("El archivo firmado se guarda como <nombre>" + SolicitudFirma.SUFIJO_FIRMADO + ".pdf");
    }
}
