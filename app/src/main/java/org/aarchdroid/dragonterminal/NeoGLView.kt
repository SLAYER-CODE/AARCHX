package org.aarchdroid.dragonterminal

import android.view.View
import org.aarchdroid.dragonterminal.xorg.NeoXorgViewClient

class NeoGLView(client: NeoXorgViewClient) : View(client.getContext()) {
    fun callNativeScreenVisibleRect(left: Int, top: Int, right: Int, bottom: Int) {
    }

    fun callNativeScreenKeyboardShown(shown: Int) {
    }

    fun onPause() {
    }

    fun onResume() {
    }

    fun exitApp() {
    }
}
