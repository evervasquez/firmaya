package pe.firmador.escritorio;

/**
 * Interpreta el numero de pagina que el usuario escribe en la barra de navegacion.
 *
 * <p>Nada de lo que escriba puede romper la navegacion: un texto vacio, con letras, con espacios
 * o fuera del documento deja la pagina donde estaba. Es la misma idea del campo editable de
 * ReFirma, pero sin castigar al usuario por una errata.</p>
 *
 * <p>No guarda estado: es segura para varios hilos.</p>
 */
public final class NavegacionPaginas {

    private NavegacionPaginas() {
    }

    /**
     * @param escrito lo que el usuario tecleo, tal cual
     * @param total   paginas del documento abierto
     * @param actual  pagina que se esta viendo, a la que se vuelve si lo escrito no sirve
     * @return la pagina a la que hay que ir, siempre dentro del documento
     * @throws IllegalArgumentException si el total o la pagina actual no son paginas validas
     */
    public static int interpretar(String escrito, int total, int actual) {
        if (total < 1) {
            throw new IllegalArgumentException("El documento debe tener al menos una página: " + total);
        }
        if (actual < 1 || actual > total) {
            throw new IllegalArgumentException(
                    "La página actual está fuera del documento: " + actual + " de " + total);
        }
        if (escrito == null) {
            return actual;
        }
        try {
            int pedida = Integer.parseInt(escrito.trim());
            return pedida >= 1 && pedida <= total ? pedida : actual;
        } catch (NumberFormatException e) {
            // Una errata no es un fallo que haya que reportar: se vuelve a la página actual.
            return actual;
        }
    }
}
