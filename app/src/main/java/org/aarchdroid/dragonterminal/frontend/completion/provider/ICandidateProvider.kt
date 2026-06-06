package org.aarchdroid.dragonterminal.frontend.completion.provider

import org.aarchdroid.dragonterminal.frontend.completion.model.CompletionCandidate

/**
 * @author kiva
 */

interface ICandidateProvider {
    val providerName: String

    fun provideCandidates(text: String): List<CompletionCandidate>?

    fun canComplete(text: String): Boolean
}
