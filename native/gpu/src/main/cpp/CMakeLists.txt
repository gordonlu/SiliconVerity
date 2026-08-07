cmake_minimum_required(VERSION 3.22.1)

project(sv_gpu LANGUAGES CXX)

set(GLSLC "")
if(DEFINED ENV{ANDROID_NDK_HOME})
    set(GLSLC "$ENV{ANDROID_NDK_HOME}/shader-tools/linux-x86_64/glslc")
elseif(DEFINED ENV{ANDROID_NDK})
    set(GLSLC "$ENV{ANDROID_NDK}/shader-tools/linux-x86_64/glslc")
endif()
if(NOT EXISTS "${GLSLC}" AND ANDROID_NDK)
    set(GLSLC "${ANDROID_NDK}/shader-tools/linux-x86_64/glslc")
endif()
if(NOT EXISTS "${GLSLC}")
    set(GLSLC "/data/android/sdk/ndk/29.0.14206865/shader-tools/linux-x86_64/glslc")
endif()
if(NOT EXISTS "${GLSLC}")
    message(FATAL_ERROR "glslc not found")
endif()

set(SHADER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/shaders)
set(BIN ${CMAKE_CURRENT_BINARY_DIR})

function(compile_shader name)
    set(comp ${SHADER_DIR}/${name}.comp)
    set(spv ${BIN}/${name}.spv)
    set(hdr ${BIN}/${name}_spv.h)
    add_custom_command(
        OUTPUT ${spv}
        COMMAND ${GLSLC} --target-env=vulkan1.1 -fshader-stage=compute ${comp} -o ${spv}
        DEPENDS ${comp}
        COMMENT "glslc ${name}.comp -> ${name}.spv"
        VERBATIM
    )
    add_custom_command(
        OUTPUT ${hdr}
        COMMAND ${CMAKE_COMMAND} -DINPUT=${spv} -DOUTPUT=${hdr} -DNAME=${name}_spv -P ${CMAKE_CURRENT_SOURCE_DIR}/embed_spv.cmake
        DEPENDS ${spv} ${CMAKE_CURRENT_SOURCE_DIR}/embed_spv.cmake
        COMMENT "embed ${name}.spv -> ${name}_spv.h"
        VERBATIM
    )
endfunction()

compile_shader(fp32)
compile_shader(fp32_independent)
compile_shader(buffer_triad)

add_library(sv_gpu SHARED
    vulkan_bench.cpp
    ${BIN}/fp32_spv.h
    ${BIN}/fp32_independent_spv.h
    ${BIN}/buffer_triad_spv.h
)

target_include_directories(sv_gpu PRIVATE ${BIN})
target_compile_features(sv_gpu PRIVATE cxx_std_20)
target_compile_options(sv_gpu PRIVATE -O3 -ffp-contract=fast)

find_library(vulkan-lib vulkan REQUIRED)
find_library(log-lib log REQUIRED)
find_library(android-lib android REQUIRED)
target_link_libraries(sv_gpu ${vulkan-lib} ${log-lib} ${android-lib})
