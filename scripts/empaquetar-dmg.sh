#!/usr/bin/env bash
#
# Genera el instalador .dmg de la aplicacion de escritorio con el runtime de Java incluido,
# para que el usuario final no tenga que instalar nada.
#
# Uso:  ./scripts/empaquetar-dmg.sh
#
# Requisitos: el JDK 17 instalado (jpackage viene con el) y el JAR ya construido
# (el script lo construye si falta).

set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$RAIZ"

NOMBRE_APP="Firmaya"
# La version vive en el archivo VERSION, que es la fuente unica. El pom.xml debe
# coincidir: si no, se detiene en vez de generar un instalador mal etiquetado.
VERSION="$(tr -d '[:space:]' < VERSION)"
VERSION_POM="$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' pom.xml | head -1)"
if [[ "$VERSION" != "$VERSION_POM" ]]; then
  echo "ERROR: VERSION dice '$VERSION' y pom.xml dice '$VERSION_POM'." >&2
  echo "Corrija el <version> del pom.xml para que coincida con el archivo VERSION." >&2
  exit 1
fi
JAR="target/firmador.jar"
ENTRADA="target/dmg-entrada"
SALIDA="target/instalador"

# Se reconstruye SIEMPRE: empaquetar un JAR viejo produce un instalador que no
# refleja los cambios del codigo, y el error es silencioso y dificil de notar.
echo "==> Construyendo $JAR"
./mvnw -q package -DskipTests

echo "==> Preparando la carpeta de entrada de jpackage"
rm -rf "$ENTRADA" "$SALIDA"
mkdir -p "$ENTRADA" "$SALIDA"
# jpackage empaqueta TODO lo que encuentre en --input: la carpeta solo debe tener el JAR.
cp "$JAR" "$ENTRADA/"

echo "==> Generando el .dmg (esto tarda un par de minutos)"
jpackage \
  --type dmg \
  --name "$NOMBRE_APP" \
  --app-version "$VERSION" \
  --input "$ENTRADA" \
  --main-jar "$(basename "$JAR")" \
  --main-class pe.firmador.Lanzador \
  --dest "$SALIDA" \
  --vendor "Firmaya" \
  --description "Firmaya, firma digital de documentos PDF" \
  --copyright "Firmaya" \
  --mac-package-identifier pe.firmador.escritorio \
  --mac-package-name "Firmaya" \
  --icon assets/Firmaya.icns \
  --java-options "-Xmx1g" \
  --java-options "-Dfile.encoding=UTF-8"

echo
echo "==> Listo:"
ls -lh "$SALIDA"
echo
echo "El .dmg NO está firmado ni notarizado: al abrirlo por primera vez macOS lo bloqueará."
echo "Vea la sección 'Gatekeeper' del README para desbloquearlo o para firmarlo de verdad."
