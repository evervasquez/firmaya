package pe.firmador;

import java.time.Instant;
import java.util.Objects;

/**
 * Lo que se puede leer de una firma ya incrustada en un PDF.
 *
 * <p>Se usa tanto para el resumen que se imprime despues de firmar como para el modo de
 * verificacion: en ambos casos los datos salen del archivo firmado, no de los parametros
 * de entrada, de modo que el resumen refleje lo que realmente quedo en el documento.</p>
 *
 * @param titular   nombre comun (CN) del certificado firmante
 * @param motivo    contenido de la entrada {@code /Reason} del diccionario de firma
 * @param fecha     momento declarado de la firma
 * @param filtro    entrada {@code /Filter}, normalmente {@code Adobe.PPKLite}
 * @param subFiltro entrada {@code /SubFilter}, que para PAdES es {@code ETSI.CAdES.detached}
 * @param nivel     nivel PAdES detectado por DSS
 * @param algoritmo algoritmo de firma efectivamente usado
 * @param integra   si el resumen criptografico del documento cuadra con la firma
 */
public record DatosFirma(String titular, String motivo, Instant fecha, String filtro,
                         String subFiltro, String nivel, String algoritmo, boolean integra) {

    /** Texto con el que se rellena un campo que la firma no trae. */
    public static final String DESCONOCIDO = "(no declarado)";

    public DatosFirma {
        Objects.requireNonNull(titular, "titular es obligatorio");
        Objects.requireNonNull(motivo, "motivo es obligatorio");
        Objects.requireNonNull(fecha, "fecha es obligatoria");
        Objects.requireNonNull(filtro, "filtro es obligatorio");
        Objects.requireNonNull(subFiltro, "subFiltro es obligatorio");
        Objects.requireNonNull(nivel, "nivel es obligatorio");
        Objects.requireNonNull(algoritmo, "algoritmo es obligatorio");
    }
}
