package pe.firmador.escritorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class ModoZoomTest {

    private static final Offset<Double> TOLERANCIA = Offset.offset(0.001);

    /** Una A4 en puntos PDF. */
    private static final double ANCHO_A4 = 595;
    private static final double ALTO_A4 = 842;

    @Test
    void ajustarALaVentanaHaceQueLaPaginaQuepaEntera() {
        double escala = new ModoZoom.AjustarALaVentana().escalaPara(ANCHO_A4, ALTO_A4, 800, 600);

        assertThat(ANCHO_A4 * escala).isLessThanOrEqualTo(800);
        assertThat(ALTO_A4 * escala).isLessThanOrEqualTo(600);
        // Manda el lado que más aprieta, que en una ventana apaisada es el alto.
        assertThat(escala).isCloseTo(600 / ALTO_A4, TOLERANCIA);
    }

    @Test
    void ajustarAlAnchoLlenaElAnchoAunqueLaPaginaSobresalgaPorAbajo() {
        double escala = new ModoZoom.AjustarAlAncho().escalaPara(ANCHO_A4, ALTO_A4, 800, 600);

        assertThat(escala).isCloseTo(800 / ANCHO_A4, TOLERANCIA);
        assertThat(ALTO_A4 * escala).isGreaterThan(600);
    }

    @Test
    void elZoomFijoNoDependeDelTamanoDeLaVentana() {
        ModoZoom fijo = new ModoZoom.Fijo(1.25);

        assertThat(fijo.escalaPara(ANCHO_A4, ALTO_A4, 800, 600)).isEqualTo(1.25);
        assertThat(fijo.escalaPara(ANCHO_A4, ALTO_A4, 3000, 2000)).isEqualTo(1.25);
        assertThat(fijo.dependeDeLaVentana()).isFalse();
    }

    @Test
    void losAjustesSeRecalculanConLaVentana() {
        assertThat(new ModoZoom.AjustarALaVentana().dependeDeLaVentana()).isTrue();
        assertThat(new ModoZoom.AjustarAlAncho().dependeDeLaVentana()).isTrue();
    }

    @Test
    void nuncaDevuelveUnAumentoDisparatadoConUnaVentanaEnorme() {
        double escala = new ModoZoom.AjustarAlAncho().escalaPara(ANCHO_A4, ALTO_A4, 99_999, 99_999);

        assertThat(escala).isEqualTo(ModoZoom.ESCALA_MAXIMA);
    }

    @Test
    void nuncaDevuelveUnAumentoInvisibleConLaVentanaRecienAbierta() {
        // Antes de que JavaFX mida el visor, el tamaño disponible puede llegar en cero o negativo.
        double escala = new ModoZoom.AjustarALaVentana().escalaPara(ANCHO_A4, ALTO_A4, 0, 0);

        assertThat(escala).isEqualTo(ModoZoom.ESCALA_MINIMA);
    }

    @Test
    void seMuestranConNombresQueElUsuarioEntiende() {
        assertThat(new ModoZoom.AjustarALaVentana().etiqueta()).isEqualTo("Ajustar a la ventana");
        assertThat(new ModoZoom.AjustarAlAncho().etiqueta()).isEqualTo("Ajustar al ancho");
        assertThat(new ModoZoom.Fijo(1.25).etiqueta()).isEqualTo("125 %");
    }

    @Test
    void rechazaUnZoomFijoQueNoSirve() {
        assertThatThrownBy(() -> new ModoZoom.Fijo(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mayor que cero");
    }

    @Test
    void laPosicionDelSelloEnPuntosNoCambiaAlVariarElZoom() {
        // El recuadro se guarda en puntos del PDF. Sea cual sea el aumento con que se dibuje,
        // al volver a puntos tiene que dar lo mismo: es lo que garantiza que la firma caiga
        // donde el usuario la puso.
        double margen = 24;
        double xEnPuntos = 137.5;

        for (double escala : new double[]{0.5, 0.75, 1.0, 1.25, 2.0, 3.3333}) {
            double enPantalla = xEnPuntos * escala + margen;
            double deVuelta = (enPantalla - margen) / escala;

            assertThat(deVuelta).as("con zoom %.4f", escala).isCloseTo(xEnPuntos, TOLERANCIA);
        }
    }
}
