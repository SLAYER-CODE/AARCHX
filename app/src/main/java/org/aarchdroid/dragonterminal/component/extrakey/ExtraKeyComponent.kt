package org.aarchdroid.dragonterminal.component.extrakey

import android.content.Context
import android.os.Handler
import androidx.core.app.ActivityCompat.finishAffinity
import io.neolang.visitor.ConfigVisitor
import org.aarchdroid.AArchDroidApp
import org.aarchdroid.dragonterminal.frontend.component.helper.ConfigFileBasedComponent
import org.aarchdroid.dragonterminal.frontend.config.NeoTermPath
import org.aarchdroid.dragonterminal.frontend.logging.NLog
import org.aarchdroid.dragonterminal.frontend.terminal.extrakey.ExtraKeysView
import org.aarchdroid.dragonterminal.utils.AssetsUtils
import java.io.File
import org.aarchdroid.dragonterminal.component.extrakey.NeoExtraKey
import org.aarchdroid.dragonterminal.ui.term.NeoTermActivity


/**
 * @author kiva
 */
class ExtraKeyComponent : ConfigFileBasedComponent<NeoExtraKey>(AArchDroidApp.get().filesDir.absolutePath + "/home/.neoterm/eks") {
    override val checkComponentFileWhenObtained
            get() = true

    private val extraKeys: MutableMap<String, NeoExtraKey> = mutableMapOf()

    override fun onCheckComponentFiles() {
        val defaultFile = File(AArchDroidApp.get().filesDir.absolutePath+"/home/.neoterm/eks/default.nl")
        if (!defaultFile.exists()) {

            extractDefaultConfig(AArchDroidApp.get())

        }

        reloadExtraKeyConfig()

    }

    override fun onCreateComponentObject(configVisitor: ConfigVisitor): NeoExtraKey {
        return NeoExtraKey()
    }

    fun showShortcutKeys(program: String, extraKeysView: ExtraKeysView?) {
        if (extraKeysView == null) {
            return
        }

        val extraKey = extraKeys[program]
        if (extraKey != null) {
            extraKey.applyExtraKeys(extraKeysView)
            return
        }

        extraKeysView.loadDefaultUserKeys()
    }

    private fun registerShortcutKeys(extraKey: NeoExtraKey) =
            extraKey.programNames.forEach {
                extraKeys[it] = extraKey
            }

    private fun extractDefaultConfig(context: Context) {
        try {
            AssetsUtils.extractAssetsDir(context, "eks", AArchDroidApp.get().filesDir.absolutePath+"/home/.neoterm/eks")
        } catch (e: Exception) {
            NLog.e("ExtraKey", "Failed to extract configure: ${e.localizedMessage}")
        }
    }

    private fun reloadExtraKeyConfig() {
        extraKeys.clear()

        File(AArchDroidApp.get().filesDir.absolutePath+"/home/.neoterm/eks")
                .listFiles(NEOLANG_FILTER)
                .filter { it.absolutePath != AArchDroidApp.get().filesDir.absolutePath+"/home/.neoterm/eks" }
                .mapNotNull { this.loadConfigure(it) }
                .forEach {
                    registerShortcutKeys(it)
                }


    }
}