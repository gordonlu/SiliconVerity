cmake_minimum_required(VERSION 3.22.1)

project(sv_cpu LANGUAGES CXX)

add_library(sv_cpu_int SHARED
    cpu_integer_jni.cpp
    fp32_fma_jni.cpp
    cpu_features_jni.cpp
    int_branch_jni.cpp
    compress_jni.cpp
    multithread_jni.cpp
)

target_compile_features(sv_cpu_int PRIVATE cxx_std_20)
target_compile_options(sv_cpu_int PRIVATE -O3 -ffp-contract=fast)

find_library(log-lib log)

target_link_libraries(sv_cpu_int
    ${log-lib}
)
