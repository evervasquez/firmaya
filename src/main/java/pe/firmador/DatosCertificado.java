package pe.firmador;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;

/**
 * Lo que la aplicacion necesita saber de un certificado ya abierto: quien es su titular, con
 * que documento esta identificado, quien lo emitio y hasta cuando sirve.
 *
 * <p>Se construye leyendo el .p12 con su contrasena, asi que existir ya significa que el
 * certificado se pudo abrir de verdad. Es inmutable y seguro para varios hilos.</p>
 *
 * @param titular       nombre comun (CN) del sujeto, el mismo que se imprime en el sello
 * @param documento     valor crudo del {@code serialNumber} del sujeto, tal como viene en el
 *                      certificado (RENIEC lo emite como {@code DNI-12345678}); cadena vacia
 *                      si el certificado no lo trae
 * @param emisor        nombre comun (CN) de la entidad que emitio el certificado
 * @param vigenteDesde  inicio del periodo de validez
 * @param vigenteHasta  fin del periodo de validez, el {@code notAfter} del certificado
 */
public record DatosCertificado(String titular, String documento, String emisor,
                               Instant vigenteDesde, Instant vigenteHasta) {

    /** A partir de aqui se avisa de que el certificado esta por vencer. */
    public static final int DIAS_DE_AVISO = 30;

    /** Las fechas de los certificados peruanos se leen en la hora local del titular. */
    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT);

    /**
     * @throws NullPointerException     si falta cualquiera de los datos
     * @throws IllegalArgumentException si el periodo de validez esta invertido
     */
    public DatosCertificado {
        Objects.requireNonNull(titular, "titular es obligatorio");
        Objects.requireNonNull(documento, "documento es obligatorio");
        Objects.requireNonNull(emisor, "emisor es obligatorio");
        Objects.requireNonNull(vigenteDesde, "vigenteDesde es obligatorio");
        Objects.requireNonNull(vigenteHasta, "vigenteHasta es obligatorio");
        if (vigenteHasta.isBefore(vigenteDesde)) {
            throw new IllegalArgumentException("El certificado de " + titular
                    + " vence antes de empezar a ser válido: " + vigenteDesde + " a " + vigenteHasta);
        }
    }

    /** Estado del certificado frente a la fecha en que se quiere firmar. */
    public enum Vigencia {

        /** Todavia no empieza su periodo de validez. */
        AUN_NO_VIGENTE("Aún no vigente"),

        /** Sirve para firmar y le queda holgura. */
        VIGENTE("Vigente"),

        /** Sirve para firmar, pero vence dentro de {@link #DIAS_DE_AVISO} dias o menos. */
        POR_VENCER("Por vencer"),

        /** Ya no sirve: lo que se firme con el no validara. */
        VENCIDO("Vencido");

        private final String etiqueta;

        Vigencia(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        /**
         * @return el nombre del estado en espanol, listo para mostrar al usuario
         */
        public String etiqueta() {
            return etiqueta;
        }

        /**
         * @return {@code true} si con este estado se puede firmar un documento que valide
         */
        public boolean permiteFirmar() {
            return this == VIGENTE || this == POR_VENCER;
        }
    }

    /**
     * @param reloj fuente de la fecha actual; se inyecta para que las pruebas no dependan del dia
     * @return el estado del certificado en ese momento
     */
    public Vigencia vigencia(Clock reloj) {
        Objects.requireNonNull(reloj, "reloj es obligatorio");
        Instant ahora = reloj.instant();
        if (ahora.isBefore(vigenteDesde)) {
            return Vigencia.AUN_NO_VIGENTE;
        }
        if (!ahora.isBefore(vigenteHasta)) {
            return Vigencia.VENCIDO;
        }
        return diasParaVencer(reloj) <= DIAS_DE_AVISO ? Vigencia.POR_VENCER : Vigencia.VIGENTE;
    }

    /**
     * @param reloj fuente de la fecha actual
     * @return dias completos que faltan para el vencimiento; negativo si ya vencio
     */
    public long diasParaVencer(Clock reloj) {
        Objects.requireNonNull(reloj, "reloj es obligatorio");
        return ChronoUnit.DAYS.between(reloj.instant(), vigenteHasta);
    }

    /**
     * @return la fecha de vencimiento en formato {@code dd/MM/aaaa}, en hora de Perú
     */
    public String fechaDeVencimiento() {
        return FORMATO_FECHA.format(vigenteHasta.atZone(ZONA));
    }

    /**
     * Documento del titular en forma legible: {@code DNI-12345678} se muestra como
     * {@code DNI 12345678}.
     *
     * @return el documento listo para mostrar, o cadena vacia si el certificado no lo trae
     */
    public String documentoLegible() {
        return documento.replaceFirst("-", " ");
    }
}
