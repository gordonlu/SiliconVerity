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

# 整数 ILP workload 保持标量: 防编译器把 8 条链向量化成 NEON
set_source_files_properties(cpu_integer_jni.cpp PROPERTIES COMPILE_OPTIONS "-fno-vectorize;-fno-slp-vectorize")

find_library(log-lib log)

target_link_libraries(sv_cpu_int
    ${log-lib}
)
