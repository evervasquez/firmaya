package pe.firmador.escritorio;

import java.time.Clock;
import java.util.Objects;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import pe.firmador.DatosCertificado;
import pe.firmador.OrigenClave;

/**
 * Ficha de solo lectura del certificado configurado: quien firma, con que documento, hasta
 * cuando vale y quien lo emitio.
 *
 * <p>Aqui no se escribe nada. El certificado es configuracion, no un campo de formulario: se
 * elige una vez en {@link VentanaCertificado} y esta tarjeta solo muestra el resultado, con un
 * boton para cambiarlo si algun dia hace falta.</p>
 *
 * <p>Solo debe usarse desde el hilo de la interfaz de JavaFX.</p>
 */
public final class TarjetaCertificado extends VBox {

    private static final String VERDE = "#1b7f3b";
    private static final String AMBAR = "#a15c00";
    private static final String ROJO = "#b00020";
    private static final String GRIS = "#666";

    private static final double SEPARACION = 8;

    private final Clock reloj;
    private final Label titulo = new Label("Certificado de firma");
    private final Button configurar = new Button();
    private final VBox cuerpo = new VBox(4);

    private Runnable alConfigurar = () -> { };

    /**
     * @param reloj fuente de la fecha con la que se juzga la vigencia
     */
    public TarjetaCertificado(Clock reloj) {
        this.reloj = Objects.requireNonNull(reloj, "reloj es obligatorio");

        titulo.setFont(Font.font(titulo.getFont().getFamily(), 14));
        titulo.setStyle("-fx-font-weight: bold;");
        configurar.setOnAction(evento -> alConfigurar.run());

        Region separador = new Region();
        HBox.setHgrow(separador, Priority.ALWAYS);
        HBox encabezado = new HBox(SEPARACION, titulo, separador, configurar);
        encabezado.setAlignment(Pos.CENTER_LEFT);

        setSpacing(SEPARACION);
        setPadding(new Insets(12));
        setStyle("-fx-background-color: #f6f6f6; -fx-background-radius: 8; "
                + "-fx-border-color: #dcdcdc; -fx-border-radius: 8;");
        getChildren().addAll(encabezado, cuerpo);
        mostrar(new EstadoCertificado.SinConfigurar());
    }

    /**
     * Quita el boton de configurar. Se usa cuando la tarjeta sirve solo de vista previa, dentro
     * de la ventana de configuracion.
     */
    public void sinBoton() {
        configurar.setVisible(false);
        configurar.setManaged(false);
    }

    /**
     * @param accion que hacer cuando el usuario pulsa el boton de cargar o cambiar el certificado
     */
    public void alConfigurar(Runnable accion) {
        this.alConfigurar = Objects.requireNonNull(accion, "accion es obligatoria");
    }

    /**
     * Pinta la tarjeta segun en que situacion este el certificado.
     *
     * @param estado situacion actual
     */
    public void mostrar(EstadoCertificado estado) {
        Objects.requireNonNull(estado, "estado es obligatorio");
        cuerpo.getChildren().clear();

        // El proyecto compila contra Java 17, donde el switch con patrones todavia es preview:
        // la jerarquia sellada se consume con instanceof con patron.
        if (estado instanceof EstadoCertificado.Listo listo) {
            pintarConfigurado(listo);
        } else if (estado instanceof EstadoCertificado.Problema problema) {
            pintarProblema(problema);
        } else {
            pintarSinConfigurar();
        }
    }

    private void pintarSinConfigurar() {
        configurar.setText("Cargar certificado");
        configurar.setDefaultButton(true);
        cuerpo.getChildren().addAll(
                nota("Se configura una sola vez. Después la aplicación firma sola, sin volver a "
                        + "pedirle el archivo ni la contraseña."));
    }

    private void pintarConfigurado(EstadoCertificado.Listo listo) {
        configurar.setText("Editar");
        configurar.setDefaultButton(false);

        DatosCertificado datos = listo.datos();
        cuerpo.getChildren().add(dato("Titular", datos.titular()));
        if (!datos.documentoLegible().isBlank()) {
            cuerpo.getChildren().add(dato("Documento", datos.documentoLegible()));
        }
        cuerpo.getChildren().add(vigencia(datos));
        if (!datos.emisor().isBlank()) {
            cuerpo.getChildren().add(dato("Emisor", datos.emisor()));
        }
        cuerpo.getChildren().add(dato("Origen", origenDe(listo.origen())));
        avisoDeVigencia(datos);
    }

    private String origenDe(OrigenClave origen) {
        if (origen instanceof OrigenClave.Llavero) {
            return "Llavero de macOS · " + origen.descripcion();
        }
        return "Archivo · " + origen.descripcion();
    }

    private void avisoDeVigencia(DatosCertificado datos) {
        DatosCertificado.Vigencia vigencia = datos.vigencia(reloj);
        if (vigencia == DatosCertificado.Vigencia.VENCIDO) {
            cuerpo.getChildren().add(aviso("Este certificado venció el " + datos.fechaDeVencimiento()
                    + ". Lo que firme con él no pasará la validación oficial: renuévelo y cárguelo "
                    + "aquí con «Editar».", ROJO));
            return;
        }
        if (vigencia == DatosCertificado.Vigencia.POR_VENCER) {
            cuerpo.getChildren().add(aviso("Vence en " + datos.diasParaVencer(reloj) + " días. "
                    + "Cuando le entreguen el renovado, cárguelo con «Editar».", AMBAR));
            return;
        }
        if (vigencia == DatosCertificado.Vigencia.AUN_NO_VIGENTE) {
            cuerpo.getChildren().add(aviso("Este certificado todavía no entra en vigencia; hasta "
                    + "que lo haga, las firmas no serán válidas.", AMBAR));
        }
    }

    private void pintarProblema(EstadoCertificado.Problema problema) {
        configurar.setText("Configurar certificado");
        configurar.setDefaultButton(true);
        if (!problema.titular().isBlank()) {
            cuerpo.getChildren().add(dato("Titular", problema.titular()));
        }
        cuerpo.getChildren().add(aviso(problema.mensaje(), AMBAR));
    }

    private HBox dato(String etiqueta, String valor) {
        Label nombre = new Label(etiqueta);
        nombre.setMinWidth(78);
        nombre.setStyle("-fx-text-fill: " + GRIS + ";");

        Label contenido = new Label(valor);
        contenido.setWrapText(true);
        HBox.setHgrow(contenido, Priority.ALWAYS);

        HBox fila = new HBox(6, nombre, contenido);
        fila.setAlignment(Pos.TOP_LEFT);
        return fila;
    }

    private HBox vigencia(DatosCertificado datos) {
        DatosCertificado.Vigencia estado = datos.vigencia(reloj);
        HBox fila = dato("Vigencia", "");
        Label insignia = new Label(estado.etiqueta());
        insignia.setStyle("-fx-text-fill: white; -fx-background-radius: 4; -fx-padding: 1 6 1 6; "
                + "-fx-background-color: " + colorDe(estado) + ";");

        Label detalle = new Label(textoDeVigencia(datos, estado));
        detalle.setWrapText(true);

        HBox valor = new HBox(6, insignia, detalle);
        valor.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(valor, Priority.ALWAYS);
        fila.getChildren().set(1, valor);
        return fila;
    }

    private String textoDeVigencia(DatosCertificado datos, DatosCertificado.Vigencia estado) {
        if (estado == DatosCertificado.Vigencia.VENCIDO) {
            return "venció el " + datos.fechaDeVencimiento();
        }
        return "hasta el " + datos.fechaDeVencimiento();
    }

    private String colorDe(DatosCertificado.Vigencia estado) {
        if (estado == DatosCertificado.Vigencia.VIGENTE) {
            return VERDE;
        }
        return estado == DatosCertificado.Vigencia.VENCIDO ? ROJO : AMBAR;
    }

    private Label nota(String texto) {
        Label etiqueta = new Label(texto);
        etiqueta.setWrapText(true);
        etiqueta.setStyle("-fx-text-fill: " + GRIS + "; -fx-font-size: 11px;");
        return etiqueta;
    }

    private Label aviso(String texto, String color) {
        Label etiqueta = new Label(texto);
        etiqueta.setWrapText(true);
        etiqueta.setStyle("-fx-text-fill: " + color + ";");
        return etiqueta;
    }
}
