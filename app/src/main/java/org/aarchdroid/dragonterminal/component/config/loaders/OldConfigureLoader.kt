package org.aarchdroid.dragonterminal.component.config.loaders

import org.aarchdroid.dragonterminal.component.config.IConfigureLoader
import org.aarchdroid.dragonterminal.frontend.config.NeoConfigureFile
import java.io.File

/**
 * @author kiva
 */
class OldConfigureLoader(private val configFile: File) : IConfigureLoader {
    override fun loadConfigure(): NeoConfigureFile? {
        return when (configFile.extension) {
            "eks" -> returnConfigure(OldExtraKeysConfigureFile(configFile))
            "color" -> returnConfigure(OldColorSchemeConfigureFile(configFile))
            else -> null
        }
    }

    private fun returnConfigure(configureFile: NeoConfigureFile): NeoConfigureFile? {
        return if (configureFile.parseConfigure()) configureFile else null
    }
}