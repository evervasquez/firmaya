package pe.firmador.escritorio;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.Objects;

import javafx.concurrent.Task;

import pe.firmador.ServicioFirmaPades;
import pe.firmador.SolicitudFirma;

/**
 * Ejecuta la firma fuera del hilo de la interfaz.
 *
 * <p>Con un certificado de archivo, la contrasena llega como {@code char[]}, se usa una sola vez
 * y se limpia en cuanto termina la firma, salga bien o mal: la tarea toma posesion del arreglo y
 * el llamador no debe reutilizarlo. Con una identidad del llavero no hay contrasena que manejar,
 * porque la clave privada no sale del llavero.</p>
 */
public final class TareaFirma extends Task<Path> {

    private final ServicioFirmaPades servicio;
    private final SolicitudFirma solicitud;
    private final char[] contrasena;

    private TareaFirma(SolicitudFirma solicitud, char[] contrasena, Clock reloj) {
        this.solicitud = Objects.requireNonNull(solicitud, "solicitud es obligatoria");
        this.contrasena = contrasena;
        this.servicio = new ServicioFirmaPades(Objects.requireNonNull(reloj, "reloj es obligatorio"));
    }

    /**
     * @param solicitud  parametros de firma ya validados, con origen de archivo
     * @param contrasena contrasena del certificado; esta tarea la limpia al terminar
     * @param reloj      fuente de la hora de firma
     * @return la tarea lista para ejecutarse
     */
    public static TareaFirma conArchivo(SolicitudFirma solicitud, char[] contrasena, Clock reloj) {
        Objects.requireNonNull(contrasena, "contraseña es obligatoria");
        return new TareaFirma(solicitud, contrasena, reloj);
    }

    /**
     * @param solicitud parametros de firma ya validados, con origen en el llavero
     * @param reloj     fuente de la hora de firma
     * @return la tarea lista para ejecutarse
     */
    public static TareaFirma conLlavero(SolicitudFirma solicitud, Clock reloj) {
        return new TareaFirma(solicitud, null, reloj);
    }

    @Override
    protected Path call() {
        updateMessage("Firmando el documento...");
        if (contrasena == null) {
            return servicio.firmarConLlavero(solicitud);
        }
        try {
            return servicio.firmar(solicitud, contrasena);
        } finally {
            Arrays.fill(contrasena, '\0');
        }
    }
}
