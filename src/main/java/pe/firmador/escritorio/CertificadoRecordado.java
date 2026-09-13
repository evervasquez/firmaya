package pe.firmador.escritorio;

import java.util.Objects;

import pe.firmador.OrigenClave;

/**
 * Un certificado que ya se configuro alguna vez, guardado para poder volver a elegirlo sin
 * tener que buscarlo otra vez en el disco.
 *
 * <p>Aqui no hay ningun secreto: solo de donde sale la clave y a nombre de quien esta, que es
 * el mismo nombre que queda impreso en el sello del documento firmado.</p>
 *
 * @param origen  archivo .p12 o identidad del llavero
 * @param titular nombre del titular tal como se leyo del certificado
 */
public record CertificadoRecordado(OrigenClave origen, String titular) {

    /** @throws NullPointerException si falta alguno de los dos datos */
    public CertificadoRecordado {
        Objects.requireNonNull(origen, "origen es obligatorio");
        Objects.requireNonNull(titular, "titular es obligatorio");
    }

    /**
     * @return como se muestra en la lista de certificados ya usados
     */
    public String etiqueta() {
        return titular.isBlank() ? origen.descripcion() : titular + " · " + origen.descripcion();
    }
}
