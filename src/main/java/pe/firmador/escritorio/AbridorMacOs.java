package pe.firmador.escritorio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.firmador.FirmaException;

/**
 * Abre archivos con el comando {@code /usr/bin/open} de macOS.
 *
 * <p>La ruta viaja siempre como un <strong>argumento propio</strong> del proceso, nunca pegada
 * dentro de una orden de shell. Es lo que hace que funcione sin sorpresas con los nombres que
 * genera esta aplicacion, que llevan corchetes ({@code acta[R].pdf}) y a menudo espacios: un
 * shell interpretaria esos caracteres, y {@link ProcessBuilder} no interpreta nada.</p>
 *
 * <p>Esta clase no guarda estado mutable; cada llamada lanza un proceso corto.</p>
 */
public final class AbridorMacOs implements AbridorDeArchivos {

    private static final Logger log = LoggerFactory.getLogger(AbridorMacOs.class);

    private static final String EJECUTABLE = "/usr/bin/open";

    /** Opcion de {@code open} que abre el Finder con el archivo seleccionado. */
    private static final String REVELAR = "-R";

    private static final int ESPERA_SEGUNDOS = 10;

    @Override
    public void mostrarEnElGestor(Path archivo) {
        ejecutar(ordenPara(Accion.MOSTRAR, exigirQueExista(archivo)), archivo);
    }

    @Override
    public void abrir(Path archivo) {
        ejecutar(ordenPara(Accion.ABRIR, exigirQueExista(archivo)), archivo);
    }

    /** Que se quiere hacer con el archivo. */
    enum Accion {

        /** Mostrarlo en el Finder, seleccionado dentro de su carpeta. */
        MOSTRAR,

        /** Abrirlo con la aplicacion por omision. */
        ABRIR
    }

    /**
     * Arma la orden del sistema. La ruta va entera en su propio argumento, tal cual, sin comillas
     * ni escapes: los pone el sistema operativo, no nosotros.
     *
     * @param accion  que hacer con el archivo
     * @param archivo documento, ya comprobado
     * @return los argumentos del proceso, empezando por el ejecutable
     */
    static List<String> ordenPara(Accion accion, Path archivo) {
        Objects.requireNonNull(accion, "accion es obligatoria");
        Objects.requireNonNull(archivo, "archivo es obligatorio");
        String ruta = archivo.toAbsolutePath().toString();
        return accion == Accion.MOSTRAR
                ? List.of(EJECUTABLE, REVELAR, ruta)
                : List.of(EJECUTABLE, ruta);
    }

    private Path exigirQueExista(Path archivo) {
        Objects.requireNonNull(archivo, "archivo es obligatorio");
        if (!Files.exists(archivo)) {
            throw new FirmaException("El archivo ya no está en " + archivo
                    + ". Puede que lo haya movido o borrado.");
        }
        return archivo;
    }

    private void ejecutar(List<String> orden, Path archivo) {
        ProcessBuilder constructor = new ProcessBuilder(orden);
        constructor.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        constructor.redirectError(ProcessBuilder.Redirect.DISCARD);
        try {
            Process proceso = constructor.start();
            if (!proceso.waitFor(ESPERA_SEGUNDOS, TimeUnit.SECONDS)) {
                proceso.destroyForcibly();
                log.warn("El comando open no respondió en {} segundos", ESPERA_SEGUNDOS);
                return;
            }
            if (proceso.exitValue() != 0) {
                throw new FirmaException("El sistema no pudo abrir " + archivo
                        + " (código " + proceso.exitValue() + ")");
            }
        } catch (IOException e) {
            throw new FirmaException("No se pudo abrir " + archivo + " con el sistema", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FirmaException("Se interrumpió la apertura de " + archivo, e);
        }
    }
}
