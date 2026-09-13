package pe.firmador;

import java.nio.file.Path;
import java.util.Objects;

/**
 * De donde sale la clave privada con la que se firma.
 *
 * <p>Son dos caminos y solo dos: un archivo PKCS#12 en disco, que necesita contrasena, o una
 * identidad ya guardada en el llavero de inicio de sesion de macOS, que no la necesita porque
 * la custodia el sistema.</p>
 */
public sealed interface OrigenClave {

    /**
     * @return texto corto para mostrar al usuario, sin datos sensibles
     */
    String descripcion();

    /**
     * Archivo PKCS#12 en disco.
     *
     * @param ruta archivo .p12 o .pfx
     */
    record Archivo(Path ruta) implements OrigenClave {

        /** @throws NullPointerException si falta la ruta */
        public Archivo {
            Objects.requireNonNull(ruta, "ruta es obligatoria");
        }

        @Override
        public String descripcion() {
            return ruta.getFileName().toString();
        }
    }

    /**
     * Identidad del llavero de inicio de sesion de macOS.
     *
     * <p>Solo debe usarse con identidades del llavero del usuario. Las del llavero del sistema
     * ({@code /Library/Keychains/System.keychain}) exigen credenciales de administrador, y esta
     * aplicacion no las pide nunca.</p>
     *
     * @param alias nombre con el que el llavero identifica la identidad
     */
    record Llavero(String alias) implements OrigenClave {

        /**
         * @throws NullPointerException     si falta el alias
         * @throws IllegalArgumentException si el alias esta en blanco
         */
        public Llavero {
            Objects.requireNonNull(alias, "alias es obligatorio");
            if (alias.isBlank()) {
                throw new IllegalArgumentException("El alias de la identidad del llavero no puede estar vacío");
            }
        }

        @Override
        public String descripcion() {
            return alias;
        }
    }
}
