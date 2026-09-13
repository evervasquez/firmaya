package pe.firmador.escritorio;

import java.util.Objects;
import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import pe.firmador.FirmaException;

/**
 * Deja el documento firmado a un clic: ensena su ruta completa, permite copiarla y ofrece
 * abrirlo o mostrarlo en el Finder, para no tener que ir a buscarlo a mano.
 *
 * <p>La ruta va en un campo de texto de solo lectura y no en una etiqueta: de una etiqueta no se
 * puede seleccionar ni copiar el texto, que es justo lo que hace falta para pegar la ruta en un
 * correo o en una carpeta.</p>
 *
 * <p>Solo debe usarse desde el hilo de la interfaz de JavaFX.</p>
 */
public final class AccesoDocumentoFirmado extends VBox {

    private static final double SEPARACION = 6;

    private final AbridorDeArchivos abridor;
    private final Consumer<String> alFallar;

    private final TextField rutaVisible = new TextField();
    private final Button mostrar = new Button("Mostrar en Finder");
    private final Button abrir = new Button("Abrir documento");

    private DocumentoFirmado documento;

    /**
     * @param abridor  acceso al sistema operativo
     * @param alFallar que hacer con el mensaje cuando el archivo ya no se puede abrir
     */
    public AccesoDocumentoFirmado(AbridorDeArchivos abridor, Consumer<String> alFallar) {
        this.abridor = Objects.requireNonNull(abridor, "abridor es obligatorio");
        this.alFallar = Objects.requireNonNull(alFallar, "alFallar es obligatorio");

        rutaVisible.setEditable(false);
        rutaVisible.setFocusTraversable(false);
        rutaVisible.setPromptText("Todavía no ha firmado ningún documento");
        // Sin borde ni fondo parece una etiqueta, pero deja seleccionar y copiar la ruta.
        rutaVisible.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; "
                + "-fx-padding: 0;");
        rutaVisible.setTooltip(new Tooltip("Seleccione el texto para copiar la ruta"));
        HBox.setHgrow(rutaVisible, Priority.ALWAYS);

        mostrar.setTooltip(new Tooltip("Abre el Finder con el documento seleccionado"));
        abrir.setTooltip(new Tooltip("Abre el documento con la aplicación de PDF del sistema"));
        mostrar.setOnAction(evento -> intentar(() -> documento.mostrarEnElGestor(abridor)));
        abrir.setOnAction(evento -> intentar(() -> documento.abrir(abridor)));

        Label titulo = new Label("Documento firmado");
        titulo.setStyle("-fx-font-weight: bold;");

        HBox botones = new HBox(SEPARACION, mostrar, abrir);
        botones.setAlignment(Pos.CENTER_LEFT);

        setSpacing(SEPARACION);
        getChildren().addAll(titulo, rutaVisible, botones);
        limpiar();
    }

    /**
     * Muestra el documento recien firmado y habilita los botones.
     *
     * @param firmado documento que acaba de quedar firmado
     */
    public void mostrar(DocumentoFirmado firmado) {
        this.documento = Objects.requireNonNull(firmado, "firmado es obligatorio");
        rutaVisible.setText(firmado.rutaVisible());
        rutaVisible.positionCaret(0);
        habilitar(true);
    }

    /** Vuelve al estado inicial: sin documento y con los botones apagados. */
    public void limpiar() {
        this.documento = null;
        rutaVisible.clear();
        habilitar(false);
    }

    private void habilitar(boolean hayDocumento) {
        mostrar.setDisable(!hayDocumento);
        abrir.setDisable(!hayDocumento);
    }

    private void intentar(Runnable accion) {
        if (documento == null) {
            return;
        }
        try {
            accion.run();
        } catch (FirmaException e) {
            // El archivo pudo moverse o borrarse desde que se firmó: se dice, no se revienta.
            alFallar.accept(e.getMessage());
        }
    }
}
