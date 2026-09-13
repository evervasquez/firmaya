# ReFirma, los validadores y el sello de tiempo

> Parte de la documentación de [Firmaya](../README.md).

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
