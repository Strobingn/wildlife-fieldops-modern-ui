package com.strobingn.wildlifefieldops.ui.ar

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.MotionEvent
import com.google.ar.core.Anchor
import com.google.ar.core.Camera
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.strobingn.wildlifefieldops.ai.ARMeasurementHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Sceneform-free ARCore camera surface for two-tap plane measurement.
 * Drives [Session.update], blits the camera feed, and resolves hit-tests at taps
 * (or at the crosshair via [queueCenterHit]).
 */
class ArTwoTapSurfaceView(
    context: Context,
    private val onStatus: (ArSessionStatus) -> Unit,
    private val onFatal: (String) -> Unit
) : GLSurfaceView(context) {

    data class HitSample(
        val pose: Pose,
        val planeType: String,
        val confidence: Float,
        val anchor: Anchor?
    )

    data class ArSessionStatus(
        val tracking: Boolean,
        val planesVisible: Int,
        val message: String
    )

    private val renderer = Renderer()
    private val pendingTap = AtomicReference<Pair<Float, Float>?>(null)
    private val pendingCenterHit = AtomicBoolean(false)
    @Volatile private var hitListener: ((HitSample?) -> Unit)? = null
    @Volatile private var sessionHeld: Session? = null
    @Volatile private var installFailed = false
    @Volatile private var textureReady = false

    init {
        preserveEGLContextOnPause = true
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        // Create Session on UI thread (safer than GL thread for native init).
        try {
            val session = ARMeasurementHelper.createARSession(context)
            if (session == null) {
                installFailed = true
                post { onFatal("ARCore unavailable on this device") }
            } else {
                sessionHeld = session
            }
        } catch (t: Throwable) {
            installFailed = true
            Log.e(TAG, "AR session create failed", t)
            post { onFatal(t.message ?: "AR init failed") }
        }
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setOnHitListener(listener: ((HitSample?) -> Unit)?) {
        hitListener = listener
    }

    /** Place a point at the screen-center crosshair (Compose overlay). */
    fun queueCenterHit() {
        pendingCenterHit.set(true)
    }

    fun pauseSession() {
        try {
            onPause()
        } catch (_: Throwable) {
        }
        try {
            sessionHeld?.pause()
        } catch (t: Throwable) {
            Log.i(TAG, "session pause: ${t.message}")
        }
    }

    fun resumeSession() {
        if (installFailed || sessionHeld == null) return
        try {
            sessionHeld?.resume()
        } catch (t: Throwable) {
            Log.i(TAG, "session resume: ${t.message}")
            post { onFatal("AR session resume failed — use manual measure") }
            return
        }
        try {
            onResume()
        } catch (_: Throwable) {
        }
    }

    fun destroySession() {
        try {
            pauseSession()
        } catch (_: Throwable) {
        }
        try {
            sessionHeld?.close()
        } catch (_: Throwable) {
        }
        sessionHeld = null
        textureReady = false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            pendingTap.set(event.x to event.y)
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private inner class Renderer : GLSurfaceView.Renderer {
        private val background = ArCameraBackgroundRenderer()
        private val uvSrc: FloatBuffer = ByteBuffer.allocateDirect(8 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f))
            .also { it.position(0) }
        private val uvDst: FloatBuffer = ByteBuffer.allocateDirect(8 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        private val uvDraw = FloatArray(8)
        private var viewportW = 0
        private var viewportH = 0
        private var rotation = 0
        private var lastStatusPostMs = 0L

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            if (installFailed || sessionHeld == null) return
            try {
                GLES20.glClearColor(0f, 0f, 0f, 1f)
                background.createOnGlThread()
                sessionHeld?.setCameraTextureName(background.cameraTextureId)
                textureReady = true
                post {
                    onStatus(
                        ArSessionStatus(
                            tracking = false,
                            planesVisible = 0,
                            message = "Move phone slowly to detect a plane…"
                        )
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "AR GL init failed", t)
                textureReady = false
                post { onFatal(t.message ?: "AR GL init failed") }
            }
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            viewportW = width
            viewportH = height
            GLES20.glViewport(0, 0, width, height)
            rotation = try {
                display?.rotation ?: 0
            } catch (_: Throwable) {
                0
            }
            try {
                sessionHeld?.setDisplayGeometry(rotation, width, height)
            } catch (t: Throwable) {
                Log.i(TAG, "setDisplayGeometry: ${t.message}")
            }
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            val session = sessionHeld ?: return
            if (!textureReady) return
            try {
                val rot = try {
                    display?.rotation ?: rotation
                } catch (_: Throwable) {
                    rotation
                }
                if (rot != rotation && viewportW > 0 && viewportH > 0) {
                    rotation = rot
                    session.setDisplayGeometry(rotation, viewportW, viewportH)
                }

                session.setCameraTextureName(background.cameraTextureId)
                val frame: Frame = session.update()
                val camera: Camera = frame.camera

                uvSrc.position(0)
                uvDst.position(0)
                try {
                    frame.transformDisplayUvCoords(uvSrc, uvDst)
                    uvDst.position(0)
                    uvDst.get(uvDraw)
                } catch (_: Throwable) {
                    uvDraw[0] = 0f; uvDraw[1] = 1f
                    uvDraw[2] = 1f; uvDraw[3] = 1f
                    uvDraw[4] = 0f; uvDraw[5] = 0f
                    uvDraw[6] = 1f; uvDraw[7] = 0f
                }
                background.draw(uvDraw)

                val now = System.currentTimeMillis()
                if (now - lastStatusPostMs > 250L) {
                    lastStatusPostMs = now
                    val planes = try {
                        session.getAllTrackables(Plane::class.java)
                            .count { it.trackingState == TrackingState.TRACKING }
                    } catch (_: Throwable) {
                        0
                    }
                    val isTracking = camera.trackingState == TrackingState.TRACKING
                    val msg = when {
                        !isTracking -> "Point camera at surfaces — waiting for tracking…"
                        planes == 0 -> "Scan floors/walls until a plane appears…"
                        else -> "Plane locked — tap point A, then point B"
                    }
                    post { onStatus(ArSessionStatus(isTracking, planes, msg)) }
                }

                val tap = pendingTap.getAndSet(null)
                val center = pendingCenterHit.getAndSet(false)
                val hitXY: Pair<Float, Float>? = when {
                    tap != null -> tap
                    center && viewportW > 0 && viewportH > 0 ->
                        (viewportW * 0.5f) to (viewportH * 0.5f)
                    else -> null
                }
                if (hitXY != null) {
                    val sample = ARMeasurementHelper.hitTestBest(frame, hitXY.first, hitXY.second)
                    val mapped = sample?.let { hs ->
                        val anchor = try {
                            hs.hitResult.createAnchor()
                        } catch (_: Throwable) {
                            try {
                                session.createAnchor(hs.pose)
                            } catch (_: Throwable) {
                                null
                            }
                        }
                        HitSample(
                            pose = hs.pose,
                            planeType = hs.planeType,
                            confidence = hs.confidence,
                            anchor = anchor
                        )
                    }
                    val listener = hitListener
                    post { listener?.invoke(mapped) }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "onDrawFrame: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    companion object {
        private const val TAG = "ArTwoTapSurface"
    }
}
