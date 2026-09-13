package pe.firmador.escritorio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.firmador.DestinoFirma;
import pe.firmador.MotivosReFirma;
import pe.firmador.NivelFirma;
import pe.firmador.OrigenClave;

/**
 * Lo que la aplicacion recuerda entre sesiones: la ultima carpeta usada, el certificado
 * configurado con su titular y la ultima colocacion del sello.
 *
 * <p><strong>Aqui no se guarda ningun secreto.</strong> Solo hay rutas, numeros, el motivo
 * elegido y el nombre del titular, que es el mismo que queda impreso en el sello del
 * documento firmado. La contrasena del certificado nunca se escribe en las preferencias ni
 * en disco en claro: si el usuario pide expresamente que se recuerde, va al llavero del
 * sistema a traves de {@link AlmacenContrasenas}.</p>
 *
 * <p>Se apoya en {@link Preferences} del JDK, que en macOS escribe en el dominio de
 * preferencias del usuario. Si el almacen no esta disponible, la aplicacion sigue
 * funcionando con los valores por defecto.</p>
 *
 * <p>Esta clase no es segura para varios hilos; se usa solo desde el hilo de la interfaz.</p>
 */
public final class PreferenciasUsuario {

    private static final Logger log = LoggerFactory.getLogger(PreferenciasUsuario.class);

    private static final String CLAVE_CARPETA = "carpeta.última";
    private static final String CLAVE_CERTIFICADO = "certificado.ultimo";
    private static final String CLAVE_TITULAR = "certificado.titular";
    private static final String CLAVE_ORIGEN_TIPO = "certificado.origen.tipo";
    private static final String CLAVE_ORIGEN_VALOR = "certificado.origen.valor";
    private static final String PREFIJO_CONOCIDO = "certificado.conocido.";

    /** Cuantos certificados ya usados se recuerdan para volver a elegirlos sin buscarlos. */
    private static final int MAXIMO_CONOCIDOS = 8;

    private static final String TIPO_ARCHIVO = "archivo";
    private static final String TIPO_LLAVERO = "llavero";
    private static final String CLAVE_MOTIVO = "motivo.indice";
    private static final String CLAVE_X = "sello.x";
    private static final String CLAVE_Y = "sello.y";
    private static final String CLAVE_ANCHO = "sello.ancho";
    private static final String CLAVE_TSA = "tsa.url";
    private static final String CLAVE_DESTINO_MODO = "destino.modo";
    private static final String CLAVE_DESTINO_CARPETA = "destino.carpeta";

    private static final String DESTINO_EN_CARPETA = "carpeta";

    private static final double SIN_VALOR = -1;

    private final Preferences almacen;

    /** Abre el almacen de preferencias del usuario para este paquete. */
    public PreferenciasUsuario() {
        this(Preferences.userNodeForPackage(PreferenciasUsuario.class));
    }

    /**
     * @param almacen almacen a usar; se inyecta para que las pruebas no toquen el del usuario
     */
    public PreferenciasUsuario(Preferences almacen) {
        this.almacen = Objects.requireNonNull(almacen, "almacen es obligatorio");
    }

    /**
     * @return la ultima carpeta usada, si todavia existe
     */
    public Optional<Path> ultimaCarpeta() {
        return rutaExistente(CLAVE_CARPETA).filter(Files::isDirectory);
    }

    /**
     * @param carpeta carpeta a recordar
     */
    public void recordarCarpeta(Path carpeta) {
        Objects.requireNonNull(carpeta, "carpeta es obligatoria");
        almacen.put(CLAVE_CARPETA, carpeta.toAbsolutePath().toString());
    }

    /**
     * @return la ruta del ultimo certificado usado, si el archivo todavia existe
     */
    public Optional<Path> ultimoCertificado() {
        return rutaExistente(CLAVE_CERTIFICADO).filter(Files::isRegularFile);
    }

    /**
     * Recuerda la ruta del certificado configurado.
     *
     * <p>La contrasena no se guarda aqui: si el usuario pidio recordarla, vive en el llavero
     * del sistema, detras de {@link AlmacenContrasenas}.</p>
     *
     * @param certificado ruta del archivo .p12
     */
    public void recordarCertificado(Path certificado) {
        Objects.requireNonNull(certificado, "certificado es obligatorio");
        almacen.put(CLAVE_CERTIFICADO, certificado.toAbsolutePath().toString());
    }

    /**
     * Titular leido la ultima vez que se comprobo el certificado configurado. Sirve para
     * mostrarlo y dibujar la vista previa del sello sin tener que abrir el .p12 al arrancar.
     *
     * @return el nombre comun (CN) recordado, o cadena vacia si todavia no se comprobo ninguno
     */
    public String titularCertificado() {
        return almacen.get(CLAVE_TITULAR, "");
    }

    /**
     * @param titular nombre comun (CN) leido del certificado
     */
    public void recordarTitular(String titular) {
        Objects.requireNonNull(titular, "titular es obligatorio");
        almacen.put(CLAVE_TITULAR, titular);
    }

    /**
     * De donde sale la clave con la que se firma, tal como quedo configurada la ultima vez.
     *
     * <p>Un origen de archivo cuyo .p12 ya no existe se sigue devolviendo: quien llama necesita
     * saber que habia uno configurado para poder explicar que se movio, en vez de mostrar la
     * pantalla de «todavia no hay certificado» como si nunca se hubiera configurado.</p>
     *
     * @return el origen configurado, o vacio si nunca se configuro ninguno
     */
    public Optional<OrigenClave> origenConfigurado() {
        String tipo = almacen.get(CLAVE_ORIGEN_TIPO, "");
        String valor = almacen.get(CLAVE_ORIGEN_VALOR, "");
        return construirOrigen(tipo, valor);
    }

    /**
     * Deja configurado el certificado con el que se firma y lo agrega a la lista de conocidos,
     * al principio y sin repetirlo.
     *
     * <p>La contrasena no se guarda aqui nunca: si el usuario pidio recordarla, vive en el
     * llavero del sistema, detras de {@link AlmacenContrasenas}.</p>
     *
     * @param origen  de donde sale la clave
     * @param titular nombre del titular leido del certificado
     */
    public void recordarOrigen(OrigenClave origen, String titular) {
        Objects.requireNonNull(origen, "origen es obligatorio");
        Objects.requireNonNull(titular, "titular es obligatorio");

        almacen.put(CLAVE_ORIGEN_TIPO, origen instanceof OrigenClave.Llavero ? TIPO_LLAVERO : TIPO_ARCHIVO);
        almacen.put(CLAVE_ORIGEN_VALOR, valorDe(origen));
        almacen.put(CLAVE_TITULAR, titular);
        if (origen instanceof OrigenClave.Archivo archivo) {
            recordarCertificado(archivo.ruta());
        }
        guardarConocidos(conListaEncabezadaPor(new CertificadoRecordado(origen, titular)));
    }

    /**
     * Certificados que ya se configuraron antes, el mas reciente primero, para que el usuario
     * los reconozca y no tenga que volver a buscarlos en el disco.
     *
     * @return lista inmutable; los archivos que ya no existen quedan fuera
     */
    public List<CertificadoRecordado> certificadosConocidos() {
        List<CertificadoRecordado> conocidos = new ArrayList<>();
        for (int posicion = 0; posicion < MAXIMO_CONOCIDOS; posicion++) {
            leerConocido(posicion).ifPresent(conocidos::add);
        }
        return List.copyOf(conocidos);
    }

    /**
     * Olvida un certificado de la lista de conocidos. Se usa cuando el usuario ya no lo quiere
     * ver ofrecido.
     *
     * @param origen certificado a quitar de la lista
     */
    public void olvidarConocido(OrigenClave origen) {
        Objects.requireNonNull(origen, "origen es obligatorio");
        List<CertificadoRecordado> restantes = new ArrayList<>(certificadosConocidos());
        restantes.removeIf(conocido -> conocido.origen().equals(origen));
        guardarConocidos(restantes);
    }

    private List<CertificadoRecordado> conListaEncabezadaPor(CertificadoRecordado nuevo) {
        List<CertificadoRecordado> lista = new ArrayList<>();
        lista.add(nuevo);
        for (CertificadoRecordado conocido : certificadosConocidos()) {
            if (!conocido.origen().equals(nuevo.origen()) && lista.size() < MAXIMO_CONOCIDOS) {
                lista.add(conocido);
            }
        }
        return lista;
    }

    private void guardarConocidos(List<CertificadoRecordado> conocidos) {
        for (int posicion = 0; posicion < MAXIMO_CONOCIDOS; posicion++) {
            if (posicion < conocidos.size()) {
                escribirConocido(posicion, conocidos.get(posicion));
            } else {
                borrarConocido(posicion);
            }
        }
    }

    private void escribirConocido(int posicion, CertificadoRecordado conocido) {
        String prefijo = PREFIJO_CONOCIDO + posicion + ".";
        almacen.put(prefijo + "tipo",
                conocido.origen() instanceof OrigenClave.Llavero ? TIPO_LLAVERO : TIPO_ARCHIVO);
        almacen.put(prefijo + "valor", valorDe(conocido.origen()));
        almacen.put(prefijo + "titular", conocido.titular());
    }

    private void borrarConocido(int posicion) {
        String prefijo = PREFIJO_CONOCIDO + posicion + ".";
        almacen.remove(prefijo + "tipo");
        almacen.remove(prefijo + "valor");
        almacen.remove(prefijo + "titular");
    }

    private Optional<CertificadoRecordado> leerConocido(int posicion) {
        String prefijo = PREFIJO_CONOCIDO + posicion + ".";
        String tipo = almacen.get(prefijo + "tipo", "");
        String valor = almacen.get(prefijo + "valor", "");
        String titular = almacen.get(prefijo + "titular", "");
        return construirOrigen(tipo, valor)
                .filter(this::sigueExistiendo)
                .map(origen -> new CertificadoRecordado(origen, titular));
    }

    /** Un .p12 borrado ya no se ofrece; una identidad del llavero solo la puede confirmar el llavero. */
    private boolean sigueExistiendo(OrigenClave origen) {
        if (origen instanceof OrigenClave.Archivo archivo) {
            return Files.isRegularFile(archivo.ruta());
        }
        return true;
    }

    private Optional<OrigenClave> construirOrigen(String tipo, String valor) {
        if (valor.isBlank()) {
            return Optional.empty();
        }
        if (TIPO_LLAVERO.equals(tipo)) {
            return Optional.of(new OrigenClave.Llavero(valor));
        }
        if (TIPO_ARCHIVO.equals(tipo)) {
            return Optional.of(new OrigenClave.Archivo(Path.of(valor)));
        }
        return Optional.empty();
    }

    private String valorDe(OrigenClave origen) {
        if (origen instanceof OrigenClave.Llavero llavero) {
            return llavero.alias();
        }
        return ((OrigenClave.Archivo) origen).ruta().toAbsolutePath().toString();
    }

    /**
     * @return indice del ultimo motivo elegido, siempre dentro de rango
     */
    public int ultimoMotivo() {
        int guardado = almacen.getInt(CLAVE_MOTIVO, MotivosReFirma.INDICE_POR_DEFECTO);
        boolean enRango = guardado >= 0 && guardado < MotivosReFirma.todos().size();
        return enRango ? guardado : MotivosReFirma.INDICE_POR_DEFECTO;
    }

    /**
     * @param indice indice del motivo elegido
     */
    public void recordarMotivo(int indice) {
        almacen.putInt(CLAVE_MOTIVO, indice);
    }

    /**
     * @return la ultima colocacion del sello, si se guardo alguna
     */
    public Optional<Recuadro> ultimoRecuadro() {
        double x = almacen.getDouble(CLAVE_X, SIN_VALOR);
        double y = almacen.getDouble(CLAVE_Y, SIN_VALOR);
        if (x < 0 || y < 0) {
            return Optional.empty();
        }
        // De versiones anteriores puede quedar guardado un tamano: se ignora. El sello mide
        // siempre lo que mide en ReFirma, asi que solo se recupera donde estaba.
        return Optional.of(new Recuadro(x, y));
    }

    /**
     * @param recuadro colocacion del sello a recordar
     */
    public void recordarRecuadro(Recuadro recuadro) {
        Objects.requireNonNull(recuadro, "recuadro es obligatorio");
        almacen.putDouble(CLAVE_X, recuadro.x());
        almacen.putDouble(CLAVE_Y, recuadro.y());
        almacen.remove(CLAVE_ANCHO);
    }

    /**
     * Donde se guardan los documentos firmados, tal como quedo configurado la ultima vez.
     *
     * <p>Si se habia elegido una carpeta y esa carpeta ya no existe, se vuelve a guardar junto al
     * original en vez de arrastrar una configuracion rota: el usuario siempre puede volver a
     * elegirla.</p>
     *
     * @return el destino configurado; junto al original si nunca se cambio
     */
    public DestinoFirma destinoDeLosFirmados() {
        if (!DESTINO_EN_CARPETA.equals(almacen.get(CLAVE_DESTINO_MODO, ""))) {
            return new DestinoFirma.JuntoAlOriginal();
        }
        String carpeta = almacen.get(CLAVE_DESTINO_CARPETA, "");
        if (carpeta.isBlank() || !Files.isDirectory(Path.of(carpeta))) {
            return new DestinoFirma.JuntoAlOriginal();
        }
        return new DestinoFirma.EnCarpeta(Path.of(carpeta));
    }

    /**
     * @param destino donde guardar los documentos firmados a partir de ahora
     */
    public void recordarDestino(DestinoFirma destino) {
        Objects.requireNonNull(destino, "destino es obligatorio");
        if (destino instanceof DestinoFirma.EnCarpeta enCarpeta) {
            almacen.put(CLAVE_DESTINO_MODO, DESTINO_EN_CARPETA);
            almacen.put(CLAVE_DESTINO_CARPETA, enCarpeta.carpeta().toString());
            return;
        }
        almacen.remove(CLAVE_DESTINO_MODO);
        almacen.remove(CLAVE_DESTINO_CARPETA);
    }

    /**
     * @return la direccion del servidor de sellado de tiempo recordada, o la de fabrica
     */
    public String urlSelloTiempo() {
        return almacen.get(CLAVE_TSA, NivelFirma.TSA_POR_DEFECTO);
    }

    /**
     * @param url direccion del servidor de sellado de tiempo
     */
    public void recordarUrlSelloTiempo(String url) {
        Objects.requireNonNull(url, "url es obligatoria");
        almacen.put(CLAVE_TSA, url);
    }

    /** Vuelca los cambios al almacen del sistema. Un fallo aqui no rompe la aplicacion. */
    public void guardar() {
        try {
            almacen.flush();
        } catch (BackingStoreException e) {
            // Perder las preferencias solo significa empezar la proxima sesion con los
            // valores por defecto: no justifica interrumpir al usuario.
            log.warn("No se pudieron guardar las preferencias: {}", e.getMessage());
        }
    }

    private Optional<Path> rutaExistente(String clave) {
        String guardada = almacen.get(clave, "");
        if (guardada.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Path.of(guardada));
    }
}
