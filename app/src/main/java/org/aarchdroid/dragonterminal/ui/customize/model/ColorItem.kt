package org.aarchdroid.dragonterminal.ui.customize.model

import org.aarchdroid.dragonterminal.util.SortedListAdapter
import org.aarchdroid.AArchDroidApp
import org.aarchdroid.R
import org.aarchdroid.dragonterminal.component.colorscheme.NeoColorScheme

/**
 * @author kiva
 */
class ColorItem(var colorType: Int, var colorValue: String) : SortedListAdapter.ViewModel {
    override fun <T> isSameModelAs(t: T): Boolean {
        if (t is ColorItem) {
            return t.colorName == colorName
                    && t.colorValue == colorValue
                    && t.colorType == colorType
        }
        return false
    }

    override fun <T> isContentTheSameAs(t: T): Boolean {
        return isSameModelAs(t)
    }

    var colorName = AArchDroidApp.get().resources
            .getStringArray(R.array.color_item_names)[colorType - NeoColorScheme.COLOR_TYPE_BEGIN]
}