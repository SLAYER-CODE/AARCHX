package org.aarchdroid.dragonterminal.frontend.config

import android.annotation.SuppressLint
import org.aarchdroid.AArchDroidApp

/**
 * @author kiva
 */
object NeoTermPath {
    @SuppressLint("SdCardPath")
    @JvmField var ROOT_PATH = AArchDroidApp.get().filesDir.absolutePath
    @JvmField val USR_PATH = "$ROOT_PATH/usr"
    @JvmField val HOME_PATH = "$ROOT_PATH/home"
    //@JvmField val APT_BIN_PATH = "$USR_PATH/bin/apt"
    //@JvmField val LIB_PATH = "$USR_PATH/lib"

    @JvmField val CUSTOM_PATH = "$HOME_PATH/.neoterm"
    @JvmField val NEOTERM_LOGIN_SHELL_PATH = "$CUSTOM_PATH/shell"
    @JvmField val EKS_PATH = "$CUSTOM_PATH/eks"
    @JvmField val EKS_DEFAULT_FILE = "$EKS_PATH/default.nl"
    @JvmField val FONT_PATH = "$CUSTOM_PATH/font"
    @JvmField val COLORS_PATH = "$CUSTOM_PATH/color"
    @JvmField val USER_SCRIPT_PATH = "$CUSTOM_PATH/script"
    @JvmField val PROFILE_PATH = "$CUSTOM_PATH/profile"


}