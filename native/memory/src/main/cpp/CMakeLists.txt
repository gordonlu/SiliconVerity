cmake_minimum_required(VERSION 3.22.1)

project(sv_mem LANGUAGES CXX)

add_library(sv_mem SHARED
    mem_jni.cpp
)

target_compile_features(sv_mem PRIVATE cxx_std_20)
target_compile_options(sv_mem PRIVATE -O3)

find_library(log-lib log)

target_link_libraries(sv_mem
    ${log-lib}
)
