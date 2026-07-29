#!/bin/bash
# setup.sh — Instala dependencias y configura el entorno de compilación
# Ejecutar una vez: ./Compilation/setup.sh
set -e

COMPILATION_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$COMPILATION_DIR/.." && pwd)"
PREFIX="${COMPILATION_DIR}/deps"
LOG_DIR="${COMPILATION_DIR}/logs"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log()  { echo -e "${GREEN}[OK]${NC} $1"; }
warn() { echo -e "${YELLOW}[!]${NC} $1"; }
err()  { echo -e "${RED}[ERR]${NC} $1"; }
step() { echo -e "\n${CYAN}=== $1 ===${NC}"; }

mkdir -p "${PREFIX}/lib" "${PREFIX}/lib64" "${PREFIX}/include" "${PREFIX}/bin" "${LOG_DIR}"

# ──────────────────────────────────────────────────────────────
# 1. Detectar sistema de paquetes e instalar deps del sistema
# ──────────────────────────────────────────────────────────────
step "1/5 — Detectando sistema de paquetes"

install_system_deps() {
    if command -v pacman &>/dev/null; then
        log "Arch Linux (pacman) detectado"
        pacman -Sy --noconfirm 2>/dev/null || true
        pacman -S --noconfirm --needed \
            gcc make cmake pkg-config \
            sqlite \
            base-devel 2>/dev/null || true
    elif command -v apt-get &>/dev/null; then
        log "Debian/Ubuntu (apt) detectado"
        apt-get update -qq 2>/dev/null || true
        apt-get install -y -qq \
            gcc g++ make cmake pkg-config \
            libsqlite3-dev 2>/dev/null || true
    elif command -v apk &>/dev/null; then
        log "Alpine (apk) detectado"
        apk update 2>/dev/null || true
        apk add gcc g++ make cmake pkgconfig \
            sqlite-dev sqlite-libs 2>/dev/null || true
    else
        warn "No se detectó gestor de paquetes conocido"
        warn "Instala manualmente: gcc, g++, make, cmake, pkg-config, sqlite-dev"
    fi
}

install_system_deps

# Verificar herramientas básicas
for tool in gcc g++ make cmake; do
    if command -v "$tool" &>/dev/null; then
        log "$tool: $(which $tool)"
    else
        err "$tool no encontrado — instálalo manualmente"
        exit 1
    fi
done

# ──────────────────────────────────────────────────────────────
# 2. Copiar/symlink dependencias estáticas pre-compiladas
# ──────────────────────────────────────────────────────────────
step "2/5 — Configurando dependencias estáticas"

# Fuente de deps pre-compiladas (cross-compiled aarch64, servirán para build nativo)
CROSSCOMP_DEPS="${PROJECT_ROOT}/Herramientas"

# --- Tesseract + Leptonica (deps de Cornea/Lexis) ---
TESS_SRC="${CROSSCOMP_DEPS}/Cornea/deps/tesseract_build/install"
if [ -d "${TESS_SRC}" ]; then
    log "Tesseract: copiando headers y libs..."
    cp -rn "${TESS_SRC}/include/"* "${PREFIX}/include/" 2>/dev/null || true
    cp -f "${TESS_SRC}/lib/"*.a "${PREFIX}/lib/" 2>/dev/null || true
    # También copiar tessdata
    if [ -d "${TESS_SRC}/../tessdata" ]; then
        mkdir -p "${COMPILATION_DIR}/tessdata"
        cp -f "${TESS_SRC}/../tessdata/"*.traineddata "${COMPILATION_DIR}/tessdata/" 2>/dev/null || true
        log "  tessdata copiado"
    fi
    log "  Tesseract OK"
else
    warn "Tesseract no encontrado en ${TESS_SRC}"
    warn "Ejecuta build_tesseract_static.sh primero"
fi

# --- ncnn (deps de Cornea) ---
NCNN_SRC="${CROSSCOMP_DEPS}/Cornea/deps/ncnn-install"
if [ -d "${NCNN_SRC}" ]; then
    log "ncnn: copiando headers y libs..."
    cp -rn "${NCNN_SRC}/include/"* "${PREFIX}/include/" 2>/dev/null || true
    cp -f "${NCNN_SRC}/lib/"*.a "${PREFIX}/lib/" 2>/dev/null || true
    log "  ncnn OK"
else
    warn "ncnn no encontrado en ${NCNN_SRC}"
fi

# --- OpenSSL (deps de meOrion) ---
OPENSSL_SRC="${CROSSCOMP_DEPS}/meOrion/deps/openssl-aarch64"
if [ -d "${OPENSSL_SRC}" ]; then
    log "OpenSSL: copiando headers y libs..."
    cp -rn "${OPENSSL_SRC}/include/"* "${PREFIX}/include/" 2>/dev/null || true
    cp -f "${OPENSSL_SRC}/lib/"*.a "${PREFIX}/lib/" 2>/dev/null || true
    log "  OpenSSL OK"
else
    warn "OpenSSL no encontrado en ${OPENSSL_SRC}"
fi

# --- libssh2 (deps de meOrion) ---
LIBSSH2_SRC="${CROSSCOMP_DEPS}/meOrion/deps/libssh2-aarch64"
if [ -d "${LIBSSH2_SRC}" ]; then
    log "libssh2: copiando headers y libs..."
    cp -rn "${LIBSSH2_SRC}/include/"* "${PREFIX}/include/" 2>/dev/null || true
    cp -f "${LIBSSH2_SRC}/lib/"*.a "${PREFIX}/lib/" 2>/dev/null || true
    log "  libssh2 OK"
else
    warn "libssh2 no encontrado en ${LIBSSH2_SRC}"
fi

# --- libnl (deps de Mandela) ---
NL_SRC="/tmp/libnl-aarch64"
if [ -d "${NL_SRC}" ]; then
    log "libnl: copiando headers y libs..."
    cp -rn "${NL_SRC}/include/"* "${PREFIX}/include/" 2>/dev/null || true
    cp -f "${NL_SRC}/lib/"*.a "${PREFIX}/lib/" 2>/dev/null || true
    log "  libnl OK"
else
    # Buscar en deps del proyecto
    NL_ALT=$(find "${CROSSCOMP_DEPS}" -path "*/libnl-aarch64" -type d 2>/dev/null | head -1)
    if [ -n "${NL_ALT}" ]; then
        log "libnl: copiando desde ${NL_ALT}..."
        cp -rn "${NL_ALT}/include/"* "${PREFIX}/include/" 2>/dev/null || true
        cp -f "${NL_ALT}/lib/"*.a "${PREFIX}/lib/" 2>/dev/null || true
        log "  libnl OK"
    else
        warn "libnl no encontrado — Mandela requiere libnl-3 y libnl-genl-3"
    fi
fi

# --- SQLite3 (deps de Mandela/WifiOneshot) ---
SQLITE_SRC="/tmp/libsqlite3_aarch64.a"
SQLITE_AMALG=$(find "${CROSSCOMP_DEPS}" -name "sqlite3.c" -path "*/sqlite-amalgamation*" 2>/dev/null | head -1)

if [ -f "${SQLITE_SRC}" ]; then
    log "SQLite3: copiando lib static..."
    cp -f "${SQLITE_SRC}" "${PREFIX}/lib/libsqlite3.a"
    log "  SQLite3 OK"
elif [ -n "${SQLITE_AMALG}" ]; then
    log "SQLite3: compilando desde amalgamation..."
    gcc -O3 -fPIC -c "${SQLITE_AMALG}" -o /tmp/sqlite3_native.o \
        -DSQLITE_ENABLE_FTS5 \
        -DSQLITE_ENABLE_JSON1 2>/dev/null || \
    gcc -O3 -fPIC -c "${SQLITE_AMALG}" -o /tmp/sqlite3_native.o
    ar rcs "${PREFIX}/lib/libsqlite3.a" /tmp/sqlite3_native.o
    rm -f /tmp/sqlite3_native.o
    log "  SQLite3 OK"
else
    # Intentar compilar desde source
    warn "SQLite3: intentando descargar amalgamation..."
    SQLITE_URL="https://www.sqlite.org/2024/sqlite-amalgamation-3450100.zip"
    if curl -sL -o /tmp/sqlite3.zip "${SQLITE_URL}" 2>/dev/null; then
        cd /tmp && unzip -o sqlite3.zip 2>/dev/null
        gcc -O3 -fPIC -c /tmp/sqlite-amalgamation-*/sqlite3.c -o /tmp/sqlite3_native.o
        ar rcs "${PREFIX}/lib/libsqlite3.a" /tmp/sqlite3_native.o
        rm -f /tmp/sqlite3_native.o
        log "  SQLite3 OK (descargado)"
    else
        err "SQLite3 no disponible — instala sqlite-dev desde tu gestor de paquetes"
    fi
fi

# Copiar headers SQLite3 si existen
SQLITE_HDR=$(find "${CROSSCOMP_DEPS}" -name "sqlite3.h" -path "*/sqlite-amalgamation*" 2>/dev/null | head -1)
if [ -n "${SQLITE_HDR}" ]; then
    cp -f "${SQLITE_HDR}" "${PREFIX}/include/sqlite3.h"
fi

# --- libiris_static.a (shared lib) ---
IRIS_BUILD="${PROJECT_ROOT}/app/src/main/cpp/lib/build_aarch64"
if [ ! -f "${IRIS_BUILD}/libiris_static.a" ]; then
    log "Compilando libiris_static.a..."
    LIB_SRC="${PROJECT_ROOT}/app/src/main/cpp/lib"
    mkdir -p "${IRIS_BUILD}"
    cd "${IRIS_BUILD}"
    cmake "${LIB_SRC}" \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_C_COMPILER=gcc \
        -DCMAKE_CXX_COMPILER=g++ \
        -DIRIS_USE_SKIA=OFF \
        > "${LOG_DIR}/libiris_build.log" 2>&1
    make -j$(nproc) >> "${LOG_DIR}/libiris_build.log" 2>&1
    cd "${COMPILATION_DIR}"
fi

if [ -f "${IRIS_BUILD}/libiris_static.a" ]; then
    log "libiris_static.a OK"
else
    err "No se pudo compilar libiris_static.a"
fi

# ──────────────────────────────────────────────────────────────
# 3. Crear pkg-config files para libs sin .pc
# ──────────────────────────────────────────────────────────────
step "3/5 — Generando pkg-config files"

mkdir -p "${PREFIX}/lib/pkgconfig"

# Tesseract
if [ -f "${PREFIX}/lib/libtesseract.a" ] && [ ! -f "${PREFIX}/lib/pkgconfig/tesseract.pc" ]; then
    cat > "${PREFIX}/lib/pkgconfig/tesseract.pc" << 'EOF'
prefix=/PLACEHOLDER
exec_prefix=${prefix}
libdir=${exec_prefix}/lib
includedir=${prefix}/include

Name: Tesseract
Description: OCR Engine
Version: 5.5.0
Libs: -L${libdir} -ltesseract
Cflags: -I${includedir}/tesseract
EOF
    sed -i "s|/PLACEHOLDER|${PREFIX}|" "${PREFIX}/lib/pkgconfig/tesseract.pc"
    log "tesseract.pc"
fi

# ncnn
if [ -f "${PREFIX}/lib/libncnn.a" ] && [ ! -f "${PREFIX}/lib/pkgconfig/ncnn.pc" ]; then
    cat > "${PREFIX}/lib/pkgconfig/ncnn.pc" << EOF
prefix=${PREFIX}
exec_prefix=\${prefix}
libdir=\${exec_prefix}/lib
includedir=\${prefix}/include

Name: ncnn
Description: Neural Network Inference Framework
Version: latest
Libs: -L\${libdir} -lncnn -fopenmp
Cflags: -I\${includedir}
EOF
    log "ncnn.pc"
fi

# libssh2
if [ -f "${PREFIX}/lib/libssh2.a" ] && [ ! -f "${PREFIX}/lib/pkgconfig/libssh2.pc" ]; then
    cat > "${PREFIX}/lib/pkgconfig/libssh2.pc" << EOF
prefix=${PREFIX}
exec_prefix=\${prefix}
libdir=\${exec_prefix}/lib
includedir=\${prefix}/include

Name: libssh2
Description: SSH protocol library
Version: 1.11.1
Libs: -L\${libdir} -lssh2
Cflags: -I\${includedir}
EOF
    log "libssh2.pc"
fi

# ──────────────────────────────────────────────────────────────
# 4. Crear scripts de build nativos
# ──────────────────────────────────────────────────────────────
step "4/5 — Generando scripts de build"

SCRIPTS_DIR="${COMPILATION_DIR}/scripts"
mkdir -p "${SCRIPTS_DIR}"

# --- build-iris.sh ---
cat > "${SCRIPTS_DIR}/build-iris.sh" << 'IRIS_EOF'
#!/bin/bash
set -e
COMPILATION_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PROJECT_ROOT="$(cd "$COMPILATION_DIR/.." && pwd)"
PREFIX="${COMPILATION_DIR}/deps"
BUILD_DIR="${COMPILATION_DIR}/build/iris"
LIB_SRC="${PROJECT_ROOT}/app/src/main/cpp/lib"
LIB_BUILD="${LIB_SRC}/build_aarch64"

echo "[Iris] Compilando libiris_static.a..."
mkdir -p "${LIB_BUILD}"
cd "${LIB_BUILD}"
cmake "${LIB_SRC}" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_C_COMPILER=gcc \
    -DCMAKE_CXX_COMPILER=g++ \
    -DIRIS_USE_SKIA=OFF
make -j$(nproc)

echo "[Iris] Configurando..."
mkdir -p "${BUILD_DIR}"
cd "${BUILD_DIR}"
cmake "${PROJECT_ROOT}/Herramientas/Iris" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_C_COMPILER=gcc \
    -DCMAKE_CXX_COMPILER=g++ \
    -DIRIS_USE_SKIA=OFF \
    -DSHARED_IRIS_LIB_DIR="${LIB_BUILD}"

echo "[Iris] Compilando..."
make -j$(nproc)

echo ""
echo "[Iris] OK: ${BUILD_DIR}/iris"
ls -lh "${BUILD_DIR}/iris" 2>/dev/null
echo ""
echo "Deploy:"
echo "  cp ${BUILD_DIR}/iris /data/local/aarchdroid/root/iris"
IRIS_EOF
chmod +x "${SCRIPTS_DIR}/build-iris.sh"

# --- build-mandela.sh ---
cat > "${SCRIPTS_DIR}/build-mandela.sh" << 'MANDELA_EOF'
#!/bin/bash
set -e
COMPILATION_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PROJECT_ROOT="$(cd "$COMPILATION_DIR/.." && pwd)"
PREFIX="${COMPILATION_DIR}/deps"
BUILD_DIR="${COMPILATION_DIR}/build/mandela"
LIB_SRC="${PROJECT_ROOT}/app/src/main/cpp/lib"
LIB_BUILD="${LIB_SRC}/build_aarch64"

SQLITE_STATIC="${PREFIX}/lib/libsqlite3.a"
SQLITE_INCLUDE="${PREFIX}/include"

NL_INCLUDE="${PREFIX}/include/libnl3"
NL_LIB_NL3="${PREFIX}/lib/libnl-3.a"
NL_LIB_NL_GENL3="${PREFIX}/lib/libnl-genl-3.a"

# Verificar deps
if [ ! -f "${SQLITE_STATIC}" ]; then
    echo "[Mandela] ERROR: SQLite3 static no encontrado en ${SQLITE_STATIC}"
    exit 1
fi
if [ ! -f "${NL_LIB_NL3}" ]; then
    echo "[Mandela] ERROR: libnl-3 static no encontrado en ${NL_LIB_NL3}"
    echo "  Instala libnl-3 desde tu gestor de paquetes"
    exit 1
fi

echo "[Mandela] Compilando libiris_static.a..."
mkdir -p "${LIB_BUILD}"
cd "${LIB_BUILD}"
cmake "${LIB_SRC}" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_C_COMPILER=gcc \
    -DCMAKE_CXX_COMPILER=g++ \
    -DIRIS_USE_SKIA=OFF
make -j$(nproc)

echo "[Mandela] Configurando..."
cmake -S "${PROJECT_ROOT}/Herramientas/Mandela" -B "${BUILD_DIR}" \
    -DCMAKE_SYSTEM_NAME=Linux \
    -DCMAKE_C_COMPILER=gcc \
    -DCMAKE_CXX_COMPILER=g++ \
    -DCMAKE_BUILD_TYPE=Release \
    -DMANDELA_USE_SKIA=OFF \
    -DSHARED_IRIS_LIB_DIR="${LIB_BUILD}" \
    -DSQLite3_INCLUDE_DIR="${SQLITE_INCLUDE}" \
    -DSQLite3_LIBRARY="${SQLITE_STATIC}" \
    -DNL3_INCLUDE_DIRS="${NL_INCLUDE}" \
    -DNL3_LIB_nl_3="${NL_LIB_NL3}" \
    -DNL3_LIB_nl_genl_3="${NL_LIB_NL_GENL3}"

echo "[Mandela] Compilando..."
cmake --build "${BUILD_DIR}" -j$(nproc) --target mandela

# Strip
strip "${BUILD_DIR}/mandela" -o "${BUILD_DIR}/mandela_stripped" 2>/dev/null || true

echo ""
echo "[Mandela] OK: ${BUILD_DIR}/mandela_stripped"
ls -lh "${BUILD_DIR}/mandela_stripped" 2>/dev/null || ls -lh "${BUILD_DIR}/mandela" 2>/dev/null
echo ""
echo "Deploy:"
echo "  cp ${BUILD_DIR}/mandela_stripped /data/local/aarchdroid/root/mandela"
MANDELA_EOF
chmod +x "${SCRIPTS_DIR}/build-mandela.sh"

# --- build-wifioneshot.sh ---
cat > "${SCRIPTS_DIR}/build-wifioneshot.sh" << 'WIFI_EOF'
#!/bin/bash
set -e
COMPILATION_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PREFIX="${COMPILATION_DIR}/deps"
SRC_DIR="${COMPILATION_DIR}/../Herramientas/WifiOneshot"
BUILD_DIR="${COMPILATION_DIR}/build/wifioneshot"

echo "[WifiOneshot] Configurando..."
mkdir -p "${BUILD_DIR}"
cd "${BUILD_DIR}"

# Copiar source para build limpio
cp -f "${SRC_DIR}"/*.c "${SRC_DIR}"/*.h . 2>/dev/null || true

# Compilar con gcc nativo
echo "[WifiOneshot] Compilando..."
for f in main db pin wpas wps scan bruteforce ui; do
    gcc -s -O3 -I. -I"${PREFIX}/include" -c "${f}.c" -o "${f}.o"
done

gcc -s -O3 -o oneshot_aarch64 *.o -L"${PREFIX}/lib" -lsqlite3 -lpthread
strip -s oneshot_aarch64

echo ""
echo "[WifiOneshot] OK: ${BUILD_DIR}/oneshot_aarch64"
ls -lh oneshot_aarch64
echo ""
echo "Deploy:"
echo "  cp ${BUILD_DIR}/oneshot_aarch64 /data/local/aarchdroid/root/OneShot-C/oneshot"
WIFI_EOF
chmod +x "${SCRIPTS_DIR}/build-wifioneshot.sh"

# --- build-meorion.sh ---
cat > "${SCRIPTS_DIR}/build-meorion.sh" << 'ORION_EOF'
#!/bin/bash
set -e
COMPILATION_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PROJECT_ROOT="$(cd "$COMPILATION_DIR/.." && pwd)"
PREFIX="${COMPILATION_DIR}/deps"
SRC_DIR="${PROJECT_ROOT}/Herramientas/meOrion"
BUILD_DIR="${COMPILATION_DIR}/build/meorion"

OPENSSL_DIR="${PREFIX}"
LIBSSH2_DIR="${PREFIX}"

echo "[meOrion] Verificando dependencias..."
if [ ! -f "${OPENSSL_DIR}/lib/libssl.a" ]; then
    echo "ERROR: libssl.a no encontrado en ${OPENSSL_DIR}/lib/"
    exit 1
fi
if [ ! -f "${LIBSSH2_DIR}/lib/libssh2.a" ]; then
    echo "ERROR: libssh2.a no encontrado en ${LIBSSH2_DIR}/lib/"
    exit 1
fi

echo "[meOrion] Compilando..."
mkdir -p "${BUILD_DIR}"
cd "${BUILD_DIR}"

SOURCES=$(find "${SRC_DIR}/src" -name "*.cpp" | sort)
COUNT=$(echo "$SOURCES" | wc -l)
echo "  Compilando ${COUNT} archivos..."

g++ -std=c++17 -O2 -Wall -Wno-unused-variable -Wno-sign-compare \
    -I"${SRC_DIR}/include" \
    -I"${SRC_DIR}/src" \
    -I"${OPENSSL_DIR}/include" \
    -I"${LIBSSH2_DIR}/include" \
    ${SOURCES} \
    -static \
    -L"${LIBSSH2_DIR}/lib" -lssh2 \
    -L"${OPENSSL_DIR}/lib" -lssl -lcrypto \
    -lpthread -ldl \
    -o "${BUILD_DIR}/orion"

echo ""
echo "[meOrion] OK: ${BUILD_DIR}/orion"
ls -lh "${BUILD_DIR}/orion"
echo ""
echo "Deploy:"
echo "  cp ${BUILD_DIR}/orion /data/local/aarchdroid/root/orion"
ORION_EOF
chmod +x "${SCRIPTS_DIR}/build-meorion.sh"

# --- build-cornea.sh ---
cat > "${SCRIPTS_DIR}/build-cornea.sh" << 'CORNEA_EOF'
#!/bin/bash
set -e
COMPILATION_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PROJECT_ROOT="$(cd "$COMPILATION_DIR/.." && pwd)"
PREFIX="${COMPILATION_DIR}/deps"
BUILD_DIR="${COMPILATION_DIR}/build/cornea"
LIB_SRC="${PROJECT_ROOT}/app/src/main/cpp/lib"
LIB_BUILD="${LIB_SRC}/build_aarch64"
CORNEA_SRC="${PROJECT_ROOT}/Herramientas/Cornea"

TESSERACT_STATIC_DIR="${PREFIX}"
NCNN_STATIC_DIR="${PREFIX}"

echo "[Cornea] Compilando libiris_static.a..."
mkdir -p "${LIB_BUILD}"
cd "${LIB_BUILD}"
cmake "${LIB_SRC}" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_C_COMPILER=gcc \
    -DCMAKE_CXX_COMPILER=g++ \
    -DIRIS_USE_SKIA=OFF
make -j$(nproc)

echo "[Cornea] Configurando..."
mkdir -p "${BUILD_DIR}"
cd "${BUILD_DIR}"
cmake "${CORNEA_SRC}" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_C_COMPILER=gcc \
    -DCMAKE_CXX_COMPILER=g++ \
    -DTESSERACT_STATIC_DIR="${TESSERACT_STATIC_DIR}" \
    -DNCNN_STATIC_DIR="${NCNN_STATIC_DIR}" \
    -DSHARED_IRIS_LIB_DIR="${LIB_BUILD}"

echo "[Cornea] Compilando..."
make -j$(nproc)

# Strip
strip --strip-unneeded "${BUILD_DIR}/cornea" -o "${BUILD_DIR}/cornea_stripped" 2>/dev/null || true

echo ""
echo "[Cornea] OK: ${BUILD_DIR}/cornea_stripped"
ls -lh "${BUILD_DIR}/cornea_stripped" 2>/dev/null || ls -lh "${BUILD_DIR}/cornea" 2>/dev/null
echo ""
echo "Deploy:"
echo "  mkdir -p /data/local/aarchdroid/root/cornea/tessdata"
echo "  cp ${BUILD_DIR}/cornea_stripped /data/local/aarchdroid/root/cornea/cornea"
CORNEA_EOF
chmod +x "${SCRIPTS_DIR}/build-cornea.sh"

# --- build-lexis.sh ---
cat > "${SCRIPTS_DIR}/build-lexis.sh" << 'LEXIS_EOF'
#!/bin/bash
set -e
COMPILATION_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PROJECT_ROOT="$(cd "$COMPILATION_DIR/.." && pwd)"
PREFIX="${COMPILATION_DIR}/deps"
BUILD_DIR="${COMPILATION_DIR}/build/lexis"
LIB_SRC="${PROJECT_ROOT}/app/src/main/cpp/lib"
LIB_BUILD="${LIB_SRC}/build_aarch64"
LEXIS_SRC="${PROJECT_ROOT}/Herramientas/Lexis"

TESSERACT_STATIC_DIR="${PREFIX}"

echo "[Lexis] Compilando libiris_static.a..."
mkdir -p "${LIB_BUILD}"
cd "${LIB_BUILD}"
cmake "${LIB_SRC}" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_C_COMPILER=gcc \
    -DCMAKE_CXX_COMPILER=g++ \
    -DIRIS_USE_SKIA=OFF
make -j$(nproc)

echo "[Lexis] Configurando..."
mkdir -p "${BUILD_DIR}"
cd "${BUILD_DIR}"
cmake "${LEXIS_SRC}" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_C_COMPILER=gcc \
    -DCMAKE_CXX_COMPILER=g++ \
    -DTESSERACT_STATIC_DIR="${TESSERACT_STATIC_DIR}" \
    -DSHARED_IRIS_LIB_DIR="${LIB_BUILD}"

echo "[Lexis] Compilando..."
make -j$(nproc)

# Strip
strip lexis -o lexis_stripped 2>/dev/null || true

echo ""
echo "[Lexis] OK: ${BUILD_DIR}/lexis_stripped"
ls -lh "${BUILD_DIR}/lexis_stripped" 2>/dev/null || ls -lh "${BUILD_DIR}/lexis" 2>/dev/null
echo ""
echo "Deploy:"
echo "  mkdir -p /data/local/aarchdroid/root/lexis/tessdata"
echo "  cp ${BUILD_DIR}/lexis_stripped /data/local/aarchdroid/root/lexis/lexis"
LEXIS_EOF
chmod +x "${SCRIPTS_DIR}/build-lexis.sh"

# --- build-all.sh ---
cat > "${SCRIPTS_DIR}/build-all.sh" << 'ALL_EOF'
#!/bin/bash
set -e
SCRIPTS_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$(cd "$SCRIPTS_DIR/../logs" && pwd)"

TOOLS=("wifioneshot" "iris" "mandela" "meorion" "cornea" "lexis")
FAILED=()
PASSED=()

for tool in "${TOOLS[@]}"; do
    echo ""
    echo "============================================"
    echo "  Building: ${tool}"
    echo "============================================"
    SCRIPT="${SCRIPTS_DIR}/build-${tool}.sh"
    LOG="${LOG_DIR}/${tool}_build.log"

    if [ ! -f "${SCRIPT}" ]; then
        echo "[!] Script no encontrado: ${SCRIPT}"
        FAILED+=("${tool}")
        continue
    fi

    if bash "${SCRIPT}" 2>&1 | tee "${LOG}"; then
        PASSED+=("${tool}")
    else
        echo "[FAIL] ${tool} — ver ${LOG}"
        FAILED+=("${tool}")
    fi
done

echo ""
echo "============================================"
echo "  RESUMEN"
echo "============================================"
echo "  OK:    ${PASSED[*]:-ninguno}"
echo "  FAIL:  ${FAILED[*]:-ninguno}"
echo "  Logs:  ${LOG_DIR}/"
echo ""
ALL_EOF
chmod +x "${SCRIPTS_DIR}/build-all.sh"

log "Scripts generados en ${SCRIPTS_DIR}/"

# ──────────────────────────────────────────────────────────────
# 5. Verificar entorno completo
# ──────────────────────────────────────────────────────────────
step "5/5 — Verificación final"

echo ""
echo "=== Librerías estáticas en PREFIX ==="
for lib in libtesseract.a libleptonica.a libncnn.a libssl.a libcrypto.a libssh2.a libsqlite3.a libnl-3.a libnl-genl-3.a; do
    if [ -f "${PREFIX}/lib/${lib}" ]; then
        SIZE=$(du -h "${PREFIX}/lib/${lib}" | cut -f1)
        log "  ${lib} (${SIZE})"
    else
        warn "  ${lib} — FALTANTE"
    fi
done

echo ""
echo "=== iris_static.a ==="
if [ -f "${PROJECT_ROOT}/app/src/main/cpp/lib/build_aarch64/libiris_static.a" ]; then
    log "  libiris_static.a OK"
else
    err "  libiris_static.a FALTANTE"
fi

echo ""
echo "=== Compiladores ==="
gcc --version 2>&1 | head -1
g++ --version 2>&1 | head -1
cmake --version 2>&1 | head -1

echo ""
echo "=== Estructura ==="
echo "  ${COMPILATION_DIR}/"
echo "  ├── deps/          (libs + headers)"
echo "  ├── scripts/       (build-*.sh)"
echo "  ├── build/         (output por tool)"
echo "  ├── logs/          (build logs)"
echo "  ├── tessdata/      (tesseract models)"
echo "  ├── env.sh         (source para activar entorno)"
echo "  └── setup.sh       (este script)"
echo ""
echo "Próximos pasos:"
echo "  1. source Compilation/env.sh"
echo "  2. build-wifioneshot    (la más simple, solo necesita sqlite)"
echo "  3. build-iris           (librería compartida)"
echo "  4. build-mandela        (requiere libnl + sqlite)"
echo "  5. build-all            (compila todo)"
echo ""
