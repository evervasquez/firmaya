# Desarrollo y empaquetado

> Parte de la documentación de [Firmaya](../README.md).

## Requisitos para desarrollar

| Pieza | Versión |
|---|---|
| JDK | 17 (`maven.compiler.release=17`) |
| JavaFX | 21.0.8 (línea LTS que corre sobre JDK 17) |
| Maven | vía `./mvnw`, no hace falta instalarlo |
| Pruebas | JUnit 6 + AssertJ |

No hace falta instalar JavaFX aparte: viaja como dependencia Maven y queda dentro del JAR.

## Compilar y probar

```bash
./mvnw verify
```

Genera `target/firmador.jar`, un JAR único con todas las dependencias (incluido JavaFX para
macOS arm64).

## Calidad del sello: 384 dpi, y el escudo sin achatar

Las medidas **físicas** del sello son las de ReFirma y no se tocan: 192 × 61,5 pt en el PDF, con
el escudo ocupando el 41 % del ancho. Lo que sí cambia respecto a ReFirma es el detalle interno.

**Se rasteriza a 4x (384 dpi), no a 96 dpi.** El criterio anterior —imitar los 96 dpi del
original para no producir un sello «distinguible»— estaba mal planteado: lo que se compara de una
firma es su validez criptográfica y sus medidas, que no cambian, mientras que un sello borroso lo
nota cualquiera que reciba el documento. Copiar los defectos de una herramienta no es fidelidad.

**El escudo ya no se deforma.** El archivo original mide 104 × 74 px (proporción 1,405) y antes se
estiraba hasta llenar 105 × 82 px (proporción 1,280): quedaba achatado al 91 % de su forma real.
Ahora se dibuja conservando su proporción y centrado en su zona, aunque sobre algo de margen.

Lo que cuesta, medido sobre el mismo PDF de prueba (15,6 KB sin firmar):

| Resolución | PNG del sello | PDF firmado |
|---|---|---|
| 96 dpi (ReFirma) | 13,7 KB | 50,8 KB |
| 192 dpi (2x) | 44,4 KB | ~81 KB |
| 288 dpi (3x) | 88,2 KB | ~129 KB |
| **384 dpi (4x)** | **143,5 KB** | **184,1 KB** |

Se eligió 4x: 184 KB por documento firmado es irrelevante hoy —un PDF escaneado de una página pesa
más—, y cada firma adicional suma otros ~143 KB. Si algún día importara el peso, 3x da un sello
visiblemente mejor que el original por 55 KB menos.

### El escudo: vectorial, y el rótulo como texto

El bitmap de 104 × 74 px que traía ReFirma era el techo de la calidad: al ampliarlo quedaba suave
pero nunca nítido, y el «FIRMA DIGITAL» que lleva dentro se veía borroso porque formaba parte de
la imagen. Se sustituyó por el **Escudo Nacional del Perú vectorial** (símbolo de dominio público,
de Wikimedia Commons), rasterizado a 1680 × 1920 px en `assets/escudo-nacional.png`; el SVG
original queda en `assets/escudo-nacional.svg` por si algún día hay que rasterizar a otra medida.

Así queda la zona izquierda del sello, que sigue ocupando el 41 % del ancho:

- marco rojo alrededor, como en el original;
- el escudo pegado a la izquierda, ocupando el alto disponible y **conservando su proporción**
  (0,875), sin achatarse;
- **«FIRMA DIGITAL» dibujado como texto** en dos líneas, en Courier New Bold —con la
  monoespaciada del sistema como respaldo si no está instalada—, **centrado verticalmente
  respecto al escudo**;
- **sin el texto curvo «REPÚBLICA DEL PERÚ»**, que se descartó por ilegible a este tamaño.

El resultado se ve en `target/comparacion-escudo-vectorial.png`, que enfrenta el sello anterior
con el actual, y en `target/comparacion-sello.png`, que compara las resoluciones.

El escudo se dibuja desde `assets/escudo-nacional.png`, rasterizado del SVG de dominio público
que acompaña al proyecto. Firmaya no incluye ningún recurso gráfico perteneciente a RENIEC.

## El visor: margen y zoom

La página se dibuja con un **margen de 24 px alrededor** sobre el fondo gris, para que se lea como
una hoja sobre una mesa y no como una imagen recortada contra los bordes.

El desplegable de zoom ofrece **Ajustar a la ventana** (la página entera cabe), **Ajustar al
ancho** y los porcentajes fijos de siempre. Un documento **se abre ajustado a la ventana**, no a
un 125 % fijo: ver la hoja entera es lo que permite decidir dónde va la firma. Los dos ajustes se
recalculan al cambiar el tamaño de la ventana.

El margen entra en la conversión entre píxeles de pantalla y puntos del PDF, que es donde es fácil
equivocarse. El recuadro del sello se guarda **en puntos**, así que su posición no depende del
aumento con que se esté viendo la página. Comprobado a 0,75x, 1x y 2x: el mismo recuadro se dibuja
en x = 99, 124 y 224 px de pantalla, y las tres veces corresponde a **100,00 pt** del documento.

## Generar el instalador `.dmg`

```bash
./scripts/empaquetar-dmg.sh
```

o, equivalentemente, por Maven:

```bash
./mvnw -Pdmg package
```

El resultado queda en `target/instalador/Firmaya-1.0.0.dmg` (≈77 MB). Incluye el runtime
de Java, así que **el usuario final no necesita instalar Java**.

## Gatekeeper: primera apertura sin firmar

El `.dmg` que genera el script **no está firmado ni notarizado**. macOS lo bloqueará al abrirlo
por primera vez con un mensaje del tipo *"«Firmaya» está dañada y no se puede abrir"* o
*"no se puede abrir porque procede de un desarrollador no identificado"*.

Para abrirlo igualmente en un equipo propio:

1. Arrastre la app a `/Applications` desde el `.dmg`.
2. Quite la marca de cuarentena:

   ```bash
   xattr -dr com.apple.quarantine "/Applications/Firmaya.app"
   ```

3. Ábrala normalmente.

Alternativa sin terminal: clic derecho sobre la app → **Abrir** → **Abrir** en el diálogo, o
**Ajustes del Sistema → Privacidad y seguridad → Abrir igualmente**.

## Firmar y notarizar con cuenta Apple Developer

Para distribuir la app a terceros sin que tengan que hacer nada de lo anterior hace falta una
cuenta **Apple Developer Program** (de pago, anual) y un certificado
**Developer ID Application**.

1. **Obtener los certificados.** En el portal de Apple Developer cree un certificado
   *Developer ID Application* (para la app) y otro *Developer ID Installer* (si además firma un
   `.pkg`). Instálelos en el llavero del equipo que construye. Compruebe cuál está disponible:

   ```bash
   security find-identity -v -p codesigning
   ```

2. **Firmar durante el empaquetado.** Añada al `jpackage` de `scripts/empaquetar-dmg.sh`:

   ```bash
   --mac-sign \
   --mac-signing-key-user-name "Nombre Apellido (TEAMID)" \
   --mac-package-signing-prefix pe.firmador.
   ```

   `jpackage` firma la app y el `.dmg` con ese certificado. Como la app incluye un runtime de
   Java con bibliotecas nativas, hace falta *hardened runtime*, que `jpackage` activa al firmar.

3. **Guardar las credenciales de notarización** una sola vez. Use una
   [contraseña específica de la app](https://appleid.apple.com), no la del Apple ID:

   ```bash
   xcrun notarytool store-credentials "notarizacion-firmador" \
     --apple-id "correo@ejemplo.com" \
     --team-id "TEAMID" \
     --password "xxxx-xxxx-xxxx-xxxx"
   ```

4. **Notarizar el `.dmg`** y esperar el resultado:

   ```bash
   xcrun notarytool submit "target/instalador/Firmaya-1.0.0.dmg" \
     --keychain-profile "notarizacion-firmador" --wait
   ```

   Si sale `Invalid`, pida el detalle con
   `xcrun notarytool log <id-de-envio> --keychain-profile "notarizacion-firmador"`.

5. **Grapar el ticket** al `.dmg`, para que valide incluso sin conexión:

   ```bash
   xcrun stapler staple "target/instalador/Firmaya-1.0.0.dmg"
   xcrun stapler validate "target/instalador/Firmaya-1.0.0.dmg"
   ```

6. **Comprobar** que Gatekeeper la acepta:

   ```bash
   spctl -a -vvv -t install "/Applications/Firmaya.app"
   ```

Con el ticket grapado, el usuario final abre el `.dmg` con doble clic y no ve ninguna
advertencia.

## Limitaciones conocidas

- **El `.dmg` se construye para macOS arm64.** JavaFX se resuelve con el clasificador de la
  plataforma que construye; para un `.dmg` Intel hay que construir en un equipo Intel o fijar el
  clasificador `mac` en el `pom.xml`.
- **Páginas giradas.** Si una página declara `/Rotate` distinto de cero, la barra superior avisa:
  la posición del sello puede no coincidir con lo que se ve en pantalla.
- **La contraseña pasa por un `String` de JavaFX.** `PasswordField` guarda su contenido en un
  `String` que no se puede limpiar a mano. Se convierte a `char[]` y el campo se vacía en cuanto
  se puede —al terminar la firma, o al cerrarse la ventana de configuración, que necesita
  conservarlo entre **Comprobar** y **Guardar**—, pero ese `String` intermedio queda a merced del
  recolector de basura. Es una limitación del control de JavaFX, no del código de firma.
- **Aviso de arranque.** Al abrir la ventana, JavaFX imprime en el registro
  `Unsupported JavaFX configuration: classes were loaded from 'unnamed module'`. Es inofensivo y
  se debe a que JavaFX viaja en el classpath y no en el `module-path`.
