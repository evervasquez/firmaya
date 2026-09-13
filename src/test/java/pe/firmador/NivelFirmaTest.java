package pe.firmador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.europa.esig.dss.enumerations.SignatureLevel;

import org.junit.jupiter.api.Test;

class NivelFirmaTest {

    @Test
    void elNivelPorDefectoEsElBasicoQueYaValidoElValidadorOficial() {
        assertThat(NivelFirma.porDefecto()).isInstanceOf(NivelFirma.Basico.class);
        assertThat(NivelFirma.porDefecto().nivelDss()).isEqualTo(SignatureLevel.PAdES_BASELINE_B);
    }

    @Test
    void elNivelDeLargoPlazoSeTraduceAPadesBaselineLt() {
        NivelFirma nivel = new NivelFirma.LargoPlazo("http://timestamp.ejemplo.pe");

        assertThat(nivel.nivelDss()).isEqualTo(SignatureLevel.PAdES_BASELINE_LT);
    }

    @Test
    void rechazaElNivelDeLargoPlazoSinServidorDeSelloDeTiempo() {
        assertThatThrownBy(() -> new NivelFirma.LargoPlazo("  "))
                .isInstanceOf(FirmaException.class)
                .hasMessageContaining("sellado de tiempo");
    }

    @Test
    void rechazaUnaDireccionDeSelloDeTiempoQueNoEsHttp() {
        assertThatThrownBy(() -> new NivelFirma.LargoPlazo("ftp://tsa.ejemplo.pe"))
                .isInstanceOf(FirmaException.class)
                .hasMessageContaining("http://");
    }

    @Test
    void cadaNivelTraeUnaEtiquetaParaLaInterfaz() {
        assertThat(new NivelFirma.Basico().etiqueta()).contains("PAdES-B");
        assertThat(new NivelFirma.LargoPlazo(NivelFirma.TSA_POR_DEFECTO).etiqueta()).contains("PAdES-LT");
    }
}
