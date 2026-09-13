package pe.firmador.escritorio;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import pe.firmador.FirmaException;

/**
 * Almacen de contrasenas de juego, para que las pruebas no toquen el llavero real del equipo.
 */
final class AlmacenEnMemoria implements AlmacenContrasenas {

    private final Map<String, char[]> guardadas = new HashMap<>();

    private boolean disponible = true;

    @Override
    public boolean disponible() {
        return disponible;
    }

    /** Simula un equipo sin llavero disponible. */
    void marcarNoDisponible() {
        this.disponible = false;
    }

    @Override
    public Optional<char[]> recuperar(String cuenta) {
        if (!disponible) {
            return Optional.empty();
        }
        return Optional.ofNullable(guardadas.get(cuenta)).map(char[]::clone);
    }

    @Override
    public void guardar(String cuenta, char[] contrasena) {
        if (!disponible) {
            throw new FirmaException("El llavero no está disponible en este equipo");
        }
        guardadas.put(cuenta, contrasena.clone());
    }

    @Override
    public void olvidar(String cuenta) {
        guardadas.remove(cuenta);
    }

    /** Simula que el certificado se renovo: la contrasena guardada ya no es la que lo abre. */
    void cambiarPorUnaEquivocada(String cuenta) {
        guardadas.put(cuenta, "ya-no-sirve".toCharArray());
    }

    int cuantasGuardadas() {
        return guardadas.size();
    }
}
