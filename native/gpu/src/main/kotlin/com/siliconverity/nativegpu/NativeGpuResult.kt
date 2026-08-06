package com.siliconverity.nativegpu

data class NativeGpuResult(
    val supported: Boolean,
    val deviceName: String?,
    val driverVersion: String?,
    val vulkanVersion: String?,
    val metricValue: Double?,
    val metricUnit: String?,
    val medianNs: Long?,
    val coefficientOfVariation: Double?,
    val checksumValid: Boolean,
    val invalidReason: String?,
) {
    companion object {
        fun parse(s: String): NativeGpuResult {
            val map = HashMap<String, String>()
            for (part in s.split(";")) {
                val eq = part.indexOf('=')
                if (eq > 0) map[part.substring(0, eq).trim()] = part.substring(eq + 1)
            }
            fun g(k: String): String? = map[k]?.takeIf { it.isNotEmpty() }
            return NativeGpuResult(
                supported = g("supported") == "1",
                deviceName = g("deviceName"),
                driverVersion = g("driverVersion"),
                vulkanVersion = g("vulkanVersion"),
                metricValue = g("metricValue")?.toDoubleOrNull(),
                metricUnit = g("metricUnit"),
                medianNs = g("medianNs")?.toLongOrNull(),
                coefficientOfVariation = g("cv")?.toDoubleOrNull(),
                checksumValid = g("checksumValid") == "1",
                invalidReason = g("invalidReason"),
            )
        }
    }
}
