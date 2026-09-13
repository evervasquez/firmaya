# Avisos de terceros

Firmaya se distribuye bajo **LGPL-2.1** (ver `LICENSE`).

Se eligió esa licencia porque Firmaya incorpora DSS, que es LGPL-2.1, y el instalador
empaqueta todas las dependencias dentro de un único JAR. Adoptar la misma licencia evita
las obligaciones adicionales que la LGPL impone cuando se enlaza estáticamente desde
software con otra licencia.

## Dependencias

| Componente | Licencia | Uso |
|---|---|---|
| [DSS](https://github.com/esig/dss) — `eu.europa.ec.joinup.sd-dss` | LGPL-2.1 | Motor de firma PAdES |
| [Apache PDFBox](https://pdfbox.apache.org/) | Apache-2.0 | Lectura y dibujo del PDF |
| [Bouncy Castle](https://www.bouncycastle.org/) | Licencia MIT adaptada | Criptografía |
| [OpenJFX](https://openjfx.io/) | GPL-2.0 con Classpath Exception | Interfaz de escritorio |
| [SLF4J](https://www.slf4j.org/) | MIT | Registro |
| [JUnit](https://junit.org/) | EPL-2.0 | Pruebas |
| [AssertJ](https://assertj.github.io/doc/) | Apache-2.0 | Pruebas |

La excepción de *classpath* de OpenJFX permite distribuirlo junto a código con otra
licencia sin que ésta quede sujeta a la GPL.

## Escudo Nacional del Perú

La imagen `assets/escudo-nacional.png` y su original `assets/escudo-nacional.svg` provienen
de [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Escudo_nacional_del_Per%C3%BA.svg).

El Escudo Nacional del Perú es un símbolo patrio establecido por ley y su reproducción es
libre. Firmaya lo usa con la misma finalidad que el software oficial: identificar
visualmente una firma digital dentro del documento.

## Relación con RENIEC

**Firmaya no es un producto de RENIEC ni está afiliado a esa institución.**

Es un desarrollo independiente que reproduce el formato de firma y la representación visual
de ReFirma PDF para que los documentos resulten familiares a quien los recibe. Se construyó
sobre la misma librería de firma que usa ReFirma (DSS), que es software libre.

Este repositorio **no redistribuye** ningún archivo perteneciente a RENIEC. Las medidas y
convenciones que replica (192 × 61,5 pt, los cinco motivos de firma, el sufijo `[R]`) son
observaciones documentadas en el README, no copias de sus recursos.

## Marcas

ReFirma, RENIEC y Firma Perú son nombres de sus respectivos titulares y se mencionan
únicamente con fines descriptivos y de interoperabilidad.
