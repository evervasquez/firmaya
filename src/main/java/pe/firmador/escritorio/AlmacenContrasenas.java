package pe.firmador.escritorio;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Guarda la contrasena de un certificado fuera de la aplicacion, en el almacen de secretos
 * del sistema operativo.
 *
 * <p>Guardarla es siempre una decision explicita del usuario. La aplicacion funciona igual
 * sin almacen: en ese caso pide la contrasena en cada firma.</p>
 *
 * <p>La contrasena viaja como {@code char[]} para poder borrarla de memoria en cuanto se
 * usa. Quien recibe el arreglo de {@link #recuperar(String)} es su dueno y debe limpiarlo
 * con {@code Arrays.fill} despues de usarlo.</p>
 *
 * <p>Las implementaciones no tienen por que ser seguras para varios hilos: se usan desde el
 * hilo de la interfaz.</p>
 */
public interface AlmacenContrasenas {

    /**
     * Nombre de cuenta con el que se identifica la contrasena de un certificado concreto.
     *
     * <p>Es la ruta absoluta y normalizada del archivo: asi, cambiar de certificado nunca
     * reutiliza por error la contrasena del anterior.</p>
     *
     * @param certificado ruta del archivo .p12
     * @return el nombre de cuenta bajo el que se guarda su contrasena
     */
    static String cuentaPara(Path certificado) {
        Objects.requireNonNull(certificado, "certificado es obligatorio");
        return certificado.toAbsolutePath().normalize().toString();
    }

    /**
     * @return {@code true} si este almacen puede usarse en el equipo actual
     */
    boolean disponible();

    /**
     * Busca la contrasena guardada para una cuenta.
     *
     * @param cuenta identificador del certificado, obtenido de {@link #cuentaPara(Path)}
     * @return la contrasena guardada, o vacio si no hay ninguna o el almacen no respondio
     */
    Optional<char[]> recuperar(String cuenta);

    /**
     * Guarda o reemplaza la contrasena de una cuenta.
     *
     * @param cuenta     identificador del certificado
     * @param contrasena contrasena a guardar; el arreglo sigue siendo del llamador, que es
     *                   quien debe limpiarlo
     * @throws pe.firmador.FirmaException si el almacen no pudo guardarla
     */
    void guardar(String cuenta, char[] contrasena);

    /**
     * Borra la contrasena de una cuenta. No falla si no habia ninguna guardada.
     *
     * @param cuenta identificador del certificado
     */
    void olvidar(String cuenta);
}
