package org.aarchdroid.dragonterminal.ui.term

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.aarchdroid.*
import org.aarchdroid.dragonterminal.bridge.Bridge
import org.aarchdroid.dragonterminal.ui.term.getRecentTools
import org.aarchdroid.dragonterminal.ui.term.saveRecentTool
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import android.os.Handler
import android.os.Looper
import java.util.HashSet

class ToolCategoryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    private val categoryName: String = "",
    private val bannerResId: Int = 0,
    private val statsToolsCount: String = "0",
    private val categoryDbKey: String = ""
) : FrameLayout(context, attrs, defStyleAttr) {

    private var adapter: ToolAdapter? = null
    private val processingTools = HashSet<String>()
    private var layoutSet = false
    private var scrollListenerAttached = false
    private var hasRoot: Boolean? = null
    private var onDismissRequest: (() -> Unit)? = null
    private val exitCheckHandler = Handler(Looper.getMainLooper())
    private var exitCheckDone = false

    private val exitCheckRunnable = object : Runnable {
        override fun run() {
            if (exitCheckDone) return
            Log.d(TAG, "exitCheckRunnable: checking for exit files...")
            val stateDir = File(context.filesDir, "install-state")
            if (processToolExitFiles(context)) {
                Log.d(TAG, "exitCheckRunnable: found exit files, processing")
                exitCheckDone = true
                processStaleInstalls(stateDir)
                refreshStatusesAsync()
            } else {
                Log.d(TAG, "exitCheckRunnable: no exit files, scheduling next check")
                exitCheckHandler.postDelayed(this, 2000)
            }
        }
    }

    fun setOnDismissRequest(listener: () -> Unit) {
        onDismissRequest = listener
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        exitCheckDone = true
        exitCheckHandler.removeCallbacks(exitCheckRunnable)
    }

    init {
        val root = LayoutInflater.from(context).inflate(R.layout.dco_list_scaffold, this, true)

        root.findViewById<TextView>(R.id.title).text = categoryName
        root.findViewById<ImageView>(R.id.banner).setImageResource(bannerResId)
        root.findViewById<TextView>(R.id.stats_tools).text = statsToolsCount

        val list = root.findViewById<RecyclerView>(R.id.tool_list)
        list.layoutManager = LinearLayoutManager(context)

        val tools = buildToolList()
        adapter = ToolAdapter(tools, object : ToolAdapter.OnToolClickListener {
            override fun onToolClick(item: ToolItem) = handleCardClick(item)
            override fun onInstallClick(toolKey: String) = processInstallTool(toolKey)
            override fun onUninstallClick(toolKey: String) = this@ToolCategoryView.onUninstallClick(toolKey)
            override fun onLaunchTool(toolKey: String) = this@ToolCategoryView.onLaunchTool(toolKey)
        })
        list.adapter = adapter
        list.setHasFixedSize(true)

        processExitFiles()
        refreshStatusesAsync()
        exitCheckHandler.postDelayed(exitCheckRunnable, 2000)
        setupDynamicSizing(list)
    }

    private fun buildToolList(): List<ToolItem> {
        val ctx = context
        val db = ToolDatabase.getInstance()
        val infos = db.getToolsByCategory(categoryDbKey)
        if (infos.isEmpty()) {
            Log.w(TAG, "No tools in DB for category '$categoryDbKey', using fallback")
            return buildFallbackToolList(ctx)
        }
        val recent = getRecentTools(ctx)
        return infos.map { info ->
            ToolItem().apply {
                key = info.toolKey
                displayName = info.displayName ?: info.toolKey
                description = info.description ?: ""
                source = info.source ?: ""
                cmd = info.toolKey
                iconResId = resolveIcon(info, ctx)
            }
        }.sortedWith(compareBy<ToolItem> {
            val idx = recent.indexOf(it.key)
            if (idx >= 0) idx else Int.MAX_VALUE
        }.thenBy { it.displayName })
    }

    private fun buildFallbackToolList(ctx: Context): List<ToolItem> {
        val db = ToolDatabase.getInstance()
        val tools = db.getToolsByCategory(categoryDbKey)
        return tools.map { info ->
            ToolItem().apply {
                key = info.toolKey
                displayName = info.displayName ?: info.toolKey
                description = info.description ?: ""
                source = info.source ?: ""
                cmd = info.toolKey
                iconResId = resolveIcon(info, ctx)
            }
        }
    }

    fun refresh() {
        processExitFiles()
        refreshStatusesAsync()
    }

    private fun refreshStatusesAsync() {
        val cat = categoryDbKey
        Log.d(TAG, "refreshStatusesAsync: category=$cat")
        Thread {
            try {
                val db = ToolDatabase.getInstance()
                val statuses = db.getStatusMap(cat)
                val toolInfos = db.getToolInfoMap(cat)
                val sample = statuses.entries.take(5).joinToString { "${it.key}=${it.value}" }
                Log.d(TAG, "refreshStatusesAsync: statuses sample: $sample (total=${statuses.size})")
                post {
                    adapter?.updateCache(statuses, toolInfos)
                    adapter?.notifyDataSetChanged()
                    updateStatsSize()
                    Log.d(TAG, "refreshStatusesAsync: adapter updated on UI thread")
                }
            } catch (e: Exception) {
                Log.e(TAG, "refreshStatusesAsync error", e)
            }
        }.start()
    }

    private fun updateStatsSize() {
        val sizeView = findViewById<TextView>(R.id.stats_size) ?: return
        val toolsView = findViewById<TextView>(R.id.stats_tools) ?: return
        val compactView = findViewById<TextView>(R.id.stats_compact)
        val downView = findViewById<TextView>(R.id.stats_downloaded)
        val updView = findViewById<TextView>(R.id.stats_updated)

        val stats = ToolDatabase.getInstance().getCategoryStats(categoryDbKey)
        val totalSizeMb = stats?.installedSizeMb ?: 0
        sizeView.text = "${totalSizeMb}mb"
        sizeView.setTextColor(
            when {
                totalSizeMb == 0L -> Color.parseColor("#3D6B3D")
                totalSizeMb < 500 -> Color.parseColor("#B87333")
                else -> Color.parseColor("#8B0000")
            }
        )

        val h = toolsView.text.toString()
        val d = downView?.text?.toString() ?: "0"
        val a = updView?.text?.toString() ?: "-"

        val neon = Color.parseColor("#39FF14")
        val cyan = Color.parseColor("#00FFFF")
        val green = Color.parseColor("#90EE90")
        val orange = Color.parseColor("#FF8C00")

        val ssb = SpannableStringBuilder()
        var start = ssb.length; ssb.append("H:"); ssb.setSpan(ForegroundColorSpan(neon), start, ssb.length, 0)
        start = ssb.length; ssb.append(h); ssb.setSpan(ForegroundColorSpan(cyan), start, ssb.length, 0)
        start = ssb.length; ssb.append(" D:"); ssb.setSpan(ForegroundColorSpan(neon), start, ssb.length, 0)
        start = ssb.length; ssb.append(d); ssb.setSpan(ForegroundColorSpan(green), start, ssb.length, 0)
        start = ssb.length; ssb.append(" A:"); ssb.setSpan(ForegroundColorSpan(neon), start, ssb.length, 0)
        start = ssb.length; ssb.append(a); ssb.setSpan(ForegroundColorSpan(orange), start, ssb.length, 0)
        compactView?.text = ssb
    }

    private fun processExitFiles() {
        val found = processToolExitFiles(context)
        Log.d(TAG, "processExitFiles: processToolExitFiles returned $found")
        val stateDir = File(context.filesDir, "install-state")
        if (found) {
            processStaleInstalls(stateDir)
        }
        refreshStatusesAsync()
    }

    private fun processStaleInstalls(stateDir: File) {
        try {
            val db = ToolDatabase.getInstance()
            if (categoryDbKey.isEmpty()) return
            val statuses = db.getStatusMap(categoryDbKey)
            for ((toolKey, st) in statuses) {
                if (st != "installing" && st != "uninstalling") {
                    Log.d(TAG, "processStaleInstalls: $toolKey status=$st - skipping")
                    continue
                }
                val pendingFile = File(stateDir, "${toolKey}.pending")
                val exitFile = File(stateDir, "${toolKey}.exit")
                if (exitFile.exists()) { Log.d(TAG, "processStaleInstalls: $toolKey has exitFile, skipping"); continue }
                if (pendingFile.exists()) { Log.d(TAG, "processStaleInstalls: $toolKey has pendingFile, skipping"); continue }
                Log.d(TAG, "processStaleInstalls: $toolKey has no exit/pending, marking as stale")
                if (st == "installing") db.markFailed(toolKey, "Installation aborted or state lost")
                else db.markInstalled(toolKey)
            }
        } catch (e: Exception) {
            Log.e(TAG, "processStaleInstalls error", e)
        }
    }

    private fun processInstallTool(toolKey: String) {
        val nk = ToolDatabase.normalizeKey(toolKey)
        saveRecentTool(context, nk)
        if (!processingTools.add(nk)) {
            Log.d(TAG, "processInstallTool($toolKey) already processing — ignored")
            return
        }
        val installCmd = ToolDatabase.getInstance().getInstallCommand(nk)
        if (installCmd != null) {
            try {
                ToolDatabase.getInstance().markInstalling(nk, installCmd)
            } catch (e: Exception) {
                Log.e(TAG, "markInstalling failed", e)
                processingTools.remove(nk)
                return
            }
            File(context.filesDir, "install-state").mkdirs()
            try {
                File(context.filesDir, "install-state/${nk}.pending").createNewFile()
            } catch (_: Exception) {}
            val inline = buildInstallInline(nk, installCmd)
            runHackCmd(inline, 0, nk)
        } else {
            Log.d(TAG, "processInstallTool: no install command for $toolKey")
            Toast.makeText(context, "No install command for $toolKey", Toast.LENGTH_SHORT).show()
            processingTools.remove(nk)
        }
    }

    private fun onUninstallClick(toolKey: String) {
        val nk = ToolDatabase.normalizeKey(toolKey)
        saveRecentTool(context, nk)
        if (!processingTools.add(nk)) {
            Log.d(TAG, "onUninstallClick($toolKey) already processing — ignored")
            return
        }
        val cmd = ToolDatabase.getInstance().getUninstallCommand(nk)
        if (cmd != null) {
            ToolDatabase.getInstance().setStatus(nk, "uninstalling")
            File(context.filesDir, "install-state").mkdirs()
            try {
                File(context.filesDir, "install-state/${nk}.pending").createNewFile()
            } catch (_: Exception) {}
            val inline = buildUninstallInline(nk, cmd)
            runHackCmd(inline, 0, nk)
        } else {
            Log.d(TAG, "onUninstallClick: no uninstall command for $toolKey")
            Toast.makeText(context, "No uninstall command for $toolKey", Toast.LENGTH_SHORT).show()
            processingTools.remove(nk)
        }
    }

    private fun handleCardClick(item: ToolItem) {
        saveRecentTool(context, item.key)
        if (item.source == "github") {
            runHackCmd("cd /Herramientas/${item.key} && ls -la", item.iconResId)
        } else {
            runHackCmd(item.cmd, item.iconResId)
        }
    }

    private fun onLaunchTool(toolKey: String) {
        saveRecentTool(context, toolKey)
        val source = ToolDatabase.getInstance().getSource(toolKey)
        if (source == "github") {
            runHackCmd("cd /Herramientas/$toolKey && ls -la")
        } else {
            runHackCmd("$toolKey -h")
        }
    }

    private fun runHackCmd(cmd: String, iconResId: Int = 0, toolKey: String? = null) {
        Log.d(TAG, "runHackCmd: $cmd icon=$iconResId toolKey=$toolKey")

        if (hasRoot == null) {
            hasRoot = checkRoot()
        }

        if (hasRoot != true) {
            Toast.makeText(context, "Root no detectado", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Bridge.createExecuteIntent(cmd, iconResId)
        intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        if (toolKey != null) {
            intent.putExtra("tool_key", toolKey)
        }
        try {
            context.startActivity(intent)
            onDismissRequest?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "runHackCmd failed: ${e.message}", e)
        }
    }

    private fun checkRoot(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            os.writeBytes("id\n")
            os.writeBytes("exit\n")
            os.flush()
            var found = false
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line?.contains("uid=0") == true) {
                    found = true
                    break
                }
            }
            process.waitFor()
            found
        } catch (e: Exception) {
            Log.e(TAG, "Root check failed", e)
            false
        }
    }

    private fun buildInstallInline(toolKey: String, installCmd: String): String {
        val appDir = "/data/data/" + context.packageName + "/"
        val stateDir = "${appDir}files/install-state"
        val logFile = "$stateDir/$toolKey.log"
        val exitFile = "$stateDir/$toolKey.exit"

        val cmd = if (installCmd.startsWith("pacman ")) {
            installCmd.replaceFirst("^pacman ", "pacman --color always --disable-download-timeout ")
        } else installCmd

        val sb = StringBuilder()
        sb.append("export PATH=/usr/local/sbin:/usr/local/bin:/usr/bin:/usr/sbin:\$PATH && mkdir -p '$stateDir' && ")
        sb.append("echo; echo -e \"\\033[1;34m[AArchDroid]\\033[0m \\033[1;33mInstalando\\033[0m: $toolKey\"; echo; ")
        sb.append("($cmd; echo \$? > '$exitFile') 2>&1 | tee '$logFile'; ")
        sb.append("EC=\$(cat '$exitFile' 2>/dev/null); ")
        if (installCmd.startsWith("pacman")) {
            sb.append("if [ \"\$EC\" != \"0\" ]; then ")
            sb.append("echo -e \"\\033[1;33m  -\\033[0m Sync repos...\"; ")
            sb.append("(pacman --color always --disable-download-timeout -Sy; echo \$? > '$exitFile') 2>&1 | tee -a '$logFile'; ")
            sb.append("EC2=\$(cat '$exitFile' 2>/dev/null); ")
            sb.append("if [ \"\$EC2\" = \"0\" ]; then ")
            sb.append("($cmd; echo \$? > '$exitFile') 2>&1 | tee -a '$logFile'; ")
            sb.append("EC=\$(cat '$exitFile' 2>/dev/null); ")
            sb.append("else echo -e \"\\033[1;31m  -\\033[0m Sync failed\"; fi; fi; ")
        }
        sb.append("echo; echo ========================================; ")
        sb.append("if [ \"\$EC\" = \"0\" ]; then ")
        sb.append("echo -e \"\\033[1;32m  [AArchDroid] OK\\033[0m\"; ")
        sb.append("else ")
        sb.append("echo -e \"\\033[1;31m  [AArchDroid] FAILED (exit \$EC)\\033[0m\"; ")
        sb.append("fi")

        val raw = sb.toString()
        val escaped = raw.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("$", "\\$")
        return "sh -c \"$escaped\""
    }

    private fun buildUninstallInline(toolKey: String, uninstallCmd: String): String {
        val appDir = "/data/data/" + context.packageName + "/"
        val stateDir = "${appDir}files/install-state"
        val logFile = "$stateDir/$toolKey.log"
        val exitFile = "$stateDir/$toolKey.exit"

        val cmd = if (uninstallCmd.startsWith("pacman ")) {
            uninstallCmd.replaceFirst("^pacman ", "pacman --color always --disable-download-timeout ")
        } else uninstallCmd

        val uninstallMarker = "$stateDir/$toolKey.uninstall"
        val sb = StringBuilder()
        sb.append("export PATH=/usr/local/sbin:/usr/local/bin:/usr/bin:/usr/sbin:\$PATH && mkdir -p '$stateDir' && ")
        sb.append("echo; echo -e \"\\033[1;34m[AArchDroid]\\033[0m \\033[1;33mDesinstalando\\033[0m: $toolKey\"; echo; ")
        sb.append("($cmd; echo \$? > '$exitFile') 2>&1 | tee '$logFile'; ")
        sb.append("EC=\$(cat '$exitFile' 2>/dev/null); ")
        sb.append("if [ \"\$EC\" != \"0\" ] && ! pacman -Q '$toolKey' 2>/dev/null; then EC=0; echo \"\$EC\" > '$exitFile'; fi; ")
        sb.append(": > '$uninstallMarker' && ")
        sb.append("echo; echo ========================================; ")
        sb.append("if [ \"\$EC\" = \"0\" ]; then ")
        sb.append("echo -e \"\\033[1;32m  [AArchDroid] Uninstall OK\\033[0m\"; ")
        sb.append("else ")
        sb.append("echo -e \"\\033[1;31m  [AArchDroid] Uninstall FAILED (exit \$EC)\\033[0m\"; ")
        sb.append("fi")

        val raw = sb.toString()
        val escaped = raw.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("$", "\\$")
        return "sh -c \"$escaped\""
    }

    private fun setupDynamicSizing(@Suppress("UNUSED_PARAMETER") list: RecyclerView) {
        if (layoutSet) return
        layoutSet = true

        val dm = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        wm.defaultDisplay.getMetrics(dm)
        val maxH = (dm.heightPixels * 0.85).toInt()

        viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                private var done = false
                override fun onPreDraw(): Boolean {
                    if (done) return true
                    val rv = findViewById<RecyclerView>(R.id.tool_list) ?: return true
                    if (rv.adapter == null) return true
                    if (rv.height == 0) return true
                    done = true

                    val density = resources.displayMetrics.density
                    val occupied = (56 * density + 2 * density + 12 * density + 16 * density).toInt()
                    var rvMax = maxH - occupied
                    if (rvMax < 0) rvMax = 0

                    if (rv.height > rvMax) {
                        val lp = rv.layoutParams
                        lp.height = rvMax
                        rv.layoutParams = lp
                        rv.post {
                            setupScrollIndicator(rv)
                            updateStatsSize()
                        }
                        viewTreeObserver.removeOnPreDrawListener(this)
                        return false
                    }

                    setupScrollIndicator(rv)
                    updateStatsSize()
                    viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            })
    }

    private fun setupScrollIndicator(rv: RecyclerView) {
        if (scrollListenerAttached) return
        scrollListenerAttached = true

        val indicator = findViewById<View>(R.id.scroll_indicator) ?: return
        val thumb = findViewById<View>(R.id.scroll_thumb) ?: return

        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateScrollThumb(recyclerView, indicator, thumb)
            }
        })
        rv.post { updateScrollThumb(rv, indicator, thumb) }
    }

    private fun updateScrollThumb(rv: RecyclerView, indicator: View, thumb: View) {
        val lm = rv.layoutManager as? LinearLayoutManager ?: return
        val totalItems = lm.itemCount
        val firstVisible = lm.findFirstCompletelyVisibleItemPosition()
        val lastVisible = lm.findLastCompletelyVisibleItemPosition()

        val fv = if (firstVisible == -1) lm.findFirstVisibleItemPosition() else firstVisible
        val lv = if (lastVisible == -1) lm.findLastVisibleItemPosition() else lastVisible

        var visibleItems = lv - fv + 1
        if (visibleItems <= 0) visibleItems = 1
        if (totalItems <= 0) return

        if (visibleItems >= totalItems) {
            indicator.visibility = View.GONE
            return
        }
        indicator.visibility = View.VISIBLE

        val progress = (fv.toFloat() / Math.max(1, totalItems - visibleItems))
            .coerceIn(0f, 1f)
        val visibleRatio = visibleItems.toFloat() / totalItems

        val trackWidth = indicator.width
        if (trackWidth <= 0) return

        thumb.pivotX = 0f
        thumb.scaleX = Math.max(visibleRatio, dpToPx(8).toFloat() / trackWidth)

        val available = trackWidth * (1 - visibleRatio)
        thumb.translationX = available * progress
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun resolveIcon(info: ToolInfo, ctx: Context): Int {
        val drawableName = if (!info.drawable.isNullOrEmpty()) {
            info.drawable
        } else {
            info.toolKey.replace('-', '_')
        }
        return ctx.resources.getIdentifier(drawableName, "drawable", ctx.packageName)
            .takeIf { it != 0 } ?: R.drawable.andraxtool
    }

    companion object {
        private const val TAG = "ToolCategoryView"
    }
}

internal fun processToolExitFiles(context: Context): Boolean {
    val TAG = "ToolCatView"
    try {
        val stateDir = File(context.filesDir, "install-state")
        Log.d(TAG, "processToolExitFiles: stateDir=$stateDir exists=${stateDir.exists()}")
        if (!stateDir.exists()) return false
        val files = stateDir.listFiles() ?: return false
        val fileNames = files.map { it.name }
        Log.d(TAG, "processToolExitFiles: files in dir: $fileNames")
        var changed = false
        for (f in files) {
            val name = f.name
            if (!name.endsWith(".exit")) continue
            val toolKey = name.substring(0, name.length - 5)
            Log.d(TAG, "processToolExitFiles: processing exit file for $toolKey")
            try {
                val content = String(FileInputStream(f).readBytes()).trim()
                val exitCode = content.toInt()
                Log.d(TAG, "processToolExitFiles: $toolKey exitCode=$exitCode")
                val isUninstall = File(stateDir, "${toolKey}.uninstall").exists()
                if (isUninstall) {
                    Log.d(TAG, "processToolExitFiles: $toolKey is uninstall")
                    if (exitCode == 0) ToolDatabase.getInstance().markUninstalled(toolKey)
                    else ToolDatabase.getInstance().markInstalled(toolKey)
                    File(stateDir, "${toolKey}.uninstall").delete()
                } else {
                    if (exitCode == 0) {
                        Log.d(TAG, "processToolExitFiles: calling markInstalled($toolKey)")
                        ToolDatabase.getInstance().markInstalled(toolKey)
                    } else {
                        val logFile = File(stateDir, "${toolKey}.log")
                        val error = if (logFile.exists()) {
                            val logBytes = FileInputStream(logFile).readBytes()
                            val s = String(logBytes)
                            if (s.length > 1000) s.substring(s.length - 1000) else s
                        } else ""
                        Log.d(TAG, "processToolExitFiles: calling markFailed($toolKey)")
                        ToolDatabase.getInstance().markFailed(toolKey, error)
                    }
                }
                f.delete()
                File(stateDir, "${toolKey}.log").delete()
                changed = true
                Log.d(TAG, "processToolExitFiles: $toolKey processed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "processExitFiles: error for $toolKey", e)
            }
        }
        Log.d(TAG, "processToolExitFiles: returning $changed")
        return changed
    } catch (e: Exception) {
        Log.e(TAG, "processExitFiles error", e)
        return false
    }
}
