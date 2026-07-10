package org.aarchdroid.dragonterminal.backend

import android.net.LocalServerSocket
import android.net.LocalSocket

/**
 * Wrapper alrededor de LocalServerSocket que siempre usa socket abstracto
 * (namespace ANDROID). En Android, LocalServerSocket(nombre) automáticamente
 * crea un socket AF_UNIX abstracto con ese nombre.
 */
class LocalServerSocketExt(name: String) {
    private val impl = LocalServerSocket(name)

    fun accept(): LocalSocket = impl.accept()
    fun close() = impl.close()
}