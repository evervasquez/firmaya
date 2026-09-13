package pe.firmador;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dibuja la representacion grafica de la firma replicando el sello de ReFirma PDF 1.6.
 *
 * <p>Medidas tomadas de un documento real firmado con ReFirma:</p>
 * <ul>
 *   <li>recuadro de 192 x 61,5 pt, que a 96 dpi son exactamente 256 x 82 px;</li>
 *   <li>el escudo ocupa los primeros 105 px de ancho (41 %) y todo el alto;</li>
 *   <li>el texto arranca en x = 105 y dispone de 151 px;</li>
 *   <li>tamano de letra 7 pt ({@code DescFontSize=7} en la configuracion de ReFirma).</li>
 * </ul>
 *
 * <p>Las medidas <strong>fisicas</strong> son las de ReFirma —192 x 61,5 pt en el PDF, con el
 * escudo ocupando el 41 % del ancho—, pero el sello se rasteriza a
 * {@value #FACTOR_RESOLUCION}x, o sea a 384 dpi en vez de los 96 dpi del original. El PDF mide
 * lo mismo; lo que cambia es el detalle interno.</p>
 *
 * <p>Antes se rasterizaba a 96 dpi con el argumento de no producir un sello «distinguible» del
 * de ReFirma. Era un mal criterio: lo que se compara de una firma es su validez criptografica y
 * sus medidas, que no cambian, mientras que un sello borroso lo nota cualquiera que reciba el
 * documento. Copiar los defectos de una herramienta no es fidelidad.</p>
 *
 * <p>Esta clase no guarda estado y es segura para varios hilos.</p>
 */
public final class SelloVisual {

    private static final Logger log = LoggerFactory.getLogger(SelloVisual.class);

    /** Ancho del recuadro en puntos PDF. */
    public static final float ANCHO_PT = 192f;

    /** Alto del recuadro en puntos PDF. */
    public static final float ALTO_PT = 61.5f;

    /**
     * Cuantas veces se multiplica la resolucion de ReFirma. A 4x el sello sale a 384 dpi: el
     * texto lo dibuja el motor de fuentes a ese tamano, asi que las letras salen limpias en
     * pantalla y en papel.
     */
    private static final int FACTOR_RESOLUCION = 4;

    /** Medidas del sello de ReFirma a 96 dpi, que siguen siendo las fisicas dentro del PDF. */
    private static final int ANCHO_BASE_PX = 256;
    private static final int ALTO_BASE_PX = 82;
    private static final int ANCHO_ESCUDO_BASE_PX = 105;

    private static final int ANCHO_PX = ANCHO_BASE_PX * FACTOR_RESOLUCION;
    private static final int ALTO_PX = ALTO_BASE_PX * FACTOR_RESOLUCION;
    private static final int ANCHO_ESCUDO_PX = ANCHO_ESCUDO_BASE_PX * FACTOR_RESOLUCION;

    private static final float TAMANO_FUENTE_PT = 7f;
    private static final float PUNTOS_A_PIXELES = 96f / 72f;

    /**
     * Tamano de letra en pixeles del lienzo. Son los 7 pt de ReFirma llevados a la resolucion a
     * la que se dibuja: el texto ocupa lo mismo dentro del sello, pero se traza con cuatro veces
     * mas detalle.
     */
    private static final float TAMANO_FUENTE_INICIAL_PX =
            TAMANO_FUENTE_PT * PUNTOS_A_PIXELES * FACTOR_RESOLUCION;

    /** Hasta aqui puede bajar la letra cuando el texto no cabe; por debajo ya no se lee. */
    private static final float TAMANO_MINIMO_FUENTE_PX = 5f * FACTOR_RESOLUCION;

    /** Cuanto se reduce la letra en cada intento. */
    private static final float PASO_REDUCCION_FUENTE_PX = 0.5f * FACTOR_RESOLUCION;

    private static final int MARGEN_IZQUIERDO_TEXTO_PX = 3 * FACTOR_RESOLUCION;
    private static final int MARGEN_DERECHO_TEXTO_PX = 2 * FACTOR_RESOLUCION;
    private static final int MARGEN_SUPERIOR_TEXTO_PX = 2 * FACTOR_RESOLUCION;

    /**
     * Escudo Nacional del Peru rasterizado del SVG de dominio publico de Wikimedia Commons
     * (1680 x 1920 px). Sustituye al bitmap de 104 x 74 px que traia ReFirma, que no daba para
     * mas de 96 dpi, por lo que se sustituyo por el Escudo Nacional vectorial.
     */
    private static final String RECURSO_ESCUDO = "/escudo-nacional.png";

    /** Grosor del recuadro rojo que enmarca la zona del escudo, como en el sello original. */
    private static final int GROSOR_MARCO_PX = 1 * FACTOR_RESOLUCION;

    /** Aire entre el marco rojo y lo que hay dentro. */
    private static final int MARGEN_INTERIOR_PX = 2 * FACTOR_RESOLUCION;

    /** Separacion entre el escudo y el rotulo «FIRMA DIGITAL». */
    private static final int SEPARACION_ROTULO_PX = 3 * FACTOR_RESOLUCION;

    /**
     * Cuanto del alto libre ocupa el escudo. No llena el hueco entero a proposito: dejarle algo
     * de aire da al rotulo el ancho que necesita para leerse, que era el desequilibrio de partida.
     */
    private static final double PROPORCION_ALTO_ESCUDO = 0.88;

    /** El rotulo del sello, en dos lineas como en ReFirma. */
    private static final List<String> ROTULO = List.of("FIRMA", "DIGITAL");

    /** Maquina de escribir, como el rotulo original; el respaldo es la monoespaciada del sistema. */
    private static final List<String> FUENTES_DEL_ROTULO = List.of("Courier New", "Courier");

    private static final Color ROJO_DEL_MARCO = new Color(0xCC, 0x00, 0x00);

    /** ReFirma corre sobre Windows con Arial; estas son las equivalencias razonables. */
    private static final List<String> FUENTES_PREFERIDAS = List.of("Arial", "Helvetica", "Liberation Sans");

    /**
     * Genera el PNG del sello.
     *
     * <p>El tamano es siempre el de ReFirma y no se puede cambiar: estirar el sello descoloca
     * su contenido y acaba cortando el texto, que en un documento con valor legal es
     * inaceptable. Lo que el usuario elige es donde ponerlo, no cuanto mide.</p>
     *
     * @param texto contenido a dibujar
     * @return los bytes del PNG, de 1024 x 328 px para un recuadro de 192 x 61,5 pt
     * @throws FirmaException si falta el recurso del escudo o falla la codificacion del PNG
     */
    public byte[] generar(TextoSello texto) {
        Objects.requireNonNull(texto, "texto es obligatorio");

        BufferedImage imagen = new BufferedImage(ANCHO_PX, ALTO_PX, BufferedImage.TYPE_INT_RGB);
        Graphics2D lienzo = imagen.createGraphics();
        try {
            prepararLienzo(lienzo);
            dibujarEscudo(lienzo);
            dibujarTexto(lienzo, texto);
        } finally {
            lienzo.dispose();
        }
        return codificarPng(imagen);
    }

    private void prepararLienzo(Graphics2D lienzo) {
        lienzo.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        lienzo.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        lienzo.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        lienzo.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        lienzo.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        lienzo.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        lienzo.setColor(Color.WHITE);
        lienzo.fillRect(0, 0, ANCHO_PX, ALTO_PX);
    }

    /**
     * Dibuja la zona izquierda del sello: el marco rojo, el Escudo Nacional y el rotulo
     * «FIRMA DIGITAL» a su derecha, centrado verticalmente respecto al escudo.
     */
    private void dibujarEscudo(Graphics2D lienzo) {
        BufferedImage escudo = leerEscudo();

        lienzo.setColor(ROJO_DEL_MARCO);
        lienzo.setStroke(new BasicStroke(GROSOR_MARCO_PX));
        double mitad = GROSOR_MARCO_PX / 2d;
        lienzo.draw(new Rectangle2D.Double(mitad, mitad,
                ANCHO_ESCUDO_PX - GROSOR_MARCO_PX, ALTO_PX - GROSOR_MARCO_PX));

        int borde = GROSOR_MARCO_PX + MARGEN_INTERIOR_PX;
        int altoLibre = ALTO_PX - borde * 2;
        int altoEscudo = (int) Math.round(altoLibre * PROPORCION_ALTO_ESCUDO);
        int anchoEscudo = (int) Math.round(altoEscudo * proporcionDe(escudo));

        int xEscudo = borde;
        int yEscudo = (ALTO_PX - altoEscudo) / 2;
        lienzo.drawImage(escudo, xEscudo, yEscudo, anchoEscudo, altoEscudo, null);

        int xRotulo = xEscudo + anchoEscudo + SEPARACION_ROTULO_PX;
        int anchoRotulo = ANCHO_ESCUDO_PX - borde - xRotulo;
        dibujarRotulo(lienzo, xRotulo, anchoRotulo, yEscudo, altoEscudo);
    }

    /**
     * Escribe «FIRMA DIGITAL» en dos lineas, tan grande como permita el hueco, y centrado
     * verticalmente respecto al escudo: es la disposicion que pidio el usuario.
     */
    private void dibujarRotulo(Graphics2D lienzo, int x, int anchoDisponible, int yEscudo,
                               int altoEscudo) {
        if (anchoDisponible <= 0) {
            log.warn("No queda espacio para el rótulo del sello; se dibuja solo el escudo");
            return;
        }
        Font fuente = fuenteDelRotulo(lienzo, anchoDisponible, altoEscudo);
        lienzo.setFont(fuente);
        lienzo.setColor(Color.BLACK);

        FontMetrics metrica = lienzo.getFontMetrics();
        int altoLinea = metrica.getHeight();
        int altoTotal = altoLinea * ROTULO.size();
        int lineaBase = yEscudo + (altoEscudo - altoTotal) / 2 + metrica.getAscent();
        for (String linea : ROTULO) {
            lienzo.drawString(linea, x, lineaBase);
            lineaBase += altoLinea;
        }
    }

    /** El mayor tamano con el que las dos lineas del rotulo caben en el hueco que les toca. */
    private Font fuenteDelRotulo(Graphics2D lienzo, int anchoDisponible, int altoDisponible) {
        Font fuente = fuenteMonoespaciada(TAMANO_FUENTE_INICIAL_PX);
        float tamano = TAMANO_FUENTE_INICIAL_PX;
        while (tamano < ANCHO_ESCUDO_PX) {
            Font mayor = fuenteMonoespaciada(tamano + PASO_REDUCCION_FUENTE_PX);
            FontMetrics metrica = lienzo.getFontMetrics(mayor);
            boolean cabeDeAncho = ROTULO.stream()
                    .allMatch(linea -> metrica.stringWidth(linea) <= anchoDisponible);
            if (!cabeDeAncho || metrica.getHeight() * ROTULO.size() > altoDisponible) {
                break;
            }
            tamano += PASO_REDUCCION_FUENTE_PX;
            fuente = mayor;
        }
        return fuente;
    }

    private Font fuenteMonoespaciada(float tamanoEnPixeles) {
        for (String nombre : FUENTES_DEL_ROTULO) {
            if (estaInstalada(nombre)) {
                return new Font(nombre, Font.BOLD, 1).deriveFont(tamanoEnPixeles);
            }
        }
        return new Font(Font.MONOSPACED, Font.BOLD, 1).deriveFont(tamanoEnPixeles);
    }

    private double proporcionDe(BufferedImage imagen) {
        return imagen.getWidth() / (double) imagen.getHeight();
    }

    private BufferedImage leerEscudo() {
        try (InputStream entrada = SelloVisual.class.getResourceAsStream(RECURSO_ESCUDO)) {
            if (entrada == null) {
                throw new FirmaException("Falta el recurso " + RECURSO_ESCUDO + " dentro del JAR");
            }
            BufferedImage escudo = ImageIO.read(entrada);
            if (escudo == null) {
                throw new FirmaException("El recurso " + RECURSO_ESCUDO + " no es una imagen legible");
            }
            return escudo;
        } catch (IOException e) {
            throw new FirmaException("No se pudo leer el recurso " + RECURSO_ESCUDO, e);
        }
    }

    /**
     * Escribe el texto dentro del hueco que deja el escudo.
     *
     * <p>Con el tamano fijo y un nombre de largo normal, el texto entra con la letra de 7 pt de
     * ReFirma. Si el titular tuviera un nombre tan largo que no cupiera, la letra se reduce por
     * pasos hasta que quepa entera: mas pequena se lee peor, pero cortada no se lee, y un sello
     * con el nombre a medias no vale.</p>
     */
    private void dibujarTexto(Graphics2D lienzo, TextoSello texto) {
        int origenX = ANCHO_ESCUDO_PX + MARGEN_IZQUIERDO_TEXTO_PX;
        int anchoDisponible = ANCHO_PX - origenX - MARGEN_DERECHO_TEXTO_PX;
        int altoDisponible = ALTO_PX - MARGEN_SUPERIOR_TEXTO_PX;

        lienzo.setColor(Color.BLACK);
        Composicion composicion = componer(lienzo, texto, anchoDisponible, altoDisponible);

        lienzo.setFont(composicion.fuente());
        int altoLinea = composicion.metrica().getHeight();
        int lineaBase = MARGEN_SUPERIOR_TEXTO_PX + composicion.metrica().getAscent();
        for (String linea : composicion.lineas()) {
            lienzo.drawString(linea, origenX, lineaBase);
            lineaBase += altoLinea;
        }
    }

    /**
     * Busca el mayor tamano de letra, empezando por el de ReFirma, con el que el texto entero
     * cabe en el hueco. Solo baja cuando hace falta.
     */
    private Composicion componer(Graphics2D lienzo, TextoSello texto, int anchoDisponible,
                                 int altoDisponible) {
        float tamano = TAMANO_FUENTE_INICIAL_PX;
        Composicion ultima = componerCon(lienzo, texto, anchoDisponible, tamano);
        while (!ultima.cabeEn(altoDisponible) && tamano > TAMANO_MINIMO_FUENTE_PX) {
            tamano = Math.max(TAMANO_MINIMO_FUENTE_PX, tamano - PASO_REDUCCION_FUENTE_PX);
            ultima = componerCon(lienzo, texto, anchoDisponible, tamano);
        }
        if (!ultima.cabeEn(altoDisponible)) {
            log.warn("El texto del sello no cabe ni con la letra más pequeña; se dibuja lo que entra");
        }
        return ultima;
    }

    private Composicion componerCon(Graphics2D lienzo, TextoSello texto, int anchoDisponible,
                                    float tamano) {
        Font fuente = fuenteDelSello(tamano);
        FontMetrics metrica = lienzo.getFontMetrics(fuente);
        return new Composicion(fuente, metrica, partirEnLineas(texto.bloques(), metrica, anchoDisponible));
    }

    /** Una forma de escribir el texto: con que letra, con que metrica y partido en que lineas. */
    private record Composicion(Font fuente, FontMetrics metrica, List<String> lineas) {

        boolean cabeEn(int altoDisponible) {
            return lineas.size() * metrica.getHeight() <= altoDisponible;
        }
    }

    private Font fuenteDelSello(float tamanoEnPixeles) {
        for (String nombre : FUENTES_PREFERIDAS) {
            if (estaInstalada(nombre)) {
                return new Font(nombre, Font.PLAIN, 1).deriveFont(tamanoEnPixeles);
            }
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, 1).deriveFont(tamanoEnPixeles);
    }

    private boolean estaInstalada(String nombre) {
        String[] disponibles = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String disponible : disponibles) {
            if (disponible.equalsIgnoreCase(nombre)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parte cada bloque en las lineas que quepan en el ancho disponible, cortando por palabras.
     * Una palabra que por si sola no cabe se corta por caracteres para no desbordar el recuadro.
     */
    private List<String> partirEnLineas(List<String> bloques, FontMetrics metrica, int anchoMaximo) {
        List<String> lineas = new ArrayList<>();
        for (String bloque : bloques) {
            StringBuilder actual = new StringBuilder();
            for (String palabra : bloque.split(" ")) {
                agregarPalabra(lineas, actual, palabra, metrica, anchoMaximo);
            }
            if (actual.length() > 0) {
                lineas.add(actual.toString());
            }
        }
        return List.copyOf(lineas);
    }

    private void agregarPalabra(List<String> lineas, StringBuilder actual, String palabra,
                                FontMetrics metrica, int anchoMaximo) {
        if (actual.length() == 0) {
            actual.append(palabra);
            partirPalabraLarga(lineas, actual, metrica, anchoMaximo);
            return;
        }
        String tentativa = actual + " " + palabra;
        if (metrica.stringWidth(tentativa) <= anchoMaximo) {
            actual.setLength(0);
            actual.append(tentativa);
            return;
        }
        lineas.add(actual.toString());
        actual.setLength(0);
        actual.append(palabra);
        partirPalabraLarga(lineas, actual, metrica, anchoMaximo);
    }

    private void partirPalabraLarga(List<String> lineas, StringBuilder actual,
                                    FontMetrics metrica, int anchoMaximo) {
        while (metrica.stringWidth(actual.toString()) > anchoMaximo && actual.length() > 1) {
            int corte = actual.length() - 1;
            while (corte > 1 && metrica.stringWidth(actual.substring(0, corte)) > anchoMaximo) {
                corte--;
            }
            lineas.add(actual.substring(0, corte));
            actual.delete(0, corte);
        }
    }

    private byte[] codificarPng(BufferedImage imagen) {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            if (!ImageIO.write(imagen, "png", salida)) {
                throw new FirmaException("Esta JVM no tiene codificador PNG disponible");
            }
            return salida.toByteArray();
        } catch (IOException e) {
            throw new FirmaException("No se pudo codificar el sello como PNG", e);
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "SelloVisual[%.1f x %.1f pt, %d x %d px, %dx]",
                ANCHO_PT, ALTO_PT, ANCHO_PX, ALTO_PX, FACTOR_RESOLUCION);
    }

}
