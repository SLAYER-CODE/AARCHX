#!/system/bin/sh
# AArchDroid - Smart Tool Installer
# Usage: install-tool.sh <tool-name>
# Reads /data/data/org.aarchdroid/files/tool-manifest.json to determine install method

TOOL_NAME=$(echo "$1" | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9-]/-/g' | sed 's/--*/-/g')
MANIFEST="/data/data/org.aarchdroid/files/tool-manifest.json"

if [ -z "$TOOL_NAME" ]; then
    echo "Usage: install-tool.sh <tool-name>"
    exit 1
fi

echo "[AArchDroid] Installing: $TOOL_NAME"

# Try to read manifest if available
if [ -f "$MANIFEST" ]; then
    # Use grep/sed to extract info (no jq dependency)
    SOURCE=$(grep -o "\"$TOOL_NAME\":{[^}]*}" "$MANIFEST" | grep -o '"source":"[^"]*"' | cut -d'"' -f4)
    PKG=$(grep -o "\"$TOOL_NAME\":{[^}]*}" "$MANIFEST" | grep -o '"pkg":"[^"]*"' | cut -d'"' -f4)
    URL=$(grep -o "\"$TOOL_NAME\":{[^}]*}" "$MANIFEST" | grep -o '"url":"[^"]*"' | cut -d'"' -f4)
    REPO=$(grep -o "\"$TOOL_NAME\":{[^}]*}" "$MANIFEST" | grep -o '"repo":"[^"]*"' | cut -d'"' -f4)
    NOTE=$(grep -o "\"$TOOL_NAME\":{[^}]*}" "$MANIFEST" | grep -o '"note":"[^"]*"' | cut -d'"' -f4)
    
    [ -z "$SOURCE" ] && SOURCE="blackarch"
    [ -z "$PKG" ] && PKG="$TOOL_NAME"
else
    SOURCE="blackarch"
    PKG="$TOOL_NAME"
fi

case "$SOURCE" in
    blackarch|arch)
        echo "[AArchDroid] Instalando desde repositorio: $PKG"
        pacman --color always --disable-download-timeout -S --noconfirm "$PKG" 2>&1 | tail -10
        if command -v "$TOOL_NAME" >/dev/null 2>&1; then
            echo "[AArchDroid] ✓ $TOOL_NAME instalado correctamente"
            exit 0
        else
            echo "[AArchDroid] ! El paquete se instaló pero '$TOOL_NAME' no está en PATH"
            exit 0
        fi
        ;;
    pip)
        echo "[AArchDroid] Instalando via pip: $PKG"
        pip install "$PKG"
        ;;
    gem)
        echo "[AArchDroid] Instalando via gem: $PKG"
        gem install "$PKG"
        ;;
    go)
        echo "[AArchDroid] Instalando via go: $PKG"
        go install "$PKG"
        ;;
    github)
        echo "[AArchDroid] Descargando desde GitHub: $REPO"
        if command -v git >/dev/null 2>&1; then
            git clone "https://github.com/$REPO" "/opt/$TOOL_NAME" 2>&1 | tail -5
            echo "[AArchDroid] ✓ $TOOL_NAME descargado en /opt/$TOOL_NAME"
        else
            echo "[AArchDroid] ERROR: git no está instalado"
            exit 1
        fi
        ;;
    url)
        echo "[AArchDroid] Descargando desde URL: $URL"
        mkdir -p "/opt/$TOOL_NAME"
        wget -q "$URL" -O "/opt/$TOOL_NAME/$TOOL_NAME" 2>&1
        chmod +x "/opt/$TOOL_NAME/$TOOL_NAME"
        echo "[AArchDroid] ✓ $TOOL_NAME descargado en /opt/$TOOL_NAME"
        ;;
    local)
        echo "[AArchDroid] ✓ $TOOL_NAME es una herramienta de sistema pre-instalada"
        exit 0
        ;;
    ubuntu_only)
        echo "[AArchDroid] ⚠ $NOTE"
        echo "[AArchDroid] Busca alternativas en: pacman -Ss $TOOL_NAME"
        exit 1
        ;;
    *)
        echo "[AArchDroid] ERROR: Origen desconocido '$SOURCE'"
        exit 1
        ;;
esac
