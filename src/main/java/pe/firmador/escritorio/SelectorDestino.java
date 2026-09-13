package pe.firmador.escritorio;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import pe.firmador.DestinoFirma;

/**
 * Deja elegir donde se guardan los documentos firmados: junto al original, como hace ReFirma, o
 * en una carpeta fija.
 *
 * <p>Es configuracion, no una pregunta de cada firma: la eleccion se avisa hacia fuera en cuanto
 * cambia para que quede recordada entre sesiones.</p>
 *
 * <p>Solo debe usarse desde el hilo de la interfaz de JavaFX.</p>
 */
public final class SelectorDestino extends VBox {

    private static final String GRIS = "#777";
    private static final double SEPARACION = 6;

    private final Supplier<Optional<Path>> pedirCarpeta;
    private final Consumer<DestinoFirma> alCambiar;

    private final ToggleGroup modo = new ToggleGroup();
    private final RadioButton juntoAlOriginal = new RadioButton("Junto al documento original");
    private final RadioButton enCarpeta = new RadioButton("En esta carpeta:");
    private final Label carpetaElegida = new Label();
    private final Button cambiar = new Button("Elegir carpeta...");

    private Path carpeta;

    /**
     * @param pedirCarpeta abre el selector de carpetas del sistema y devuelve la elegida
     * @param alCambiar    recibe el destino cada vez que el usuario lo cambia
     */
    public SelectorDestino(Supplier<Optional<Path>> pedirCarpeta, Consumer<DestinoFirma> alCambiar) {
        this.pedirCarpeta = Objects.requireNonNull(pedirCarpeta, "pedirCarpeta es obligatorio");
        this.alCambiar = Objects.requireNonNull(alCambiar, "alCambiar es obligatorio");

        juntoAlOriginal.setToggleGroup(modo);
        enCarpeta.setToggleGroup(modo);
        modo.selectedToggleProperty().addListener((observable, previo, actual) -> cambiarDeModo());

        carpetaElegida.setStyle("-fx-text-fill: " + GRIS + "; -fx-font-size: 11px;");
        carpetaElegida.setWrapText(true);
        carpetaElegida.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(carpetaElegida, Priority.ALWAYS);

        cambiar.setTooltip(new Tooltip("Elegir la carpeta donde se guardan los documentos firmados"));
        cambiar.setOnAction(evento -> elegirCarpeta());

        HBox filaCarpeta = new HBox(SEPARACION, carpetaElegida, cambiar);
        filaCarpeta.setAlignment(Pos.CENTER_LEFT);

        Label titulo = new Label("Dónde se guarda el documento firmado");
        titulo.setStyle("-fx-font-weight: bold;");

        setSpacing(SEPARACION);
        getChildren().addAll(titulo, juntoAlOriginal, enCarpeta, filaCarpeta);
        mostrar(new DestinoFirma.JuntoAlOriginal());
    }

    /**
     * Pinta el destino ya configurado, sin avisar del cambio: no lo decidio el usuario ahora.
     *
     * @param destino destino recordado
     */
    public void mostrar(DestinoFirma destino) {
        Objects.requireNonNull(destino, "destino es obligatorio");
        if (destino instanceof DestinoFirma.EnCarpeta guardado) {
            this.carpeta = guardado.carpeta();
            modo.selectToggle(enCarpeta);
        } else {
            modo.selectToggle(juntoAlOriginal);
        }
        refrescar();
    }

    /**
     * @return el destino elegido ahora mismo
     */
    public DestinoFirma destinoElegido() {
        return enCarpeta.isSelected() && carpeta != null
                ? new DestinoFirma.EnCarpeta(carpeta)
                : new DestinoFirma.JuntoAlOriginal();
    }

    /** Al pasar a «en esta carpeta» sin tener ninguna, se pide en el acto: si no, no se guarda nada. */
    private void cambiarDeModo() {
        if (enCarpeta.isSelected() && carpeta == null) {
            elegirCarpeta();
        }
        refrescar();
        alCambiar.accept(destinoElegido());
    }

    private void elegirCarpeta() {
        pedirCarpeta.get().ifPresent(elegida -> {
            this.carpeta = elegida.toAbsolutePath().normalize();
            modo.selectToggle(enCarpeta);
            refrescar();
            alCambiar.accept(destinoElegido());
        });
        if (carpeta == null) {
            // El usuario cerró el selector sin elegir: se vuelve a lo de siempre.
            modo.selectToggle(juntoAlOriginal);
            refrescar();
        }
    }

    private void refrescar() {
        boolean usaCarpeta = enCarpeta.isSelected();
        carpetaElegida.setText(carpeta == null
                ? "Sin carpeta elegida"
                : carpeta.toString());
        carpetaElegida.setDisable(!usaCarpeta);
        cambiar.setDisable(!usaCarpeta);
    }
}
