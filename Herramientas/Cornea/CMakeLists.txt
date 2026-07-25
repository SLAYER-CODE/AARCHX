cmake_minimum_required(VERSION 3.16)
project(cornea VERSION 0.1.0 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# ── Static Tesseract + Leptonica (cross-compiled) ──────────────────
set(TESSERACT_STATIC_DIR "${CMAKE_SOURCE_DIR}/deps/tesseract_build/install"
    CACHE PATH "Cross-compiled Tesseract/Leptonica install prefix")

# ── ncnn (cross-compiled) ─────────────────────────────────────────
set(NCNN_STATIC_DIR "${CMAKE_SOURCE_DIR}/deps/ncnn-install"
    CACHE PATH "Cross-compiled ncnn install prefix")

# ── Iris static lib ────────────────────────────────────────────────
if(NOT DEFINED SHARED_IRIS_LIB_DIR)
    set(SHARED_IRIS_LIB_DIR "${CMAKE_SOURCE_DIR}/../../app/src/main/cpp/lib/build_aarch64")
endif()
set(SHARED_IRIS_INCLUDE_DIR "${SHARED_IRIS_LIB_DIR}/../include")

# ── Dependencies ──────────────────────────────────────────────────
find_package(Threads REQUIRED)

# ── Cornea sources ────────────────────────────────────────────────

set(CORNEA_SOURCES
    src/engine.cpp
    src/overlay_renderer.cpp
    src/config.cpp
    src/modules/ocr.cpp
    src/modules/logo_detector.cpp
    src/modules/vulndb.cpp
    src/modules/diagnostics.cpp
    src/modules/device_classifier.cpp
    src/modules/visual_detector.cpp
    ${CMAKE_SOURCE_DIR}/../../Herramientas/Iris/src/camera_canvas.cpp
)

set(CORNEA_HEADERS
    include/cornea/types.h
    include/cornea/module.h
    include/cornea/engine.h
    include/cornea/overlay_renderer.h
    include/cornea/config.h
    include/cornea/modules/ocr.h
    include/cornea/modules/logo_detector.h
    include/cornea/modules/vulndb.h
    include/cornea/modules/diagnostics.h
    include/cornea/modules/device_classifier.h
    include/cornea/modules/visual_detector.h
)

# ── Static library ───────────────────────────────────────────────
add_library(cornea_core STATIC
    ${CORNEA_SOURCES}
    ${CORNEA_HEADERS}
)

target_include_directories(cornea_core PUBLIC
    ${CMAKE_SOURCE_DIR}/include
    ${SHARED_IRIS_INCLUDE_DIR}
    ${TESSERACT_STATIC_DIR}/include
    ${TESSERACT_STATIC_DIR}/include/tesseract
    ${TESSERACT_STATIC_DIR}/include/leptonica
    ${NCNN_STATIC_DIR}/include
)

target_compile_definitions(cornea_core PRIVATE
    CORNEA_HAS_TESSERACT
    CORNEA_HAS_NCNN
)

target_link_libraries(cornea_core PUBLIC
    Threads::Threads
)

# ── Cornea executable ────────────────────────────────────────────
add_executable(cornea
    examples/cornea.cpp
)

target_include_directories(cornea PRIVATE
    ${CMAKE_SOURCE_DIR}/include
    ${SHARED_IRIS_INCLUDE_DIR}
    ${TESSERACT_STATIC_DIR}/include
    ${TESSERACT_STATIC_DIR}/include/tesseract
    ${TESSERACT_STATIC_DIR}/include/leptonica
)

target_compile_definitions(cornea PRIVATE
    CORNEA_HAS_TESSERACT
)

# ── Static link order (depends resolve left-to-right) ────────────
set(TESS_LIBS
    ${TESSERACT_STATIC_DIR}/lib/libtesseract.a
    ${TESSERACT_STATIC_DIR}/lib/libleptonica.a
    ${TESSERACT_STATIC_DIR}/lib/libtiff.a
    ${TESSERACT_STATIC_DIR}/lib/libjpeg.a
    ${TESSERACT_STATIC_DIR}/lib/libpng16.a
    ${TESSERACT_STATIC_DIR}/lib/libz.a
    ${TESSERACT_STATIC_DIR}/lib/libgif.a
)

target_link_libraries(cornea PRIVATE
    cornea_core
    ${TESS_LIBS}
    ${NCNN_STATIC_DIR}/lib/libncnn.a
    ${SHARED_IRIS_LIB_DIR}/libiris_static.a
    -fopenmp
    m
)

install(TARGETS cornea DESTINATION bin)

