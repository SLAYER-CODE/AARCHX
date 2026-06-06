package org.aarchdroid.dragonterminal.frontend.completion.listener

import org.aarchdroid.dragonterminal.frontend.completion.model.CompletionCandidate

/**
 * @author kiva
 */
interface OnCandidateSelectedListener {
    fun onCandidateSelected(candidate: CompletionCandidate)
}