package org.aarchdroid.dragonterminal.floatui

import android.content.Context

class FloatPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(context.packageName + "_float_prefs", Context.MODE_PRIVATE)

    var windowX: Int
        get() = prefs.getInt(KEY_WINDOW_X, -1)
        set(value) = prefs.edit().putInt(KEY_WINDOW_X, value).apply()

    var windowY: Int
        get() = prefs.getInt(KEY_WINDOW_Y, -1)
        set(value) = prefs.edit().putInt(KEY_WINDOW_Y, value).apply()

    var windowWidth: Int
        get() = prefs.getInt(KEY_WINDOW_WIDTH, 600)
        set(value) = prefs.edit().putInt(KEY_WINDOW_WIDTH, value).apply()

    var windowHeight: Int
        get() = prefs.getInt(KEY_WINDOW_HEIGHT, 400)
        set(value) = prefs.edit().putInt(KEY_WINDOW_HEIGHT, value).apply()

    var fontSize: Int
        get() = prefs.getInt(KEY_FONT_SIZE, 14)
        set(value) = prefs.edit().putInt(KEY_FONT_SIZE, value).apply()

    companion object {
        private const val KEY_WINDOW_X = "float_window_x"
        private const val KEY_WINDOW_Y = "float_window_y"
        private const val KEY_WINDOW_WIDTH = "float_window_width"
        private const val KEY_WINDOW_HEIGHT = "float_window_height"
        private const val KEY_FONT_SIZE = "float_font_size"
    }
}
