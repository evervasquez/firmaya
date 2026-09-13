package pe.firmador.escritorio;

import java.util.Objects;

import pe.firmador.DatosCertificado;

/**
 * Una identidad (certificado con su clave privada) guardada en el llavero de inicio de sesion
 * de macOS, lista para elegir.
 *
 * @param alias nombre con el que el llavero la identifica
 * @param datos titular, documento, emisor y vigencia leidos de su certificado
 */
public record IdentidadLlavero(String alias, DatosCertificado datos) {

    /** @throws NullPointerException si falta alguno de los dos datos */
    public IdentidadLlavero {
        Objects.requireNonNull(alias, "alias es obligatorio");
        Objects.requireNonNull(datos, "datos es obligatorio");
    }

    /**
     * @return como se muestra en la lista del modal: titular y hasta cuando vale
     */
    public String etiqueta() {
        String documento = datos.documentoLegible();
        String identificacion = documento.isBlank() ? datos.titular() : datos.titular() + " · " + documento;
        return identificacion + " · vence el " + datos.fechaDeVencimiento();
    }
}
