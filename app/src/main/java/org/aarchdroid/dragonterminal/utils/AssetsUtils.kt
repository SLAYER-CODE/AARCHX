package org.aarchdroid.dragonterminal.utils

import android.content.Context
import java.io.File
import java.io.InputStream

/**
 * @author kiva
 */
object AssetsUtils {
    fun extractAssetsDir(context: Context, dirName: String, extractDir: String) {
        val assets = context.assets
        assets.list(dirName)
                ?.map { File(extractDir, it) }
                ?.filter { true }
                ?.forEach { file ->
                    assets.open("$dirName/${file.name}").use {
                        FileUtils.writeFile(file, it)
                    }
                    file.setExecutable(true, false)
                }

    }

    fun openAssetsFile(context: Context, fileName: String) : InputStream {
        val assets = context.assets
        return assets.open(fileName)
    }
}