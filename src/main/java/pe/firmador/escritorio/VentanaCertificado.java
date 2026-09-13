package pe.firmador.escritorio;

import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.StringConverter;

import pe.firmador.DatosCertificado;
import pe.firmador.FirmaException;
import pe.firmador.OrigenClave;

/**
 * Ventana modal donde el usuario configura, una sola vez, con que certificado firma.
 *
 * <p>Hay dos caminos. Tomar una identidad del <strong>llavero de inicio de sesion</strong> de
 * macOS, que no necesita contrasena porque la clave privada la custodia el sistema; solo se
 * ofrecen las que sirven de verdad para firmar documentos y estan vigentes. O elegir un archivo
 * .p12 del disco, que es el camino comprobado contra el validador oficial: se escribe la
 * contrasena una vez, se comprueba abriendo el certificado de verdad y, si el usuario deja
 * marcada la casilla, queda guardada en el llavero para no volver a pedirla nunca.</p>
 *
 * <p>Guardar solo ocurre despues de una comprobacion correcta, y cada pulsacion produce siempre
 * algo visible: o la ficha del certificado, o un mensaje junto al campo que falta.</p>
 *
 * <p>Solo debe usarse desde el hilo de la interfaz de JavaFX.</p>
 */
public final class VentanaCertificado {

    private static final ButtonType CONFIRMAR =
            new ButtonType("Comprobar y guardar", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType CANCELAR =
            new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

    private static final String TEXTO_COMPROBAR = "Comprobar y guardar";
    private static final String TEXTO_GUARDAR = "Guardar";

    private static final double ANCHO = 580;
    private static final String GRIS = "#777";
    private static final String ROJO = "#b00020";

    private final ConfiguracionCertificado configuracion;

    private final ToggleGroup origenElegido = new ToggleGroup();
    private final RadioButton desdeLlavero = new RadioButton("Del Llavero de macOS (recomendado)");
    private final RadioButton desdeArchivo = new RadioButton("Desde un archivo .p12");
    private final ComboBox<IdentidadLlavero> identidades = new ComboBox<>();
    private final ComboBox<CertificadoRecordado> conocidos = new ComboBox<>();
    private final TextField rutaCampo = new TextField();
    private final PasswordField contrasena = new PasswordField();
    private final CheckBox recordar = new CheckBox("Recordar la contraseña en el Llavero de macOS");
    private final Label avisoLlavero = new Label();
    private final Label avisoArchivo = new Label();
    private final TarjetaCertificado ficha;
    private final BooleanProperty comprobado = new SimpleBooleanProperty(false);

    private Dialog<EstadoCertificado.Listo> dialogo;
    private Button botonConfirmar;
    private Path archivoElegido;
    private DatosCertificado datosComprobados;

    /**
     * @param configuracion el certificado como configuracion persistente
     * @param reloj         fuente de la fecha con la que se juzga la vigencia
     */
    public VentanaCertificado(ConfiguracionCertificado configuracion, Clock reloj) {
        this.configuracion = Objects.requireNonNull(configuracion, "configuración es obligatoria");
        this.ficha = new TarjetaCertificado(Objects.requireNonNull(reloj, "reloj es obligatorio"));
        this.ficha.sinBoton();
    }

    /**
     * Abre la ventana y espera a que el usuario decida.
     *
     * <p>Si acepta, el certificado queda ya configurado: recordado en las preferencias y, cuando
     * corresponde, con su contrasena guardada en el llavero.</p>
     *
     * @param duenio ventana sobre la que se muestra en modo modal
     * @return el certificado configurado, o vacio si el usuario cancelo
     */
    public Optional<EstadoCertificado.Listo> mostrar(Window duenio) {
        Objects.requireNonNull(duenio, "duenio es obligatorio");

        dialogo = new Dialog<>();
        dialogo.initOwner(duenio);
        dialogo.initModality(Modality.APPLICATION_MODAL);
        dialogo.setTitle("Certificado de firma");
        dialogo.setHeaderText("Se configura una sola vez: después la aplicación firma sin volver "
                + "a pedirle nada.");
        dialogo.setResizable(true);
        dialogo.getDialogPane().getButtonTypes().setAll(CONFIRMAR, CANCELAR);
        dialogo.getDialogPane().setContent(contenido());
        dialogo.getDialogPane().setPrefWidth(ANCHO);
        // La ventana solo se cierra cuando el certificado quedo guardado (con setResult) o cuando
        // el usuario cancela; asi ninguna pulsacion puede cerrarla sin haber hecho nada.
        dialogo.setResultConverter(boton -> null);

        botonConfirmar = (Button) dialogo.getDialogPane().lookupButton(CONFIRMAR);
        botonConfirmar.addEventFilter(ActionEvent.ACTION, this::alConfirmar);

        prepararEstadoInicial();
        try {
            return dialogo.showAndWait();
        } finally {
            contrasena.clear();
        }
    }

    /**
     * Unico camino de la pulsacion de «Comprobar y guardar»: valida, y de ahi sale siempre algo
     * visible para el usuario.
     */
    private void alConfirmar(ActionEvent evento) {
        // Se consume siempre: cerrar la ventana es decision de cada paso, no del boton.
        evento.consume();
        if (desdeLlavero.isSelected()) {
            guardarDelLlavero();
            return;
        }
        if (comprobado.get()) {
            guardarDelArchivo();
            return;
        }
        comprobar();
    }

    private VBox contenido() {
        desdeLlavero.setToggleGroup(origenElegido);
        desdeArchivo.setToggleGroup(origenElegido);
        origenElegido.selectedToggleProperty().addListener((observable, previo, actual) -> invalidar());

        ficha.setVisible(false);
        ficha.setManaged(false);
        preparar(avisoLlavero);
        preparar(avisoArchivo);

        VBox caja = new VBox(10,
                desdeLlavero, bloqueLlavero(),
                new Separator(),
                desdeArchivo, bloqueArchivo(),
                new Separator(),
                ficha);
        caja.setPadding(new Insets(12, 4, 4, 4));
        return caja;
    }

    private VBox bloqueLlavero() {
        identidades.setMaxWidth(Double.MAX_VALUE);
        identidades.setPromptText("Identidades de firma de su Llavero");
        identidades.setConverter(new StringConverter<>() {
            @Override
            public String toString(IdentidadLlavero identidad) {
                return identidad == null ? "" : identidad.etiqueta();
            }

            @Override
            public IdentidadLlavero fromString(String texto) {
                throw new UnsupportedOperationException("La lista de identidades no se escribe a mano");
            }
        });
        identidades.getSelectionModel().selectedItemProperty()
                .addListener((observable, previo, actual) -> invalidar());

        VBox caja = new VBox(6, identidades, avisoLlavero,
                pista("Solo aparecen los certificados de su Llavero que sirven para firmar "
                        + "documentos y siguen vigentes. La clave privada no sale del Llavero y no "
                        + "hay contraseña que escribir: la primera vez que firme, macOS puede "
                        + "pedirle permiso; elija «Permitir siempre» y no volverá a preguntar."));
        caja.setPadding(new Insets(0, 0, 0, 22));
        return caja;
    }

    private VBox bloqueArchivo() {
        conocidos.setMaxWidth(Double.MAX_VALUE);
        conocidos.setPromptText("Certificados que ya usó");
        conocidos.setConverter(new StringConverter<>() {
            @Override
            public String toString(CertificadoRecordado recordado) {
                return recordado == null ? "" : recordado.etiqueta();
            }

            @Override
            public CertificadoRecordado fromString(String texto) {
                throw new UnsupportedOperationException("La lista de certificados no se escribe a mano");
            }
        });
        conocidos.getSelectionModel().selectedItemProperty()
                .addListener((observable, previo, actual) -> elegirConocido(actual));

        rutaCampo.setEditable(false);
        rutaCampo.setPromptText("Ningún archivo elegido");
        HBox.setHgrow(rutaCampo, Priority.ALWAYS);

        Button examinar = new Button("Examinar...");
        examinar.setOnAction(evento -> elegirArchivo());

        contrasena.setPromptText("Contraseña del certificado");
        contrasena.textProperty().addListener((observable, previo, actual) -> invalidar());

        recordar.setSelected(true);
        recordar.setDisable(!configuracion.puedeRecordarContrasenas());

        VBox caja = new VBox(6, conocidos, new HBox(6, rutaCampo, examinar), contrasena, recordar,
                avisoArchivo, pista(textoDeLaCasilla()));
        caja.setPadding(new Insets(0, 0, 0, 22));
        return caja;
    }

    private String textoDeLaCasilla() {
        if (!configuracion.puedeRecordarContrasenas()) {
            return "Este equipo no tiene disponible el Llavero de macOS, así que la contraseña no "
                    + "se puede recordar y se pedirá cada vez que firme.";
        }
        return "La contraseña se guarda en el Llavero de macOS, cifrada por el sistema; no queda "
                + "en las preferencias ni en ningún archivo de la aplicación. Si la desmarca, "
                + "tendrá que escribirla cada vez que firme.";
    }

    /** Deja la ventana lista, con el origen que de verdad le va a funcionar al usuario. */
    private void prepararEstadoInicial() {
        List<IdentidadLlavero> delLlavero = configuracion.identidadesDelLlavero();
        identidades.setItems(FXCollections.observableArrayList(delLlavero));
        conocidos.setItems(FXCollections.observableArrayList(configuracion.conocidos()));

        boolean hayIdentidades = !delLlavero.isEmpty();
        desdeLlavero.setDisable(!hayIdentidades);
        identidades.setDisable(!hayIdentidades);
        origenElegido.selectToggle(hayIdentidades ? desdeLlavero : desdeArchivo);
        if (hayIdentidades) {
            identidades.getSelectionModel().selectFirst();
        } else {
            avisar(avisoLlavero, textoSinIdentidades(), GRIS);
        }
        actualizarBoton();
    }

    /**
     * Explica por que no hay nada que elegir en el Llavero. El caso frecuente es tener el
     * certificado de firma en el llavero del sistema: ahi esta, pero usarlo obligaria a pedir
     * credenciales de administrador en cada firma.
     */
    private String textoSinIdentidades() {
        int enElSistema = configuracion.identidadesEnElLlaveroDelSistema();
        if (enElSistema > 0) {
            return "Su certificado de firma está en el llavero del sistema, no en el suyo de "
                    + "inicio de sesión. Esta aplicación no lo usa desde ahí porque macOS pediría "
                    + "contraseña de administrador en cada firma. Use el archivo .p12 —es el camino "
                    + "comprobado con el validador oficial— o importe el certificado a su llavero "
                    + "personal siguiendo el README.";
        }
        return "Su Llavero no tiene certificados que sirvan para firmar documentos. Elija el "
                + "archivo .p12 que le entregó su entidad certificadora.";
    }

    private void elegirConocido(CertificadoRecordado recordado) {
        if (recordado == null || !(recordado.origen() instanceof OrigenClave.Archivo archivo)) {
            return;
        }
        origenElegido.selectToggle(desdeArchivo);
        fijarRuta(archivo.ruta());
        invalidar();
        contrasena.requestFocus();
    }

    private void elegirArchivo() {
        FileChooser selector = new FileChooser();
        selector.setTitle("Elegir certificado");
        selector.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Certificados PKCS#12", "*.p12", "*.pfx"));
        if (archivoElegido != null && archivoElegido.getParent() != null) {
            selector.setInitialDirectory(archivoElegido.getParent().toFile());
        }
        File archivo = selector.showOpenDialog(dialogo.getDialogPane().getScene().getWindow());
        if (archivo != null) {
            origenElegido.selectToggle(desdeArchivo);
            fijarRuta(archivo.toPath());
            invalidar();
            contrasena.requestFocus();
        }
    }

    private void fijarRuta(Path ruta) {
        this.archivoElegido = ruta.toAbsolutePath().normalize();
        rutaCampo.setText(this.archivoElegido.toString());
        rutaCampo.positionCaret(rutaCampo.getLength());
    }

    /** Cualquier cambio invalida la comprobacion anterior: no se guarda lo que no se comprobo. */
    private void invalidar() {
        comprobado.set(false);
        datosComprobados = null;
        ficha.setVisible(false);
        ficha.setManaged(false);
        actualizarBoton();
    }

    private void actualizarBoton() {
        if (botonConfirmar == null) {
            return;
        }
        // Con el llavero no hay nada que comprobar: el certificado ya se leyo para la lista. El
        // boton nunca se deshabilita; si falta algo, al pulsarlo se dice cual es.
        boolean guardaYa = desdeLlavero.isSelected() || comprobado.get();
        botonConfirmar.setText(guardaYa ? TEXTO_GUARDAR : TEXTO_COMPROBAR);
    }

    private void comprobar() {
        if (archivoElegido == null) {
            avisar(avisoArchivo, "Elija el archivo .p12 del certificado con «Examinar…».", ROJO);
            return;
        }
        if (contrasena.getText().isEmpty()) {
            avisar(avisoArchivo, "Escriba la contraseña del certificado.", ROJO);
            contrasena.requestFocus();
            return;
        }
        char[] escrita = contrasena.getText().toCharArray();
        try {
            datosComprobados = configuracion.comprobarArchivo(archivoElegido, escrita);
            comprobado.set(true);
            mostrarFicha(new OrigenClave.Archivo(archivoElegido), datosComprobados);
            avisar(avisoArchivo, "Certificado abierto correctamente. Revise los datos y pulse "
                    + "«Guardar».", GRIS);
        } catch (FirmaException e) {
            invalidar();
            avisar(avisoArchivo, e.getMessage(), ROJO);
        } finally {
            Arrays.fill(escrita, '\0');
            actualizarBoton();
        }
    }

    private void guardarDelLlavero() {
        IdentidadLlavero identidad = identidades.getSelectionModel().getSelectedItem();
        if (identidad == null) {
            avisar(avisoLlavero, identidades.getItems().isEmpty()
                    ? textoSinIdentidades()
                    : "Elija en la lista la identidad con la que va a firmar.", ROJO);
            return;
        }
        try {
            terminar(configuracion.guardarDelLlavero(identidad));
        } catch (FirmaException e) {
            avisar(avisoLlavero, "No se pudo guardar: " + e.getMessage(), ROJO);
        }
    }

    private void guardarDelArchivo() {
        char[] escrita = contrasena.getText().toCharArray();
        try {
            terminar(configuracion.guardarArchivo(
                    archivoElegido, datosComprobados, escrita, recordar.isSelected()));
        } catch (FirmaException e) {
            avisar(avisoArchivo, "No se pudo guardar: " + e.getMessage(), ROJO);
        } finally {
            Arrays.fill(escrita, '\0');
        }
    }

    /** Cierra la ventana devolviendo el certificado ya configurado. */
    private void terminar(EstadoCertificado.Listo configurado) {
        dialogo.setResult(configurado);
        dialogo.close();
    }

    private void mostrarFicha(OrigenClave origen, DatosCertificado datos) {
        ficha.mostrar(new EstadoCertificado.Listo(origen, datos));
        ficha.setVisible(true);
        ficha.setManaged(true);
    }

    private void preparar(Label aviso) {
        aviso.setWrapText(true);
        aviso.setVisible(false);
        aviso.setManaged(false);
        aviso.setMaxWidth(ANCHO - 80);
    }

    private void avisar(Label donde, String texto, String color) {
        donde.setText(texto);
        donde.setStyle("-fx-text-fill: " + color + ";");
        donde.setVisible(true);
        donde.setManaged(true);
    }

    private Label pista(String texto) {
        Label etiqueta = new Label(texto);
        etiqueta.setWrapText(true);
        etiqueta.setStyle("-fx-text-fill: " + GRIS + "; -fx-font-size: 11px;");
        etiqueta.setMaxWidth(ANCHO - 80);
        return etiqueta;
    }
}
