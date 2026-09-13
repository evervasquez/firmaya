package pe.firmador.escritorio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import pe.firmador.FirmaException;

/**
 * El documento que acaba de quedar firmado, con lo poco que la interfaz necesita saber de el:
 * que ruta ensenar y como llegar hasta el archivo.
 *
 * <p>La ruta que se muestra y la que se abre son la misma por construccion: las dos salen de
 * {@link #ruta()}, de modo que no puede pasar que el usuario copie una ruta y el boton abra
 * otra.</p>
 *
 * @param ruta archivo firmado, en su forma absoluta
 */
public record DocumentoFirmado(Path ruta) {

    /** @throws NullPointerException si falta la ruta */
    public DocumentoFirmado {
        Objects.requireNonNull(ruta, "ruta es obligatoria");
        ruta = ruta.toAbsolutePath();
    }

    /**
     * @return la ruta tal como se muestra al usuario, para que pueda seleccionarla y copiarla
     */
    public String rutaVisible() {
        return ruta.toString();
    }

    /**
     * @return {@code true} si el archivo sigue donde se guardo
     */
    public boolean existe() {
        return Files.isRegularFile(ruta);
    }

    /**
     * Muestra el documento en el gestor de archivos del sistema.
     *
     * @param abridor acceso al sistema operativo
     * @throws FirmaException si el archivo ya no esta o el sistema no pudo abrirlo
     */
    public void mostrarEnElGestor(AbridorDeArchivos abridor) {
        Objects.requireNonNull(abridor, "abridor es obligatorio").mostrarEnElGestor(exigirQueSigaAhi());
    }

    /**
     * Abre el documento con la aplicacion por omision del sistema.
     *
     * @param abridor acceso al sistema operativo
     * @throws FirmaException si el archivo ya no esta o el sistema no pudo abrirlo
     */
    public void abrir(AbridorDeArchivos abridor) {
        Objects.requireNonNull(abridor, "abridor es obligatorio").abrir(exigirQueSigaAhi());
    }

    private Path exigirQueSigaAhi() {
        if (!existe()) {
            throw new FirmaException("El documento firmado ya no está en " + ruta
                    + ". Puede que lo haya movido, renombrado o borrado.");
        }
        return ruta;
    }
}
