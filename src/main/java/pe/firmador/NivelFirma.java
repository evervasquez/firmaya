package pe.firmador;

import java.util.Objects;

import eu.europa.esig.dss.enumerations.SignatureLevel;

/**
 * Nivel PAdES con el que se genera la firma.
 *
 * <p>Es una jerarquia cerrada porque cada nivel necesita datos distintos: el basico no
 * necesita nada y el de largo plazo necesita un servidor de sellado de tiempo.</p>
 *
 * <p>El valor por defecto es {@link Basico}: es el unico nivel que este proyecto tiene
 * comprobado contra el validador oficial de Firma Peru.</p>
 */
public sealed interface NivelFirma permits NivelFirma.Basico, NivelFirma.LargoPlazo {

    /**
     * Nivel PAdES-BASELINE-B: firma y certificado del firmante, sin sello de tiempo ni
     * prueba de revocacion. Es lo que produce ReFirma PDF 1.6 sin convenio con RENIEC.
     */
    record Basico() implements NivelFirma {

        @Override
        public SignatureLevel nivelDss() {
            return SignatureLevel.PAdES_BASELINE_B;
        }

        @Override
        public String etiqueta() {
            return "PAdES-B (básico, comprobado)";
        }
    }

    /**
     * Nivel PAdES-BASELINE-LT: agrega un sello de tiempo y deja incrustadas dentro del PDF
     * las respuestas OCSP y las CRL de toda la cadena. El documento sigue validando cuando
     * el certificado del firmante ya vencio, porque la prueba de que estaba vigente al
     * momento de firmar viaja dentro del propio archivo.
     *
     * @param urlSelloTiempo direccion del servidor de sellado de tiempo (RFC 3161)
     */
    record LargoPlazo(String urlSelloTiempo) implements NivelFirma {

        public LargoPlazo {
            Objects.requireNonNull(urlSelloTiempo, "urlSelloTiempo es obligatorio");
            if (urlSelloTiempo.isBlank()) {
                throw new FirmaException("El nivel LT necesita la direccion de un servidor "
                        + "de sellado de tiempo y se recibió vacía");
            }
            if (!urlSelloTiempo.startsWith("http://") && !urlSelloTiempo.startsWith("https://")) {
                throw new FirmaException("La direccion del servidor de sellado de tiempo debe "
                        + "empezar por http:// o https://, se recibió: " + urlSelloTiempo);
            }
        }

        @Override
        public SignatureLevel nivelDss() {
            return SignatureLevel.PAdES_BASELINE_LT;
        }

        @Override
        public String etiqueta() {
            return "PAdES-LT (largo plazo, con sello de tiempo)";
        }
    }

    /** Servidor de sellado de tiempo publico y gratuito, usado como valor inicial de la interfaz. */
    String TSA_POR_DEFECTO = "http://timestamp.digicert.com";

    /**
     * @return el nivel equivalente en la enumeracion de DSS
     */
    SignatureLevel nivelDss();

    /**
     * @return texto corto para mostrar en la interfaz, en espanol
     */
    String etiqueta();

    /**
     * Nivel que se usa cuando nadie elige otro.
     *
     * @return el nivel basico
     */
    static NivelFirma porDefecto() {
        return new Basico();
    }
}
