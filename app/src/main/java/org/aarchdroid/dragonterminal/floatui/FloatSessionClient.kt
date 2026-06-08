package org.aarchdroid.dragonterminal.floatui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.SoundPool
import android.text.TextUtils
import org.aarchdroid.dragonterminal.backend.TerminalSession
import org.aarchdroid.dragonterminal.backend.TextStyle
import org.aarchdroid.dragonterminal.frontend.config.NeoTermPath
import org.aarchdroid.dragonterminal.frontend.config.NeoPreference
import java.io.File
import java.io.FileInputStream
import java.util.Properties

class FloatSessionClient(
    private val service: FloatService,
    private val view: FloatWindowView
) {

    private var bellSoundPool: SoundPool? = null
    private var bellSoundId = 0

    fun onAttachedToWindow() {
        loadBell()
    }

    fun onDetachedFromWindow() {
        releaseBell()
    }

    fun onReload() {
        checkFontAndColors()
    }

    fun onTextChanged(session: TerminalSession) {
        if (!view.isVisible()) return
        view.terminalView.onScreenUpdated()
    }

    fun onSessionFinished(session: TerminalSession) {
        service.requestStopService()
    }

    fun onCopyText(text: String) {
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
    }

    fun onPasteText(session: TerminalSession) {
        if (!view.isVisible()) return
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip
        if (clipData != null) {
            val paste = clipData.getItemAt(0).coerceToText(service)
            if (!TextUtils.isEmpty(paste)) {
                session.emulator.paste(paste.toString())
            }
        }
    }

    fun onBell() {
        if (!view.isVisible()) return
        bellSoundPool?.let {
            if (bellSoundId != 0) {
                it.play(bellSoundId, 1f, 1f, 1, 0, 1f)
            }
        }
    }

    fun onColorsChanged() {
        updateBackgroundColor()
    }

    fun checkFontAndColors() {
        try {
            val props = Properties()

            val colorsFile = File(NeoTermPath.COLORS_PATH)
            if (colorsFile.isFile) {
                FileInputStream(colorsFile).use { props.load(it) }
            }

            if (props.isEmpty) {
                val altColorsFile = File(service.filesDir, ".colorscheme")
                if (altColorsFile.isFile) {
                    FileInputStream(altColorsFile).use { props.load(it) }
                }
            }

            val session = service.currentSession
            if (session != null && session.emulator != null) {
                session.emulator.mColors.reset()
            }

            updateBackgroundColor()

            val fontFile = File(NeoTermPath.FONT_PATH)
            val typeface = if (fontFile.exists() && fontFile.length() > 0) {
                Typeface.createFromFile(fontFile)
            } else {
                Typeface.MONOSPACE
            }
            view.terminalView.setTypeface(typeface)
            view.terminalView.setTextSize(NeoPreference.getFontSize())
        } catch (e: Exception) {
            android.util.Log.e("FloatSessionClient", "Error in checkFontAndColors", e)
        }
    }

    private fun updateBackgroundColor() {
        val session = service.currentSession
        if (session != null && session.emulator != null) {
            view.terminalView.setBackgroundColor(
                session.emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND]
            )
        }
    }

    private fun loadBell() {
        if (bellSoundPool != null) return
        bellSoundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build()
            )
            .build()
        bellSoundId = 0
    }

    private fun releaseBell() {
        bellSoundPool?.release()
        bellSoundPool = null
    }
}
