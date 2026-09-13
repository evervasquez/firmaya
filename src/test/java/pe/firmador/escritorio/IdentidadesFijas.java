package pe.firmador.escritorio;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lista de identidades de juego, para que las pruebas no lean el llavero real del equipo.
 */
final class IdentidadesFijas implements IdentidadesDelLlavero {

    private final List<IdentidadLlavero> identidades = new ArrayList<>();

    private int aptasDelSistema;

    @Override
    public boolean disponible() {
        return true;
    }

    @Override
    public List<IdentidadLlavero> disponibles() {
        return List.copyOf(identidades);
    }

    @Override
    public Optional<IdentidadLlavero> buscar(String alias) {
        return identidades.stream()
                .filter(identidad -> identidad.alias().equals(alias))
                .findFirst();
    }

    @Override
    public int aptasEnElLlaveroDelSistema() {
        return aptasDelSistema;
    }

    void agregar(IdentidadLlavero identidad) {
        identidades.add(identidad);
    }

    void quitarTodas() {
        identidades.clear();
    }

    void conAptasEnElLlaveroDelSistema(int cuantas) {
        this.aptasDelSistema = cuantas;
    }
}
