package pe.firmador.escritorio;

import java.util.Objects;

import pe.firmador.DatosCertificado;
import pe.firmador.OrigenClave;

/**
 * En que situacion esta el certificado de la aplicacion al arrancar o despues de configurarlo.
 *
 * <p>Son tres y solo tres: todavia no hay ninguno, hay uno listo para firmar, o hay uno
 * configurado que hoy no se puede usar y hay que explicarle al usuario por que.</p>
 */
public sealed interface EstadoCertificado {

    /** Todavia no se ha configurado ningun certificado. */
    record SinConfigurar() implements EstadoCertificado {
    }

    /**
     * Hay un certificado configurado y se pudo abrir: la aplicacion puede firmar sin pedir nada.
     *
     * @param origen de donde sale la clave
     * @param datos  titular, documento, emisor y vigencia leidos del certificado
     */
    record Listo(OrigenClave origen, DatosCertificado datos) implements EstadoCertificado {

        /** @throws NullPointerException si falta alguno de los dos datos */
        public Listo {
            Objects.requireNonNull(origen, "origen es obligatorio");
            Objects.requireNonNull(datos, "datos es obligatorio");
        }
    }

    /**
     * Hay un certificado configurado pero hoy no se puede usar: el archivo se movio, la
     * contrasena guardada ya no lo abre o la identidad desaparecio del llavero.
     *
     * @param titular nombre recordado del titular, o cadena vacia si no se recuerda
     * @param mensaje explicacion en espanol de que pasa y que hacer
     */
    record Problema(String titular, String mensaje) implements EstadoCertificado {

        /** @throws NullPointerException si falta alguno de los dos datos */
        public Problema {
            Objects.requireNonNull(titular, "titular es obligatorio");
            Objects.requireNonNull(mensaje, "mensaje es obligatorio");
        }
    }
}
