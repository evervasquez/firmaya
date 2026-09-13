package pe.firmador;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Contenido textual del sello visible, en los cuatro bloques que dibuja ReFirma PDF 1.6.
 *
 * <p>La fecha se expresa siempre en UTC con desplazamiento explicito ({@code +0000}),
 * tal como aparece en los documentos firmados con ReFirma: una firma hecha a las 21:40
 * de Peru se muestra como las 02:40 del dia siguiente.</p>
 *
 * @param titular nombre comun (CN) tomado del certificado del firmante
 * @param motivo  motivo de firma, uno de los de {@link MotivosReFirma}
 * @param fecha   momento de la firma
 */
public record TextoSello(String titular, String motivo, Instant fecha) {

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ssZ", Locale.ROOT).withZone(ZoneOffset.UTC);

    public TextoSello {
        Objects.requireNonNull(titular, "titular es obligatorio");
        Objects.requireNonNull(motivo, "motivo es obligatorio");
        Objects.requireNonNull(fecha, "fecha es obligatoria");
        if (titular.isBlank()) {
            throw new FirmaException("El certificado no tiene nombre comun (CN) utilizable para el sello");
        }
    }

    /**
     * Bloques de texto del sello, en orden de dibujo. Cada bloque puede partirse en varias
     * lineas si no cabe en el ancho disponible; el corte lo decide {@link SelloVisual}.
     *
     * @return lista inmutable de cuatro bloques
     */
    public List<String> bloques() {
        return List.of(
                "Firmado digitalmente por:",
                titular,
                "Motivo: " + motivo,
                "Fecha: " + FORMATO_FECHA.format(fecha));
    }
}
