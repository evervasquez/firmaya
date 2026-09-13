package pe.firmador.escritorio;

import java.nio.file.Path;

/**
 * Deja el archivo a un clic: lo muestra en el gestor de archivos del sistema o lo abre con la
 * aplicacion que corresponda.
 *
 * <p>Existe como interfaz para que las pruebas no abran ventanas del Finder ni lancen procesos
 * del sistema.</p>
 *
 * <p>Las implementaciones no tienen por que ser seguras para varios hilos: se usan desde el
 * hilo de la interfaz.</p>
 */
public interface AbridorDeArchivos {

    /**
     * Abre el gestor de archivos con el documento ya seleccionado.
     *
     * @param archivo documento a revelar
     * @throws pe.firmador.FirmaException si el sistema no pudo abrirlo
     */
    void mostrarEnElGestor(Path archivo);

    /**
     * Abre el documento con la aplicacion por omision del sistema.
     *
     * @param archivo documento a abrir
     * @throws pe.firmador.FirmaException si el sistema no pudo abrirlo
     */
    void abrir(Path archivo);
}
