cmake_minimum_required(VERSION 3.16)
project(ac_static VERSION 0.1.0 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(AC_USE_SKIA "Build with Skia rendering" OFF)
find_package(Threads QUIET)

if(AC_USE_SKIA)
    find_library(SKIA_LIBRARY skia
        PATHS ${SKIA_DIR}/out/x64 ${SKIA_DIR}/out/arm64 ${SKIA_DIR}/lib)
    if(NOT SKIA_LIBRARY)
        message(WARNING "Skia not found")
    else()
        add_definitions(-DAC_USE_SKIA)
        message(STATUS "Found Skia: ${SKIA_LIBRARY}")
    endif()
endif()

set(AC_SOURCES
    src/canvas.cpp
    src/camera_canvas.cpp
)

set(AC_HEADERS
    include/ac/types.h
    include/ac/canvas.h
    include/ac/camera_canvas.h
)

add_library(ac_static STATIC
    ${AC_SOURCES}
    ${AC_HEADERS}
)

target_include_directories(ac_static PUBLIC
    ${CMAKE_SOURCE_DIR}/include
)

if(AC_USE_SKIA AND SKIA_LIBRARY)
    target_compile_definitions(ac_static PUBLIC AC_USE_SKIA)
    target_include_directories(ac_static PRIVATE ${SKIA_DIR})
endif()
