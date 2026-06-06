package org.aarchdroid.dragonterminal.component.config

import org.aarchdroid.dragonterminal.frontend.config.NeoConfigureFile

/**
 * @author kiva
 */
interface IConfigureLoader {
    fun loadConfigure() : NeoConfigureFile?
}
