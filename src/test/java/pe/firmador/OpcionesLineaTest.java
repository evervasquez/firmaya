package pe.firmador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OpcionesLineaTest {

    @Test
    void tomaLosValoresPorDefectoDeReFirmaCuandoSoloSeDanLosDosArchivos() {
        OpcionesLinea opciones = OpcionesLinea.parsear(new String[]{"doc.pdf", "cert.p12"});

        assertThat(opciones.indiceMotivo()).isEqualTo(MotivosReFirma.INDICE_POR_DEFECTO);
        assertThat(opciones.pagina()).isEqualTo(LectorPdf.PAGINA_ULTIMA);
        assertThat(opciones.x()).isNaN();
        assertThat(opciones.y()).isNaN();
    }

    @Test
    void interpretaTodasLasOpciones() {
        OpcionesLinea opciones = OpcionesLinea.parsear(new String[]{
                "doc.pdf", "cert.p12", "--motivo", "1", "--página", "3", "--x", "40.5", "--y", "700"});

        assertThat(opciones.indiceMotivo()).isEqualTo(1);
        assertThat(opciones.pagina()).isEqualTo(3);
        assertThat(opciones.x()).isEqualTo(40.5f);
        assertThat(opciones.y()).isEqualTo(700f);
    }

    @Test
    void firmaEnNivelBasicoMientrasNoSePidaOtraCosa() {
        OpcionesLinea opciones = OpcionesLinea.parsear(new String[]{"doc.pdf", "cert.p12"});

        assertThat(opciones.nivel()).isInstanceOf(NivelFirma.Basico.class);
    }

    @Test
    void cambiaAlNivelDeLargoPlazoConLaOpcionLt() {
        OpcionesLinea opciones = OpcionesLinea.parsear(
                new String[]{"doc.pdf", "cert.p12", "--lt", "--motivo", "2"});

        assertThat(opciones.nivel()).isInstanceOf(NivelFirma.LargoPlazo.class);
        assertThat(opciones.indiceMotivo()).isEqualTo(2);
    }

    @Test
    void tomaElServidorDeSelloDeTiempoIndicado() {
        OpcionesLinea opciones = OpcionesLinea.parsear(
                new String[]{"doc.pdf", "cert.p12", "--lt", "--tsa", "http://tsa.ejemplo.pe"});

        assertThat(opciones.nivel()).isEqualTo(new NivelFirma.LargoPlazo("http://tsa.ejemplo.pe"));
    }

    @Test
    void rechazaQueFalteElCertificado() {
        assertThatThrownBy(() -> OpcionesLinea.parsear(new String[]{"doc.pdf"}))
                .isInstanceOf(FirmaException.class)
                .hasMessageContaining("Faltan argumentos");
    }

    @Test
    void rechazaUnaOpcionDesconocida() {
        assertThatThrownBy(() -> OpcionesLinea.parsear(new String[]{"doc.pdf", "cert.p12", "--color", "azul"}))
                .isInstanceOf(FirmaException.class)
                .hasMessageContaining("--color");
    }

    @Test
    void rechazaUnaOpcionSinValor() {
        assertThatThrownBy(() -> OpcionesLinea.parsear(new String[]{"doc.pdf", "cert.p12", "--motivo"}))
                .isInstanceOf(FirmaException.class)
                .hasMessageContaining("necesita un valor");
    }

    @Test
    void rechazaUnValorNoNumerico() {
        assertThatThrownBy(() -> OpcionesLinea.parsear(new String[]{"doc.pdf", "cert.p12", "--página", "última"}))
                .isInstanceOf(FirmaException.class)
                .hasMessageContaining("entero");
    }
}
