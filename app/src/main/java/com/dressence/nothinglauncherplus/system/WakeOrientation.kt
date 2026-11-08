package com.dressence.nothinglauncherplus.system

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.SystemClock
import com.dressence.nothinglauncherplus.xposed.ConfigStore
import com.dressence.nothinglauncherplus.xposed.Hooks
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * "Turn with the phone as the screen comes on": the screen starts in the orientation the phone
 * is held in, without waiting for it to be moved.
 *
 * How it was found: the window manager's rotation sensor here is MediaTek's device orientation
 * sensor (`dev_orient`, type 27), an "on-change" sensor. Android's sensor rules say such a
 * sensor reports its current value as soon as it is switched on; this one reports only
 * changes. The window manager switches it on as the screen comes on
 * (WindowOrientationListener.enable), and then learns nothing until the phone moves. DebuG23:
 * woken at 55.97 s, the screen stayed in landscape until the sensor's first report at 57.27 s.
 * ([RotationSensor] keeps the rotation the sensor knew before; this adds the one it misses.)
 *
 * What it does: right after that switch-on, it reads the accelerometer for a moment and, if the
 * phone is clearly upright or on its side (not lying flat, not at an angle between two sides),
 * hands that rotation to Android's own judge (OrientationSensorJudge.onSensorChanged) in the
 * form a `dev_orient` report has: Android's rules then decide as for any report (auto-rotate,
 * apps' orientation, the lock screen). The accelerometer is read once and switched off again.
 *
 * Unlocking checks again: the sensor is already on then, so no switch-on comes, and a phone
 * turned while the lock screen showed it portrait (the lock screen does not turn unless
 * "Lock screen rotation" is on) gave no new report either. The window manager hears of the
 * unlock in KeyguardController.setKeyguardShown (the lock screen no longer showing on the
 * phone's own display, as the log line wm_set_keyguard_shown [0,0,…] shows).
 */
internal object WakeOrientation {

    private const val LISTENER = "com.android.server.wm.WindowOrientationListener"
    private const val KEYGUARD = "com.android.server.wm.KeyguardController"
    private const val TYPE_DEVICE_ORIENTATION = 27

    /** Samples averaged, and the longest the accelerometer stays on per wake. */
    private const val SAMPLES = 3
    private const val TIMEOUT_MS = 400L
    /** Gravity's size in m/s² for a phone held still (not being swung). */
    private const val STILL_MIN = 7f
    private const val STILL_MAX = 12.5f
    /** Tilted further from upright than this (lying flat on a table): no answer. */
    private const val MAX_TILT = 60
    /** How far from straight up / sideways the phone may be held and still count. */
    private const val MAX_OFF_AXIS = 30

    /** Checks logged without diagnostic logging, so the first ones confirm it works. */
    private var logged = 0
    /** The window manager's rotation listener, once switched on. */
    @Volatile private var lastListener = java.lang.ref.WeakReference<Any>(null)

    @JvmStatic
    fun install(cl: ClassLoader, store: ConfigStore) {
        XposedHelpers.findAndHookMethod(XposedHelpers.findClass(LISTENER, cl), "enable",
            Boolean::class.javaPrimitiveType, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    lastListener = java.lang.ref.WeakReference(param.thisObject)
                    try {
                        if (store.get().wakeOrientation) check(param.thisObject, store, "screen on")
                    } catch (t: Throwable) {
                        Hooks.log("wake orientation: $t")
                    }
                }
            })
        XposedHelpers.findAndHookMethod(XposedHelpers.findClass(KEYGUARD, cl), "setKeyguardShown",
            Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        if (param.args[0] != 0 || param.args[1] == true) return
                        val state = XposedHelpers.callMethod(param.thisObject, "getDisplayState", 0)
                        if (XposedHelpers.getBooleanField(state, "mKeyguardShowing")) {
                            param.setObjectExtra("nlplus.unlocked", true)
                        }
                    } catch (t: Throwable) {
                        Hooks.log("wake orientation: $t")
                    }
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.getObjectExtra("nlplus.unlocked") == null) return
                    // Called under the window manager's lock: checked on the sensor's thread.
                    val listener = lastListener.get() ?: return
                    try {
                        (XposedHelpers.getObjectField(listener, "mHandler") as Handler).post {
                            try {
                                // Read again here, off the window manager's lock (a file read).
                                store.reloadIfChanged()
                                if (store.get().wakeOrientation) check(listener, store, "unlock")
                            } catch (t: Throwable) {
                                Hooks.log("wake orientation: $t")
                            }
                        }
                    } catch (t: Throwable) {
                        Hooks.log("wake orientation: $t")
                    }
                }
            })
        Hooks.log("wake orientation: ready")
    }

    /** Reads how the phone is held and tells the judge, once, for this screen on or unlock. */
    private fun check(listener: Any, store: ConfigStore, why: String) {
        val sensor = XposedHelpers.getObjectField(listener, "mSensor") as? Sensor ?: return
        if (sensor.type != TYPE_DEVICE_ORIENTATION ||
            !XposedHelpers.getBooleanField(listener, "mEnabled")) return
        val judge = XposedHelpers.getObjectField(listener, "mOrientationJudge") ?: return
        if (judge.javaClass.simpleName != "OrientationSensorJudge") return
        val manager = XposedHelpers.getObjectField(listener, "mSensorManager") as SensorManager
        val handler = XposedHelpers.getObjectField(listener, "mHandler") as Handler
        val accel = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return

        val sum = FloatArray(3)
        var count = 0
        lateinit var reader: SensorEventListener
        val stop = Runnable { manager.unregisterListener(reader) }
        reader = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (count >= SAMPLES) return
                for (i in 0..2) sum[i] += event.values[i]
                if (++count < SAMPLES) return
                handler.removeCallbacks(stop)
                manager.unregisterListener(this)
                try {
                    deliver(listener, judge, sensor, sum.map { it / SAMPLES }, store, why)
                } catch (t: Throwable) {
                    Hooks.log("wake orientation: $t")
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
        manager.registerListener(reader, accel, SensorManager.SENSOR_DELAY_GAME, handler)
        handler.postDelayed(stop, TIMEOUT_MS)
    }

    /**
     * The rotation for gravity (x, y, z) in the phone's own axes, worked out as Android's own
     * accelerometer judge does (AccelSensorJudge: the angle of gravity around the screen and
     * the tilt out of it), or why there is none.
     */
    private fun deliver(listener: Any, judge: Any, sensor: Sensor, g: List<Float>,
                        store: ConfigStore, why: String) {
        val (x, y, z) = g
        val size = sqrt(x * x + y * y + z * z)
        val tilt = Math.toDegrees(asin((z / size).coerceIn(-1f, 1f).toDouble())).roundToInt()
        var angle = Math.toDegrees(-atan2(-x, y).toDouble()).roundToInt()
        if (angle < 0) angle += 360
        val rotation = ((angle + 45) / 90) % 4
        val offAxis = abs(angle - rotation * 90).let { if (it > 180) 360 - it else it }
        val reason = when {
            size < STILL_MIN || size > STILL_MAX -> "moving"
            abs(tilt) > MAX_TILT -> "lying flat"
            offAxis > MAX_OFF_AXIS -> "between two sides"
            !XposedHelpers.getBooleanField(listener, "mEnabled") -> "screen off again"
            else -> null
        }
        if (reason == null) {
            // As dev_orient reports it: the rotation in values[0], now.
            val report = SensorEvent::class.java.getConstructor(Sensor::class.java,
                Int::class.javaPrimitiveType, Long::class.javaPrimitiveType,
                FloatArray::class.java).newInstance(sensor, SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
                SystemClock.elapsedRealtimeNanos(), floatArrayOf(rotation.toFloat()))
            (judge as SensorEventListener).onSensorChanged(report)
        }
        if (store.get().diagnosticLogging || logged < 3) {
            logged++
            Hooks.log("wake orientation ($why): held at angle $angle, tilt $tilt -> " +
                (reason ?: "rotation $rotation told to the window manager"))
        }
    }
}
