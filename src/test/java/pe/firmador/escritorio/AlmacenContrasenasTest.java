package pe.firmador.escritorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.firmador.FirmaException;

/**
 * Comportamiento que debe cumplir cualquier {@link AlmacenContrasenas}. Se prueba contra
 * {@link AlmacenEnMemoria} para no tocar el llavero real del usuario.
 */
class AlmacenContrasenasTest {

    private static final String CUENTA = "/Users/prueba/firma.p12";
    private static final String OTRA_CUENTA = "/Users/prueba/otra-firma.p12";

    private AlmacenEnMemoria almacen;

    @BeforeEach
    void prepararAlmacen() {
        almacen = new AlmacenEnMemoria();
    }

    @Test
    void devuelveVacioCuandoNoHayNadaGuardado() {
        assertThat(almacen.recuperar(CUENTA)).isEmpty();
    }

    @Test
    void recuperaLaContrasenaGuardada() {
        almacen.guardar(CUENTA, "clave-secreta".toCharArray());

        assertThat(almacen.recuperar(CUENTA)).contains("clave-secreta".toCharArray());
    }

    @Test
    void devuelveUnaCopiaParaQueLimpiarlaNoBorreLaGuardada() {
        almacen.guardar(CUENTA, "clave-secreta".toCharArray());

        char[] primera = almacen.recuperar(CUENTA).orElseThrow();
        Arrays.fill(primera, '\0');

        assertThat(almacen.recuperar(CUENTA)).contains("clave-secreta".toCharArray());
    }

    @Test
    void reemplazaLaContrasenaAnteriorDeLaMismaCuenta() {
        almacen.guardar(CUENTA, "antigua".toCharArray());
        almacen.guardar(CUENTA, "nueva".toCharArray());

        assertThat(almacen.recuperar(CUENTA)).contains("nueva".toCharArray());
    }

    @Test
    void olvidarBorraLaContrasenaGuardada() {
        almacen.guardar(CUENTA, "clave-secreta".toCharArray());

        almacen.olvidar(CUENTA);

        assertThat(almacen.recuperar(CUENTA)).isEmpty();
    }

    @Test
    void olvidarUnaCuentaDesconocidaNoFalla() {
        almacen.olvidar(CUENTA);

        assertThat(almacen.recuperar(CUENTA)).isEmpty();
    }

    @Test
    void noDevuelveLaContrasenaDeOtroCertificado() {
        almacen.guardar(CUENTA, "clave-secreta".toCharArray());

        assertThat(almacen.recuperar(OTRA_CUENTA)).isEmpty();
    }

    @Test
    void olvidarUnCertificadoNoAfectaAlOtro() {
        almacen.guardar(CUENTA, "primera".toCharArray());
        almacen.guardar(OTRA_CUENTA, "segunda".toCharArray());

        almacen.olvidar(CUENTA);

        assertThat(almacen.recuperar(OTRA_CUENTA)).contains("segunda".toCharArray());
    }

    @Test
    void cadaCertificadoTieneSuPropiaCuenta() {
        String una = AlmacenContrasenas.cuentaPara(Path.of("/Users/prueba/firma.p12"));
        String otra = AlmacenContrasenas.cuentaPara(Path.of("/Users/prueba/otra-firma.p12"));

        assertThat(una).isNotEqualTo(otra);
    }

    @Test
    void laCuentaEsLaRutaAbsolutaYNormalizada() {
        String cuenta = AlmacenContrasenas.cuentaPara(Path.of("/Users/prueba/./sub/../firma.p12"));

        assertThat(cuenta).isEqualTo("/Users/prueba/firma.p12");
    }

    @Test
    void sinAlmacenDisponibleNoRecuperaNada() {
        almacen.guardar(CUENTA, "clave-secreta".toCharArray());
        almacen.marcarNoDisponible();

        assertThat(almacen.disponible()).isFalse();
        assertThat(almacen.recuperar(CUENTA)).isEmpty();
    }

    @Test
    void sinAlmacenDisponibleGuardarAvisaEnVezDeCallar() {
        almacen.marcarNoDisponible();

        assertThatThrownBy(() -> almacen.guardar(CUENTA, "clave-secreta".toCharArray()))
                .isInstanceOf(FirmaException.class);
    }
}
