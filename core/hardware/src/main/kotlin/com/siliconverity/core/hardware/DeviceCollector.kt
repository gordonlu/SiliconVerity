package com.siliconverity.core.hardware

import android.content.Context
import android.os.Build
import com.siliconverity.core.model.Evidence
import com.siliconverity.core.model.SourceType

class DeviceCollector : HardwareCollector {
    override val key: String = "device"

    override fun collect(context: Context): List<CollectedFact> = listOf(
        CollectedFact(
            key = "device.model",
            evidence = listOf(
                Evidence(SourceType.PUBLIC_API, "Build.MANUFACTURER + Build.MODEL", "${Build.MANUFACTURER} ${Build.MODEL}".trim()),
            ),
        ),
        CollectedFact(
            key = "device.android_version",
            evidence = listOf(
                Evidence(SourceType.PUBLIC_API, "Build.VERSION.RELEASE", Build.VERSION.RELEASE),
            ),
        ),
        CollectedFact(
            key = "device.api_level",
            evidence = listOf(
                Evidence(SourceType.PUBLIC_API, "Build.VERSION.SDK_INT", Build.VERSION.SDK_INT.toString()),
            ),
        ),
        CollectedFact(
            key = "device.security_patch",
            evidence = listOf(
                Evidence(SourceType.PUBLIC_API, "Build.VERSION.SECURITY_PATCH", Build.VERSION.SECURITY_PATCH),
            ),
        ),
    )
}
