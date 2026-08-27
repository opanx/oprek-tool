cmake_minimum_required(VERSION 3.22)
project(oprek_native C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD 11)

# IL2CPP Dumper functions (compiled into shared library)
set(IL2CPP_DUMPER_SOURCES
    il2cpp_dumper.c
)

# Capstone - download and build from source
include(FetchContent)
FetchContent_Declare(
    capstone
    GIT_REPOSITORY https://github.com/capstone-engine/capstone.git
    GIT_TAG 5.0.3
    GIT_SHALLOW TRUE
)

# Build only what we need (ARM, ARM64, x86)
set(CAPSTONE_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(CAPSTONE_BUILD_CSTOOL OFF CACHE BOOL "" FORCE)
set(CAPSTONE_ARM OFF CACHE BOOL "" FORCE)
set(CAPSTONE_ARM64 ON CACHE BOOL "" FORCE)
set(CAPSTONE_X86 ON CACHE BOOL "" FORCE)
set(CAPSTONE_X86_ATT DISABLED CACHE BOOL "" FORCE)
set(CAPSTONE_MIPS OFF CACHE BOOL "" FORCE)
set(CAPSTONE_PPC OFF CACHE BOOL "" FORCE)
set(CAPSTONE_SPARC OFF CACHE BOOL "" FORCE)
set(CAPSTONE_SYSZ OFF CACHE BOOL "" FORCE)
set(CAPSTONE_XCORE OFF CACHE BOOL "" FORCE)
set(CAPSTONE_EVM OFF CACHE BOOL "" FORCE)
set(CAPSTONE_M68K OFF CACHE BOOL "" FORCE)
set(CAPSTONE_TMS320C64X OFF CACHE BOOL "" FORCE)
set(CAPSTONE_M680X OFF CACHE BOOL "" FORCE)
set(CAPSTONE_HPPA OFF CACHE BOOL "" FORCE)
set(CAPSTONE_V810 OFF CACHE BOOL "" FORCE)
set(CAPSTONE_CTLOFF OFF CACHE BOOL "" FORCE)
set(CAPSTONE_WASM OFF CACHE BOOL "" FORCE)
set(CAPSTONE_BPF OFF CACHE BOOL "" FORCE)
set(CAPSTONE_RISCV OFF CACHE BOOL "" FORCE)
set(CAPSTONE_ALPHA OFF CACHE BOOL "" FORCE)
set(CAPSTONE_LOONGARCH OFF CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(capstone)

# Build our native library
add_library(oprek_native SHARED
    jni_bridge.cpp
    il2cpp_dumper.c
)

target_link_libraries(oprek_native
    log
    android
    capstone
)

target_include_directories(oprek_native PRIVATE
    ${capstone_SOURCE_DIR}/include
    ${capstone_BINARY_DIR}/include
)
