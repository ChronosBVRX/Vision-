package com.chronosbvrx.visionplus.ui.player

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Renderer GL propio para el motor X4K.
 *
 * X4K sigue decodificando con su MediaCodec; solo se le entrega un `SurfaceTexture`
 * intermedio en vez de su `TextureView`. Este renderer dibuja el vídeo con shader
 * (color, curva, nitidez y deband) en un `GLSurfaceView` que queda dentro del
 * contenedor nativo, debajo de los controles.
 *
 * Es experimental y siempre tiene fallback a la TextureView nativa.
 */
internal class X4KGlesRenderer(
    context: Context,
    private val onSurfaceReady: (Surface) -> Unit,
    private val onFirstFrame: () -> Unit,
) {
    companion object {
        private const val TAG = "X4KGlesRenderer"
    }

    private val renderer = VideoRenderer()

    val view: GLSurfaceView = GLSurfaceView(context).apply {
        setEGLContextClientVersion(2)
        runCatching { setEGLConfigChooser(8, 8, 8, 8, 16, 0) }
        setZOrderMediaOverlay(false)
        setZOrderOnTop(false)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var released = false
    @Volatile private var surfaceTexture: SurfaceTexture? = null
    @Volatile private var engineSurface: Surface? = null
    @Volatile private var textureId = 0
    @Volatile private var videoWidth = 0
    @Volatile private var videoHeight = 0
    @Volatile private var aspectMode = 0
    @Volatile private var zoom = 1f
    @Volatile private var preset = X4KImagePreset.CINE
    @Volatile private var frameSeen = false
    @Volatile private var firstFrameNotified = false

    fun start() {
        runCatching { view.onResume() }
        view.requestRender()
    }

    fun pause() {
        runCatching { view.onPause() }
    }

    fun resume() {
        if (released) return
        runCatching { view.onResume() }
        view.requestRender()
    }

    fun hasFrame(): Boolean = frameSeen

    fun currentSurface(): Surface? = engineSurface

    fun setVideoSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        videoWidth = width
        videoHeight = height
        surfaceTexture?.setDefaultBufferSize(width, height)
        view.requestRender()
    }

    fun setAspect(mode: Int) {
        aspectMode = mode
        view.requestRender()
    }

    fun setZoom(value: Float) {
        zoom = value.coerceIn(1f, 1.30f)
        view.requestRender()
    }

    fun setPreset(value: X4KImagePreset) {
        preset = value
        view.requestRender()
    }

    fun isCreated(): Boolean = surfaceTexture != null

    fun release() {
        if (released) return
        released = true
        runCatching {
            view.queueEvent {
                releaseGl()
            }
        }
        runCatching { view.onPause() }
    }

    private fun releaseGl() {
        runCatching { GLES20.glDeleteProgram(renderer.program) }
        renderer.program = 0
        runCatching { engineSurface?.release() }
        engineSurface = null
        runCatching { surfaceTexture?.release() }
        surfaceTexture = null
        if (textureId != 0) {
            runCatching { GLES20.glDeleteTextures(1, intArrayOf(textureId), 0) }
            textureId = 0
        }
        frameSeen = false
    }

    private fun notifySurfaceReady(surface: Surface) {
        if (released) return
        mainHandler.post {
            if (!released) onSurfaceReady(surface)
        }
    }

    private fun notifyFirstFrame() {
        if (firstFrameNotified || released) return
        firstFrameNotified = true
        mainHandler.post {
            if (!released) onFirstFrame()
        }
    }

    private inner class VideoRenderer : GLSurfaceView.Renderer {
        var program = 0

        private var uSTMatrix = 0
        private var uScale = 0
        private var uTexture = 0
        private var uColor = 0
        private var uOffset = 0
        private var uTexel = 0
        private var uSharpness = 0
        private var uDeband = 0
        private var uCurve = 0

        private var aPosition = 0
        private var aTexCoord = 0

        private val quad: FloatBuffer = floatBuffer(
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f,
        )
        private val texCoords: FloatBuffer = floatBuffer(
            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f,
        )
        private val stMatrix = FloatArray(16)
        private val colorMatrix = FloatArray(9)
        private val colorOffset = FloatArray(3)
        private val scale = FloatArray(2)

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            if (released) return
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glDisable(GLES20.GL_BLEND)
            releaseGl()
            program = buildProgram()
            if (program == 0) {
                Log.e(TAG, "shader GL no compiló")
                return
            }
            aPosition = GLES20.glGetAttribLocation(program, "aPosition")
            aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
            uSTMatrix = GLES20.glGetUniformLocation(program, "uSTMatrix")
            uScale = GLES20.glGetUniformLocation(program, "uScale")
            uTexture = GLES20.glGetUniformLocation(program, "sTexture")
            uColor = GLES20.glGetUniformLocation(program, "uColor")
            uOffset = GLES20.glGetUniformLocation(program, "uOffset")
            uTexel = GLES20.glGetUniformLocation(program, "uTexel")
            uSharpness = GLES20.glGetUniformLocation(program, "uSharpness")
            uDeband = GLES20.glGetUniformLocation(program, "uDeband")
            uCurve = GLES20.glGetUniformLocation(program, "uCurve")

            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            textureId = textures[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR,
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR,
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE,
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE,
            )
            val texture = SurfaceTexture(textureId)
            texture.setDefaultBufferSize(
                videoWidth.takeIf { it > 0 } ?: 1280,
                videoHeight.takeIf { it > 0 } ?: 720,
            )
            texture.setOnFrameAvailableListener {
                if (!released) view.requestRender()
            }
            surfaceTexture = texture
            val surface = Surface(texture)
            engineSurface = surface
            frameSeen = false
            firstFrameNotified = false
            notifySurfaceReady(surface)
            Log.i(TAG, "SurfaceTexture listo (${videoWidth}x$videoHeight)")
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
            view.requestRender()
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            val texture = surfaceTexture ?: return
            if (engineSurface == null) return
            if (program == 0) return
            try {
                texture.updateTexImage()
                texture.getTransformMatrix(stMatrix)
            } catch (e: Exception) {
                return
            }

            val grade = X4KImageEnhancer.gradeFor(preset)
            val matrix = X4KImageEnhancer.colorMatrix(preset)?.array
            if (matrix == null) {
                colorMatrix[0] = 1f; colorMatrix[1] = 0f; colorMatrix[2] = 0f
                colorMatrix[3] = 0f; colorMatrix[4] = 1f; colorMatrix[5] = 0f
                colorMatrix[6] = 0f; colorMatrix[7] = 0f; colorMatrix[8] = 1f
                colorOffset[0] = 0f; colorOffset[1] = 0f; colorOffset[2] = 0f
            } else {
                // ColorMatrix es 4x5 por filas; GLSL mat3 es column-major.
                colorMatrix[0] = matrix[0]
                colorMatrix[1] = matrix[5]
                colorMatrix[2] = matrix[10]
                colorMatrix[3] = matrix[1]
                colorMatrix[4] = matrix[6]
                colorMatrix[5] = matrix[11]
                colorMatrix[6] = matrix[2]
                colorMatrix[7] = matrix[7]
                colorMatrix[8] = matrix[12]
                colorOffset[0] = matrix[4] / 255f
                colorOffset[1] = matrix[9] / 255f
                colorOffset[2] = matrix[14] / 255f
            }
            updateScale()

            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glUniform1i(uTexture, 0)
            GLES20.glUniformMatrix4fv(uSTMatrix, 1, false, stMatrix, 0)
            GLES20.glUniformMatrix3fv(uColor, 1, false, colorMatrix, 0)
            GLES20.glUniform3fv(uOffset, 1, colorOffset, 0)
            GLES20.glUniform2f(
                uTexel,
                1f / (videoWidth.coerceAtLeast(1)),
                1f / (videoHeight.coerceAtLeast(1)),
            )
            GLES20.glUniform1f(uSharpness, grade.sharpness)
            GLES20.glUniform1f(uDeband, grade.deband)
            GLES20.glUniform1f(uCurve, grade.curve)
            GLES20.glUniform2fv(uScale, 1, scale, 0)

            quad.position(0)
            GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, quad)
            GLES20.glEnableVertexAttribArray(aPosition)
            texCoords.position(0)
            GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 0, texCoords)
            GLES20.glEnableVertexAttribArray(aTexCoord)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(aPosition)
            GLES20.glDisableVertexAttribArray(aTexCoord)
            if (!frameSeen) {
                frameSeen = true
                notifyFirstFrame()
            }
        }

        private fun updateScale() {
            val vw = view.width.takeIf { it > 0 } ?: 1920
            val vh = view.height.takeIf { it > 0 } ?: 1080
            val vAspect = vw.toFloat() / vh.toFloat()
            val sourceAspect = if (videoWidth > 0 && videoHeight > 0) {
                videoWidth.toFloat() / videoHeight.toFloat()
            } else {
                16f / 9f
            }
            val targetAspect = when (aspectMode) {
                4 -> 16f / 9f
                5 -> 4f / 3f
                else -> sourceAspect
            }
            // q = alto lógico del cuadro; fit o fill respetando el target.
            val fitQ = minOf(vw / targetAspect, vh.toFloat())
            val fillQ = maxOf(vw / targetAspect, vh.toFloat())
            val q = (if (aspectMode == 1) fillQ else fitQ) * zoom
            scale[0] = (targetAspect * q) / vw
            scale[1] = q / vh
        }

        private fun buildProgram(): Int {
            val vertex = compileShader(GLES20.GL_VERTEX_SHADER, Shaders.VERTEX_SHADER)
            val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, Shaders.FRAGMENT_SHADER)
            if (vertex == 0 || fragment == 0) return 0
            val p = GLES20.glCreateProgram()
            GLES20.glAttachShader(p, vertex)
            GLES20.glAttachShader(p, fragment)
            GLES20.glLinkProgram(p)
            val status = IntArray(1)
            GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0)
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)
            if (status[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "link: " + GLES20.glGetProgramInfoLog(p))
                GLES20.glDeleteProgram(p)
                return 0
            }
            return p
        }

        private fun compileShader(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "compile: " + GLES20.glGetShaderInfoLog(shader))
                GLES20.glDeleteShader(shader)
                return 0
            }
            return shader
        }
    }

    private fun floatBuffer(vararg values: Float): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }

    private object Shaders {
        const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uSTMatrix;
            uniform vec2 uScale;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = vec4(aPosition.x * uScale.x, aPosition.y * uScale.y, 0.0, 1.0);
                vTexCoord = (uSTMatrix * aTexCoord).xy;
            }
        """

        const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES sTexture;
            uniform mat3 uColor;
            uniform vec3 uOffset;
            uniform vec2 uTexel;
            uniform float uSharpness;
            uniform float uDeband;
            uniform float uCurve;
            varying vec2 vTexCoord;
            float hash(vec2 p) {
                return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
            }
            void main() {
                vec4 src = texture2D(sTexture, vTexCoord);
                vec3 color = uColor * src.rgb + uOffset;
                if (uCurve > 0.001) {
                    vec3 curved = smoothstep(0.0, 1.0, color);
                    color = mix(color, curved, uCurve);
                }
                if (uSharpness > 0.001) {
                    vec3 n = texture2D(sTexture, vTexCoord + vec2(0.0, uTexel.y)).rgb;
                    vec3 s = texture2D(sTexture, vTexCoord - vec2(0.0, uTexel.y)).rgb;
                    vec3 e = texture2D(sTexture, vTexCoord + vec2(uTexel.x, 0.0)).rgb;
                    vec3 w = texture2D(sTexture, vTexCoord - vec2(uTexel.x, 0.0)).rgb;
                    vec3 blur = (n + s + e + w) * 0.25;
                    color += (src.rgb - blur) * uSharpness;
                }
                if (uDeband > 0.001) {
                    color += (hash(gl_FragCoord.xy) - 0.5) * (uDeband / 255.0);
                }
                gl_FragColor = vec4(clamp(color, 0.0, 1.0), src.a);
            }
        """
    }
}
