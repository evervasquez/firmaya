package pe.firmador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class TextoSelloTest {

    /** 22/02/2024 02:40:20 UTC, el mismo instante del sello de referencia de ReFirma. */
    private static final Instant INSTANTE_DE_REFERENCIA = Instant.parse("2024-02-22T02:40:20Z");

    @Test
    void muestraLaFechaEnUtcConSufijoMasCeroCero() {
        TextoSello texto = new TextoSello("PEREZ GARCIA JUAN CARLOS",
                "En señal de conformidad", INSTANTE_DE_REFERENCIA);

        assertThat(texto.bloques().get(3)).isEqualTo("Fecha: 22/02/2024 02:40:20+0000");
    }

    @Test
    void laFechaNoDependeDeLaZonaHorariaDeLaMaquina() {
        // La firma real se hizo a las 21:40 hora de Peru y el sello dice 02:40 del dia siguiente.
        Instant nocheEnPeru = Instant.parse("2024-02-22T02:40:20Z");
        TextoSello texto = new TextoSello("TITULAR", "Doy fe", nocheEnPeru);

        assertThat(texto.bloques().get(3)).endsWith("+0000");
    }

    @Test
    void armaLosCuatroBloquesEnElOrdenDeReFirma() {
        TextoSello texto = new TextoSello("TITULAR", "Doy fe", INSTANTE_DE_REFERENCIA);

        assertThat(texto.bloques()).containsExactly(
                "Firmado digitalmente por:",
                "TITULAR",
                "Motivo: Doy fe",
                "Fecha: 22/02/2024 02:40:20+0000");
    }

    @Test
    void rechazaUnTitularVacio() {
        assertThatThrownBy(() -> new TextoSello("   ", "Doy fe", INSTANTE_DE_REFERENCIA))
                .isInstanceOf(FirmaException.class)
                .hasMessageContaining("nombre comun");
    }

    @Test
    void rechazaUnaFechaAusente() {
        assertThatThrownBy(() -> new TextoSello("TITULAR", "Doy fe", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("fecha");
    }
}
