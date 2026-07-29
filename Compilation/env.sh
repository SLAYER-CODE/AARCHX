#!/bin/bash
# env.sh — Entorno de compilación para Herramientas AArchDroid (nativo aarch64)
# Uso: source Compilation/env.sh

COMPILATION_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$COMPILATION_DIR/.." && pwd)"
PREFIX="${COMPILATION_DIR}/deps"
BUILD_DIR="${COMPILATION_DIR}/build"
LOG_DIR="${COMPILATION_DIR}/logs"

# Herramientas
export CC=gcc
export CXX=g++
export AR=ar
export RANLIB=ranlib
export STRIP=strip
export MAKEFLAGS="-j$(nproc)"

# Paths de dependencias
export PKG_CONFIG_PATH="${PREFIX}/lib/pkgconfig:${PREFIX}/lib64/pkgconfig:${PKG_CONFIG_PATH}"
export CPATH="${PREFIX}/include:${CPATH}"
export LIBRARY_PATH="${PREFIX}/lib:${PREFIX}/lib64:${LIBRARY_PATH}"
export LD_LIBRARY_PATH="${PREFIX}/lib:${PREFIX}/lib64:${LD_LIBRARY_PATH}"

# Tesseract/Leptonica (static)
export TESSERACT_STATIC_DIR="${PREFIX}"
export TESSERACT_INCLUDE="${PREFIX}/include"
export TESSERACT_LIB="${PREFIX}/lib"

# ncnn (static)
export NCNN_STATIC_DIR="${PREFIX}"

# OpenSSL + libssh2 (static, para meOrion)
export OPENSSL_DIR="${PREFIX}"
export LIBSSH2_DIR="${PREFIX}"

# libnl (static, para Mandela)
export NL3_DIR="${PREFIX}"

# SQLite3 (static)
export SQLITE3_STATIC="${PREFIX}/lib/libsqlite3.a"
export SQLITE3_INCLUDE="${PREFIX}/include"

# libiris_static.a (shared)
export IRIS_LIB_DIR="${PROJECT_ROOT}/app/src/main/cpp/lib/build_aarch64"
export IRIS_INCLUDE_DIR="${PROJECT_ROOT}/app/src/main/cpp/lib/include"

# Scripts de build
export PATH="${PREFIX}/bin:${COMPILATION_DIR}/scripts:${PATH}"

# Aliases para tools
alias build-iris="${COMPILATION_DIR}/scripts/build-iris.sh"
alias build-mandela="${COMPILATION_DIR}/scripts/build-mandela.sh"
alias build-wifioneshot="${COMPILATION_DIR}/scripts/build-wifioneshot.sh"
alias build-meorion="${COMPILATION_DIR}/scripts/build-meorion.sh"
alias build-cornea="${COMPILATION_DIR}/scripts/build-cornea.sh"
alias build-lexis="${COMPILATION_DIR}/scripts/build-lexis.sh"
alias build-all="${COMPILATION_DIR}/scripts/build-all.sh"
alias clean-builds="rm -rf ${BUILD_DIR}/*"

echo "=== AArchDroid Compilation Environment ==="
echo "  PREFIX:      ${PREFIX}"
echo "  BUILD_DIR:   ${BUILD_DIR}"
echo "  CC:          ${CC} ($(which ${CC}) 2>/dev/null || echo 'not found')"
echo "  CXX:         ${CXX} ($(which ${CXX}) 2>/dev/null || echo 'not found')"
echo "  CMAKE:       $(cmake --version 2>/dev/null | head -1 || echo 'not found')"
echo ""
echo "  Comandos disponibles:"
echo "    build-iris        Compilar Iris"
echo "    build-mandela     Compilar Mandela"
echo "    build-wifioneshot Compilar WifiOneshot"
echo "    build-meorion     Compilar meOrion"
echo "    build-cornea      Compilar Cornea"
echo "    build-lexis       Compilar Lexis"
echo "    build-all         Compilar todo"
echo "    clean-builds      Limpiar directorios de build"
echo ""
