package com.siliconverity.core.benchmark

enum class ArithmeticType { FP32, FP16, INT8 }

enum class ArithmeticContract {
    DEVICE_DEFAULT,
    NO_CONTRACTION,
    DEVICE_FAST,
    STRICT_CONFORMANT,
}
