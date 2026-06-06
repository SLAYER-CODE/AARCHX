package org.aarchdroid.dragonterminal.component.completion

import org.aarchdroid.dragonterminal.component.completion.provider.FileCompletionProvider
import org.aarchdroid.dragonterminal.component.completion.provider.ProgramCompletionProvider
import org.aarchdroid.dragonterminal.frontend.completion.CompletionManager
import org.aarchdroid.dragonterminal.frontend.component.NeoComponent

/**
 * @author kiva
 */
class CompletionComponent : NeoComponent {
    override fun onServiceInit() {
        CompletionManager.registerProvider(FileCompletionProvider())
        CompletionManager.registerProvider(ProgramCompletionProvider())
    }

    override fun onServiceDestroy() {
    }

    override fun onServiceObtained() {
    }
}