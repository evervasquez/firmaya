package pe.firmador;

import pe.firmador.escritorio.AplicacionFirmador;

/**
 * Punto de entrada del JAR: decide si arrancar la interfaz grafica o la linea de comandos.
 *
 * <p>Sin argumentos abre la ventana; con argumentos se comporta exactamente como antes y
 * delega en {@link Firmador}.</p>
 *
 * <p>Es una clase aparte y no {@link AplicacionFirmador} a proposito: cuando JavaFX viaja
 * dentro del JAR (no en el {@code module-path}), arrancar desde una clase que extiende
 * {@code Application} falla con "JavaFX runtime components are missing". Este envoltorio lo
 * evita.</p>
 */
public final class Lanzador {

    private Lanzador() {
    }

    /**
     * @param argumentos argumentos de la linea de comandos; vacios para abrir la ventana
     */
    public static void main(String[] argumentos) {
        if (argumentos.length == 0) {
            AplicacionFirmador.main(argumentos);
            return;
        }
        Firmador.main(argumentos);
    }
}
