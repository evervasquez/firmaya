package pe.firmador;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Donde se guarda el documento firmado.
 *
 * <p>Son dos y solo dos: junto al original, que es lo que hace ReFirma y sigue siendo lo normal,
 * o en una carpeta que el usuario elige una vez y se recuerda entre sesiones.</p>
 *
 * <p>En los dos casos el nombre sale del documento de origen ({@code acta.pdf} produce
 * {@code acta[R].pdf}) y <strong>nunca se sobrescribe nada</strong>: si ese nombre ya esta
 * ocupado en la carpeta de destino, se sigue numerando hasta encontrar uno libre.</p>
 */
public sealed interface DestinoFirma {

    /**
     * Calcula el archivo a escribir para un documento de origen.
     *
     * @param pdfOrigen documento que se va a firmar
     * @return la ruta libre donde guardar el documento firmado
     * @throws FirmaException si la carpeta de destino no existe o no admite escritura
     */
    Path rutaPara(Path pdfOrigen);

    /**
     * @return texto corto para mostrar al usuario
     */
    String descripcion();

    /** Junto al documento original, en su misma carpeta. Es el comportamiento por defecto. */
    record JuntoAlOriginal() implements DestinoFirma {

        @Override
        public Path rutaPara(Path pdfOrigen) {
            Objects.requireNonNull(pdfOrigen, "pdfOrigen es obligatorio");
            return SolicitudFirma.rutaFirmada(pdfOrigen);
        }

        @Override
        public String descripcion() {
            return "Junto al documento original";
        }
    }

    /**
     * En una carpeta fija elegida por el usuario.
     *
     * @param carpeta carpeta donde se guardan los documentos firmados
     */
    record EnCarpeta(Path carpeta) implements DestinoFirma {

        /** @throws NullPointerException si falta la carpeta */
        public EnCarpeta {
            Objects.requireNonNull(carpeta, "carpeta es obligatoria");
            carpeta = carpeta.toAbsolutePath().normalize();
        }

        @Override
        public Path rutaPara(Path pdfOrigen) {
            Objects.requireNonNull(pdfOrigen, "pdfOrigen es obligatorio");
            comprobarQueSePuedeEscribir();
            return SolicitudFirma.rutaFirmadaEn(pdfOrigen, carpeta);
        }

        /**
         * Comprueba que la carpeta sirva antes de ponerse a firmar, para poder explicar el
         * problema en vez de fallar a mitad de camino.
         *
         * @throws FirmaException si la carpeta ya no esta, no es una carpeta o no deja escribir
         */
        public void comprobarQueSePuedeEscribir() {
            if (!Files.exists(carpeta)) {
                throw new FirmaException("La carpeta donde guarda los documentos firmados ya no "
                        + "existe: " + carpeta + ". Elija otra carpeta.");
            }
            if (!Files.isDirectory(carpeta)) {
                throw new FirmaException("El destino de los documentos firmados no es una carpeta: "
                        + carpeta + ". Elija otra carpeta.");
            }
            if (!Files.isWritable(carpeta)) {
                throw new FirmaException("No se puede guardar en " + carpeta + ": macOS no da "
                        + "permiso de escritura sobre esa carpeta. Elija otra, o concédale acceso "
                        + "a la aplicación en Ajustes del Sistema → Privacidad y seguridad.");
            }
        }

        @Override
        public String descripcion() {
            return carpeta.toString();
        }
    }
}
