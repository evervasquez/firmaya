package pe.firmador.escritorio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.firmador.FirmaException;

/**
 * Almacen de contrasenas apoyado en el llavero de macOS, al que se llega invocando el
 * comando {@code /usr/bin/security} del propio sistema. No hace falta ninguna dependencia
 * ni codigo nativo: el llavero se encarga de cifrar el secreto y de atarlo a la sesion de
 * inicio del usuario.
 *
 * <p><strong>La contrasena nunca viaja como argumento del comando.</strong> Todo lo que se
 * pasa en la linea de ordenes es visible para cualquier proceso del equipo con un simple
 * {@code ps}, asi que {@code add-generic-password} se invoca con {@code -w} al final, sin
 * valor: en esa forma {@code security} pide el dato por su entrada estandar (lo solicita dos
 * veces, para confirmarlo), y es ahi donde se escribe.</p>
 *
 * <p>El dato se guarda codificado en Base64 y no en claro. No es una medida de seguridad
 * (el llavero ya cifra el contenido), sino de correccion: {@code find-generic-password -w}
 * imprime el valor en hexadecimal cuando contiene algo que no sea ASCII imprimible, y no hay
 * forma de distinguir esa salida de una contrasena que casualmente solo tenga digitos
 * hexadecimales, como {@code abc123}. Con Base64 la salida es siempre ASCII imprimible y la
 * lectura es exacta para cualquier contrasena.</p>
 *
 * <p>El secreto se guarda y se busca siempre en el <strong>llavero de inicio de sesion</strong>
 * del usuario, nombrandolo de forma explicita en cada comando. Nunca se toca el llavero del
 * sistema ({@code /Library/Keychains/System.keychain}): escribir ahi obliga a macOS a pedir
 * credenciales de administrador, y esta aplicacion no las pide jamas.</p>
 *
 * <p>Fuera de macOS, o si el comando no esta disponible, {@link #disponible()} devuelve
 * {@code false} y la clase se degrada: no guarda nada y nunca recupera nada, de modo que la
 * aplicacion sigue funcionando pidiendo la contrasena en cada firma.</p>
 *
 * <p>Esta clase no guarda estado mutable, pero cada llamada lanza un proceso externo; se usa
 * desde el hilo de la interfaz y sus operaciones son cortas.</p>
 */
public final class LlaveroMacOs implements AlmacenContrasenas {

    private static final Logger log = LoggerFactory.getLogger(LlaveroMacOs.class);

    /** Nombre de servicio propio del firmador, para no pisar elementos de otras aplicaciones. */
    private static final String SERVICIO = "pe.firmador.certificado";

    /** Lo que el usuario vera en Acceso a Llaveros al buscar el elemento. */
    private static final String ETIQUETA = "Firmaya";

    private static final String COMENTARIO =
            "Contraseña del certificado .p12, guardada a pedido del usuario por Firmaya "
                    + "y codificada en Base64.";

    private static final String EJECUTABLE = "/usr/bin/security";

    /** {@code errSecItemNotFound}: el elemento no existe. No es un fallo. */
    private static final int NO_ENCONTRADO = 44;

    /** Codigo propio para "el comando no llego a responder"; no lo usa {@code security}. */
    private static final int SIN_RESPUESTA = -1;

    /**
     * Tope de espera del comando. Con el llavero abierto responde en milisegundos; el tope
     * existe para que un llavero bloqueado que abre un dialogo del sistema no deje la
     * ventana congelada: al vencer, se pide la contrasena a mano.
     */
    private static final int ESPERA_SEGUNDOS = 10;

    private final boolean disponible;
    private final String llaveroDelUsuario;

    /** Comprueba una sola vez si el equipo puede usar el llavero y cual es el del usuario. */
    public LlaveroMacOs() {
        this.disponible = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac")
                && Files.isExecutable(Path.of(EJECUTABLE));
        this.llaveroDelUsuario = disponible ? llaveroDeInicioDeSesion() : "";
    }

    /**
     * Ruta del llavero de inicio de sesion, preguntandosela al propio {@code security} en vez de
     * suponerla. Si no responde, se queda vacia y los comandos usan el llavero por omision del
     * usuario, que es ese mismo.
     */
    private String llaveroDeInicioDeSesion() {
        Resultado resultado = ejecutar(List.of(EJECUTABLE, "default-keychain", "-d", "user"),
                new byte[0]);
        if (resultado.codigo() != 0) {
            return "";
        }
        return new String(resultado.salida(), StandardCharsets.UTF_8).replace("\"", "").trim();
    }

    /** Agrega al comando el llavero del usuario, cuando se sabe cual es. */
    private List<String> enElLlaveroDelUsuario(List<String> comando) {
        if (llaveroDelUsuario.isBlank()) {
            return comando;
        }
        List<String> completo = new ArrayList<>(comando);
        completo.add(llaveroDelUsuario);
        return completo;
    }

    @Override
    public boolean disponible() {
        return disponible;
    }

    @Override
    public Optional<char[]> recuperar(String cuenta) {
        Objects.requireNonNull(cuenta, "cuenta es obligatoria");
        if (!disponible) {
            return Optional.empty();
        }
        Resultado resultado = ejecutar(enElLlaveroDelUsuario(
                List.of(EJECUTABLE, "find-generic-password", "-w", "-s", SERVICIO, "-a", cuenta)),
                new byte[0]);
        if (resultado.codigo() == NO_ENCONTRADO) {
            return Optional.empty();
        }
        if (resultado.codigo() != 0) {
            log.warn("El llavero de macOS no devolvió la contraseña (código {})", resultado.codigo());
            return Optional.empty();
        }
        return descodificar(resultado.salida());
    }

    @Override
    public void guardar(String cuenta, char[] contrasena) {
        Objects.requireNonNull(cuenta, "cuenta es obligatoria");
        Objects.requireNonNull(contrasena, "contraseña es obligatoria");
        if (!disponible) {
            throw new FirmaException("El llavero de macOS no está disponible en este equipo");
        }
        byte[] entrada = entradaConfirmada(contrasena);
        try {
            Resultado resultado = ejecutar(enElLlaveroDelUsuario(
                    List.of(EJECUTABLE, "add-generic-password", "-U", "-s", SERVICIO, "-a", cuenta,
                            "-l", ETIQUETA, "-j", COMENTARIO, "-w")), entrada);
            if (resultado.codigo() != 0) {
                throw new FirmaException("El llavero de macOS no aceptó guardar la contraseña"
                        + " del certificado " + cuenta + " (código " + resultado.codigo() + ")");
            }
        } finally {
            Arrays.fill(entrada, (byte) 0);
        }
    }

    @Override
    public void olvidar(String cuenta) {
        Objects.requireNonNull(cuenta, "cuenta es obligatoria");
        if (!disponible) {
            return;
        }
        Resultado resultado = ejecutar(enElLlaveroDelUsuario(
                List.of(EJECUTABLE, "delete-generic-password", "-s", SERVICIO, "-a", cuenta)),
                new byte[0]);
        if (resultado.codigo() != 0 && resultado.codigo() != NO_ENCONTRADO) {
            log.warn("No se pudo borrar la contraseña del llavero (código {})", resultado.codigo());
        }
    }

    /** Salida de un comando: su codigo de retorno y lo que escribio por la salida estandar. */
    private record Resultado(int codigo, byte[] salida) {
    }

    /**
     * Lanza {@code security} con la entrada ya preparada.
     *
     * <p>Se espera al proceso <em>antes</em> de leer su salida, y no al reves: leer primero
     * bloquearia sin tope si el comando se quedara esperando (por ejemplo, con un dialogo del
     * sistema abierto). Las salidas que interesan aqui son de unos pocos cientos de bytes,
     * muy por debajo del tamano de la tuberia, asi que el proceso nunca se bloquea por no
     * poder escribir.</p>
     */
    private Resultado ejecutar(List<String> comando, byte[] entrada) {
        ProcessBuilder constructor = new ProcessBuilder(comando);
        // El error estandar solo trae los avisos interactivos del comando; se descarta para
        // no arrastrarlo al registro y para que nunca pueda acabar ahi un dato del llavero.
        constructor.redirectError(ProcessBuilder.Redirect.DISCARD);
        try {
            Process proceso = constructor.start();
            // Cerrar la entrada es lo que le dice al comando que ya no hay mas datos.
            try (OutputStream hacia = proceso.getOutputStream()) {
                hacia.write(entrada);
            }
            try (InputStream desde = proceso.getInputStream()) {
                if (!proceso.waitFor(ESPERA_SEGUNDOS, TimeUnit.SECONDS)) {
                    proceso.destroyForcibly();
                    log.warn("El comando security no respondió en {} segundos", ESPERA_SEGUNDOS);
                    return new Resultado(SIN_RESPUESTA, new byte[0]);
                }
                return new Resultado(proceso.exitValue(), desde.readAllBytes());
            }
        } catch (IOException e) {
            log.warn("No se pudo ejecutar {}: {}", EJECUTABLE, e.getMessage());
            return new Resultado(SIN_RESPUESTA, new byte[0]);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Resultado(SIN_RESPUESTA, new byte[0]);
        }
    }

    /** La contrasena en Base64, repetida y terminada en salto de linea: el dato y su confirmacion. */
    private byte[] entradaConfirmada(char[] contrasena) {
        byte[] base64 = aBase64(contrasena);
        byte[] entrada = new byte[(base64.length + 1) * 2];
        System.arraycopy(base64, 0, entrada, 0, base64.length);
        entrada[base64.length] = '\n';
        System.arraycopy(base64, 0, entrada, base64.length + 1, base64.length);
        entrada[entrada.length - 1] = '\n';
        Arrays.fill(base64, (byte) 0);
        return entrada;
    }

    private byte[] aBase64(char[] contrasena) {
        ByteBuffer utf8 = StandardCharsets.UTF_8.encode(CharBuffer.wrap(contrasena));
        byte[] crudos = new byte[utf8.remaining()];
        utf8.get(crudos);
        limpiar(utf8);
        byte[] base64 = Base64.getEncoder().encode(crudos);
        Arrays.fill(crudos, (byte) 0);
        return base64;
    }

    private Optional<char[]> descodificar(byte[] salida) {
        byte[] recortado = sinSaltoFinal(salida);
        Arrays.fill(salida, (byte) 0);
        byte[] utf8;
        try {
            utf8 = Base64.getDecoder().decode(recortado);
        } catch (IllegalArgumentException e) {
            // El elemento existe pero no lo escribio esta aplicacion. Devolver algo distinto
            // de vacio haria fallar la firma con un "contraseña incorrecta" desconcertante.
            log.warn("El elemento del llavero no tiene el formato esperado;"
                    + " se pedirá la contraseña a mano", e);
            return Optional.empty();
        } finally {
            Arrays.fill(recortado, (byte) 0);
        }
        CharBuffer caracteres = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(utf8));
        char[] contrasena = new char[caracteres.remaining()];
        caracteres.get(contrasena);
        Arrays.fill(utf8, (byte) 0);
        limpiar(caracteres);
        return Optional.of(contrasena);
    }

    private byte[] sinSaltoFinal(byte[] salida) {
        int fin = salida.length;
        while (fin > 0 && (salida[fin - 1] == '\n' || salida[fin - 1] == '\r')) {
            fin--;
        }
        return Arrays.copyOf(salida, fin);
    }

    private void limpiar(ByteBuffer buffer) {
        if (buffer.hasArray()) {
            Arrays.fill(buffer.array(), (byte) 0);
        }
    }

    private void limpiar(CharBuffer buffer) {
        if (buffer.hasArray()) {
            Arrays.fill(buffer.array(), '\0');
        }
    }
}
