package com.dressence.nothinglauncherplus

import android.app.BroadcastOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Broadcasts between the module's parts that carry who sent them, and the check on the other
 * side, so a receiver acts only for the module app and Nothing Launcher.
 *
 * Why (0.70.18, from the audit's third and fourth passes): the module's receivers in
 * system_server were open to every app. Any installed app could make the system open the
 * playing media app from the background with the system's own rights, pull the notification
 * shade down, open Settings screens or reset the system font's crash guard. Android tells a
 * receiver who sent a broadcast only if the sender agrees to share it
 * (BroadcastOptions.setShareIdentityEnabled, Android 14); System UI's panels have checked it
 * this way since the performance overlay (HudWindows), proven on the phone.
 */
object Broadcasts {

    /** The packages allowed to ask: the module app, and its hooks inside Nothing Launcher. */
    private val TRUSTED = setOf(Config.MODULE_PKG, Config.LAUNCHER_PKG)

    /** Options that tell the receiver this app sent it. */
    @JvmStatic
    fun withIdentity(): Bundle =
        BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle()

    /** [intent], sent so that its receiver can see who sent it. */
    @JvmStatic
    fun send(context: Context, intent: Intent) {
        context.sendBroadcast(intent, null, withIdentity())
    }

    /** True if the broadcast [receiver] is handling came from the module app or the launcher. */
    @JvmStatic
    fun fromModule(receiver: BroadcastReceiver): Boolean =
        receiver.sentFromPackage in TRUSTED
}
