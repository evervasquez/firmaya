package pe.firmador.escritorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NavegacionPaginasTest {

    private static final int TOTAL = 8;
    private static final int ACTUAL = 3;

    @Test
    void saltaALaPaginaEscrita() {
        assertThat(NavegacionPaginas.interpretar("6", TOTAL, ACTUAL)).isEqualTo(6);
    }

    @Test
    void aceptaElNumeroConEspaciosAlrededor() {
        assertThat(NavegacionPaginas.interpretar("  6 ", TOTAL, ACTUAL)).isEqualTo(6);
    }

    @Test
    void aceptaLaPrimeraYLaUltimaPagina() {
        assertThat(NavegacionPaginas.interpretar("1", TOTAL, ACTUAL)).isEqualTo(1);
        assertThat(NavegacionPaginas.interpretar("8", TOTAL, ACTUAL)).isEqualTo(TOTAL);
    }

    @Test
    void seQuedaDondeEstabaSiLaPaginaNoExiste() {
        assertThat(NavegacionPaginas.interpretar("9", TOTAL, ACTUAL)).isEqualTo(ACTUAL);
        assertThat(NavegacionPaginas.interpretar("0", TOTAL, ACTUAL)).isEqualTo(ACTUAL);
        assertThat(NavegacionPaginas.interpretar("-2", TOTAL, ACTUAL)).isEqualTo(ACTUAL);
    }

    @Test
    void seQuedaDondeEstabaConTextoQueNoEsUnNumero() {
        assertThat(NavegacionPaginas.interpretar("tres", TOTAL, ACTUAL)).isEqualTo(ACTUAL);
        assertThat(NavegacionPaginas.interpretar("", TOTAL, ACTUAL)).isEqualTo(ACTUAL);
        assertThat(NavegacionPaginas.interpretar("   ", TOTAL, ACTUAL)).isEqualTo(ACTUAL);
        assertThat(NavegacionPaginas.interpretar(null, TOTAL, ACTUAL)).isEqualTo(ACTUAL);
    }

    @Test
    void seQuedaDondeEstabaConUnNumeroEnorme() {
        assertThat(NavegacionPaginas.interpretar("99999999999999999999", TOTAL, ACTUAL))
                .isEqualTo(ACTUAL);
    }

    @Test
    void rechazaUnDocumentoSinPaginas() {
        assertThatThrownBy(() -> NavegacionPaginas.interpretar("1", 0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos una página");
    }

    @Test
    void rechazaUnaPaginaActualFueraDelDocumento() {
        assertThatThrownBy(() -> NavegacionPaginas.interpretar("1", TOTAL, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fuera del documento");
    }
}
