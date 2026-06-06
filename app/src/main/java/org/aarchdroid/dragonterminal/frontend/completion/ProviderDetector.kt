package org.aarchdroid.dragonterminal.frontend.completion

import org.aarchdroid.dragonterminal.frontend.completion.listener.MarkScoreListener
import org.aarchdroid.dragonterminal.frontend.completion.provider.ICandidateProvider

/**
 * @author kiva
 */
class ProviderDetector(val providers: List<ICandidateProvider>) : MarkScoreListener {
    private var detectedProvider: ICandidateProvider? = null

    override fun onMarkScore(score: Int) {
        // TODO: Save provider score
    }

    fun detectBest(): ICandidateProvider? {
        // TODO: detect best
        detectedProvider = if (providers.isEmpty())
            null
        else
            providers[0]

        return detectedProvider
    }
}