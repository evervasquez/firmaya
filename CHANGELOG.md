# Changelog

Todos los cambios relevantes de Firmaya se anotan en este archivo.

El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)
y el proyecto usa [versionado semántico](https://semver.org/lang/es/).

## [1.0.0] - 2026-09-13

Primera versión pública.

### Added
- **Firma PAdES-BASELINE-B** sobre DSS, la implementación de referencia de ETSI. Un documento
  firmado con este motor obtuvo resultado **válido** en el validador oficial de Firma Perú
  (plataforma de la PCM), por lo que la lógica de firma queda fijada.
- **Aplicación de escritorio en JavaFX**: abrir el PDF, navegar entre páginas, colocar el sello
  con un clic y firmar. El recuadro acompaña a la página que se esté viendo y la firma va a esa
  página, igual que ReFirma.
- **Sello visible** con las medidas reales de ReFirma (192 × 61,5 pt), dibujado a **384 dpi** con
  el Escudo Nacional vectorial de dominio público y el rótulo «FIRMA DIGITAL» como texto.
- **Configuración del certificado una sola vez**: ficha de solo lectura con titular, documento,
  vigencia con semáforo y emisor. La contraseña se guarda en el **Llavero de macOS**, nunca en
  las preferencias ni en disco.
- **Certificado desde el llavero de macOS** además del archivo `.p12`. Solo se ofrecen las
  identidades aptas para firmar documentos: se descartan las de firma de código y las vencidas.
- **Firma encadenada**: varias firmas sobre el mismo documento mediante revisión incremental,
  sin invalidar las anteriores. Los archivos se numeran `[R]`, `[R2]`, `[R3]`.
- **Destino configurable**: junto al documento original o en una carpeta fija que se recuerda.
- **Acceso al documento firmado** desde la propia aplicación, con la ruta copiable y botones para
  mostrarlo en el Finder o abrirlo.
- **Nivel PAdES-LT** como opción, que incrusta la prueba de revocación para que el documento
  siga validando después de que venza el certificado.
- **Herramienta de línea de comandos** para firmar y para verificar firmas existentes.
- **Instalador `.dmg`** generado con `jpackage`, con el runtime de Java incluido.
- Documentación de lo averiguado sobre ReFirma, los dos validadores oficiales y el estado del
  sello de tiempo.

### Notes
- Requiere macOS con Apple Silicon. El `.dmg` se construye para arm64.
- La aplicación se distribuye sin firmar ni notarizar: la primera apertura exige quitar la marca
  de cuarentena o abrirla con clic derecho.
- El servidor de sellado de tiempo que viene por defecto **no está acreditado en Perú**; sirve
  para probar, no para valor legal.

[1.0.0]: https://github.com/evervasquez/firmaya/releases/tag/v1.0.0
