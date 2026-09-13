package pe.firmador.escritorio;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import pe.firmador.DestinoFirma;
import pe.firmador.MotivosReFirma;
import pe.firmador.NivelFirma;
import pe.firmador.SelloVisual;
import pe.firmador.TextoSello;

/**
 * Panel lateral con todo lo que el usuario decide antes de firmar: motivo, nivel PAdES y
 * vista previa del sello, mas el resumen del certificado que tiene configurado.
 *
 * <p>El certificado y su contrasena no se eligen aqui: se configuran una sola vez en
 * {@link VentanaCertificado}, a la que se llega con el boton de este panel.</p>
 *
 * <p>Solo debe usarse desde el hilo de la interfaz de JavaFX.</p>
 */
public final class PanelFirma extends VBox {

    /** Texto que se muestra como titular mientras no se haya comprobado el certificado. */
    private static final String TITULAR_DESCONOCIDO = "TITULAR DEL CERTIFICADO";

    private static final double ANCHO_PANEL = 360;
    private static final double SEPARACION = 10;

    private static final String VERDE = "#1b7f3b";
    private static final String AMBAR = "#a15c00";

    private final SelloVisual selloVisual = new SelloVisual();
    private final Clock reloj;
    private final PreferenciasUsuario preferencias;

    private final TarjetaCertificado tarjeta;
    private final AccesoDocumentoFirmado acceso;
    private final SelectorDestino destino;
    private final ChoiceBox<String> motivo = new ChoiceBox<>();
    private final ChoiceBox<String> nivel = new ChoiceBox<>();
    private final TextField urlSelloTiempo = new TextField();
    private final Label avisoNivel = new Label();
    private final ImageView vistaPrevia = new ImageView();
    private final Label medidasSello = new Label();
    private final Label ubicacionSello = new Label();
    private final Label firmasExistentes = new Label();
    private final Button firmar = new Button("Firmar documento");
    private final ProgressIndicator progreso = new ProgressIndicator();
    private final Label estado = new Label();
    private final BooleanProperty ocupado = new SimpleBooleanProperty(false);

    private String titular = TITULAR_DESCONOCIDO;
    private Recuadro recuadroActual;
    private Runnable alFirmar = () -> { };

    /**
     * @param preferencias almacen de lo recordado entre sesiones
     * @param reloj        fuente de la hora usada en la vista previa
     * @param tarjeta      ficha de solo lectura del certificado configurado
     * @param acceso       bloque con la ruta del documento firmado y sus botones
     * @param destino      selector de la carpeta donde se guarda lo firmado
     */
    public PanelFirma(PreferenciasUsuario preferencias, Clock reloj, TarjetaCertificado tarjeta,
                      AccesoDocumentoFirmado acceso, SelectorDestino destino) {
        this.preferencias = Objects.requireNonNull(preferencias, "preferencias es obligatorio");
        this.reloj = Objects.requireNonNull(reloj, "reloj es obligatorio");
        this.tarjeta = Objects.requireNonNull(tarjeta, "tarjeta es obligatoria");
        this.acceso = Objects.requireNonNull(acceso, "acceso es obligatorio");
        this.destino = Objects.requireNonNull(destino, "destino es obligatorio");

        setSpacing(SEPARACION);
        setPadding(new Insets(14));
        setPrefWidth(ANCHO_PANEL);
        setMinWidth(ANCHO_PANEL);

        getChildren().addAll(
                titulo("Sello"),
                bloqueVistaPrevia(),
                new Separator(),
                titulo("Firma"),
                bloqueMotivo(),
                tarjeta,
                new Separator(),
                destino,
                new Separator(),
                titulo("Nivel"),
                bloqueNivel(),
                new Separator(),
                bloqueAccion(),
                new Separator(),
                acceso);

        prepararEstadoInicial();
    }

    /**
     * @return la propiedad que vale {@code true} mientras hay una firma en curso
     */
    public BooleanProperty ocupadoProperty() {
        return ocupado;
    }

    /**
     * @param accion que hacer cuando el usuario pulsa el boton de la ficha del certificado
     */
    public void alConfigurarCertificado(Runnable accion) {
        tarjeta.alConfigurar(Objects.requireNonNull(accion, "accion es obligatoria"));
    }

    /**
     * @param accion que hacer cuando el usuario pulsa "Firmar documento"
     */
    public void alFirmar(Runnable accion) {
        this.alFirmar = Objects.requireNonNull(accion, "accion es obligatoria");
    }

    /**
     * Dice cuantas firmas trae ya el documento abierto, para que el usuario sepa que esta
     * agregando la suya a las que hay y no reemplazandolas.
     *
     * @param cuantas firmas encontradas en el documento; cero si no tiene ninguna
     */
    public void mostrarFirmasExistentes(int cuantas) {
        if (cuantas <= 0) {
            firmasExistentes.setText("");
            firmasExistentes.setVisible(false);
            firmasExistentes.setManaged(false);
            return;
        }
        firmasExistentes.setText(cuantas == 1
                ? "Este documento ya tiene 1 firma. La suya se añadirá sin invalidarla."
                : "Este documento ya tiene " + cuantas + " firmas. La suya se añadirá sin "
                        + "invalidarlas.");
        firmasExistentes.setVisible(true);
        firmasExistentes.setManaged(true);
    }

    /**
     * Dice en que pagina va a quedar la firma, que es siempre la que el usuario tiene delante:
     * el recuadro le acompana al cambiar de pagina.
     *
     * @param sello         colocacion actual del sello, o {@code null} si todavia no se dibujo
     * @param totalPaginas  paginas del documento abierto; cero si no hay ninguno
     * @param paginaVisible pagina que se esta viendo
     */
    public void mostrarUbicacionDelSello(Recuadro sello, int totalPaginas, int paginaVisible) {
        if (totalPaginas == 0) {
            ubicacionSello.setText("Sin documento — abra el PDF que quiere firmar");
            ubicacionSello.setStyle("-fx-text-fill: " + AMBAR + ";");
            return;
        }
        if (sello == null) {
            ubicacionSello.setText("Sin ubicar — arrastre sobre la página para colocar el sello");
            ubicacionSello.setStyle("-fx-text-fill: " + AMBAR + ";");
            return;
        }
        ubicacionSello.setText("La firma irá en la página " + paginaVisible + " de " + totalPaginas);
        ubicacionSello.setStyle("-fx-text-fill: " + VERDE + ";");
    }

    /**
     * Pinta la ficha del certificado y pone en la vista previa del sello el nombre de quien
     * firma.
     *
     * <p>Solo se habilita el boton de firmar cuando el certificado esta listo y todavia sirve:
     * firmar con uno vencido produce un documento que no validara.</p>
     *
     * @param estado situacion del certificado configurado
     */
    public void mostrarCertificado(EstadoCertificado estado) {
        Objects.requireNonNull(estado, "estado es obligatorio");
        tarjeta.mostrar(estado);

        this.titular = estado instanceof EstadoCertificado.Listo listo
                ? listo.datos().titular()
                : TITULAR_DESCONOCIDO;
        actualizarVistaPrevia(recuadroActual);
    }

    /**
     * Redibuja la vista previa del sello con las medidas reales del recuadro.
     *
     * @param recuadro colocacion actual del sello, o {@code null} si no hay ninguna
     */
    public void actualizarVistaPrevia(Recuadro recuadro) {
        this.recuadroActual = recuadro;

        byte[] png = selloVisual.generar(new TextoSello(titular, motivoElegido(), reloj.instant()));
        vistaPrevia.setImage(new Image(new ByteArrayInputStream(png)));
        // Un punto PDF por pixel de pantalla: asi la vista previa se ve al tamano real.
        vistaPrevia.setFitWidth(SelloVisual.ANCHO_PT);
        vistaPrevia.setFitHeight(SelloVisual.ALTO_PT);
    }

    /**
     * @return el texto del motivo elegido
     */
    public String motivoElegido() {
        return MotivosReFirma.porIndice(indiceMotivo());
    }

    /**
     * @return el indice del motivo elegido dentro de {@link MotivosReFirma}
     */
    public int indiceMotivo() {
        int indice = motivo.getSelectionModel().getSelectedIndex();
        return indice < 0 ? MotivosReFirma.INDICE_POR_DEFECTO : indice;
    }

    /**
     * @return el nivel PAdES elegido, ya con su configuracion
     */
    public NivelFirma nivelElegido() {
        if (nivel.getSelectionModel().getSelectedIndex() == 1) {
            return new NivelFirma.LargoPlazo(urlSelloTiempo.getText().trim());
        }
        return NivelFirma.porDefecto();
    }

    /**
     * @return donde se guardara el documento firmado
     */
    public DestinoFirma destinoElegido() {
        return destino.destinoElegido();
    }

    /**
     * Deja el documento recien firmado a mano en el panel, con su ruta y sus botones, durante
     * el resto de la sesion.
     *
     * @param firmado documento que acaba de quedar firmado
     */
    public void mostrarDocumentoFirmado(DocumentoFirmado firmado) {
        acceso.mostrar(Objects.requireNonNull(firmado, "firmado es obligatorio"));
    }

    /**
     * @param texto mensaje corto de estado para el usuario
     */
    public void mostrarEstado(String texto) {
        estado.setText(Objects.requireNonNull(texto, "texto es obligatorio"));
    }

    private void prepararEstadoInicial() {
        motivo.getItems().setAll(MotivosReFirma.todos());
        motivo.getSelectionModel().select(preferencias.ultimoMotivo());
        motivo.getSelectionModel().selectedIndexProperty()
                .addListener((observable, anterior, actual) -> actualizarVistaPrevia(recuadroActual));

        nivel.getItems().setAll(
                new NivelFirma.Basico().etiqueta(),
                new NivelFirma.LargoPlazo(NivelFirma.TSA_POR_DEFECTO).etiqueta());
        nivel.getSelectionModel().selectFirst();

        urlSelloTiempo.setText(preferencias.urlSelloTiempo());

        progreso.visibleProperty().bind(ocupado);
        progreso.managedProperty().bind(progreso.visibleProperty());
        // El botón solo se bloquea mientras hay una firma en curso, para no dispararla dos
        // veces. Si falta algo (documento, sello o certificado), se pulsa y se explica qué falta:
        // un botón apagado sin motivo deja al usuario sin saber qué hacer.
        firmar.disableProperty().bind(ocupado);
        actualizarAvisoNivel();
        actualizarVistaPrevia(null);
    }

    private void actualizarAvisoNivel() {
        boolean largoPlazo = nivel.getSelectionModel().getSelectedIndex() == 1;
        urlSelloTiempo.setDisable(!largoPlazo);
        avisoNivel.setText(largoPlazo
                ? "LT necesita internet: pide sello de tiempo y prueba de revocación (OCSP/CRL) "
                        + "y las deja dentro del PDF. Este nivel NO está comprobado todavía contra "
                        + "el validador oficial."
                : "PAdES-B es el nivel comprobado como válido en el validador oficial de Firma Perú.");
    }

    private Label titulo(String texto) {
        Label etiqueta = new Label(texto);
        etiqueta.setFont(Font.font(etiqueta.getFont().getFamily(), 14));
        etiqueta.setStyle("-fx-font-weight: bold;");
        return etiqueta;
    }

    private VBox bloqueVistaPrevia() {
        vistaPrevia.setPreserveRatio(false);
        vistaPrevia.setSmooth(true);
        medidasSello.setStyle("-fx-text-fill: #555;");
        medidasSello.setText(String.format(Locale.ROOT, "%.1f x %.1f pt (tamaño original de ReFirma)",
                SelloVisual.ANCHO_PT, SelloVisual.ALTO_PT));

        HBox marco = new HBox(vistaPrevia);
        marco.setAlignment(Pos.CENTER_LEFT);
        marco.setPadding(new Insets(6));
        marco.setStyle("-fx-border-color: #bbb; -fx-border-style: dashed; -fx-background-color: white;");

        ubicacionSello.setWrapText(true);

        firmasExistentes.setWrapText(true);
        firmasExistentes.setStyle("-fx-text-fill: " + VERDE + ";");
        firmasExistentes.setVisible(false);
        firmasExistentes.setManaged(false);

        return new VBox(6, marco, medidasSello, ubicacionSello, firmasExistentes,
                pista("Haga clic en la página donde quiere el sello, o arrástrelo para ajustarlo. "
                        + "El tamaño es fijo, el mismo de ReFirma, para que el texto nunca salga "
                        + "cortado. El recuadro le acompaña al cambiar de página: la firma va en la "
                        + "que tenga delante."));
    }

    private VBox bloqueMotivo() {
        motivo.setMaxWidth(Double.MAX_VALUE);
        return new VBox(4, new Label("Motivo de la firma"), motivo);
    }

    private VBox bloqueNivel() {
        nivel.setMaxWidth(Double.MAX_VALUE);
        nivel.getSelectionModel().selectedIndexProperty()
                .addListener((observable, anterior, actual) -> actualizarAvisoNivel());
        urlSelloTiempo.setPromptText("Servidor de sellado de tiempo");
        avisoNivel.setWrapText(true);
        avisoNivel.setStyle("-fx-text-fill: #555;");

        return new VBox(4, nivel, new Label("Servidor de sellado de tiempo (solo LT)"),
                urlSelloTiempo, avisoNivel);
    }

    private VBox bloqueAccion() {
        firmar.setMaxWidth(Double.MAX_VALUE);
        firmar.setDefaultButton(true);
        firmar.setOnAction(evento -> alFirmar.run());

        progreso.setPrefSize(22, 22);
        progreso.setVisible(false);
        estado.setWrapText(true);

        return new VBox(8, firmar, new HBox(8, progreso, estado),
                pista("La contraseña del certificado no se guarda en las preferencias ni en ningún "
                        + "archivo de la aplicación: vive en el Llavero de macOS, cifrada por el "
                        + "sistema, y solo si usted lo pidió al configurarlo."));
    }

    private Label pista(String texto) {
        Label etiqueta = new Label(texto);
        etiqueta.setWrapText(true);
        etiqueta.setStyle("-fx-text-fill: #777; -fx-font-size: 11px;");
        return etiqueta;
    }
}
