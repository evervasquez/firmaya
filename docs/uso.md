# Guía de uso

> Parte de la documentación de [Firmaya](../README.md).

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
