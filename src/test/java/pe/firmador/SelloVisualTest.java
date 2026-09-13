package pe.firmador;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class SelloVisualTest {

    /** El sello se rasteriza a 4x: 1024 x 328 px para el recuadro de 192 x 61,5 pt de ReFirma. */
    private static final int ANCHO_ESPERADO_PX = 1024;
    private static final int ALTO_ESPERADO_PX = 328;

    /** El escudo ocupa el 41 % del ancho, como en ReFirma. */
    private static final int ANCHO_ESCUDO_PX = 420;

    /**
     * Proporción del Escudo Nacional vectorial (1680 x 1920 px): es la que debe conservar al
     * dibujarse. El SVG trae algo de aire transparente a los lados, de ahí la tolerancia.
     */
    private static final double PROPORCION_DEL_ESCUDO = 1680 / 1920d;

    /** Zona interior de la franja del escudo, por dentro del marco rojo. */
    private static final int INTERIOR_DESDE = 8;
    private static final int INTERIOR_HASTA_Y = 320;

    private static final Instant INSTANTE_DE_REFERENCIA = Instant.parse("2024-02-22T02:40:20Z");
    private static final String TITULAR_DE_REFERENCIA = "PEREZ GARCIA JUAN CARLOS";
    private static final String MOTIVO_DE_REFERENCIA = "En señal de conformidad";

    /** Donde se deja la muestra para compararla a ojo con assets/sello_referencia.png. */
    private static final Path MUESTRA = Path.of("target", "sello-generado.png");

    private final SelloVisual selloVisual = new SelloVisual();

    @Test
    void generaUnPngDeDoscientosCincuentaySeisPorOchentaYDos() throws Exception {
        byte[] png = selloVisual.generar(
                new TextoSello(TITULAR_DE_REFERENCIA, MOTIVO_DE_REFERENCIA, INSTANTE_DE_REFERENCIA));

        BufferedImage imagen = leer(png);
        assertThat(imagen.getWidth()).isEqualTo(ANCHO_ESPERADO_PX);
        assertThat(imagen.getHeight()).isEqualTo(ALTO_ESPERADO_PX);
    }

    @Test
    void laProporcionDelPngCoincideConElRecuadroDeReFirma() throws Exception {
        byte[] png = selloVisual.generar(
                new TextoSello(TITULAR_DE_REFERENCIA, MOTIVO_DE_REFERENCIA, INSTANTE_DE_REFERENCIA));

        BufferedImage imagen = leer(png);
        float proporcionImagen = (float) imagen.getWidth() / imagen.getHeight();
        float proporcionRecuadro = SelloVisual.ANCHO_PT / SelloVisual.ALTO_PT;

        assertThat(proporcionImagen).isCloseTo(proporcionRecuadro, org.assertj.core.data.Offset.offset(0.01f));
    }

    @Test
    void dibujaElEscudoEnLaFranjaIzquierdaYDejaLaDerechaParaElTexto() throws Exception {
        byte[] png = selloVisual.generar(
                new TextoSello(TITULAR_DE_REFERENCIA, MOTIVO_DE_REFERENCIA, INSTANTE_DE_REFERENCIA));

        BufferedImage imagen = leer(png);
        assertThat(hayTinta(imagen, 0, ANCHO_ESCUDO_PX)).as("franja del escudo").isTrue();
        assertThat(hayTinta(imagen, ANCHO_ESCUDO_PX, ANCHO_ESPERADO_PX)).as("franja del texto").isTrue();
    }

    @Test
    void conUnNombreMuyLargoReduceLaLetraEnVezDeCortarElTexto() throws Exception {
        // Si el texto desbordara, la última línea saldría pegada al borde o directamente
        // recortada. Que quede margen blanco abajo es la señal de que entró entero.
        String nombreInterminable = "MARIA DE LOS ANGELES BUSTAMANTE Y RIVERA DE LA TORRE "
                + "VILLANUEVA DEL CASTILLO";

        byte[] png = selloVisual.generar(
                new TextoSello(nombreInterminable, MOTIVO_DE_REFERENCIA, INSTANTE_DE_REFERENCIA));

        BufferedImage imagen = leer(png);
        assertThat(imagen.getWidth()).isEqualTo(ANCHO_ESPERADO_PX);
        assertThat(imagen.getHeight()).isEqualTo(ALTO_ESPERADO_PX);
        assertThat(ultimaFilaConTinta(imagen, ANCHO_ESCUDO_PX, ANCHO_ESPERADO_PX))
                .as("última fila con texto, dentro del sello")
                .isLessThan(imagen.getHeight() - 1);
    }

    @Test
    void elSelloMideSiempreLoMismoSeaCualSeaElTexto() throws Exception {
        byte[] corto = selloVisual.generar(new TextoSello("ANA PAZ", MOTIVO_DE_REFERENCIA,
                INSTANTE_DE_REFERENCIA));
        byte[] largo = selloVisual.generar(new TextoSello(TITULAR_DE_REFERENCIA + " " + TITULAR_DE_REFERENCIA,
                MOTIVO_DE_REFERENCIA, INSTANTE_DE_REFERENCIA));

        assertThat(leer(corto).getWidth()).isEqualTo(leer(largo).getWidth()).isEqualTo(ANCHO_ESPERADO_PX);
        assertThat(leer(corto).getHeight()).isEqualTo(leer(largo).getHeight()).isEqualTo(ALTO_ESPERADO_PX);
    }

    @Test
    void dejaUnaMuestraEnTargetParaCompararConElOriginal() throws Exception {
        byte[] png = selloVisual.generar(
                new TextoSello(TITULAR_DE_REFERENCIA, MOTIVO_DE_REFERENCIA, INSTANTE_DE_REFERENCIA));

        Files.createDirectories(MUESTRA.getParent());
        Files.write(MUESTRA, png);

        assertThat(MUESTRA).exists();
    }

    private BufferedImage leer(byte[] png) throws Exception {
        try (ByteArrayInputStream entrada = new ByteArrayInputStream(png)) {
            return ImageIO.read(entrada);
        }
    }

    /** Hay algo dibujado si algun pixel de la franja no es blanco. */
    private boolean hayTinta(BufferedImage imagen, int desdeX, int hastaX) {
        for (int x = desdeX; x < hastaX; x++) {
            for (int y = 0; y < imagen.getHeight(); y++) {
                if ((imagen.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void dibujaElEscudoSinAchatarlo() throws Exception {
        // El escudo es lo único de color dentro del marco: el rótulo es negro y el fondo blanco.
        byte[] png = selloVisual.generar(
                new TextoSello(TITULAR_DE_REFERENCIA, MOTIVO_DE_REFERENCIA, INSTANTE_DE_REFERENCIA));

        int[] caja = cajaDelEscudo(leer(png));
        double anchoDibujado = caja[2] - caja[0] + 1;
        double altoDibujado = caja[3] - caja[1] + 1;

        assertThat(anchoDibujado / altoDibujado)
                .as("proporción del escudo dibujado")
                .isCloseTo(PROPORCION_DEL_ESCUDO, org.assertj.core.data.Offset.offset(0.03));
    }

    @Test
    void escribeElRotuloALaDerechaDelEscudoYCentradoConEl() throws Exception {
        byte[] png = selloVisual.generar(
                new TextoSello(TITULAR_DE_REFERENCIA, MOTIVO_DE_REFERENCIA, INSTANTE_DE_REFERENCIA));

        BufferedImage imagen = leer(png);
        int[] escudo = cajaDelEscudo(imagen);
        int[] rotulo = cajaDelRotulo(imagen, escudo[2] + 5);

        assertThat(rotulo[0]).as("el rótulo empieza a la derecha del escudo").isGreaterThan(escudo[2]);
        assertThat(rotulo[2]).as("el rótulo no se sale de su zona").isLessThan(ANCHO_ESCUDO_PX);

        double centroDelEscudo = (escudo[1] + escudo[3]) / 2d;
        double centroDelRotulo = (rotulo[1] + rotulo[3]) / 2d;
        assertThat(centroDelRotulo)
                .as("el rótulo va centrado verticalmente respecto al escudo")
                .isCloseTo(centroDelEscudo, org.assertj.core.data.Offset.offset(12d));
    }

    @Test
    void elRotuloOcupaUnaParteRazonableDeLaZonaDelEscudo() throws Exception {
        // Con el escudo comiéndose todo el ancho, «FIRMA DIGITAL» quedaba ilegible.
        byte[] png = selloVisual.generar(
                new TextoSello(TITULAR_DE_REFERENCIA, MOTIVO_DE_REFERENCIA, INSTANTE_DE_REFERENCIA));

        BufferedImage imagen = leer(png);
        int[] escudo = cajaDelEscudo(imagen);
        int[] rotulo = cajaDelRotulo(imagen, escudo[2] + 5);
        double anchoDelRotulo = rotulo[2] - rotulo[0] + 1;

        assertThat(anchoDelRotulo / ANCHO_ESCUDO_PX)
                .as("parte de la zona que ocupa el rótulo")
                .isBetween(0.25, 0.5);
    }

    /** Caja del escudo: lo único con color dentro del marco. Devuelve {x0, y0, x1, y1}. */
    private int[] cajaDelEscudo(BufferedImage imagen) {
        return caja(imagen, INTERIOR_DESDE, ANCHO_ESCUDO_PX - INTERIOR_DESDE, true);
    }

    /** Caja del rótulo: lo negro que hay a la derecha del escudo. */
    private int[] cajaDelRotulo(BufferedImage imagen, int desdeX) {
        return caja(imagen, desdeX, ANCHO_ESCUDO_PX - INTERIOR_DESDE, false);
    }

    private int[] caja(BufferedImage imagen, int desdeX, int hastaX, boolean conColor) {
        int x0 = Integer.MAX_VALUE;
        int y0 = Integer.MAX_VALUE;
        int x1 = -1;
        int y1 = -1;
        for (int fila = INTERIOR_DESDE; fila < INTERIOR_HASTA_Y; fila++) {
            for (int columna = desdeX; columna < hastaX; columna++) {
                int rgb = imagen.getRGB(columna, fila);
                int rojo = (rgb >> 16) & 0xFF;
                int verde = (rgb >> 8) & 0xFF;
                int azul = rgb & 0xFF;
                boolean blanco = rojo > 245 && verde > 245 && azul > 245;
                boolean sinColor = Math.abs(rojo - verde) < 25 && Math.abs(verde - azul) < 25;
                boolean interesa = conColor ? (!blanco && !sinColor) : (!blanco && sinColor);
                if (interesa) {
                    x0 = Math.min(x0, columna);
                    y0 = Math.min(y0, fila);
                    x1 = Math.max(x1, columna);
                    y1 = Math.max(y1, fila);
                }
            }
        }
        return new int[]{x0, y0, x1, y1};
    }

    @Test
    void dejaAlEscudoElCuarentaYUnPorCientoDelAnchoComoEnReFirma() throws Exception {
        byte[] png = selloVisual.generar(
                new TextoSello(TITULAR_DE_REFERENCIA, MOTIVO_DE_REFERENCIA, INSTANTE_DE_REFERENCIA));

        BufferedImage imagen = leer(png);

        assertThat(ANCHO_ESCUDO_PX / (double) imagen.getWidth())
                .isCloseTo(105 / 256d, org.assertj.core.data.Offset.offset(0.005));
    }

    /** Caja delimitadora de lo dibujado dentro de una franja: {x0, y0, x1, y1}. */
    private int[] cajaDeLaTinta(BufferedImage imagen, int desdeX, int hastaX) {
        int x0 = Integer.MAX_VALUE;
        int y0 = Integer.MAX_VALUE;
        int x1 = -1;
        int y1 = -1;
        for (int fila = 0; fila < imagen.getHeight(); fila++) {
            for (int columna = desdeX; columna < hastaX; columna++) {
                if (imagen.getRGB(columna, fila) != Color.WHITE.getRGB()) {
                    x0 = Math.min(x0, columna);
                    y0 = Math.min(y0, fila);
                    x1 = Math.max(x1, columna);
                    y1 = Math.max(y1, fila);
                }
            }
        }
        return new int[]{x0, y0, x1, y1};
    }

    /** Ultima fila de pixeles con algo dibujado dentro de la franja indicada, o -1 si no hay. */
    private int ultimaFilaConTinta(BufferedImage imagen, int desdeX, int hastaX) {
        for (int fila = imagen.getHeight() - 1; fila >= 0; fila--) {
            for (int columna = desdeX; columna < hastaX; columna++) {
                if (imagen.getRGB(columna, fila) != Color.WHITE.getRGB()) {
                    return fila;
                }
            }
        }
        return -1;
    }
}
