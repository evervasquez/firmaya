package pe.firmador.escritorio;

import java.util.Objects;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Cursor;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;

/**
 * Muestra una pagina del PDF y deja dibujar sobre ella el recuadro donde ira el sello.
 *
 * <p>La pagina se dibuja con un margen alrededor, sobre el fondo gris del visor, para que se
 * lea como una hoja sobre una mesa y no como una imagen recortada. Ese margen entra en todas las
 * conversiones entre pixeles de pantalla y puntos del PDF.</p>
 *
 * <p>Un clic coloca el sello centrado en ese punto y arrastrarlo lo mueve. No se puede cambiar
 * de tamano, y es a proposito: el sello mide siempre lo que mide en ReFirma, porque estirarlo
 * descoloca su texto y produce un sello mutilado en un documento con valor legal.</p>
 *
 * <p>Solo debe usarse desde el hilo de la interfaz de JavaFX.</p>
 */
public final class PanelDocumento extends Pane {

    /** Aire entre la pagina y el borde del visor, en pixeles de pantalla. */
    private static final double MARGEN_PX = 24;

    private static final double GROSOR_BORDE = 1.5;
    private static final Color COLOR_BORDE = Color.web("#0a58ca");
    private static final Color COLOR_RELLENO = Color.web("#0a58ca", 0.16);

    private final ImageView vista = new ImageView();
    private final Rectangle recuadroDibujado = new Rectangle();
    private final ObjectProperty<Recuadro> recuadro = new SimpleObjectProperty<>();

    private double escala = 1;
    private int paginaVisible = 1;
    private double anchoPaginaPt;
    private double altoPaginaPt;

    private boolean moviendo;
    private double origenGestoX;
    private double origenGestoY;
    private Recuadro recuadroAlEmpezar;

    /** Construye el panel vacio, sin documento cargado. */
    public PanelDocumento() {
        setStyle("-fx-background-color: #8a8a8a;");
        vista.setSmooth(true);
        vista.setPreserveRatio(true);

        recuadroDibujado.setFill(COLOR_RELLENO);
        recuadroDibujado.setStroke(COLOR_BORDE);
        recuadroDibujado.setStrokeWidth(GROSOR_BORDE);
        recuadroDibujado.setStrokeLineCap(StrokeLineCap.SQUARE);
        recuadroDibujado.setVisible(false);
        recuadroDibujado.setCursor(Cursor.MOVE);

        getChildren().addAll(vista, recuadroDibujado);

        conectarGestos();
        recuadro.addListener((observable, anterior, actual) -> redibujarRecuadro());
    }

    /**
     * Recuadro elegido por el usuario. Vale {@code null} mientras no haya dibujado ninguno.
     *
     * @return la propiedad observable del recuadro
     */
    public ReadOnlyObjectProperty<Recuadro> recuadroProperty() {
        return recuadro;
    }

    /**
     * @return el recuadro actual, o {@code null} si todavia no hay ninguno
     */
    public Recuadro recuadroActual() {
        return recuadro.get();
    }

    /**
     * Fija el recuadro desde fuera, por ejemplo al recuperar el de la sesion anterior.
     *
     * @param nuevo recuadro a mostrar, o {@code null} para quitarlo
     */
    public void fijarRecuadro(Recuadro nuevo) {
        recuadro.set(nuevo == null ? null : nuevo.dentroDe(anchoPaginaPt, altoPaginaPt));
    }

    /**
     * Muestra una pagina ya dibujada.
     *
     * @param imagen    imagen de la pagina
     * @param pagina    numero de pagina, empezando en uno
     * @param anchoPt   ancho de la pagina en puntos
     * @param altoPt    alto de la pagina en puntos
     * @param escalaPagina pixeles por punto con que se dibujo la imagen
     */
    public void mostrar(Image imagen, int pagina, double anchoPt, double altoPt, double escalaPagina) {
        Objects.requireNonNull(imagen, "imagen es obligatoria");
        this.paginaVisible = pagina;
        this.anchoPaginaPt = anchoPt;
        this.altoPaginaPt = altoPt;
        this.escala = escalaPagina;

        vista.setImage(imagen);
        vista.setX(MARGEN_PX);
        vista.setY(MARGEN_PX);
        double anchoTotal = imagen.getWidth() + MARGEN_PX * 2;
        double altoTotal = imagen.getHeight() + MARGEN_PX * 2;
        setPrefSize(anchoTotal, altoTotal);
        setMinSize(anchoTotal, altoTotal);
        setMaxSize(anchoTotal, altoTotal);
        redibujarRecuadro();
    }

    /**
     * @return la pagina que se esta mostrando, empezando en uno
     */
    public int paginaVisible() {
        return paginaVisible;
    }

    private void conectarGestos() {
        setOnMousePressed(this::colocarDondeSeHizoClic);
        recuadroDibujado.setOnMousePressed(this::empezarMovimiento);
        recuadroDibujado.setOnMouseDragged(this::mover);
        recuadroDibujado.setOnMouseReleased(evento -> moviendo = false);
    }

    /** Un clic sobre la pagina deja el sello centrado en ese punto: no hace falta arrastrar nada. */
    private void colocarDondeSeHizoClic(MouseEvent evento) {
        if (vista.getImage() == null) {
            return;
        }
        recuadro.set(Recuadro.centradoEn(aPuntos(evento.getX()), aPuntos(evento.getY()))
                .dentroDe(anchoPaginaPt, altoPaginaPt));
        evento.consume();
    }

    private void empezarMovimiento(MouseEvent evento) {
        moviendo = true;
        origenGestoX = evento.getSceneX();
        origenGestoY = evento.getSceneY();
        recuadroAlEmpezar = recuadro.get();
        evento.consume();
    }

    private void mover(MouseEvent evento) {
        if (!moviendo || recuadroAlEmpezar == null) {
            return;
        }
        double desplazamientoX = (evento.getSceneX() - origenGestoX) / escala;
        double desplazamientoY = (evento.getSceneY() - origenGestoY) / escala;
        recuadro.set(new Recuadro(
                recuadroAlEmpezar.x() + desplazamientoX,
                recuadroAlEmpezar.y() + desplazamientoY)
                .dentroDe(anchoPaginaPt, altoPaginaPt));
        evento.consume();
    }

    /**
     * Pasa una coordenada de pantalla a puntos del PDF, descontando el margen del visor.
     *
     * <p>Es la conversion que mantiene el sello donde el usuario lo puso pase lo que pase con el
     * zoom: el recuadro se guarda en puntos, no en pixeles.</p>
     */
    private double aPuntos(double pixeles) {
        return (pixeles - MARGEN_PX) / escala;
    }

    private double aPixeles(double puntos) {
        return puntos * escala + MARGEN_PX;
    }

    private void redibujarRecuadro() {
        Recuadro actual = recuadro.get();
        // El recuadro se dibuja sobre cualquier pagina que se este viendo: acompana al usuario
        // mientras navega y la firma acaba en la pagina que tenga delante.
        boolean visible = actual != null && vista.getImage() != null;

        recuadroDibujado.setVisible(visible);
        if (!visible) {
            return;
        }
        recuadroDibujado.setX(aPixeles(actual.x()));
        recuadroDibujado.setY(aPixeles(actual.y()));
        recuadroDibujado.setWidth(actual.ancho() * escala);
        recuadroDibujado.setHeight(actual.alto() * escala);
    }

}
