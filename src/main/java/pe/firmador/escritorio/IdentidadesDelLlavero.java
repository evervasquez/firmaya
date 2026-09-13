package pe.firmador.escritorio;

import java.util.List;
import java.util.Optional;

/**
 * Las identidades con clave privada que el usuario tiene en su llavero y puede usar para firmar.
 *
 * <p>Existe como interfaz para que las pruebas no toquen el llavero real del equipo: en las
 * pruebas se usa una lista fija en memoria.</p>
 *
 * <p>Las implementaciones no tienen por que ser seguras para varios hilos: se usan desde el
 * hilo de la interfaz.</p>
 */
public interface IdentidadesDelLlavero {

    /**
     * @return {@code true} si en este equipo se puede leer el llavero
     */
    boolean disponible();

    /**
     * Identidades que sirven de verdad para firmar documentos y se pueden usar sin pedir
     * permisos de administrador: las del llavero de <strong>inicio de sesion</strong> del
     * usuario cuyo certificado declara que vale para firmar.
     *
     * <p>Se dejan fuera dos grupos. Los certificados de <em>firma de codigo</em> —los de Apple
     * Developer, por ejemplo—, porque una firma hecha con ellos no vale como firma de un
     * documento. Y las identidades del llavero del sistema, porque usar su clave privada obliga
     * a macOS a pedir credenciales de administrador, y esta aplicacion no las pide nunca.</p>
     *
     * @return lista inmutable, vacia si no hay ninguna o el llavero no respondio
     */
    List<IdentidadLlavero> disponibles();

    /**
     * Busca una identidad concreta por su alias, se muestre o no en {@link #disponibles()}.
     *
     * <p>Hace falta porque la lista que se ofrece al usuario esta agrupada: un mismo certificado
     * puede estar varias veces en el llavero y solo se muestra una. Al recuperar lo que quedo
     * configurado hay que poder encontrar el alias exacto que se guardo.</p>
     *
     * @param alias nombre con el que el llavero identifica la identidad
     * @return la identidad, o vacio si ya no esta o no sirve para firmar
     */
    Optional<IdentidadLlavero> buscar(String alias);

    /**
     * @return cuantas identidades <strong>aptas para firmar documentos</strong> se quedaron fuera
     *         por estar en el llavero del sistema; sirve para explicarle al usuario por que no ve
     *         la suya y que puede hacer
     */
    int aptasEnElLlaveroDelSistema();
}
