package org.aarchdroid.dragonterminal.component.config.loaders

import org.aarchdroid.dragonterminal.component.config.IConfigureLoader
import org.aarchdroid.dragonterminal.frontend.config.NeoConfigureFile
import java.io.File

/**
 * @author kiva
 */
class NeoLangConfigureLoader(private val configFile: File) : IConfigureLoader {
    override fun loadConfigure(): NeoConfigureFile? {
        val configureFile = NeoConfigureFile(configFile)
        return if (configureFile.parseConfigure()) configureFile else null
    }
}