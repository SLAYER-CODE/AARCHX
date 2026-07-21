cmake_minimum_required(VERSION 3.16)
project(iris_static VERSION 0.1.0 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(IRIS_USE_SKIA "Build with Skia rendering" OFF)

find_package(Threads QUIET)

if(IRIS_USE_SKIA)
    find_library(SKIA_LIBRARY skia
        PATHS ${SKIA_DIR}/out/x64 ${SKIA_DIR}/out/arm64 ${SKIA_DIR}/lib)
    if(NOT SKIA_LIBRARY)
        message(WARNING "Skia not found")
    else()
        add_definitions(-DMANDELA_USE_SKIA)
        message(STATUS "Found Skia: ${SKIA_LIBRARY}")
    endif()
endif()

set(IRIS_SOURCES
    src/canvas.cpp
)

set(IRIS_HEADERS
    include/iris/types.h
    include/iris/canvas.h
)

add_library(iris_static STATIC
    ${IRIS_SOURCES}
    ${IRIS_HEADERS}
)

target_include_directories(iris_static PUBLIC
    ${CMAKE_SOURCE_DIR}/include
)

if(IRIS_USE_SKIA AND SKIA_LIBRARY)
    target_compile_definitions(iris_static PUBLIC MANDELA_USE_SKIA)
    target_include_directories(iris_static PRIVATE ${SKIA_DIR})
endif()
