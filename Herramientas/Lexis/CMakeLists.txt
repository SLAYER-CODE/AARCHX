cmake_minimum_required(VERSION 3.16)
project(lexis VERSION 0.1.0 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# ── Static Tesseract + Leptonica (cross-compiled) ──────────────────
set(TESSERACT_STATIC_DIR "/tmp/tesseract_build/install"
    CACHE PATH "Cross-compiled Tesseract/Leptonica install prefix")

# ── Iris static lib ────────────────────────────────────────────────
if(NOT DEFINED SHARED_IRIS_LIB_DIR)
    set(SHARED_IRIS_LIB_DIR "${CMAKE_SOURCE_DIR}/../../app/src/main/cpp/lib/build_aarch64")
endif()
set(SHARED_IRIS_INCLUDE_DIR "${SHARED_IRIS_LIB_DIR}/../include")

# ── Dependencies ──────────────────────────────────────────────────
find_package(Threads REQUIRED)

# ── Lexis sources ────────────────────────────────────────────────

set(LEXIS_SOURCES
    src/engine.cpp
    src/overlay_renderer.cpp
    src/config.cpp
    src/modules/ocr.cpp
    src/modules/document_analyzer.cpp
    src/modules/diagnostics.cpp
)

set(LEXIS_HEADERS
    include/lexis/types.h
    include/lexis/module.h
    include/lexis/engine.h
    include/lexis/overlay_renderer.h
    include/lexis/config.h
    include/lexis/modules/ocr.h
    include/lexis/modules/document_analyzer.h
    include/lexis/modules/diagnostics.h
)

# ── Static library ───────────────────────────────────────────────
add_library(lexis_core STATIC
    ${LEXIS_SOURCES}
    ${LEXIS_HEADERS}
)

target_include_directories(lexis_core PUBLIC
    ${CMAKE_SOURCE_DIR}/include
    ${SHARED_IRIS_INCLUDE_DIR}
    ${TESSERACT_STATIC_DIR}/include
    ${TESSERACT_STATIC_DIR}/include/tesseract
    ${TESSERACT_STATIC_DIR}/include/leptonica
)

target_compile_definitions(lexis_core PRIVATE
    LEXIS_HAS_TESSERACT
)

target_link_libraries(lexis_core PUBLIC
    Threads::Threads
)

# ── Lexis executable ────────────────────────────────────────────
add_executable(lexis
    examples/lexis.cpp
)

target_include_directories(lexis PRIVATE
    ${CMAKE_SOURCE_DIR}/include
    ${SHARED_IRIS_INCLUDE_DIR}
    ${TESSERACT_STATIC_DIR}/include
    ${TESSERACT_STATIC_DIR}/include/tesseract
    ${TESSERACT_STATIC_DIR}/include/leptonica
)

target_compile_definitions(lexis PRIVATE
    LEXIS_HAS_TESSERACT
)

# ── Static link order (depends resolve left-to-right) ────────────
set(TESS_LIBS
    ${TESSERACT_STATIC_DIR}/lib/libtesseract.a
    ${TESSERACT_STATIC_DIR}/lib/libtesseract_neon.a
    ${TESSERACT_STATIC_DIR}/lib/libtesseract_native.a
    ${TESSERACT_STATIC_DIR}/lib/libleptonica.a
    ${TESSERACT_STATIC_DIR}/lib/libtiff.a
    ${TESSERACT_STATIC_DIR}/lib/libjpeg.a
    ${TESSERACT_STATIC_DIR}/lib/libpng16.a
    ${TESSERACT_STATIC_DIR}/lib/libz.a
    ${TESSERACT_STATIC_DIR}/lib/libgif.a
)

target_link_libraries(lexis PRIVATE
    lexis_core
    ${TESS_LIBS}
    ${SHARED_IRIS_LIB_DIR}/libiris_static.a
    -fopenmp
    m
)

install(TARGETS lexis DESTINATION bin)
