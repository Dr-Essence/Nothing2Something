package com.dressence.nothinglauncherplus

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice

/**
 * What counts as a game controller, in one place (until 0.70.9 the same test was written out in
 * launcher.Gamepad, keyboard.HeliBoardGamepad, system.ControllerHome and ui.GamingSpaceActivity).
 *
 * A device's sources say what kind of input it gives; a gamepad reports SOURCE_GAMEPAD (its
 * buttons), a joystick SOURCE_JOYSTICK (its sticks). Each is a bit pattern of several bits, so
 * the test is "all its bits are set", not "any bit".
 */
object Controllers {

    /** True if [sources] (a device's or a key event's) include a gamepad or a joystick. */
    @JvmStatic
    fun isController(sources: Int): Boolean =
        sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

    /**
     * True if a real controller is connected: not a virtual device (such as the one Android
     * makes for injected keys) and external (not part of the phone). ControllerHome switches
     * home with this, and Gaming Space leaves with it, so the two agree (until 0.70.9 Gaming
     * Space did not ask for "external").
     */
    @JvmStatic
    fun connected(context: Context): Boolean {
        val input = context.getSystemService(InputManager::class.java) ?: return false
        return input.inputDeviceIds.any { id ->
            val device = input.getInputDevice(id) ?: return@any false
            !device.isVirtual && device.isExternal && isController(device.sources)
        }
    }
}
