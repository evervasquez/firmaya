package pe.firmador.escritorio;

import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.firmador.DatosCertificado;
import pe.firmador.DatosFirma;
import pe.firmador.DestinoFirma;
import pe.firmador.FirmaException;
import pe.firmador.NivelFirma;
import pe.firmador.OrigenClave;
import pe.firmador.SolicitudFirma;
import pe.firmador.VerificadorPdf;

/**
 * Ventana principal del firmador: abrir el PDF, colocar el sello sobre la pagina, elegir el
 * motivo y firmar.
 *
 * <p>El certificado se configura aparte, una sola vez, en {@link VentanaCertificado}. A partir
 * de ahi la aplicacion firma sin pedir nada: si la clave esta en el llavero, la usa el sistema;
 * si es un archivo .p12, la contrasena sale del llavero y se limpia de memoria en cuanto se
 * usa.</p>
 *
 * <p>Todo el trabajo pesado (dibujar paginas y firmar) ocurre fuera del hilo de la interfaz.
 * El hilo de la interfaz solo pinta y recoge decisiones del usuario.</p>
 */
public final class AplicacionFirmador extends Application {

    private static final Logger log = LoggerFactory.getLogger(AplicacionFirmador.class);

    private static final String TITULO = "Firmaya";
    private static final double ANCHO_VENTANA = 1180;
    private static final double ALTO_VENTANA = 820;
    private static final double MARGEN_SELLO_PT = 20;

    /** Lo que el visor reserva alrededor de la página, en píxeles: el margen de PanelDocumento. */
    private static final double MARGEN_VISOR_PX = 56;

    /**
     * Lo que ofrece el desplegable de zoom. Los dos ajustes van primero y el documento se abre
     * con el primero: una hoja entera a la vista es lo que deja decidir donde va la firma.
     */
    private static final List<ModoZoom> ZOOMS = List.of(
            new ModoZoom.AjustarALaVentana(),
            new ModoZoom.AjustarAlAncho(),
            new ModoZoom.Fijo(0.75),
            new ModoZoom.Fijo(1.0),
            new ModoZoom.Fijo(1.25),
            new ModoZoom.Fijo(1.5),
            new ModoZoom.Fijo(2.0));

    /** Cuanto tiene que cambiar el visor para merecer un redibujado al ajustar. */
    private static final double CAMBIO_MINIMO_PX = 4;

    /** Ancho del campo de página: entra un número de cuatro cifras, que es más que suficiente. */
    private static final double ANCHO_CAMPO_PAGINA = 52;

    private final Clock reloj = Clock.systemUTC();
    private final PreferenciasUsuario preferencias = new PreferenciasUsuario();
    private final ConfiguracionCertificado configuracion = new ConfiguracionCertificado(
            preferencias, new LlaveroMacOs(), new LlaveroIdentidadesMacOs(reloj), reloj);
    private final AbridorDeArchivos abridor = new AbridorMacOs();
    private final PanelDocumento panelDocumento = new PanelDocumento();
    private final Label avisoGiro = new Label();
    private final ChoiceBox<String> zoom = new ChoiceBox<>();
    private final ScrollPane desplazable = new ScrollPane();
    private final Button primera = new Button("|<");
    private final Button anterior = new Button("<");
    private final Button siguiente = new Button(">");
    private final Button ultima = new Button(">|");
    private final TextField paginaEscrita = new TextField();
    private final Label totalPaginas = new Label();
    private final TextArea resultado = new TextArea();

    private PanelFirma panelFirma;
    private EstadoCertificado certificado = new EstadoCertificado.SinConfigurar();
    private Stage ventana;
    private ExecutorService dibujante;
    private RenderizadorPdf renderizador;
    private Path pdfAbierto;
    private int paginaActual = 1;

    @Override
    public void start(Stage escenario) {
        this.ventana = escenario;
        this.panelFirma = new PanelFirma(preferencias, reloj, new TarjetaCertificado(reloj),
                new AccesoDocumentoFirmado(abridor, mensaje -> avisar("No se pudo abrir", mensaje)),
                new SelectorDestino(this::elegirCarpetaDeDestino, this::recordarDestino));
        this.dibujante = Executors.newSingleThreadExecutor(hiloDemonio("dibujante-pdf"));

        conectarPanelFirma();
        conectarRecuadro();
        // El certificado ya configurado se carga solo: si su contrasena esta en el llavero, la
        // ventana abre lista para firmar sin pedir nada.
        aplicarCertificado(configuracion.cargar());

        Scene escena = new Scene(construirRaiz(), ANCHO_VENTANA, ALTO_VENTANA);
        habilitarArrastreDeArchivos(escena);

        // Red de seguridad: cualquier fallo que se escape de un manejador de eventos acabaria
        // en la consola, invisible para el usuario. Aqui se convierte en un aviso en pantalla.
        Thread.currentThread().setUncaughtExceptionHandler((hilo, fallo) -> mostrarError(fallo));

        escenario.setTitle(TITULO);
        escenario.setScene(escena);
        escenario.show();
        actualizarControles();
    }

    @Override
    public void stop() {
        preferencias.guardar();
        if (dibujante != null) {
            dibujante.shutdownNow();
        }
        cerrarDocumento();
    }

    /**
     * Arranca la interfaz grafica.
     *
     * @param argumentos argumentos de la linea de comandos, que esta ventana ignora
     */
    public static void main(String[] argumentos) {
        launch(argumentos);
    }

    private BorderPane construirRaiz() {
        desplazable.setContent(new StackPane(panelDocumento));
        desplazable.setPannable(false);
        desplazable.setFitToWidth(true);
        desplazable.setFitToHeight(true);
        // Con un modo de ajuste activo, cambiar el tamaño de la ventana recalcula el aumento.
        desplazable.viewportBoundsProperty().addListener(
                (observable, previo, actual) -> ajustarAlNuevoTamano(previo, actual));

        resultado.setEditable(false);
        resultado.setPrefRowCount(6);
        resultado.setVisible(false);
        resultado.setManaged(false);

        BorderPane centro = new BorderPane(desplazable);
        centro.setBottom(resultado);

        BorderPane raiz = new BorderPane();
        raiz.setTop(construirBarra());
        raiz.setCenter(centro);
        raiz.setRight(panelFirma);
        return raiz;
    }

    private ToolBar construirBarra() {
        Button abrir = new Button("Abrir PDF...");
        abrir.setOnAction(evento -> elegirPdf());

        primera.setTooltip(new Tooltip("Primera página"));
        anterior.setTooltip(new Tooltip("Página anterior"));
        siguiente.setTooltip(new Tooltip("Página siguiente"));
        ultima.setTooltip(new Tooltip("Última página"));
        primera.setOnAction(evento -> irAPagina(1));
        anterior.setOnAction(evento -> irAPagina(paginaActual - 1));
        siguiente.setOnAction(evento -> irAPagina(paginaActual + 1));
        ultima.setOnAction(evento -> irAPagina(totalDePaginas()));

        for (ModoZoom modo : ZOOMS) {
            zoom.getItems().add(modo.etiqueta());
        }
        zoom.getSelectionModel().selectFirst();
        zoom.getSelectionModel().selectedIndexProperty()
                .addListener((observable, previo, actual) -> dibujarPagina(paginaActual));

        avisoGiro.setStyle("-fx-text-fill: #a15c00;");

        return new ToolBar(abrir, new Separator(),
                primera, anterior, campoDePagina(), totalPaginas, siguiente, ultima,
                new Separator(), new Label("Zoom"), zoom, new Separator(), avisoGiro);
    }

    /**
     * Campo editable de pagina, como el de ReFirma: se escribe el numero y se salta con Enter.
     * Lo que no sea una pagina del documento devuelve el campo a la pagina actual.
     */
    private TextField campoDePagina() {
        paginaEscrita.setPrefWidth(ANCHO_CAMPO_PAGINA);
        paginaEscrita.setAlignment(Pos.CENTER);
        paginaEscrita.setTooltip(new Tooltip("Escriba el número de página y pulse Enter"));
        paginaEscrita.setOnAction(evento -> saltarALoEscrito());
        paginaEscrita.focusedProperty().addListener((observable, tenia, tiene) -> {
            if (!tiene) {
                saltarALoEscrito();
            }
        });
        return paginaEscrita;
    }

    private void saltarALoEscrito() {
        if (renderizador == null) {
            return;
        }
        int pedida = NavegacionPaginas.interpretar(
                paginaEscrita.getText(), totalDePaginas(), paginaActual);
        if (pedida == paginaActual) {
            // Lo escrito no sirve o es la página en la que ya estamos: se repinta el número real.
            actualizarControles();
            return;
        }
        irAPagina(pedida);
    }

    private int totalDePaginas() {
        return renderizador == null ? 0 : renderizador.totalPaginas();
    }

    private void conectarPanelFirma() {
        panelFirma.alConfigurarCertificado(this::configurarCertificado);
        panelFirma.alFirmar(this::firmar);
    }

    private void conectarRecuadro() {
        panelDocumento.recuadroProperty().addListener((observable, previo, actual) -> {
            panelFirma.actualizarVistaPrevia(actual);
            actualizarControles();
        });
    }

    private void habilitarArrastreDeArchivos(Scene escena) {
        escena.setOnDragOver(this::aceptarArrastre);
        escena.setOnDragDropped(this::soltarArchivo);
    }

    private void aceptarArrastre(DragEvent evento) {
        if (primerPdf(evento.getDragboard()).isPresent()) {
            evento.acceptTransferModes(TransferMode.COPY);
        }
        evento.consume();
    }

    private void soltarArchivo(DragEvent evento) {
        Optional<Path> soltado = primerPdf(evento.getDragboard());
        evento.setDropCompleted(soltado.isPresent());
        evento.consume();
        soltado.ifPresent(this::abrirPdf);
    }

    private Optional<Path> primerPdf(Dragboard portapapeles) {
        if (!portapapeles.hasFiles()) {
            return Optional.empty();
        }
        return portapapeles.getFiles().stream()
                .map(File::toPath)
                .filter(ruta -> ruta.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
                .findFirst();
    }

    private void elegirPdf() {
        FileChooser selector = new FileChooser();
        selector.setTitle("Elegir documento PDF");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documentos PDF", "*.pdf"));
        preferencias.ultimaCarpeta().ifPresent(carpeta -> selector.setInitialDirectory(carpeta.toFile()));

        File elegido = selector.showOpenDialog(ventana);
        if (elegido != null) {
            abrirPdf(elegido.toPath());
        }
    }

    private void abrirPdf(Path pdf) {
        try {
            cerrarDocumento();
            renderizador = new RenderizadorPdf(pdf);
            pdfAbierto = pdf.toAbsolutePath();
            paginaActual = 1;
            ventana.setTitle(TITULO + " - " + pdf.getFileName());

            Path carpeta = pdfAbierto.getParent();
            if (carpeta != null) {
                preferencias.recordarCarpeta(carpeta);
            }
            resultado.setVisible(false);
            resultado.setManaged(false);
            panelDocumento.fijarRecuadro(null);
            dibujarPagina(1);
            contarFirmasExistentes(pdfAbierto);
        } catch (FirmaException e) {
            mostrarError(e);
        }
    }

    private void irAPagina(int pagina) {
        if (renderizador == null || pagina < 1 || pagina > renderizador.totalPaginas()) {
            return;
        }
        dibujarPagina(pagina);
    }

    private void dibujarPagina(int pagina) {
        if (renderizador == null) {
            return;
        }
        RenderizadorPdf enUso = renderizador;
        ModoZoom modo = zoomElegido();
        double anchoVisor = Math.max(1, desplazable.getViewportBounds().getWidth() - MARGEN_VISOR_PX);
        double altoVisor = Math.max(1, desplazable.getViewportBounds().getHeight() - MARGEN_VISOR_PX);

        // Todo lo que se lee del PDDocument se lee aqui, dentro del hilo dibujante:
        // PDFBox no es seguro para varios hilos.
        Task<PaginaDibujada> dibujo = new Task<>() {
            @Override
            protected PaginaDibujada call() {
                // El tamaño de la página se lee aquí, en el hilo dibujante, porque PDFBox no es
                // seguro para varios hilos; con él ya se puede resolver el aumento del ajuste.
                double anchoPt = enUso.tamano(pagina).getWidth();
                double altoPt = enUso.tamano(pagina).getHeight();
                double escala = modo.escalaPara(anchoPt, altoPt, anchoVisor, altoVisor);
                return new PaginaDibujada(
                        enUso.renderizar(pagina, escala),
                        pagina,
                        anchoPt,
                        altoPt,
                        escala,
                        enUso.rotacion(pagina));
            }
        };
        dibujo.setOnSucceeded(evento -> mostrarPagina(dibujo.getValue()));
        dibujo.setOnFailed(evento -> mostrarError(dibujo.getException()));
        dibujante.execute(dibujo);
    }

    private ModoZoom zoomElegido() {
        int elegido = zoom.getSelectionModel().getSelectedIndex();
        return ZOOMS.get(elegido < 0 ? 0 : elegido);
    }

    /**
     * Vuelve a dibujar la pagina cuando la ventana cambia de tamano y el zoom es de ajuste. Se
     * exige un cambio minimo para no entrar en un redibujado continuo: al redibujar cambia el
     * tamano del contenido y, con el, el del visor.
     */
    private void ajustarAlNuevoTamano(Bounds previo, Bounds actual) {
        if (renderizador == null || !zoomElegido().dependeDeLaVentana() || previo == null) {
            return;
        }
        boolean cambioDeVerdad = Math.abs(previo.getWidth() - actual.getWidth()) > CAMBIO_MINIMO_PX
                || Math.abs(previo.getHeight() - actual.getHeight()) > CAMBIO_MINIMO_PX;
        if (cambioDeVerdad) {
            dibujarPagina(paginaActual);
        }
    }

    private void mostrarPagina(PaginaDibujada dibujada) {
        paginaActual = dibujada.pagina();
        panelDocumento.mostrar(dibujada.imagen(), dibujada.pagina(),
                dibujada.anchoPt(), dibujada.altoPt(), dibujada.escala());

        // El recuadro acompana al usuario de pagina en pagina, ajustado a la que ahora ve:
        // si la nueva es mas pequena, se encoge para seguir cabiendo entera.
        Recuadro traido = panelDocumento.recuadroActual();
        panelDocumento.fijarRecuadro(traido == null
                ? recuadroInicial(dibujada)
                : traido.dentroDe(dibujada.anchoPt(), dibujada.altoPt()));
        avisoGiro.setText(dibujada.giro() == 0 ? "" : "Página girada " + dibujada.giro()
                + " grados: la posición del sello puede no coincidir con lo que ve.");
        actualizarControles();
    }

    /** Recupera la colocacion de la sesion anterior; si no hay, deja el sello abajo a la izquierda. */
    private Recuadro recuadroInicial(PaginaDibujada dibujada) {
        return preferencias.ultimoRecuadro()
                .orElseGet(() -> Recuadro.abajoIzquierda(dibujada.altoPt(), MARGEN_SELLO_PT))
                .dentroDe(dibujada.anchoPt(), dibujada.altoPt());
    }

    /** Resultado de dibujar una pagina, con todo lo que la interfaz necesita saber de ella. */
    private record PaginaDibujada(Image imagen, int pagina, double anchoPt, double altoPt,
                                  double escala, int giro) {
    }

    private void actualizarControles() {
        boolean hayDocumento = renderizador != null;
        int total = hayDocumento ? renderizador.totalPaginas() : 0;

        paginaEscrita.setText(hayDocumento ? String.valueOf(paginaActual) : "");
        paginaEscrita.setDisable(!hayDocumento);
        totalPaginas.setText(hayDocumento ? "de " + total : "Sin documento");
        primera.setDisable(!hayDocumento || paginaActual <= 1);
        anterior.setDisable(!hayDocumento || paginaActual <= 1);
        siguiente.setDisable(!hayDocumento || paginaActual >= total);
        ultima.setDisable(!hayDocumento || paginaActual >= total);
        zoom.setDisable(!hayDocumento);

        panelFirma.mostrarUbicacionDelSello(panelDocumento.recuadroActual(), total, paginaActual);
    }

    /** Abre el selector de carpetas del sistema para elegir donde se guardan los firmados. */
    private Optional<Path> elegirCarpetaDeDestino() {
        DirectoryChooser selector = new DirectoryChooser();
        selector.setTitle("Carpeta para los documentos firmados");
        preferencias.ultimaCarpeta()
                .ifPresent(carpeta -> selector.setInitialDirectory(carpeta.toFile()));
        return Optional.ofNullable(selector.showDialog(ventana)).map(File::toPath);
    }

    private void recordarDestino(DestinoFirma destino) {
        preferencias.recordarDestino(destino);
        preferencias.guardar();
    }

    /** Abre la ventana de configuracion y deja aplicado lo que el usuario decida. */
    private void configurarCertificado() {
        new VentanaCertificado(configuracion, reloj)
                .mostrar(ventana)
                .ifPresent(this::aplicarCertificado);
    }

    private void aplicarCertificado(EstadoCertificado estado) {
        this.certificado = estado;
        panelFirma.mostrarCertificado(estado);
        actualizarControles();
    }

    /**
     * Lanza la firma. Este es el borde exterior de la interfaz: pase lo que pase, el usuario
     * tiene que ver o el documento firmado o el motivo del fallo, nunca un boton que no hace
     * nada, por eso se atrapa aqui cualquier excepcion.
     */
    private void firmar() {
        SolicitudFirma solicitud;
        TareaFirma tarea;
        try {
            solicitud = construirSolicitud();
            tarea = prepararTarea(solicitud);
        } catch (RuntimeException e) {
            mostrarError(e);
            return;
        }

        panelFirma.ocupadoProperty().set(true);
        panelFirma.mostrarEstado("Firmando el documento…");

        tarea.setOnSucceeded(evento -> terminarFirma(tarea.getValue()));
        tarea.setOnFailed(evento -> {
            panelFirma.ocupadoProperty().set(false);
            panelFirma.mostrarEstado("La firma no se completó.");
            mostrarError(tarea.getException());
        });

        Thread hilo = hiloDemonio("firma").newThread(tarea);
        hilo.setUncaughtExceptionHandler((quien, fallo) ->
                Platform.runLater(() -> {
                    panelFirma.ocupadoProperty().set(false);
                    panelFirma.mostrarEstado("La firma no se completó.");
                    mostrarError(fallo);
                }));
        hilo.start();
    }

    private SolicitudFirma construirSolicitud() {
        if (pdfAbierto == null) {
            throw new FirmaException("Abra primero el documento PDF que quiere firmar");
        }
        Recuadro recuadro = panelDocumento.recuadroActual();
        if (recuadro == null) {
            throw new FirmaException("Dibuje sobre la página el recuadro donde va el sello");
        }
        OrigenClave origen = origenConfigurado();

        NivelFirma nivel = panelFirma.nivelElegido();
        comprobarQueElCertificadoSirve();
        recordarEleccion(recuadro, nivel);

        return new SolicitudFirma(
                pdfAbierto,
                origen,
                panelFirma.destinoElegido().rutaPara(pdfAbierto),
                panelFirma.motivoElegido(),
                paginaActual,
                (float) recuadro.x(),
                (float) recuadro.y(),
                nivel);
    }

    private void recordarEleccion(Recuadro recuadro, NivelFirma nivel) {
        preferencias.recordarRecuadro(recuadro);
        preferencias.recordarMotivo(panelFirma.indiceMotivo());
        if (nivel instanceof NivelFirma.LargoPlazo largoPlazo) {
            preferencias.recordarUrlSelloTiempo(largoPlazo.urlSelloTiempo());
        }
        preferencias.guardar();
    }

    /**
     * Un certificado vencido produce un documento que no validara: mas vale decirlo antes de
     * firmar que dejar al usuario con un PDF inservible.
     */
    private void comprobarQueElCertificadoSirve() {
        if (!(certificado instanceof EstadoCertificado.Listo listo)) {
            return;
        }
        DatosCertificado.Vigencia vigencia = listo.datos().vigencia(reloj);
        if (!vigencia.permiteFirmar()) {
            throw new FirmaException("El certificado de " + listo.datos().titular() + " está "
                    + vigencia.etiqueta().toLowerCase(Locale.ROOT) + " (venció el "
                    + listo.datos().fechaDeVencimiento() + "). Lo que firme con él no pasará la "
                    + "validación oficial: pulse «Editar» en la ficha y cargue el certificado "
                    + "renovado.");
        }
    }

    private OrigenClave origenConfigurado() {
        if (certificado instanceof EstadoCertificado.Listo listo) {
            return listo.origen();
        }
        throw new FirmaException("Configure primero el certificado con el que va a firmar: "
                + "pulse «Cargar certificado» en el panel de la derecha");
    }

    /**
     * Arma la tarea de firma segun de donde salga la clave. Con el llavero no hay contrasena que
     * manejar; con un archivo .p12 sale del llavero, y {@link TareaFirma} la limpia al terminar.
     */
    private TareaFirma prepararTarea(SolicitudFirma solicitud) {
        if (solicitud.origen() instanceof OrigenClave.Llavero) {
            return TareaFirma.conLlavero(solicitud, reloj);
        }
        Path ruta = ((OrigenClave.Archivo) solicitud.origen()).ruta();
        char[] contrasena = configuracion.contrasenaDe(ruta)
                .orElseThrow(() -> new FirmaException("La contraseña de este certificado ya no está "
                        + "en el Llavero de macOS. Pulse «Editar» en la ficha del certificado y "
                        + "vuelva a guardarla."));
        return TareaFirma.conArchivo(solicitud, contrasena, reloj);
    }

    /**
     * Cierra la firma y deja el documento <em>firmado</em> abierto en la ventana, como hace
     * ReFirma: el usuario ve su sello puesto y puede ir a otra página y volver a firmar, porque
     * cada firma nueva se agrega al PDF sin tocar las anteriores.
     */
    private void terminarFirma(Path firmado) {
        panelFirma.ocupadoProperty().set(false);
        panelFirma.mostrarEstado("Documento firmado.");
        try {
            mostrarDocumentoFirmado(firmado);
        } catch (RuntimeException e) {
            // El documento ya esta escrito: el fallo solo puede ser al mostrarlo, y aun asi hay
            // que decirlo, con la ruta por delante para que el usuario lo encuentre.
            log.error("El documento se firmó en {} pero no se pudo mostrar", firmado, e);
            avisar("El documento se firmó, pero no se pudo volver a abrir",
                    "Está guardado en " + firmado + ". Ábralo con «Abrir PDF…» para verlo.");
        }
    }

    private void mostrarDocumentoFirmado(Path firmado) {
        DocumentoFirmado documento = new DocumentoFirmado(firmado);
        resultado.setText(resumenDe(firmado));
        resultado.setVisible(true);
        resultado.setManaged(true);
        // El panel conserva la ruta y los botones durante toda la sesión, no solo en el aviso.
        panelFirma.mostrarDocumentoFirmado(documento);

        abrirPdf(firmado);
        avisarDeLaFirma(documento);
    }

    /** Aviso de firma completada, con la ruta copiable y los botones para llegar al archivo. */
    private void avisarDeLaFirma(DocumentoFirmado documento) {
        AccesoDocumentoFirmado accesoDelAviso = new AccesoDocumentoFirmado(
                abridor, mensaje -> avisar("No se pudo abrir", mensaje));
        accesoDelAviso.mostrar(documento);

        Label explicacion = new Label("Si quiere firmar en otra página, vaya a esa página, coloque "
                + "el recuadro y pulse «Firmar documento». La firma nueva se añade sin invalidar "
                + "las que ya tiene.");
        explicacion.setWrapText(true);
        explicacion.setMaxWidth(520);

        Alert aviso = new Alert(Alert.AlertType.INFORMATION);
        aviso.initOwner(ventana);
        aviso.setTitle("Firma completada");
        aviso.setHeaderText("El documento se firmó correctamente");
        aviso.getDialogPane().setContent(new VBox(12, accesoDelAviso, explicacion));
        aviso.getDialogPane().setMinWidth(560);
        aviso.showAndWait();
    }

    /**
     * Cuenta las firmas que ya trae el documento recien abierto, para decirselo al usuario. Se
     * hace fuera del hilo de la interfaz porque obliga a releer el PDF entero, y se reutiliza el
     * mismo verificador que usa el modo {@code --verificar} de la consola.
     */
    private void contarFirmasExistentes(Path pdf) {
        Task<Integer> conteo = new Task<>() {
            @Override
            protected Integer call() {
                return new VerificadorPdf().verificar(pdf).size();
            }
        };
        conteo.setOnSucceeded(evento -> panelFirma.mostrarFirmasExistentes(conteo.getValue()));
        conteo.setOnFailed(evento -> {
            // No poder contarlas no impide firmar: se deja constancia y se sigue.
            log.warn("No se pudieron leer las firmas de {}", pdf, conteo.getException());
            panelFirma.mostrarFirmasExistentes(0);
        });
        dibujante.execute(conteo);
    }

    private String resumenDe(Path firmado) {
        StringBuilder resumen = new StringBuilder("Documento firmado: ").append(firmado);
        try {
            List<DatosFirma> firmas = new VerificadorPdf().verificar(firmado);
            int numero = 1;
            for (DatosFirma firma : firmas) {
                resumen.append(System.lineSeparator())
                        .append("Firma ").append(numero).append(": ")
                        .append(firma.titular()).append(" | ").append(firma.nivel())
                        .append(" | ").append(firma.motivo())
                        .append(" | integridad ").append(firma.integra() ? "correcta" : "ALTERADA");
                numero++;
            }
        } catch (FirmaException e) {
            resumen.append(System.lineSeparator())
                    .append("No se pudo releer la firma: ").append(e.getMessage());
        }
        return resumen.toString();
    }

    private void cerrarDocumento() {
        if (renderizador != null) {
            renderizador.close();
            renderizador = null;
        }
    }

    private void avisar(String encabezado, String detalle) {
        Alert aviso = new Alert(Alert.AlertType.WARNING);
        aviso.initOwner(ventana);
        aviso.setTitle(TITULO);
        aviso.setHeaderText(encabezado);
        aviso.setContentText(detalle);
        aviso.getDialogPane().setMinWidth(520);
        aviso.showAndWait();
    }

    /**
     * Muestra el fallo en espanol. Este es el borde exterior de la interfaz: aqui no puede
     * escaparse nada sin explicacion, por eso se acepta cualquier {@code Throwable}.
     */
    private void mostrarError(Throwable fallo) {
        String detalle = fallo instanceof FirmaException
                ? fallo.getMessage()
                : "Ocurrió un problema inesperado. Revise el registro para ver el detalle técnico.";
        if (!(fallo instanceof FirmaException)) {
            log.error("Fallo no contemplado en la interfaz", fallo);
        }

        Alert error = new Alert(Alert.AlertType.ERROR);
        error.initOwner(ventana);
        error.setTitle(TITULO);
        error.setHeaderText("No se pudo completar la operación");
        error.setContentText(detalle);
        error.getDialogPane().setMinWidth(520);
        error.showAndWait();
    }

    private ThreadFactory hiloDemonio(String nombre) {
        return tarea -> {
            Thread hilo = new Thread(tarea, nombre);
            hilo.setDaemon(true);
            return hilo;
        };
    }
}
