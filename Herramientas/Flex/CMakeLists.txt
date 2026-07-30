cmake_minimum_required(VERSION 3.16)
project(flex VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(SHARED_IRIS_LIB_DIR "${CMAKE_SOURCE_DIR}/../../app/src/main/cpp/lib/build_aarch64"
    CACHE PATH "Iris static lib build dir")
set(SHARED_IRIS_INCLUDE_DIR "${SHARED_IRIS_LIB_DIR}/../include")

add_executable(flex
    src/main.cpp
)

target_include_directories(flex PRIVATE
    ${SHARED_IRIS_INCLUDE_DIR}
)

target_link_libraries(flex PRIVATE
    "${SHARED_IRIS_LIB_DIR}/libiris_static.a"
    m
    pthread
)

install(TARGETS flex DESTINATION bin)
