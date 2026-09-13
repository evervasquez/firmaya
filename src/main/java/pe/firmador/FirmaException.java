package pe.firmador;

/**
 * Falla de dominio del firmador: el documento, el certificado o la firma no son utilizables.
 *
 * <p>El mensaje describe el problema y el dato involucrado (ruta, pagina, indice de motivo).
 * Nunca contiene la contrasena del certificado ni material de clave.</p>
 */
public class FirmaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FirmaException(String mensaje) {
        super(mensaje);
    }

    public FirmaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
