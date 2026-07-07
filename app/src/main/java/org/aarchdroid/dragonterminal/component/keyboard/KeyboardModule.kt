package org.aarchdroid.dragonterminal.component.keyboard

import android.content.Context
import android.content.pm.PackageManager

object KeyboardModule {
    const val HACKERS_KEYBOARD_PACKAGE = "org.pocketworkstation.pckeyboard"

    fun isHackersKeyboardInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(HACKERS_KEYBOARD_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
