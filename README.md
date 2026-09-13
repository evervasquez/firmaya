# Firmaya

Firma digital de documentos PDF compatible con ReFirma PDF 1.6 de RENIEC, con aplicación de
escritorio y herramienta de línea de comandos.

El sello visible reproduce las medidas reales de ReFirma (192 × 61,5 pt, imagen de 256 × 82 px
a 96 dpi), los cinco motivos de firma y la convención de nombre `documento[R].pdf`.

## Estado de la validación

Un PDF firmado con este motor en nivel **PAdES-BASELINE-B** fue sometido al
**Validador de firmas digitales de Firma Perú** (`apps.firmaperu.gob.pe`, plataforma de la PCM)
con resultado:

> Resultado **válido** — Firmas procesadas (1), Firmas válidas (1), PAdES_BASELINE_B
> Generado por: Firma Perú 1.1.0 | DSS 5.11.1

Por eso **PAdES-B es el nivel por defecto y su lógica de firma no se toca**. El nivel PAdES-LT
existe como opción que el usuario activa explícitamente (ver más abajo).

Nota: el certificado del titular es de **Girasol PE / FirmEasy**, acreditada ante INDECOPI. El
validador de RENIEC solo reconoce certificados emitidos por RENIEC, por eso rechaza este
certificado aunque sea válido; el validador de Firma Perú sí lo acepta.

## Por qué existe

ReFirma PDF, el firmador oficial de RENIEC, **solo se distribuye para Windows**. Su instalador
es un ClickOnce y su integración con el almacén de certificados va por MSCAPI, la API de
criptografía de Microsoft.

Firma Perú, la plataforma oficial de la PCM, sí tiene versión de macOS, pero en esa plataforma
**solo funciona con DNIe** —el documento físico con lector de tarjetas—, no con un certificado
en archivo `.p12`. Su propia página lo dice en la pestaña de macOS.

Para firmar desde una Mac con un certificado en archivo, el camino habitual era instalar Windows
en una máquina virtual. Este proyecto existe para no tener que hacerlo.

## Qué se averiguó de ReFirma

Todo lo que este proyecto replica salió de dos fuentes verificables, no de suposiciones.

**Del JAR de ReFirma PDF 1.6**, descargado de `sp.reniec.gob.pe` y desempaquetado (viene en
formato pack200 anidado, hace falta `unpack200` de un JDK 11 o anterior):

| Componente | Versión en ReFirma |
|---|---|
| DSS (`eu.europa.ec.joinup.sd-dss`) | **5.4.3** |
| `dss-pades-pdfbox` | 5.4.3 — el que firma PDF |
| `dss-token` | 5.4.3 — el que lee el `.p12` |
| Apache PDFBox | 2.0.15 |
| BouncyCastle | presente |
| Apache Santuario (xmlsec) | 2.0.10 |

O sea que **ReFirma está construida sobre DSS**, la implementación de referencia de ETSI. Este
proyecto usa la misma librería en versión actual, y por eso produce firmas equivalentes.

De su `default.properties` salieron los valores que replicamos:

```
Reasons=Soy el autor del documento;En señal de conformidad;Doy V° B°;Por encargo;Doy fe
ReasonId=0
DescFontSize=7
SignedPdfSufix=[R]
StampOnFirstPage=false
NtpServers=horanacional.indecopi.gob.pe;time.windows.com
```

El sello de ReFirma incluye el Escudo Nacional como un mapa de bits de 104 × 74 px. Firmaya
**no redistribuye ese recurso**: dibuja el escudo a partir de la versión vectorial de dominio
público (ver `NOTICE.md`).

**De un PDF real firmado con ReFirma en Windows**, analizado con `pdfsig` y `pypdf`:

- recuadro de **192 × 61,5 pt**, rasterizado a **256 × 82 px** (96 dpi)
- el escudo ocupa los primeros **105 px** de ancho (41 %); el texto arranca en x = 105
- `SubFilter = ETSI.CAdES.detached`, nivel PAdES-B, digest SHA-256
- **sin sello de tiempo**: no hay `DocTimeStamp` ni RFC 3161
- la fecha del sello va en **UTC con sufijo `+0000`**, no en hora local

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

## Los dos validadores oficiales, y por qué dan resultados distintos

| Validador | Qué reconoce |
|---|---|
| [ReFirma Validator](https://dsp.reniec.gob.pe/refirma_suite/validator/web/main.jsf) (RENIEC) | **Solo certificados emitidos por RENIEC** |
| [Validador de Firma Perú](https://apps.firmaperu.gob.pe/web/validador.xhtml) (PCM) | La lista completa de entidades acreditadas ante INDECOPI |

Un documento firmado con un certificado de Llama.pe, Girasol/FirmEasy u otra entidad acreditada
sale **INDETERMINADO** en el de RENIEC aunque sea perfectamente válido. No es un defecto del
documento: es que ese validador solo confía en su propia cadena.

Comprobado: un documento firmado con **ReFirma en Windows**, con certificado de Llama.pe, también
sale INDETERMINADO en el validador de RENIEC. El validador correcto para certificados que no son
de RENIEC es el de Firma Perú.

## Pendiente: el sello de tiempo

Una firma PAdES-B no lleva dentro la prueba de que el certificado estaba vigente al firmar. Cuando
el certificado vence, el documento deja de poder validarse de forma concluyente — le pasó a un
documento de 2024 firmado con ReFirma, cuyo certificado de Llama.pe venció en noviembre de ese año.

Lo resuelve un **sello de tiempo** de una Autoridad de Sellado de Tiempo acreditada, que eleva la
firma a PAdES-T. Datos verificados:

- El servicio TSA de **RENIEC** se otorga por convenio a entidades de la administración pública.
  No está disponible para un profesional independiente.
- **Girasol PE / FirmEasy sí está acreditada como Autoridad de Sellado de Tiempo** ante INDECOPI.
  Su Declaración de Prácticas (v1.1, 04/03/2026) confirma cumplimiento de RFC 3161.
  Certificado de la TSA: `girasol.pe/autoridades-certificacion/firmeasy-tsa.cer`
- **La URL del servicio no está publicada.** Hay que pedirla a `soporte@girasolpe.com`.

El campo del servidor de sellado de tiempo en la aplicación trae por defecto un TSA público que
**no está acreditado en Perú**: sirve para probar, no para valor legal. Sustitúyelo por el de
Girasol en cuanto se disponga de él.

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

## Abrir la aplicación de escritorio

Con el instalador ya generado:

```bash
open "/Applications/Firmaya.app"
```

Sin instalar, directamente desde el JAR construido:

```bash
java -jar target/firmador.jar
```

Sin argumentos abre la ventana; con argumentos se comporta como la herramienta de consola.

### Configurar el certificado (una sola vez)

El certificado es **configuración, no un campo de formulario**: se elige una vez y la aplicación
no vuelve a pedir nada. Mientras no haya ninguno, el panel derecho muestra una sola cosa: el
botón **Cargar certificado**.

Al pulsarlo se abre una ventana con dos orígenes posibles:

**1. Del Llavero de macOS (recomendado).** Lista las identidades de su **llavero de inicio de
sesión** que sirven de verdad para firmar documentos. No hay contraseña que escribir ni que
guardar: la clave privada no sale del llavero y la custodia el sistema, igual que hace ReFirma en
Windows con el almacén de Windows. La primera vez que firme, macOS puede pedirle permiso para
usar esa clave; elija **Permitir siempre** y no vuelve a preguntar.

Qué se deja fuera de esa lista, y por qué:

| Se descarta | Motivo |
|---|---|
| Certificados de **firma de código** (uso extendido `1.3.6.1.5.5.7.3.3`) | Son los de Apple Developer y similares: firman aplicaciones. Una firma hecha con ellos no vale como firma de un documento. |
| Certificados cuyo `keyUsage` no permite `digitalSignature` ni `nonRepudiation` | El propio certificado declara que no es para firmar. |
| Certificados **vencidos** | Lo que se firme con ellos no pasa la validación. |
| Identidades del **llavero del sistema** (`/Library/Keychains/System.keychain`) | Usar su clave privada obliga a macOS a pedir usuario y contraseña de **administrador** en cada firma, y esta aplicación no los pide nunca. |
| Copias repetidas del mismo certificado | El llavero puede guardar la misma identidad varias veces y macOS las numera (`su nombre`, `su nombre 1`, `su nombre 2`…). Se agrupan por huella y se ofrece una sola: elegir entre copias idénticas no aporta nada. |

Si tras ese filtro no queda ninguna, la ventana no ofrece una lista con opciones inútiles: explica
la situación —incluido el caso de tener el certificado en el llavero del sistema— y deja
preseleccionado el camino del archivo `.p12`, que es el que va a funcionar.

**2. Desde un archivo `.p12`.** Con **Examinar…**, o eligiéndolo de la lista de certificados que
ya usó antes, que se muestra con el nombre del titular para reconocerlos. Se escribe la
contraseña y se deja marcada (lo está de fábrica) la casilla **Recordar la contraseña en el
Llavero de macOS**.

El botón **Comprobar y guardar** abre de verdad el certificado con esa contraseña. Solo si
funciona muestra la ficha leída —titular, documento, vigencia y emisor— y habilita **Guardar**;
si falla, lo dice y no guarda nada. Así no puede quedar configurada una contraseña equivocada
que solo fallaría el día que haya que firmar.

### La ficha del certificado

Una vez configurado, el panel muestra una tarjeta de solo lectura con lo que se leyó del propio
certificado:

| Dato | De dónde sale |
|---|---|
| Titular | nombre común (`CN`) del sujeto, el mismo que se imprime en el sello |
| Documento | `serialNumber` del sujeto (RENIEC lo emite como `DNI-12345678`) |
| Vigencia | `notAfter` del certificado, con el estado: **Vigente**, **Por vencer** o **Vencido** |
| Emisor | nombre común (`CN`) de la entidad certificadora |
| Origen | el llavero o el archivo `.p12` |

A menos de **30 días** del vencimiento la tarjeta avisa en ámbar y, si ya venció, en rojo y el
botón de firmar queda deshabilitado: firmar con un certificado vencido produce un documento que
no pasa la validación oficial. Esa es la única razón por la que la aplicación vuelve a pedir
atención. El botón **Editar** abre la misma ventana para cargar el certificado renovado.

Si al abrir la aplicación el certificado configurado no se puede usar, la tarjeta lo explica en
vez de fallar al firmar: el archivo ya no está en esa ruta, la contraseña no está en el Llavero,
la contraseña guardada ya no lo abre (lo renovaron) o la identidad desapareció del llavero.

### Importar un `.p12` al llavero de inicio de sesión

Es opcional y se hace una sola vez desde la Terminal. Después el certificado aparece en la lista
del primer origen y desaparece la contraseña del flujo:

```bash
security import /ruta/al/certificado.p12 -k ~/Library/Keychains/login.keychain-db
```

`security` pedirá la contraseña del `.p12` en un diálogo del sistema. **No use la opción `-P`
con la contraseña en la línea de comandos**: cualquier proceso del equipo la vería con un `ps`.

### Cómo se usa la ventana

1. Arrastre un PDF a la ventana o pulse **Abrir PDF…**.
2. Navegue con **|<** (primera), **<** (anterior), **>** (siguiente) y **>|** (última), o escriba
   el número en el campo de página y pulse **Enter**. Un número que no exista en el documento
   devuelve el campo a la página en la que está, sin más consecuencias.
3. **Haga clic en la página** donde quiere el sello: queda centrado en ese punto. Para
   ajustarlo, arrástrelo. **El tamaño no se toca**: el sello mide siempre 192 x 61,5 pt, igual
   que en ReFirma. Estirarlo descolocaba su contenido y acababa cortando el texto —«Firmado
   digitalmen…»—, que en un documento con valor legal es inaceptable. Si el titular tuviera un
   nombre larguísimo, la letra se reduce por pasos hasta que el texto entre entero; nunca se
   corta.
4. **El recuadro le acompaña al cambiar de página**, en la misma posición y tamaño, y la firma
   va a parar a la página que tenga delante — es la ubicación «Actual» de ReFirma. El panel
   derecho lo dice con todas sus letras: «La firma irá en la página 3 de 8». Si la página nueva
   es más pequeña y el recuadro no cabe, se encoge sin deformarse para seguir entrando entero.
5. Elija el **motivo** entre los cinco de ReFirma.
6. Pulse **Firmar documento**. No se pregunta nada: la clave sale del llavero. Mientras firma
   aparece un indicador de progreso con «Firmando el documento…» y el botón queda bloqueado para
   no disparar la firma dos veces. Al terminar siempre pasa algo visible: o se abre el documento
   firmado con su aviso, o sale el motivo del fallo. **El botón nunca está apagado sin
   explicación**: si falta el documento, el sello o el certificado, se pulsa y la aplicación dice
   qué falta.

### Dónde se guarda el documento firmado

En el panel derecho, bajo **Dónde se guarda el documento firmado**, hay dos opciones:

- **Junto al documento original** (por defecto): el comportamiento de ReFirma, que guarda el
  firmado en la misma carpeta que el original.
- **En esta carpeta**: una carpeta fija que usted elige con el selector del sistema. La ruta se
  muestra bajo la opción y se cambia con **Elegir carpeta…** sin entrar en ningún menú.

La elección **se recuerda entre sesiones**, igual que el certificado: se configura una vez. Si la
carpeta guardada ya no existe al abrir la aplicación, se vuelve a guardar junto al original en
vez de arrastrar una configuración rota.

Si al firmar la carpeta ya no está, no es una carpeta o macOS no da permiso de escritura —cosa
frecuente con Documentos, Escritorio o iCloud—, la aplicación lo dice con el motivo y sugiere
elegir otra, en vez de fallar a mitad de la firma.

**El nombre en la carpeta de destino.** Sale siempre del documento de origen, así que `acta.pdf`
produce `acta[R].pdf` también en la carpeta elegida. Si ese nombre ya está ocupado ahí, se sigue
numerando —`acta[R2].pdf`, `acta[R3].pdf`— hasta encontrar uno libre: **nunca se sobrescribe un
documento firmado**. Por eso el número indica la vuelta del firmador y no necesariamente cuántas
firmas lleva el PDF; las firmas que tiene se leen del propio documento y se muestran en el panel.

### Firmar varias veces el mismo documento

Al terminar, la aplicación **abre el documento ya firmado** y avisa de dónde quedó guardado. El
aviso —y el panel derecho, durante el resto de la sesión— traen la **ruta completa en un campo
del que se puede copiar el texto** y dos botones: **Mostrar en Finder**, que abre la carpeta con
el archivo ya seleccionado, y **Abrir documento**, que lo abre con la aplicación de PDF del
sistema. Si para entonces el archivo se movió o se borró, lo dice en vez de fallar.

Ambos botones llaman a `/usr/bin/open` pasando la ruta como **argumento del proceso**, nunca
dentro de una orden de shell: por eso funcionan sin comillas ni escapes con los nombres que
genera la propia aplicación, que llevan corchetes (`acta[R].pdf`) y a menudo espacios. Con
él delante puede ir a otra página, colocar el recuadro y volver a firmar: cada firma nueva se
añade como una revisión incremental del PDF, de modo que **las firmas anteriores siguen siendo
válidas**. El panel del sello indica cuántas trae ya el documento («Este documento ya tiene 1
firma. La suya se añadirá sin invalidarla»), leídas con el mismo verificador que usa el modo
`--verificar` de la consola.

Los nombres no se encadenan: el sufijo se numera.

| Vuelta | Archivo |
|---|---|
| Original | `documento.pdf` |
| Primera firma | `documento[R].pdf` (la convención de ReFirma, `SignedPdfSufix=[R]`) |
| Segunda firma | `documento[R2].pdf` |
| Tercera firma | `documento[R3].pdf` |

Se eligió así en vez de `documento[R][R].pdf` porque el nombre crecía sin control y no decía
nada; el número indica por cuántas vueltas del firmador pasó. Ningún archivo se sobrescribe
nunca: si el destino ya existe, la firma se detiene con un error.

### Qué se recuerda entre sesiones

Se guardan en las preferencias del usuario (`java.util.prefs`): la última carpeta usada, el
certificado configurado (la ruta del `.p12` o el alias del llavero), el nombre de su titular, la
lista de hasta ocho certificados ya usados, la última posición y tamaño del sello, el último
motivo y la dirección del servidor de sellado de tiempo. Ahí **no hay ningún
secreto**: son rutas, números y el mismo nombre que ya queda impreso en el sello del documento
firmado.

### Dónde queda la contraseña

**La contraseña nunca se escribe en las preferencias, ni en un archivo de la aplicación, ni en el
registro.** Tiene solo dos destinos posibles:

- **Si usa una identidad del llavero**: no hay contraseña. La clave privada no sale del llavero.
- **Si desmarca la casilla** al configurar un `.p12`: no se guarda en ninguna parte, y la próxima
  vez que abra la aplicación la tarjeta le pedirá cargarla de nuevo.
- **Si deja marcada la casilla** (comportamiento de fábrica): se guarda en el **llavero de inicio
  de sesión de macOS**, que es quien la cifra y la ata a su sesión. La aplicación llega al llavero invocando el propio comando
  `/usr/bin/security` del sistema, sin dependencias añadidas, y **nunca pasa la contraseña como
  argumento** (sería visible para cualquier proceso con un `ps`): se la entrega por la entrada
  estándar del comando.

Detalles del elemento que se crea en el llavero:

| Dato | Valor |
|---|---|
| Llavero | el de **inicio de sesión** del usuario; nunca el del sistema |
| Servicio (`-s`) | `pe.firmador.certificado` |
| Nombre visible | `Firmaya` |
| Cuenta (`-a`) | la ruta absoluta del `.p12` |

Esa es **la única entrada que la aplicación crea en su Llavero**, y solo si usted deja marcada la
casilla. Para verla:

```bash
security find-generic-password -s pe.firmador.certificado
```

La cuenta es la ruta del certificado a propósito: cada certificado tiene su propia entrada, así
que cambiar de certificado nunca reutiliza por error la contraseña del anterior. Al configurar
un certificado distinto, la entrada del anterior se borra: la aplicación guarda como mucho una.

El valor se almacena **codificado en Base64**, no en claro. No es una medida de seguridad —el
llavero ya cifra el contenido—, sino de corrección: `security` devuelve el dato en hexadecimal
cuando contiene algo que no sea ASCII imprimible, y esa salida es indistinguible de una
contraseña que casualmente solo tenga dígitos hexadecimales, como `abc123`. Si alguna vez
necesita leerla desde el llavero, descodifíquela con `base64 -d`.

#### Cómo revocarla

De cualquiera de estas dos formas:

- En la aplicación: **Editar** en la ficha del certificado y desmarcar la casilla.
- En **Acceso a Llaveros** (o `Aplicaciones → Utilidades → Acceso a Llaveros`): buscar
  `Firmaya`, seleccionar el elemento y eliminarlo. También sirve:

  ```bash
  security delete-generic-password -s pe.firmador.certificado -a "/ruta/al/certificado.p12"
  ```

En un equipo que no sea macOS, o si el comando `security` no está disponible, la casilla aparece
deshabilitada con el motivo.

## Línea de comandos

```bash
java -jar target/firmador.jar <pdf> <p12> [--motivo N] [--pagina N] [--x N] [--y N] [--lt] [--tsa URL]
java -jar target/firmador.jar --verificar <pdf>
java -jar target/firmador.jar --ayuda
```

La contraseña se pide siempre por consola: no se acepta como argumento ni por variable de
entorno, porque ambos quedan registrados (historial del shell, lista de procesos).

## Nivel PAdES-LT (largo plazo)

PAdES-BASELINE-B guarda la firma y el certificado del firmante, nada más. Cuando el certificado
vence, un validador ya no puede comprobar que estaba vigente al momento de firmar, y el
documento deja de validar. Es justo lo que le pasó a un documento de 2024 que hoy ya no valida.

**PAdES-BASELINE-LT** agrega un sello de tiempo y deja incrustadas dentro del propio PDF las
respuestas OCSP y las CRL de toda la cadena. La prueba viaja con el archivo, así que el
documento sigue validando después de que el certificado venza.

### Cómo probarlo

En la ventana: en el bloque **Nivel**, elija *PAdES-LT (largo plazo, con sello de tiempo)*. El
campo de servidor de sellado de tiempo se habilita; el valor de fábrica es
`http://timestamp.digicert.com`.

Por línea de comandos:

```bash
java -jar target/firmador.jar documento.pdf certificado.p12 --lt
java -jar target/firmador.jar documento.pdf certificado.p12 --lt --tsa https://freetsa.org/tsr
java -jar target/firmador.jar --verificar "documento[R].pdf"
```

En la verificación el nivel debe aparecer como `PAdES-BASELINE-LT` en lugar de
`PAdES-BASELINE-B`. Después conviene subir el PDF al validador de Firma Perú para confirmarlo
de forma independiente.

### Lo que hay que saber antes de usar LT

- **Necesita internet.** Sale a pedir el sello de tiempo y la prueba de revocación (OCSP y CRL)
  de la entidad certificadora. Sin red, la firma falla con un mensaje explícito en vez de
  producir un LT incompleto.
- **El servidor de sellado de tiempo por defecto no es una TSA acreditada en Perú.** El servicio
  de sellado de tiempo de RENIEC requiere convenio institucional. Los servidores públicos
  gratuitos (DigiCert, FreeTSA, Sectigo) sirven para que el documento tenga un sello de tiempo
  técnicamente correcto, pero si el trámite exige una TSA acreditada hay que poner la de la
  entidad que corresponda en el campo del servidor.
- **LT todavía no está comprobado contra el validador oficial.** PAdES-B sí. Por eso LT es una
  opción y no el valor por defecto.

Los servicios de revocación del certificado del titular están activos y respondiendo:
`http://ocsp.girasol.pe/firmeasy-sub-ca` (OCSP) y `http://crl.girasol.pe/firmeasy-sub-ca.crl`
(CRL, se actualiza a diario).

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

## Instalación

Descarga `Firmaya-1.0.0.dmg` de la sección *Releases*, ábrelo y arrastra **Firmaya** a
Aplicaciones. Incluye su propio Java: no hace falta instalar nada más.

La primera vez macOS la bloqueará por no estar firmada con una cuenta de Apple Developer.
Ábrela con **clic derecho → Abrir**, o quita la marca de cuarentena:

```bash
xattr -dr com.apple.quarantine "/Applications/Firmaya.app"
```

Requiere macOS con Apple Silicon (arm64) y un certificado digital en archivo `.p12`, o
importado al llavero de inicio de sesión.

## Licencia

Firmaya se distribuye bajo **LGPL-2.1**. El texto completo está en `LICENSE` y las licencias
de las dependencias en `NOTICE.md`.

Puedes usarla libremente, también con fines comerciales. Si la modificas y distribuyes esa
versión, debes publicar los cambios bajo la misma licencia.

## Aviso

Firmaya **no es un producto de RENIEC** ni está afiliada a esa institución. Es un desarrollo
independiente, construido sobre la misma librería libre de firma que usa ReFirma (DSS), que
reproduce su formato para que los documentos resulten familiares a quien los recibe.

La validez legal de una firma depende del **certificado**, no del programa que la genera. Usa
siempre un certificado emitido por una entidad acreditada ante INDECOPI, y comprueba el
resultado en el [validador oficial de Firma Perú](https://apps.firmaperu.gob.pe/web/validador.xhtml).

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
