package pe.firmador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

class DatosCertificadoTest {

    private static final Instant HOY = Instant.parse("2026-09-11T12:00:00Z");

    @Test
    void estaVigenteCuandoLeFaltaMasDeUnMes() {
        DatosCertificado datos = certificadoQueVenceEn(60);

        assertThat(datos.vigencia(reloj())).isEqualTo(DatosCertificado.Vigencia.VIGENTE);
        assertThat(datos.vigencia(reloj()).permiteFirmar()).isTrue();
    }

    @Test
    void avisaQueEstaPorVencerDentroDeLosTreintaDias() {
        DatosCertificado datos = certificadoQueVenceEn(DatosCertificado.DIAS_DE_AVISO);

        assertThat(datos.vigencia(reloj())).isEqualTo(DatosCertificado.Vigencia.POR_VENCER);
        assertThat(datos.diasParaVencer(reloj())).isEqualTo(DatosCertificado.DIAS_DE_AVISO);
    }

    @Test
    void estaVencidoElMismoInstanteEnQueTerminaSuVigencia() {
        DatosCertificado datos = certificadoQueVenceEn(0);

        assertThat(datos.vigencia(reloj())).isEqualTo(DatosCertificado.Vigencia.VENCIDO);
        assertThat(datos.vigencia(reloj()).permiteFirmar()).isFalse();
    }

    @Test
    void avisaCuandoTodaviaNoEntraEnVigencia() {
        DatosCertificado datos = new DatosCertificado("TITULAR", "", "EMISOR",
                HOY.plus(5, ChronoUnit.DAYS), HOY.plus(400, ChronoUnit.DAYS));

        assertThat(datos.vigencia(reloj())).isEqualTo(DatosCertificado.Vigencia.AUN_NO_VIGENTE);
        assertThat(datos.vigencia(reloj()).permiteFirmar()).isFalse();
    }

    @Test
    void muestraElDocumentoDelCertificadoDeFormaLegible() {
        DatosCertificado datos = new DatosCertificado("TITULAR", "DNI-12345678", "EMISOR",
                HOY, HOY.plus(1, ChronoUnit.DAYS));

        assertThat(datos.documentoLegible()).isEqualTo("DNI 12345678");
    }

    @Test
    void muestraLaFechaDeVencimientoEnHoraDePeru() {
        // Las 02:30 UTC del 29 son todavia el 28 en Peru: la fecha que ve el usuario es la suya.
        DatosCertificado datos = new DatosCertificado("TITULAR", "", "EMISOR",
                HOY, Instant.parse("2027-08-29T02:30:00Z"));

        assertThat(datos.fechaDeVencimiento()).isEqualTo("28/08/2027");
    }

    @Test
    void rechazaUnPeriodoDeVigenciaInvertido() {
        assertThatThrownBy(() -> new DatosCertificado("TITULAR", "", "EMISOR",
                HOY, HOY.minus(1, ChronoUnit.DAYS)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vence antes de empezar");
    }

    private DatosCertificado certificadoQueVenceEn(int dias) {
        return new DatosCertificado("PEREZ GARCIA JUAN CARLOS", "DNI-12345678", "FirmEasy SubCA",
                HOY.minus(400, ChronoUnit.DAYS), HOY.plus(dias, ChronoUnit.DAYS));
    }

    private Clock reloj() {
        return Clock.fixed(HOY, ZoneOffset.UTC);
    }
}
