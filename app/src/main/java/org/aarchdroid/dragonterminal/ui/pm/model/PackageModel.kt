package org.aarchdroid.dragonterminal.ui.pm.model

import org.aarchdroid.dragonterminal.util.SortedListAdapter

data class PacmanPackage(
    val name: String,
    val version: String,
    val repo: String
)

class PackageModel(val pkg: PacmanPackage) : SortedListAdapter.ViewModel {
    override fun <T> isSameModelAs(t: T): Boolean {
        if (t is PackageModel) {
            return t.pkg.name == pkg.name
        }
        return false
    }

    override fun <T> isContentTheSameAs(t: T): Boolean {
        return isSameModelAs(t)
    }

    fun getPackageDetails(): String {
        return "${pkg.repo}/${pkg.name} ${pkg.version}"
    }
}
