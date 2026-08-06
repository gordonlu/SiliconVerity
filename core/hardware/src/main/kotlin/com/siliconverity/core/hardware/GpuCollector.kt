package com.siliconverity.core.hardware

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import com.siliconverity.core.model.CapabilityStatus
import com.siliconverity.core.model.Evidence
import com.siliconverity.core.model.SourceType

class GpuCollector : HardwareCollector {
    override val key: String = "gpu"

    override fun collect(context: Context): List<CollectedFact> {
        val facts = mutableListOf<CollectedFact>()

        val display = runCatching { EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY) }.getOrNull()
        if (display == null || display === EGL14.EGL_NO_DISPLAY) {
            return listOf(CollectedFact("gpu.context", emptyList(), CapabilityStatus.Unsupported, listOf("EGL no display")))
        }

        val initOk = runCatching {
            EGL14.eglInitialize(display, IntArray(1), 0, IntArray(1), 0)
        }.getOrDefault(false)
        if (!initOk) {
            return listOf(CollectedFact("gpu.context", emptyList(), CapabilityStatus.Invalid("eglInitialize failed"), listOf("eglInitialize failed")))
        }

        try {
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            val chooseAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE,
            )
            val chose = runCatching {
                EGL14.eglChooseConfig(display, chooseAttribs, 0, configs, 0, 1, numConfigs, 0)
            }.getOrDefault(false) && numConfigs[0] > 0
            if (!chose) {
                return listOf(CollectedFact("gpu.context", emptyList(), CapabilityStatus.Invalid("eglChooseConfig failed")))
            }
            val config = configs[0]
                ?: return listOf(CollectedFact("gpu.context", emptyList(), CapabilityStatus.Invalid("eglChooseConfig returned null")))

            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
            val ctx: EGLContext? = runCatching {
                EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
            }.getOrNull()
            if (ctx == null || ctx === EGL14.EGL_NO_CONTEXT) {
                return listOf(CollectedFact("gpu.context", emptyList(), CapabilityStatus.Invalid("eglCreateContext failed")))
            }

            val surfAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            val surf: EGLSurface? = runCatching {
                EGL14.eglCreatePbufferSurface(display, config, surfAttribs, 0)
            }.getOrNull()
            if (surf == null || surf === EGL14.EGL_NO_SURFACE) {
                runCatching { EGL14.eglDestroyContext(display, ctx) }
                return listOf(CollectedFact("gpu.context", emptyList(), CapabilityStatus.Invalid("eglCreatePbufferSurface failed")))
            }

            val madeCurrent = runCatching {
                EGL14.eglMakeCurrent(display, surf, surf, ctx)
            }.getOrDefault(false)
            if (!madeCurrent) {
                runCatching { EGL14.eglDestroySurface(display, surf) }
                runCatching { EGL14.eglDestroyContext(display, ctx) }
                return listOf(CollectedFact("gpu.context", emptyList(), CapabilityStatus.Invalid("eglMakeCurrent failed")))
            }

            try {
                facts += gpuFact("gpu.vendor", "GL_VENDOR", GLES20.GL_VENDOR)
                facts += gpuFact("gpu.renderer", "GL_RENDERER", GLES20.GL_RENDERER)
                facts += gpuFact("gpu.gles_version", "GL_VERSION", GLES20.GL_VERSION)
                facts += gpuFact("gpu.glsl_version", "GL_SHADING_LANGUAGE_VERSION", GLES20.GL_SHADING_LANGUAGE_VERSION)
                facts += CollectedFact("gpu.context", emptyList(), CapabilityStatus.Supported)
            } finally {
                runCatching { EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT) }
                runCatching { EGL14.eglDestroySurface(display, surf) }
                runCatching { EGL14.eglDestroyContext(display, ctx) }
            }

            return facts
        } finally {
            runCatching { EGL14.eglTerminate(display) }
        }
    }

    private fun gpuFact(key: String, sourceId: String, glEnum: Int): CollectedFact = CollectedFact(
        key = key,
        evidence = listOf(
            Evidence(
                sourceType = SourceType.DRIVER_REPORTED,
                sourceId = sourceId,
                rawValue = runCatching { GLES20.glGetString(glEnum) }.getOrNull(),
                note = "driver-reported, not verified chip identity",
            ),
        ),
    )
}
