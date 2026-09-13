package pe.firmador.escritorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import pe.firmador.SelloVisual;

class RecuadroTest {

    private static final Offset<Double> TOLERANCIA = Offset.offset(0.01);

    private static final double ANCHO_A4 = 595;
    private static final double ALTO_A4 = 842;

    @Test
    void mideSiempreLoMismoQueElSelloDeReFirma() {
        Recuadro recuadro = new Recuadro(30, 40);

        assertThat(recuadro.ancho()).isCloseTo(SelloVisual.ANCHO_PT, TOLERANCIA);
        assertThat(recuadro.alto()).isCloseTo(SelloVisual.ALTO_PT, TOLERANCIA);
    }

    @Test
    void conservaSuTamanoDespuesDeMoverlo() {
        Recuadro original = new Recuadro(30, 40);

        Recuadro movido = new Recuadro(original.x() + 120, original.y() + 200)
                .dentroDe(ANCHO_A4, ALTO_A4);

        assertThat(movido.x()).isEqualTo(150);
        assertThat(movido.y()).isEqualTo(240);
        assertThat(movido.ancho()).isCloseTo(SelloVisual.ANCHO_PT, TOLERANCIA);
        assertThat(movido.alto()).isCloseTo(SelloVisual.ALTO_PT, TOLERANCIA);
    }

    @Test
    void conservaSuTamanoAlCambiarDePagina() {
        // Al pasar de una A4 a una media carta el sello solo se recoloca; nunca se encoge.
        Recuadro enA4 = new Recuadro(400, 700);

        Recuadro enMediaCarta = enA4.dentroDe(396, 612);

        assertThat(enMediaCarta.ancho()).isCloseTo(SelloVisual.ANCHO_PT, TOLERANCIA);
        assertThat(enMediaCarta.alto()).isCloseTo(SelloVisual.ALTO_PT, TOLERANCIA);
        assertThat(enMediaCarta.x() + enMediaCarta.ancho()).isLessThanOrEqualTo(396);
        assertThat(enMediaCarta.y() + enMediaCarta.alto()).isLessThanOrEqualTo(612);
    }

    @Test
    void colocaElSelloCentradoEnElPuntoDondeSeHizoClic() {
        Recuadro recuadro = Recuadro.centradoEn(300, 400);

        assertThat(recuadro.x() + recuadro.ancho() / 2).isCloseTo(300, TOLERANCIA);
        assertThat(recuadro.y() + recuadro.alto() / 2).isCloseTo(400, TOLERANCIA);
    }

    @Test
    void colocaElSelloAbajoALaIzquierdaConElTamanoOriginalDeReFirma() {
        Recuadro recuadro = Recuadro.abajoIzquierda(ALTO_A4, 20);

        assertThat(recuadro.x()).isEqualTo(20);
        assertThat(recuadro.y()).isCloseTo(ALTO_A4 - SelloVisual.ALTO_PT - 20, TOLERANCIA);
        assertThat(recuadro.ancho()).isCloseTo(SelloVisual.ANCHO_PT, TOLERANCIA);
    }

    @Test
    void empujaDentroDeLaPaginaUnSelloQueSeSalePorLaDerecha() {
        Recuadro fuera = new Recuadro(500, 10);

        Recuadro dentro = fuera.dentroDe(ANCHO_A4, ALTO_A4);

        assertThat(dentro.x() + dentro.ancho()).isLessThanOrEqualTo(ANCHO_A4);
        assertThat(dentro.y()).isEqualTo(10);
    }

    @Test
    void empujaDentroDeLaPaginaUnSelloConCoordenadasNegativas() {
        Recuadro fuera = new Recuadro(-50, -30);

        Recuadro dentro = fuera.dentroDe(ANCHO_A4, ALTO_A4);

        assertThat(dentro.x()).isZero();
        assertThat(dentro.y()).isZero();
    }

    @Test
    void enUnaPaginaMasEstrechaQueElSelloLoDejaPegadoAlBorde() {
        Recuadro recuadro = new Recuadro(100, 100);

        Recuadro dentro = recuadro.dentroDe(120, 40);

        assertThat(dentro.x()).isZero();
        assertThat(dentro.y()).isZero();
        assertThat(dentro.ancho()).isCloseTo(SelloVisual.ANCHO_PT, TOLERANCIA);
    }

    @Test
    void rechazaUnaPosicionQueNoEsUnNumero() {
        assertThatThrownBy(() -> new Recuadro(Double.NaN, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no es un número");
    }
}
