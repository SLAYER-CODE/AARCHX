package org.aarchdroid.dragonterminal.ui.term

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.*
import android.content.pm.PackageManager
import android.util.Log
import android.content.res.Configuration
import android.net.Uri
import android.os.*
import android.preference.PreferenceManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Color
import android.graphics.PorterDuff
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

import org.aarchdroid.AArchDroidApp
import org.aarchdroid.R
import android.content.DialogInterface
import org.aarchdroid.dragonterminal.backend.ChrootManager
import org.aarchdroid.dragonterminal.backend.TerminalSession
import org.aarchdroid.dragonterminal.component.profile.ProfileComponent
import org.aarchdroid.dragonterminal.data.CommandInterceptor
import org.aarchdroid.dragonterminal.data.SessionHistory
import org.aarchdroid.dragonterminal.frontend.component.ComponentManager
import org.aarchdroid.dragonterminal.frontend.config.NeoPermission
import org.aarchdroid.dragonterminal.frontend.config.NeoPreference
import org.aarchdroid.dragonterminal.frontend.session.shell.ShellParameter
import org.aarchdroid.dragonterminal.frontend.session.shell.ShellProfile
import org.aarchdroid.dragonterminal.frontend.session.shell.client.TermSessionCallback
import org.aarchdroid.dragonterminal.frontend.session.shell.client.TermViewClient
import org.aarchdroid.dragonterminal.frontend.session.shell.client.event.*
import org.aarchdroid.dragonterminal.frontend.session.shell.client.event.CameraPermissionEvent
import org.aarchdroid.dragonterminal.frontend.session.xorg.XParameter
import org.aarchdroid.dragonterminal.frontend.session.xorg.XSession
import org.aarchdroid.dragonterminal.floatui.FloatService
import org.aarchdroid.dragonterminal.services.NeoTermService
import org.aarchdroid.dragonterminal.ui.settings.SettingActivity
import org.aarchdroid.dragonterminal.ui.term.tab.NeoTab
import org.aarchdroid.dragonterminal.ui.pm.PackageManagerActivity
import org.aarchdroid.dragonterminal.ui.term.tab.NeoTabDecorator
import org.aarchdroid.dragonterminal.ui.term.tab.TermTab
import org.aarchdroid.dragonterminal.ui.term.tab.XSessionTab

import org.aarchdroid.dragonterminal.utils.FullScreenHelper
import org.aarchdroid.dragonterminal.utils.RangedInt
import de.mrapp.android.tabswitcher.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.*
import java.lang.Process
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale


class NeoTermActivity : AppCompatActivity(), ServiceConnection, SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        const val KEY_NO_RESTORE = "no_restore"
        const val REQUEST_SETUP = 22313
        const val REQUEST_CAMERA = 10088
        const val ACTION_ANCHOR = "aarchdroid.terminal.action.anchor"
        const val INTERNA_TARGET = "/data/local/aarchdroid/root/Interna"
        const val EXTERNA_TARGET = "/data/local/aarchdroid/root/Externa"

        private data class ToolItem(val name: String, val icon: Int, val activityClass: String)
        private val TOOLS = listOf(
            ToolItem("Information Gathering", R.drawable.information_gathering, "org.aarchdroid.Dco_Information_Gathering"),
            ToolItem("Scanning", R.drawable.scanning, "org.aarchdroid.Dco_Scanning"),
            ToolItem("Packet Crafting", R.drawable.packet_crafting, "org.aarchdroid.Dco_Packet_Crafting"),
            ToolItem("Network Hacking", R.drawable.networkhacking, "org.aarchdroid.Dco_network_hacking"),
            ToolItem("WebSite Hacking", R.drawable.websitehacking, "org.aarchdroid.Dco_website_hacking"),
            ToolItem("Password Hacking", R.drawable.passwordhacking, "org.aarchdroid.Dco_Password_Hacking"),
            ToolItem("Wireless Hacking", R.drawable.wirelesshacking, "org.aarchdroid.Dco_Wireless_Hacking"),
            ToolItem("Exploitation", R.drawable.exploit, "org.aarchdroid.Dco_exploitation"),
            ToolItem("Stress Testing", R.drawable.stress_testing, "org.aarchdroid.Dco_stress_testing"),
            ToolItem("Phishing", R.drawable.phishing, "org.aarchdroid.Dco_phishing"),
            ToolItem("VoIP/3G/4G", R.drawable.voiphopper, "org.aarchdroid.Dco_voip_3g_4g"),
            ToolItem("ICS/SCADA/IIoT/IoT", R.drawable.ics, "org.aarchdroid.Dco_ics_scada_iot"),
            ToolItem("Mainframes", R.drawable.mainframe, "org.aarchdroid.Dco_Mainframe"),
            ToolItem("Bug Bounty", R.drawable.bugbounty, "org.aarchdroid.Dco_bug_bounty"),
            ToolItem("C2/RAT", R.drawable.c2, "org.aarchdroid.Dco_c2_rat"),
            ToolItem("MacOS/iPhone", R.drawable.mobilenethacking, "org.aarchdroid.Dco_macos_iphone")
        )
    }

    private lateinit var errorDialog: Dialog
    private var toolsDialog: android.app.AlertDialog? = null

    lateinit var tabSwitcher: TabSwitcher
    private lateinit var fullScreenHelper: FullScreenHelper
    lateinit var toolbar: Toolbar

    var addSessionListener = createAddSessionListener()
    private var termService: NeoTermService? = null

    val fullscreen = NeoPreference.isFullScreenEnabled()
    var tshow = false

    @Volatile
    var rootAvailable = false

    @Volatile
    var transferringHandle: String? = null

    private var pendingAnchorSession: TerminalSession? = null

    private var sessionHistoryAdapter: SessionHistoryAdapter? = null
    private var currentHistoryOffset = 0
    private var isLoadingMore = false
    private var earlyTerminalPlaceholder: View? = null
    private val tabSessionMap = HashMap<String, String>() // TerminalSession.handle -> sessionId
    private var tabSwitcherListener: TabSwitcherListener? = null
    private var forceHistoryVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val d = toolsDialog
                if (d != null && d.isShowing) {
                    d.dismiss()
                    toolsDialog = null
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
        Log.d("AArchDroid", "NeoTermActivity: onCreate() — entering terminal activity")
        Log.d("AArchDroid", "NeoTermActivity: intent action=" + (intent?.action ?: "null") +
                " extras=" + (intent?.extras?.keySet()?.joinToString() ?: "null") +
                " flags=" + (intent?.flags?.toString() ?: "null") +
                " component=" + (intent?.component?.className ?: "null"))

        lifecycleScope.launch(Dispatchers.IO) {
            changehostname("AARCHX")
        }

        NeoPermission.initAppPermission(this, NeoPermission.REQUEST_APP_PERMISSION)
        NeoPermission.initPostNotificationsPermission(this)

        if (fullscreen) {
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }

        if (Build.VERSION.SDK_INT < 33) {
            val SDCARD_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1
            if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d("AArchDroid", "NeoTermActivity: requesting WRITE_EXTERNAL_STORAGE permission")
                ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        SDCARD_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE
                )
            }
        }

        if (intent?.action == ACTION_ANCHOR) {
            pendingAnchorSession = AArchDroidApp.transferredSession
            AArchDroidApp.transferredSession = null
            Log.d("AArchDroid", "NeoTermActivity: ACTION_ANCHOR — pending session=${pendingAnchorSession != null}")
        }

        setContentView(R.layout.ui_main)

        val terminalContainer = findViewById<FrameLayout>(R.id.terminal_container)
        earlyTerminalPlaceholder = View(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT)
            id = View.generateViewId()
        }
        terminalContainer.addView(earlyTerminalPlaceholder)

        toolbar = findViewById(R.id.terminal_toolbar)
        setSupportActionBar(toolbar)

        fullScreenHelper = FullScreenHelper.injectActivity(this, fullscreen, peekRecreating())
        fullScreenHelper.setKeyBoardListener(object : FullScreenHelper.KeyBoardListener {
            override fun onKeyboardChange(isShow: Boolean, keyboardHeight: Int) {
                if (tabSwitcher.selectedTab is TermTab) {
                    val tab = tabSwitcher.selectedTab as TermTab
                    toggleToolbar(tab.toolbar, !isShow)
                }
            }
        })

        tabSwitcher = findViewById(R.id.tab_switcher)
        tabSwitcher.decorator = NeoTabDecorator(this)
        ViewCompat.setOnApplyWindowInsetsListener(tabSwitcher, createWindowInsetsListener())
        tabSwitcher.showToolbars(false)

        Log.d("AArchDroid", "NeoTermActivity: starting and binding NeoTermService")
        val serviceIntent = Intent(this, NeoTermService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, this, 0)

        if (savedInstanceState == null) {
            val extras = intent.extras
            if (extras != null) {
                val method = extras.getString("recfromshort")
                Log.d("AArchDroid", "NeoTermActivity: extras found, recfromshort=" + (method ?: "null"))
            }
        }
    }

    private fun toggleToolbar(toolbar: Toolbar?, visible: Boolean) {
        if (toolbar == null) {
            return
        }

        if (NeoPreference.isFullScreenEnabled() || NeoPreference.isHideToolbarEnabled()) {
            val toolbarHeight = toolbar.height.toFloat()
            val translationY = if (visible) 0.toFloat() else -toolbarHeight
            if (visible) {
                toolbar.visibility = View.VISIBLE
                toolbar.animate()
                        .translationY(translationY)
                        .start()
                tshow = true
            } else {
                toolbar.animate()
                        .translationY(translationY)
                        .withEndAction {
                            toolbar.visibility = View.GONE
                        }
                        .start()
                tshow = false
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        TabSwitcher.setupWithMenu(tabSwitcher, toolbar.menu, {
            if (!tabSwitcher.isSwitcherShown) {
                val imm = this@NeoTermActivity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                if (imm.isActive && tabSwitcher.selectedTab is TermTab) {
                    val tab = tabSwitcher.selectedTab as TermTab
                    tab.requireHideIme()
                }
                toggleSwitcher(showSwitcher = true, easterEgg = true)
            } else {
                toggleSwitcher(showSwitcher = false, easterEgg = true)
            }
        })
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.menu_item_mountsdcard)?.title =
            if (isMounted(INTERNA_TARGET)) "Interna Unmount" else "Interna"
        menu?.findItem(R.id.menu_item_mount_external)?.title =
            if (isMounted(EXTERNA_TARGET)) "Externa Unmount" else "Externa"
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item?.itemId) {
            R.id.menu_item_settings -> {
                startActivity(Intent(this, SettingActivity::class.java))
                true
            }


            R.id.menu_item_recovery -> {

                val oldshell = NeoPreference.getLoginShellName()

                NeoPreference.setLoginShellName("/system/bin/sh")

                addNewSession()

                NeoPreference.setLoginShellName(oldshell)

                true
            }

            R.id.menu_item_mountsdcard -> {
                val target = INTERNA_TARGET
                if (isMounted(target)) {
                    suRunGlobal("umount -l $target")
                    item.title = "Interna"
                    Toast.makeText(this, "Interna desmontada", Toast.LENGTH_SHORT).show()
                } else {
                    suRun("/data/data/org.aarchdroid/files/bin/busybox mkdir -p $target")
                    suRunGlobal("/data/data/org.aarchdroid/files/bin/busybox mount -o bind /sdcard $target")
                    item.title = "Interna Unmount"
                    Toast.makeText(this, "Interna montada", Toast.LENGTH_SHORT).show()
                }
                true
            }

            R.id.menu_item_mount_external -> {
                val target = EXTERNA_TARGET
                if (isMounted(target)) {
                    suRunGlobal("umount -l $target")
                    item.title = "Externa"
                    Toast.makeText(this, "Externa desmontada", Toast.LENGTH_SHORT).show()
                } else {
                    val extSd = findExternalSd()
                    if (extSd == null) {
                        Toast.makeText(this, "no se detecto tarjeta externa", Toast.LENGTH_SHORT).show()
                    } else {
                        suRun("/data/data/org.aarchdroid/files/bin/busybox mkdir -p $target")
                        suRunGlobal("/data/data/org.aarchdroid/files/bin/busybox mount -o bind $extSd $target")
                        item.title = "Externa Unmount"
                        Toast.makeText(this, "Externa montada", Toast.LENGTH_SHORT).show()
                    }
                }
                true
            }

            R.id.menu_item_new_tab -> {
                addNewSession()
                true
            }

            R.id.dco_menu -> {
                val anchor = toolbar.findViewById<View>(R.id.dco_menu) ?: toolbar
                showToolsPopup(anchor)
                true
            }

            R.id.menu_item_clear_logs -> {
                SessionHistory.clearAll(this)
                sessionHistoryAdapter?.updateData(SessionHistory.ensure(this).sessions)
                updatePlaceholderVisibility()
                true
            }

            R.id.menu_item_packages -> {
                startActivity(Intent(this, PackageManagerActivity::class.java))
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showToolsPopup(anchor: View) {
        val context = ContextThemeWrapper(this, R.style.Theme_CompactGreenPopup)
        val dm = resources.displayMetrics
        val px12 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, dm).toInt()
        val px16 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, dm).toInt()

        var popup: PopupWindow? = null

        val listView = ListView(context).apply {
            background = ContextCompat.getDrawable(context, R.drawable.popup_menu_green_border)
            verticalScrollbarThumbDrawable = ColorDrawable(Color.parseColor("#39FF14"))
            verticalScrollbarTrackDrawable = ColorDrawable(Color.TRANSPARENT)
            adapter = object : BaseAdapter() {
                override fun getCount() = TOOLS.size
                override fun getItem(p: Int) = TOOLS[p]
                override fun getItemId(p: Int) = p.toLong()
                override fun getView(p: Int, v: View?, parent: ViewGroup): View {
                    val tool = TOOLS[p]
                    val icon = ContextCompat.getDrawable(context, tool.icon)?.mutate()
                    icon?.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
                    val iconSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, dm).toInt()
                    icon?.setBounds(0, 0, iconSize, iconSize)
                    val tv = (v as? TextView) ?: TextView(context).apply {
                        setPadding(px16, px12, px16, px12)
                        compoundDrawablePadding = px12
                        setTextColor(Color.WHITE)
                        textSize = 14f
                    }
                    tv.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    tv.setCompoundDrawablesRelative(icon, null, null, null)
                    tv.text = tool.name
                    return tv
                }
            }
            onItemClickListener = AdapterView.OnItemClickListener { _, _, p, _ ->
                val tool = TOOLS[p]
                val simpleName = tool.activityClass.substringAfterLast('.')
                val dbKey = simpleName.removePrefix("Dco_").lowercase()

                val toolView = ToolCategoryView(
                    context = this@NeoTermActivity,
                    categoryName = tool.name,
                    bannerResId = tool.icon,
                    statsToolsCount = "0",
                    categoryDbKey = dbKey
                )
                toolView.setOnDismissRequest {
                    toolsDialog?.dismiss()
                }
                toolView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                toolView.setBackgroundColor(Color.parseColor("#CC111111"))

                val dm = resources.displayMetrics
                AlertDialog.Builder(this@NeoTermActivity)
                    .setView(toolView)
                    .setCancelable(true)
                    .setOnDismissListener { toolsDialog = null }
                    .show()
                    .also { dialog ->
                        toolsDialog = dialog
                        dialog.window?.setLayout(
                            (dm.widthPixels * 0.95).toInt(),
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }
                popup?.dismiss()
            }
            divider = null
            dividerHeight = 0
        }

        listView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val contentWidth = listView.measuredWidth

        popup = PopupWindow(listView, contentWidth, WindowManager.LayoutParams.WRAP_CONTENT, false).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
        }

        val xoff = (anchor.width - contentWidth) / 2
        popup.showAsDropDown(anchor, xoff, 0)

        val tab = tabSwitcher.selectedTab
        if (tab is TermTab) {
            tab.termData.termView?.let { view ->
                Handler(Looper.getMainLooper()).postDelayed({
                    view.requestFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                }, 100)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d("NeoTermAct", "onPause: tabCount=${tabSwitcher.count}")
        val tab = tabSwitcher.selectedTab as NeoTab?
        tab?.onPause()
    }

    override fun onResume() {
        super.onResume()
        processToolExitFiles(this)
        Log.d("NeoTermAct", "onResume: tabCount=${tabSwitcher.count}, selectedTab=null? ${tabSwitcher.selectedTab == null}, termView=null? ${(tabSwitcher.selectedTab as? TermTab)?.termData?.termView == null}")

        try {

            PreferenceManager.getDefaultSharedPreferences(this)
                    .registerOnSharedPreferenceChangeListener(this)
            if (tabSwitcherListener == null) {
                tabSwitcherListener = object : TabSwitcherListener {
                    override fun onSwitcherShown(tabSwitcher: TabSwitcher) {
                        toolbar.setBackgroundResource(android.R.color.black)
                    }

                    override fun onSwitcherHidden(tabSwitcher: TabSwitcher) {
                        toolbar.setBackgroundResource(R.color.black_fuck)
                        updateExtraKeysButtonStates()
                        Handler(Looper.getMainLooper()).postDelayed({
                            val tab = tabSwitcher.selectedTab
                            if (tab is TermTab) {
                                tab.termData.termView?.let { view ->
                                    view.requestFocus()
                                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                    imm.restartInput(view)
                                }
                            }
                        }, 0)
                    }

                    override fun onSelectionChanged(tabSwitcher: TabSwitcher, selectedTabIndex: Int, selectedTab: Tab?) {
                        if (selectedTab is TermTab && selectedTab.termData.termSession != null) {
                            NeoPreference.storeCurrentSession(selectedTab.termData.termSession!!)
                            if (!tabSwitcher.isSwitcherShown) {
                                selectedTab.termData.termView?.let { view ->
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        view.requestFocus()
                                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                        imm.restartInput(view)
                                    }, 300)
                                }
                            }
                        }
                    }

                    override fun onTabAdded(tabSwitcher: TabSwitcher, index: Int, tab: Tab, animation: Animation) {
                        update_colors()
                        updatePlaceholderVisibility()
                    }

                    override fun onTabRemoved(tabSwitcher: TabSwitcher, index: Int, tab: Tab, animation: Animation) {
                        Log.d("NeoTermAct", "onTabRemoved idx=$index type=${tab::class.simpleName}")
                        if (tab is TermTab) {
                            val session = tab.termData.termSession
                            val isTransfer = session != null && session.mHandle == this@NeoTermActivity.transferringHandle
                            Log.d("NeoTermAct", "onTabRemoved session=${session?.mHandle} isTransfer=$isTransfer transferringHandle=${this@NeoTermActivity.transferringHandle}")
                            if (isTransfer) {
                                // Transfer to float: don't kill session, don't close history
                                this@NeoTermActivity.transferringHandle = null
                                val taken = termService?.takeSession(session!!.mHandle)
                                Log.d("NeoTermAct", "takeSession returned: ${taken != null}")
                                // Mark exit destiny as float
                                CommandInterceptor.getContext(session!!.mHandle)?.let { ctx ->
                                    SessionHistory.updateTerminalDestiny(this@NeoTermActivity, ctx.terminalId, "flotante")
                                }
                                AArchDroidApp.transferredSession = taken
                                if (taken != null) {
                                    val intent = Intent(this@NeoTermActivity, FloatService::class.java)
                                        .setAction(FloatService.ACTION_TAKEOVER)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    Log.d("NeoTermAct", "starting FloatService with ACTION_TAKEOVER")
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        startForegroundService(intent)
                                    } else {
                                        startService(intent)
                                    }
                                } else {
                                    Log.w("NeoTermAct", "takeSession returned null!")
                                }
                            } else {
                                // Normal close: kill session and close history
                                if (session != null) {
                                    CommandInterceptor.getContext(session.mHandle)?.let { ctx ->
                                        lifecycleScope.launch {
                                            withContext(Dispatchers.IO) {
                                                SessionHistory.updateTerminalDestiny(this@NeoTermActivity, ctx.terminalId, "cerrada")
                                            }
                                        }
                                    }
                                    val sid = tabSessionMap.remove(session.mHandle)
                                    if (sid != null) {
                                        lifecycleScope.launch {
                                            withContext(Dispatchers.IO) {
                                                SessionHistory.closeSession(this@NeoTermActivity, sid)
                                            }
                                            sessionHistoryAdapter?.updateData(SessionHistory.getHistory(this@NeoTermActivity).sessions)
                                        }
                                    }
                                    CommandInterceptor.unregisterSession(session.mHandle)
                                }
                                SessionRemover.removeSession(termService, tab)
                            }
                        } else if (tab is XSessionTab) {
                            SessionRemover.removeXSession(termService, tab)
                        }
                        updatePlaceholderVisibility()
                    }

                    override fun onAllTabsRemoved(tabSwitcher: TabSwitcher, tabs: Array<out Tab>, animation: Animation) {
                        // Reload session history from disk after all tabs closed
                        val h = SessionHistory.getHistory(this@NeoTermActivity)
                        sessionHistoryAdapter?.updateData(h.sessions)
                        updatePlaceholderVisibility()
                    }
                }.also { tabSwitcher.addListener(it) }
            }
            val tab = tabSwitcher.selectedTab as NeoTab?
            tab?.onResume()

            if (NeoPreference.isImeVisible()) {
                Handler(Looper.getMainLooper()).postDelayed({
                    val resumeTab = tabSwitcher.selectedTab
                    if (resumeTab is TermTab) {
                        resumeTab.termData.termView?.let { view ->
                            view.requestFocus()
                            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.showSoftInput(view, InputMethodManager.SHOW_FORCED)
                        }
                    }
                }, 100)
            }
            updateExtraKeysButtonStates()

        } catch (e: Exception) {

        }


    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        val tab = tabSwitcher.selectedTab as NeoTab?
        tab?.onStart()
    }

    override fun onStop() {
        super.onStop()
        // After stopped, window locations may changed
        // Rebind it at next time.
        forEachTab<TermTab> { it.resetAutoCompleteStatus() }
        val tab = tabSwitcher.selectedTab as NeoTab?
        tab?.onStop()
        EventBus.getDefault().unregister(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        val tab = tabSwitcher.selectedTab as NeoTab?
        tab?.onDestroy()
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this)
        tabSwitcherListener?.let { tabSwitcher.removeListener(it) }

        // Close all remaining session history records on IO
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                tabSessionMap.forEach { (handle, sid) ->
                    CommandInterceptor.getContext(handle)?.let { ctx ->
                        SessionHistory.updateTerminalDestiny(this@NeoTermActivity, ctx.terminalId, "cerrada")
                    }
                    SessionHistory.closeSession(this@NeoTermActivity, sid)
                }
            }
            SessionHistory.saveNow(this@NeoTermActivity)
        }
        tabSessionMap.clear()

        NeoTabDecorator.stopCameraServer()

        if (termService != null) {
            termService = null
        }
        try {
            unbindService(this)
        } catch (e: Exception) {
            Log.w("AArchDroid", "NeoTermActivity: unbindService failed — " + e.message)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val tab = tabSwitcher.selectedTab as NeoTab?
        tab?.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val termTab = tabSwitcher.selectedTab
            if (termTab is TermTab) {
                termTab.termData.termView?.let { view ->
                    view.updateSize()
                    view.invalidate()
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (event?.action == KeyEvent.ACTION_DOWN && tabSwitcher.isSwitcherShown && tabSwitcher.count > 0) {
                    toggleSwitcher(showSwitcher = false, easterEgg = false)
                    return true
                }
            }
            KeyEvent.KEYCODE_MENU -> {
                if (toolbar.isOverflowMenuShowing) {
                    toolbar.hideOverflowMenu()
                } else {
                    toolbar.showOverflowMenu()
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            NeoPermission.REQUEST_APP_PERMISSION -> {
                if (grantResults.isEmpty()
                        || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    AlertDialog.Builder(this).setMessage(R.string.permission_denied)
                            .setPositiveButton(android.R.string.ok, { _: DialogInterface, _: Int ->
                                finish()
                            })
                            .show()
                }
                return
            }
            NeoPermission.REQUEST_NOTIFICATION_PERMISSION -> {
                val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                Log.d("AArchDroid", "onRequestPermissionsResult: POST_NOTIFICATIONS granted=$granted")
            }
            REQUEST_CAMERA -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("AArchDroid", "CAMERA permission granted, retrying camera")
                    NeoTabDecorator.retryCamera()
                } else {
                    Log.w("AArchDroid", "CAMERA permission denied")
                }
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == getString(R.string.key_ui_fullscreen)) {
            setFullScreenMode(NeoPreference.isFullScreenEnabled())
        } else if (key == getString(R.string.key_customization_color_scheme)) {
            if (tabSwitcher.count > 0) {
                val tab = tabSwitcher.selectedTab
                if (tab is TermTab) {
                    tab.updateColorScheme()
                }
            }
        } else if (key == getString(R.string.key_general_disable_logs)) {
            updatePlaceholderVisibility()
        } else if (key == getString(R.string.key_ui_cursor_blink)) {
            Log.d("NeoTermAct", "blink pref changed: enabled=${NeoPreference.isCursorBlinkEnabled()}, tabCount=${tabSwitcher.count}")
            for (i in 0 until tabSwitcher.count) {
                val tab = tabSwitcher.getTab(i)
                if (tab is TermTab) {
                    val tv = tab.termData.termView
                    Log.d("NeoTermAct", "blink: tab=$i, termView=null? ${tv == null}, parent=null? ${tv?.parent == null}")
                    tv?.setCursorBlinkEnabled(NeoPreference.isCursorBlinkEnabled())
                }
            }
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        if (termService != null) {
            finish()
        }
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        Log.d("AArchDroid", "NeoTermActivity: onServiceConnected — service bound")
        termService = (service as NeoTermService.NeoTermBinder).service
        if (termService == null) {
            Log.e("AArchDroid", "NeoTermActivity: termService is null — finishing")
            finish()
            return
        }

        if (!isRecreating()) {
            Log.d("AArchDroid", "NeoTermActivity: service connected — entering main terminal")
            enterMain()
            loadSessionHistoryAsync()
            update_colors()
            updatePlaceholderVisibility()
            get_motherfucker_battery()
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                Log.d("AArchDroid", "NeoTermActivity: notifications disabled — continuing anyway")
            }
        } else {
            Log.d("AArchDroid", "NeoTermActivity: onServiceConnected but recreating — skipping asset extraction")
        }

        pendingAnchorSession?.let { session ->
            processPendingAnchor()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("AArchDroid", "NeoTermActivity: onNewIntent — action=" + (intent.action ?: "null"))
        if (intent.action == ACTION_ANCHOR) {
            pendingAnchorSession = AArchDroidApp.transferredSession
            AArchDroidApp.transferredSession = null
            Log.d("AArchDroid", "NeoTermActivity: onNewIntent — pending session=${pendingAnchorSession != null}")
            if (termService != null) {
                processPendingAnchor()
            }
        } else if (termService != null) {
            Log.d("AArchDroid", "NeoTermActivity: onNewIntent — picking up new sessions, count=" + termService!!.sessions.size)
            val stored = NeoPreference.getCurrentSession(termService)
            for (session in termService!!.sessions) {
                addNewSessionFromExisting(session)
            }
            if (stored != null) {
                switchToSession(stored)
            }
        }
    }

    private fun processPendingAnchor() {
        val session = pendingAnchorSession ?: return
        pendingAnchorSession = null
        Log.d("AArchDroid", "NeoTermActivity: handling anchored session")
        session.setChangeCallback(TermSessionCallback())
        termService!!.addExistingSession(session)
        addNewSessionFromExisting(session)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_SETUP -> {
                when (resultCode) {
                    Activity.RESULT_OK -> enterMain()
                    Activity.RESULT_CANCELED -> {
                        setSystemShellMode(true)
                        forceAddSystemSession()
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig == null) {
            return
        }

        // When rotate the screen, extra keys may get updated.
        forEachTab<NeoTab> {
            it.onConfigurationChanged(newConfig)
            if (it is TermTab) {

                it.resetStatus()

            }
        }
    }

    private fun forceAddSystemSession() {
        if (!tabSwitcher.isSwitcherShown) {
            toggleSwitcher(showSwitcher = true, easterEgg = false)
        }

        // Fore system shell mode to be enabled.
        try {

            addNewSession(null, true, createRevealAnimation())

        } catch (e: Exception) {

            addNewSession(null, true, createRevealAnimation())

        }
    }

    private fun enterMain() {
        Log.d("AArchDroid", "NeoTermActivity: enterMain() — sessions count=" + termService!!.sessions.size)
        setSystemShellMode(false)

        if (!termService!!.sessions.isEmpty()) {
            val lastSession = getStoredCurrentSessionOrLast()
            Log.d("AArchDroid", "NeoTermActivity: restoring " + termService!!.sessions.size + " existing sessions")

            for (session in termService!!.sessions) {
                Log.d("AArchDroid", "NeoTermActivity: iterating session handle=" + session.mHandle + " title=" + session.mSessionName)
                addNewSessionFromExisting(session)
            }
            Log.d("AArchDroid", "NeoTermActivity: lastSession=" + (lastSession?.mHandle ?: "null"))

            for (session in termService!!.xSessions) {
                addXSession(session)
            }

            if (intent?.action == Intent.ACTION_RUN) {
                Log.d("AArchDroid", "NeoTermActivity: ACTION_RUN — creating new session")
                addNewSession(null,
                        true, createRevealAnimation())
            } else {
                Log.d("AArchDroid", "NeoTermActivity: switching to last session")
                switchToSession(lastSession)
            }

        } else if (pendingAnchorSession == null) {
            if (NeoPreference.isAutoStartEnabled()) {
                Log.d("AArchDroid", "NeoTermActivity: no existing sessions — creating first session")
                toggleSwitcher(showSwitcher = true, easterEgg = false)

                rootAvailable = isRooted(this)
                Log.d("AArchDroid", "NeoTermActivity: synchronous root check — rootAvailable=" + rootAvailable)

                try {
                    if (rootAvailable && File("/data/local/aarchdroid/bin/bash").exists()) {
                        ChrootManager.ensureMounted()
                        addNewSession(null, false, createRevealAnimation())
                        Log.d("AArchDroid", "NeoTermActivity: first Arch session created")
                    } else {
                        Log.d("AArchDroid", "NeoTermActivity: status not OK — creating recovery session")
                        createRecoverySession()
                    }
                } catch (e: Exception) {
                    Log.e("AArchDroid", "NeoTermActivity: addNewSession failed — " + e.message)
                    try {
                        createRecoverySession()
                    } catch (_: Exception) {
                        val intent = Intent(AArchDroidApp.get(), NeoTermActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                }
            } else {
                Log.d("AArchDroid", "NeoTermActivity: auto-start disabled — showing empty state")
                toggleSwitcher(showSwitcher = true, easterEgg = false)
            }

        }
    }

    private fun loadSessionHistoryAsync() {
        currentHistoryOffset = 0
        isLoadingMore = false
        lifecycleScope.launch(Dispatchers.IO) {
            val hasCrashed = SessionHistory.hasUnclosedSessions(this@NeoTermActivity)
            if (hasCrashed) {
                val history = SessionHistory.getHistory(this@NeoTermActivity)
                val crashedSessions = history.sessions.filter { it.closedNormally == null }
                if (crashedSessions.isNotEmpty()) {
                    val crashTime = SimpleDateFormat("h:mm a", Locale.US).format(Date())
                    for (s in crashedSessions) {
                        SessionHistory.closeSession(this@NeoTermActivity, s.id, "Aplicacion terminada inesperadamente a las $crashTime")
                    }
                    SessionHistory.saveNow(this@NeoTermActivity)
                }
            }
            val page0 = SessionHistory.getHistoryPage(this@NeoTermActivity, 0, 4)
            currentHistoryOffset = page0.size
            val scope = this@NeoTermActivity.lifecycleScope
            withContext(Dispatchers.Main) {
                val historyList = findViewById<RecyclerView>(R.id.sessionHistoryList)
                historyList.layoutManager = LinearLayoutManager(this@NeoTermActivity)
                historyList.setHasFixedSize(true)
                historyList.setPadding(6, 0, 0, 0)
                historyList.clipToPadding = false
                val adapter = SessionHistoryAdapter(
                    sessions = page0,
                    hasMore = page0.size == 4,
                    onRestoreSession = { session ->
                        restoreSession(session)
                    },
                    onDeleteSession = { session ->
                        Log.d("NeoTermAct", "onDeleteSession: id=${session.id}, created=${session.created}, handle=${session.hashCode()}")
                        SessionHistory.deleteSession(this@NeoTermActivity, session.id)
                        val freshData = SessionHistory.getHistory(this@NeoTermActivity)
                        Log.d("NeoTermAct", "onDeleteSession: freshData sessions=${freshData.sessions.size}, adapter=null? ${sessionHistoryAdapter == null}")
                        sessionHistoryAdapter?.updateData(freshData.sessions)
                        updatePlaceholderVisibility()
                    }
                )
                sessionHistoryAdapter = adapter
                historyList.adapter = adapter
                historyList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        if (isLoadingMore || !adapter.hasMore) return
                        val lm = recyclerView.layoutManager as LinearLayoutManager
                        if (lm.findLastVisibleItemPosition() >= lm.itemCount - 2) {
                            isLoadingMore = true
                            scope.launch(Dispatchers.IO) {
                                val page = SessionHistory.getHistoryPage(this@NeoTermActivity, currentHistoryOffset, 4)
                                currentHistoryOffset += page.size
                                withContext(Dispatchers.Main) {
                                    adapter.hasMore = page.size == 4
                                    adapter.appendSessions(page)
                                    isLoadingMore = false
                                }
                            }
                        }
                    }
                })
                updatePlaceholderVisibility()
            }
        }
    }

    override fun recreate() {
        NeoPreference.store(KEY_NO_RESTORE, true)
        saveCurrentStatus()
        super.recreate()
    }

    private fun isRecreating(): Boolean {
        val result = peekRecreating()
        if (result) {
            NeoPreference.store(KEY_NO_RESTORE, !result)
        }
        return result
    }

    private fun saveCurrentStatus() {
        setSystemShellMode(getSystemShellMode())
    }

    private fun peekRecreating(): Boolean {
        return NeoPreference.loadBoolean(KEY_NO_RESTORE, false)
    }

    private fun setFullScreenMode(fullScreen: Boolean) {
        fullScreenHelper.fullScreen = fullScreen
        if (tabSwitcher.selectedTab is TermTab) {
            val tab = tabSwitcher.selectedTab as TermTab
            tab.requireHideIme()
            tab.onFullScreenModeChanged(fullScreen)
        }
        NeoPreference.store(R.string.key_ui_fullscreen, fullScreen)
        this@NeoTermActivity.recreate()
    }

    private fun showProfileDialog() {
        val profileComponent = ComponentManager.getComponent<ProfileComponent>()
        val profiles = profileComponent.getProfiles(ShellProfile.PROFILE_META_NAME)
        val profilesShell = profiles.filterIsInstance<ShellProfile>()

        if (profiles.isEmpty()) {
            AlertDialog.Builder(this)
                    .setTitle(R.string.error)
                    .setMessage(R.string.no_profile_available)
                    .setPositiveButton(android.R.string.yes, null)
                    .show()
            return
        }

        AlertDialog.Builder(this)
                .setTitle(R.string.new_session_with_profile)
                .setItems(profiles.map { it.profileName }.toTypedArray(), { dialog, which ->
                    val selectedProfile = profilesShell[which]
                    addNewSessionWithProfile(selectedProfile)
                })
                .setPositiveButton(android.R.string.no, null)
                .show()
    }

    private fun addNewSession() = addNewSessionWithProfile(ShellProfile.create())

    private fun addNewSession(sessionName: String?, systemShell: Boolean, animation: Animation)
            = addNewSessionWithProfile(sessionName, systemShell, animation, ShellProfile.create())

    private fun addNewSessionWithProfile(profile: ShellProfile, cwd: String? = null) {
        addNewSessionWithProfile(null, getSystemShellMode(),
                createRevealAnimation(), profile, cwd)
    }

    private fun addNewSessionWithProfile(sessionName: String?, systemShell: Boolean,
                                         animation: Animation, profile: ShellProfile,
                                         cwd: String? = null) {
        Log.d("AArchDroid", "NeoTermActivity: addNewSessionWithProfile — systemShell=" + systemShell +
                " profile=" + profile.profileName)

        val sessionCallback = TermSessionCallback()
        val viewClient = TermViewClient(this)

        val parameter = ShellParameter()
                .callback(sessionCallback)
                .systemShell(systemShell)
                .profile(profile)
        if (cwd != null) {
            parameter.currentWorkingDirectory(cwd)
        }

        val defaultScript = AArchDroidApp.get().filesDir.absolutePath + "/bin/archdroid.sh"
        if (!systemShell && profile.loginShell == defaultScript) {
            rootAvailable = isRooted(this@NeoTermActivity)
            if (rootAvailable && File("/data/local/aarchdroid/bin/bash").exists()) {
                ChrootManager.ensureMounted()
                parameter.executablePath("su")
                parameter.arguments(ChrootManager.getSuEntryArgs())
            } else {
                parameter.systemShell(true)
            }
        }

        val session = try {
            termService!!.createTermSession(parameter)
        } catch (e: Exception) {
            Log.e("AArchDroid", "NeoTermActivity: createTermSession failed — " + e.message)
            throw e
        }

        session.mSessionName = sessionName ?: generateSessionName("Dragon Terminal")
        Log.d("AArchDroid", "NeoTermActivity: session created — name=" + session.mSessionName +
                " handle=" + session.mHandle)

        // Create session history records on IO thread
        lifecycleScope.launch {
            val sid = withContext(Dispatchers.IO) {
                SessionHistory.startSession(this@NeoTermActivity).id
            }
            CommandInterceptor.registerSession(session.mHandle, sid, "terminal")
            tabSessionMap[session.mHandle] = sid
            val term = withContext(Dispatchers.IO) {
                SessionHistory.startTerminal(this@NeoTermActivity, sid, "terminal", "terminal")
            }
            CommandInterceptor.setTerminalId(session.mHandle, term.id)
        }

        val tab = createTab(session.mSessionName) as TermTab
        tab.termData.initializeSessionWith(session, sessionCallback, viewClient)

        addNewTab(tab, animation)
        switchToSession(tab)
        Handler(Looper.getMainLooper()).postDelayed({
            val currentTab = tabSwitcher.selectedTab
            if (currentTab is TermTab) {
                currentTab.termData.termView?.let { view ->
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }, 300)

        // Remove early terminal placeholder after real terminal tab is visible
        earlyTerminalPlaceholder?.let { placeholder ->
            placeholder.post {
                val parent = placeholder.parent as? ViewGroup
                parent?.removeView(placeholder)
                earlyTerminalPlaceholder = null
            }
        }

        Log.d("AArchDroid", "NeoTermActivity: tab added and switched")
    }

    private fun restoreSession(session: org.aarchdroid.dragonterminal.data.SessionRecord) {
        val systemShell = getSystemShellMode()
        val profile = ShellProfile.create()
        val defaultScript = AArchDroidApp.get().filesDir.absolutePath + "/bin/archdroid.sh"

        for (terminal in session.terminals) {
            val sessionCallback = TermSessionCallback()
            val viewClient = TermViewClient(this)

            val parameter = ShellParameter()
                .callback(sessionCallback)
                .systemShell(systemShell)
                .profile(profile)

            if (!systemShell && profile.loginShell == defaultScript) {
                ChrootManager.ensureMounted()
                parameter.executablePath("su")
                parameter.arguments(ChrootManager.getSuEntryArgs())
            }

            val newSession = try {
                termService!!.createTermSession(parameter)
            } catch (e: Exception) {
                Log.e("AArchDroid", "restoreSession: createTermSession failed — " + e.message)
                continue
            }

            newSession.mSessionName = generateSessionName("Restored")

            val sessionId = SessionHistory.startSession(this).id
            CommandInterceptor.registerSession(newSession.mHandle, sessionId, terminal.launchSource)
            val term = SessionHistory.startTerminal(this, sessionId, terminal.type,
                terminal.launchSource, terminal.iconResId)
            CommandInterceptor.setTerminalId(newSession.mHandle, term.id)
            tabSessionMap[newSession.mHandle] = sessionId

            val tab = createTab(newSession.mSessionName) as TermTab
            tab.termData.initializeSessionWith(newSession, sessionCallback, viewClient)

            addNewTab(tab, createRevealAnimation())
            switchToSession(tab)

            // Remove early terminal placeholder after real terminal tab is added
            earlyTerminalPlaceholder?.let { placeholder ->
                placeholder.post {
                    val parent = placeholder.parent as? ViewGroup
                    parent?.removeView(placeholder)
                    earlyTerminalPlaceholder = null
                }
            }

            // Execute saved commands with staggered delays, suppress logging
            CommandInterceptor.suppressLogging = true
            var delay = 1500L
            for (cmd in terminal.commands) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    newSession.write(cmd.cmd + "\n")
                }, delay)
                delay += 400L
            }
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                CommandInterceptor.suppressLogging = false
            }, delay)
        }

        // Close the history placeholder after restore
        sessionHistoryAdapter?.let { adapter ->
            val freshData = SessionHistory.getHistory(this)
            adapter.updateData(freshData.sessions)
        }
    }

    private fun addNewSessionFromExisting(session: TerminalSession?) {
        if (session == null) {
            Log.d("AArchDroid", "NSFE: session is null — returning")
            return
        }
        Log.d("AArchDroid", "NSFE: entering — session handle=" + session.mHandle + " title=" + session.title + " tabCount=" + tabSwitcher.count)

        // Do not add the same session again
        // Or app will crash when rotate
        val tabCount = tabSwitcher.count
        val dup = (0..(tabCount - 1))
                .map { tabSwitcher.getTab(it) }
                .any { it is TermTab && it.termData.termSession == session }
        if (dup) {
            Log.d("AArchDroid", "NSFE: session already in tabs — skipping handle=" + session.mHandle)
            return
        }

        val sessionCallback = if (session.sessionChangedCallback is TermSessionCallback) {
            session.sessionChangedCallback as TermSessionCallback
        } else {
            TermSessionCallback().also { session.setChangeCallback(it) }
        }
        val viewClient = TermViewClient(this)

        val tab = createTab(session.title) as TermTab
        tab.termData.initializeSessionWith(session, sessionCallback, viewClient)

        CommandInterceptor.getContext(session.mHandle)?.let { ctx ->
            tabSessionMap[session.mHandle] = ctx.sessionId
        }

        Log.d("AArchDroid", "NSFE: adding tab for handle=" + session.mHandle)
        addNewTab(tab, createRevealAnimation())
        switchToSession(tab)

        // Remove early terminal placeholder after real terminal tab is added
        earlyTerminalPlaceholder?.let { placeholder ->
            placeholder.post {
                val parent = placeholder.parent as? ViewGroup
                parent?.removeView(placeholder)
                earlyTerminalPlaceholder = null
            }
        }

        Log.d("AArchDroid", "NSFE: tab added and switched for handle=" + session.mHandle)
    }

    private fun addXSession() {

        if (!tabSwitcher.isSwitcherShown) {
            toggleSwitcher(showSwitcher = true, easterEgg = false)
        }

        val parameter = XParameter()
        val session = termService!!.createXSession(this, parameter)

        session.mSessionName = generateXSessionName("X")
        val tab = createXTab(session.mSessionName) as XSessionTab
        tab.session = session

        addNewTab(tab, createRevealAnimation())
        switchToSession(tab)
    }

    private fun addXSession(session: XSession?) {
        if (session == null) {
            return
        }

        // Do not add the same session again
        // Or app will crash when rotate
        val tabCount = tabSwitcher.count
        (0..(tabCount - 1))
                .map { tabSwitcher.getTab(it) }
                .filter { it is XSessionTab && it.session == session }
                .forEach { return }

        val tab = createXTab(session.mSessionName) as XSessionTab

        addNewTab(tab, createRevealAnimation())
        switchToSession(tab)
    }

    private fun generateSessionName(prefix: String): String {
        return "$prefix #${termService!!.sessions.size}"
    }

    private fun generateXSessionName(prefix: String): String {
        return "$prefix #${termService!!.xSessions.size}"
    }

    private fun switchToSession(session: TerminalSession?) {
        if (session == null) {
            return
        }

        for (i in 0 until tabSwitcher.count) {
            val tab = tabSwitcher.getTab(i)
            if (tab is TermTab && tab.termData.termSession == session) {
                switchToSession(tab)
                break
            }
        }
    }

    private fun switchToSession(tab: Tab?) {
        if (tab == null) {
            return
        }
        tabSwitcher.selectTab(tab)
    }

    private fun addNewTab(tab: Tab, animation: Animation) {
        tabSwitcher.addTab(tab, 0, animation)
    }

    private fun getStoredCurrentSessionOrLast(): TerminalSession? {
        val stored = NeoPreference.getCurrentSession(termService)
        if (stored != null) return stored
        val numberOfSessions = termService!!.sessions.size
        if (numberOfSessions == 0) return null
        return termService!!.sessions[numberOfSessions - 1]
    }

    private fun createAddSessionListener(): View.OnClickListener {
        return View.OnClickListener {
            addNewSession()
        }
    }

    private fun createTab(tabTitle: String?): Tab {
        return postTabCreated(TermTab(tabTitle ?: "Dragon Terminal"))

    }

    private fun createXTab(tabTitle: String?): Tab {
        return postTabCreated(XSessionTab(tabTitle ?: "Dragon Terminal"))
    }

    private fun <T : NeoTab> postTabCreated(tab: T): T {
        // We must create a Bundle for each tab
        // tabs can use them to store status.
        tab.parameters = Bundle()

        tab.setBackgroundColor(ContextCompat.getColor(this, R.color.tab_background_color))
        tab.setTitleTextColor(ContextCompat.getColor(this, R.color.tab_title_text_color))
        return tab
    }

    private fun createRevealAnimation(): Animation {
        var x = 0f
        var y = 0f
        val view = getNavigationMenuItem()

        if (view != null) {
            val location = IntArray(2)
            view.getLocationInWindow(location)
            x = location[0] + view.width / 2f
            y = location[1] + view.height / 2f
        }

        return RevealAnimation.Builder().setX(x).setY(y).create()
    }

    private fun getNavigationMenuItem(): View? {
        val toolbars = tabSwitcher.toolbars

        if (toolbars != null) {
            val toolbar = if (toolbars.size > 1) toolbars[1] else toolbars[0]
            val size = toolbar.childCount

            (0 until size)
                    .map { toolbar.getChildAt(it) }
                    .filterIsInstance(ImageButton::class.java)
                    .forEach { return it }
        }

        return null
    }

    private fun createWindowInsetsListener(): OnApplyWindowInsetsListener {
        return OnApplyWindowInsetsListener { _, insets ->
            tabSwitcher.setPadding(insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop, insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom)
            insets
        }
    }

    private fun toggleSwitcher(showSwitcher: Boolean, easterEgg: Boolean) {
        if (tabSwitcher.count == 0 && easterEgg) {
            AArchDroidApp.get().easterEgg(this, "Stop! You don't know what you are doing!")
            return
        }

        if (showSwitcher) {
            val tab = tabSwitcher.selectedTab
            if (tab is TermTab) {
                tab.requireHideIme()
                tab.termData.extraKeysView?.visibility = View.GONE
            }
            tabSwitcher.showSwitcher()
        } else {
            tabSwitcher.hideSwitcher()
        }
    }

    private fun setSystemShellMode(systemShell: Boolean) {
        NeoPreference.store(NeoPreference.KEY_SYSTEM_SHELL, systemShell)
    }

    private fun getSystemShellMode(): Boolean {
        return NeoPreference.loadBoolean(NeoPreference.KEY_SYSTEM_SHELL, true)
    }

    private inline fun <reified T> forEachTab(callback: (T) -> Unit) {
        (0 until tabSwitcher.count)
                .map { tabSwitcher.getTab(it) }
                .filterIsInstance(T::class.java)
                .forEach(callback)
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onTabCloseEvent(tabCloseEvent: TabCloseEvent) {
        val tab = tabCloseEvent.termTab

        if (tabSwitcher.count > 1) {
            val closingIndex = tabSwitcher.indexOf(tab)
            tabSwitcher.removeTab(tab)

            var targetIndex: Int
            if (NeoPreference.isNextTabEnabled()) {
                targetIndex = closingIndex
                if (targetIndex >= tabSwitcher.count)
                    targetIndex = tabSwitcher.count - 1
            } else {
                targetIndex = closingIndex - 1
                if (targetIndex < 0)
                    targetIndex = tabSwitcher.count - 1
            }
            switchToSession(tabSwitcher.getTab(targetIndex))
        } else {
            tab.requireHideIme()
            toggleSwitcher(showSwitcher = true, easterEgg = false)
            tabSwitcher.removeTab(tab)
        }
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onCameraPermissionEvent(event: CameraPermissionEvent) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            Log.d("AArchDroid", "CAMERA already granted, retrying camera")
            NeoTabDecorator.retryCamera()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Acceso a cámara")
            .setMessage("Iris necesita la cámara para capturar frames. ¿Permitir acceso?")
            .setPositiveButton("Permitir") { _, _ ->
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun getDefaultCameraId(): String {
        return try {
            val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            for (id in manager.cameraIdList) {
                val facing = manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) return id
            }
            manager.cameraIdList.firstOrNull() ?: "0"
        } catch (_: Exception) { "0" }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onToggleFullScreenEvent(toggleFullScreenEvent: ToggleFullScreenEvent) {
        val fullScreen = fullScreenHelper.fullScreen
        setFullScreenMode(!fullScreen)
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onToggleImeEvent(toggleImeEvent: ToggleImeEvent) {
        if (!tabSwitcher.isSwitcherShown) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
            NeoPreference.setImeVisible(!NeoPreference.isImeVisible())
        }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onTitleChangedEvent(titleChangedEvent: TitleChangedEvent) {
        if (!tabSwitcher.isSwitcherShown) {
            toolbar.title = titleChangedEvent.title
        }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onCreateNewSessionEvent(createNewSessionEvent: CreateNewSessionEvent) {
        if (NeoPreference.isSamePathEnabled()) {
            val tab = tabSwitcher.selectedTab
            if (tab is TermTab) {
                val session = tab.termData.termSession
                if (session != null && session.isRunning()) {
                    val pid = session.pid
                    val cwd = if (pid > 0) {
                        try {
                            java.io.File("/proc/$pid/cwd").canonicalPath
                        } catch (e: Exception) { null }
                    } else null
                    if (cwd != null) {
                        addNewSessionWithProfile(ShellProfile.create(), cwd)
                        return
                    }
                }
            }
        }
        addNewSession()
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSwitchSessionEvent(switchSessionEvent: SwitchSessionEvent) {
        if (tabSwitcher.count < 2) {
            return
        }

        val rangedInt = RangedInt(tabSwitcher.selectedTabIndex, (0 until tabSwitcher.count))
        val nextIndex = if (switchSessionEvent.toNext)
            rangedInt.increaseOne()
        else rangedInt.decreaseOne()
        if (!tabSwitcher.isSwitcherShown) {
            tabSwitcher.showSwitcher()
        }
        switchToSession(tabSwitcher.getTab(nextIndex))
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSwitchIndexedSessionEvent(switchIndexedSessionEvent: SwitchIndexedSessionEvent) {
        val nextIndex = switchIndexedSessionEvent.index - 1
        if (nextIndex in (0 until tabSwitcher.count) && nextIndex != tabSwitcher.selectedTabIndex) {
            // Do not show animation here, users may get tired
            switchToSession(tabSwitcher.getTab(nextIndex))
        }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onKillTerminalEvent(event: KillTerminalEvent) {
        val tab = tabSwitcher.selectedTab
        if (tab is TermTab) {
            if (tabSwitcher.count > 1) {
                tabSwitcher.removeTab(tab)
                if (tabSwitcher.count > 0) {
                    val remainingTab = tabSwitcher.getTab(0)
                    tabSwitcher.selectTab(remainingTab)
                }
            } else {
                tab.requireHideIme()
                toggleSwitcher(showSwitcher = true, easterEgg = false)
                tabSwitcher.removeTab(tab)
            }
        }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onToggleHistoryEvent(event: ToggleHistoryEvent) {
        if (NeoPreference.isLoggingDisabled()) return
        forceHistoryVisible = !forceHistoryVisible
        updatePlaceholderVisibility()
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onOpenFloatEvent(event: OpenFloatEvent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }
        val intent = Intent(this, FloatService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onNewTerminalSamePathEvent(event: NewTerminalSamePathEvent) {
        val tab = tabSwitcher.selectedTab
        if (tab is TermTab) {
            val session = tab.termData.termSession
            if (session != null && session.isRunning()) {
                val pid = session.pid
                val cwd = if (pid > 0) {
                    try {
                        java.io.File("/proc/$pid/cwd").canonicalPath
                    } catch (e: Exception) { null }
                } else null
                addNewSessionWithProfile(ShellProfile.create(), cwd)
                return
            }
        }
        addNewSession()
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSelectAllEvent(event: SelectAllEvent) {
        val tab = tabSwitcher.selectedTab
        if (tab is TermTab) {
            tab.termData.termView?.selectAllText()
        }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFloatCurrentTerminalEvent(event: FloatCurrentTerminalEvent) {
        val tab = tabSwitcher.selectedTab
        if (tab is TermTab) {
            val session = tab.termData.termSession
            if (session != null) {
                transferringHandle = session.mHandle
                tabSwitcher.removeTab(tab)
            }
        }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onToggleTerminalSwitcherEvent(event: ToggleTerminalSwitcherEvent) {
        if (tabSwitcher.count <= 1) return
        val rangedInt = RangedInt(tabSwitcher.selectedTabIndex, (0 until tabSwitcher.count))
        val nextIndex = rangedInt.increaseOne()
        tabSwitcher.selectTab(tabSwitcher.getTab(nextIndex))
    }

    fun update_colors() {
        // Simple fix to bug on custom color
        Handler().postDelayed({

            if (tabSwitcher.count > 0) {
                val tab = tabSwitcher.selectedTab
                if (tab is TermTab) {
                    tab.updateColorScheme()
                }
            }

        }, 500)

    }

    private fun updatePlaceholderVisibility() {
        val placeholder = findViewById<View>(R.id.placeholder_empty)
        val emptyContainer = findViewById<View>(R.id.empty_state_container)
        val emptyText = findViewById<TextView>(R.id.empty_logs_text)
        val historyList = findViewById<View>(R.id.sessionHistoryList)
        val launchBtn = findViewById<Button>(R.id.launch_terminal_button)
        updateExtraKeysButtonStates()

        if (forceHistoryVisible) {
            placeholder.visibility = View.VISIBLE
            toolbar.menu?.findItem(R.id.toggle_tab_switcher_menu_item)?.isVisible = tabSwitcher.count > 0
            val logsDisabled = org.aarchdroid.dragonterminal.frontend.config.NeoPreference.isLoggingDisabled()
            if (logsDisabled) {
                toolbar.title = "Terminal"
                toolbar.menu?.findItem(R.id.menu_item_clear_logs)?.isVisible = false
                emptyText.text = "Historial deshabilitado en Ajustes"
                emptyText.visibility = View.VISIBLE
                emptyContainer.visibility = View.VISIBLE
                historyList.visibility = View.GONE
            } else {
                val count = SessionHistory.getHistoryCount(this@NeoTermActivity)
                val hasLogs = count > 0
                toolbar.title = if (hasLogs) "($count) Logs" else "Terminal"
                toolbar.menu?.findItem(R.id.menu_item_clear_logs)?.isVisible = hasLogs
                if (hasLogs) {
                    emptyContainer.visibility = View.GONE
                    historyList.visibility = View.VISIBLE
                } else {
                    emptyText.visibility = View.VISIBLE
                    emptyContainer.visibility = View.VISIBLE
                    historyList.visibility = View.GONE
                }
            }
            launchBtn.setOnClickListener { addNewSession() }
            return
        }

        if (::tabSwitcher.isInitialized) {
            placeholder.visibility = if (tabSwitcher.count == 0) View.VISIBLE else View.GONE
        }
        toolbar.menu?.findItem(R.id.toggle_tab_switcher_menu_item)?.isVisible = tabSwitcher.count > 0

        Log.d("NeoTermAct", "updatePlaceholderVisibility: tabSwitcher.count=${tabSwitcher.count}, logsDisabled=${org.aarchdroid.dragonterminal.frontend.config.NeoPreference.isLoggingDisabled()}, initialized=${::tabSwitcher.isInitialized}")

        if (tabSwitcher.count == 0) {
            val logsDisabled = org.aarchdroid.dragonterminal.frontend.config.NeoPreference.isLoggingDisabled()

            if (logsDisabled) {
                toolbar.title = "Terminal"
                toolbar.menu?.findItem(R.id.menu_item_clear_logs)?.isVisible = false
                emptyText.text = "Historial deshabilitado en Ajustes"
                emptyText.visibility = View.VISIBLE
                emptyContainer.visibility = View.VISIBLE
                historyList.visibility = View.GONE
            } else {
                val count = SessionHistory.getHistoryCount(this@NeoTermActivity)
                val hasLogs = count > 0

                toolbar.title = if (hasLogs) "($count) Logs" else "Terminal"
                toolbar.menu?.findItem(R.id.menu_item_clear_logs)?.isVisible = hasLogs

                if (hasLogs) {
                    emptyContainer.visibility = View.GONE
                    historyList.visibility = View.VISIBLE
                } else {
                    emptyText.visibility = View.VISIBLE
                    emptyContainer.visibility = View.VISIBLE
                    historyList.visibility = View.GONE
                }
            }

            launchBtn.setOnClickListener { addNewSession() }
        } else {
            toolbar.title = "Terminal"
            toolbar.menu?.findItem(R.id.menu_item_clear_logs)?.isVisible = false
        }
    }

    private fun updateExtraKeysButtonStates() {
        for (i in 0 until tabSwitcher.count) {
            val tab = tabSwitcher.getTab(i)
            if (tab is TermTab) {
                tab.termData.extraKeysView?.tabCount = tabSwitcher.count
            }
        }
    }

    fun checkinstallterm() {
        val chrootMarker = File("/data/local/aarchdroid/.aarchdroid_chroot")
        if (chrootMarker.exists()) {
            Log.d("AArchDroid", "NeoTermActivity: chroot is mounted")
            AArchDroidApp.get().checkcoreversion()
        } else {
            Log.d("AArchDroid", "NeoTermActivity: chroot not found — extracting embedded rootfs")
            extractEmbeddedRootfs()
        }
    }

    private fun extractEmbeddedRootfs() {
        Thread {
            try {
                val CHROOT_DIR = "/data/local/aarchdroid"
                val BUSYBOX_DST = "/data/data/org.aarchdroid/files/bin/busybox"

                // Ensure busybox is available
                val busyboxFile = File(BUSYBOX_DST)
                if (!busyboxFile.exists()) {
                    busyboxFile.parentFile?.mkdirs()
                    try {
                        val src = assets.open("arm/static/bin/busybox")
                        src.use { input ->
                            busyboxFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        busyboxFile.setExecutable(true)
                    } catch (e: Exception) {
                        Log.e("AArchDroid", "extractEmbeddedRootfs: cannot extract busybox — " + e.message)
                    }
                }

                val mkdir = Runtime.getRuntime().exec(arrayOf("su", "-c", "mkdir -p $CHROOT_DIR"))
                mkdir.inputStream.use { it.readBytes() }
                mkdir.errorStream.use { it.readBytes() }
                mkdir.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)

                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "\"$BUSYBOX_DST\" tar -xzf - -C $CHROOT_DIR"))
                val stderrReader = Thread { try { p.errorStream.use { it.readBytes() } } catch (_: Exception) {} }
                stderrReader.start()
                val stdin = p.outputStream
                val assets = assets.open("rootfs.tgz")
                val buf = ByteArray(8192)
                var len: Int
                while (assets.read(buf).also { len = it } != -1) {
                    stdin.write(buf, 0, len)
                }
                assets.close()
                stdin.flush()
                stdin.close()
                stderrReader.join(1000)
                val exitCode = if (p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)) p.exitValue() else -1
                Log.d("AArchDroid", "NeoTermActivity: embedded rootfs extracted, exit=$exitCode")
                if (exitCode != 0) {
                    Log.w("AArchDroid", "NeoTermActivity: tar exited with code $exitCode")
                }
            } catch (e: Exception) {
                Log.e("AArchDroid", "NeoTermActivity: extractEmbeddedRootfs failed — " + e.message)
            }
        }.start()
    }


    fun suRun(cmd: String) {
        if (!rootAvailable) {
            Log.w("AArchDroid", "suRun: root not available, skipping: " + cmd.take(100))
            return
        }
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            // Consume stdout and stderr to prevent pipe buffer deadlock
            val stdoutReader = Thread { try { p.inputStream.use { it.readBytes() } } catch (_: Exception) {} }
            val stderrReader = Thread { try { p.errorStream.use { it.readBytes() } } catch (_: Exception) {} }
            stdoutReader.start(); stderrReader.start()
            if (!p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                Log.w("AArchDroid", "suRun timed out: " + cmd.take(100))
                p.destroyForcibly()
            }
            stdoutReader.join(1000); stderrReader.join(1000)
        } catch (e: Exception) {
            Log.w("AArchDroid", "suRun failed: " + cmd.take(100) + " — " + e.message)
        }
    }

    fun suRunGlobal(cmd: String) {
        if (!rootAvailable) {
            Log.w("AArchDroid", "suRunGlobal: root not available, skipping: " + cmd.take(100))
            return
        }
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-M", "-c", cmd))
            val stdoutReader = Thread { try { p.inputStream.use { it.readBytes() } } catch (_: Exception) {} }
            val stderrReader = Thread { try { p.errorStream.use { it.readBytes() } } catch (_: Exception) {} }
            stdoutReader.start(); stderrReader.start()
            if (!p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                Log.w("AArchDroid", "suRunGlobal timed out: " + cmd.take(100))
                p.destroyForcibly()
            }
            stdoutReader.join(1000); stderrReader.join(1000)
        } catch (e: Exception) {
            Log.w("AArchDroid", "suRunGlobal failed: " + cmd.take(100) + " — " + e.message)
        }
    }

    fun isMounted(path: String): Boolean {
        val result = suRunOutput("grep -Fq ' $path ' /proc/mounts && echo 1 || echo 0")
        return result == "1"
    }

    fun findExternalSd(): String? {
        val mounts = suRunOutput("cat /proc/mounts 2>/dev/null") ?: return null
        val lines = mounts.lines()
        val uuid = Regex("^[A-Z0-9]{4}-[A-Z0-9]{4}$")

        // 1) Prefer UUID-style paths under /storage/ or /mnt/media_rw/ (SD cards)
        for (line in lines) {
            val parts = line.split(" ")
            if (parts.size < 2) continue
            val path = parts[1]
            if (path.contains("emulated")) continue
            val seg = path.substringAfterLast("/")
            if (uuid.matches(seg) && (path.startsWith("/storage/") || path.startsWith("/mnt/media_rw/"))) {
                return path
            }
        }

        // 2) Any non-emulated /storage/ entry (covers OTG, odd OEM paths)
        for (line in lines) {
            val parts = line.split(" ")
            if (parts.size < 2) continue
            val path = parts[1]
            if (path.startsWith("/storage/") && !path.contains("emulated")) {
                return path
            }
        }

        // 3) Known OEM fallback paths
        val extra = suRunOutput("ls -d /mnt/external_sd /mnt/extSdCard /mnt/sdcard/external_sd 2>/dev/null | head -1")
        if (!extra.isNullOrBlank()) return extra
        return null
    }

    private fun suRunOutput(cmd: String): String? {
        if (!rootAvailable) return null
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            // Consume stderr in background to prevent deadlock
            val stderrReader = Thread { try { p.errorStream.use { it.readBytes() } } catch (_: Exception) {} }
            stderrReader.start()
            val result = p.inputStream.bufferedReader().readText().trim()
            stderrReader.join(1000)
            p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
            result
        } catch (e: Exception) {
            Log.w("AArchDroid", "suRunOutput failed: $cmd — ${e.message}")
            null
        }
    }

    private fun createRecoverySession() {
        try {
            val hasRoot = isRooted(this@NeoTermActivity)
            rootAvailable = hasRoot
            val chrootExists = File("/data/local/aarchdroid/bin/bash").exists()
            val chrootMounted = if (hasRoot && chrootExists) ChrootManager.isMounted() else false

            val banner = StringBuilder()
            banner.append("\n")
            banner.append("╔══════════════════════════════════════╗\n")
            banner.append("║       AArchDroid Terminal           ║\n")
            banner.append("╚══════════════════════════════════════╝\n")
            banner.append("\n")
            banner.append(if (hasRoot) "  [✓] Root detectado\n" else "  [✗] Root no detectado\n")
            if (hasRoot) {
                banner.append(if (chrootExists) "  [✓] Chroot instalado\n" else "  [✗] Chroot no instalado\n")
                if (chrootExists) {
                    banner.append(if (chrootMounted) "  [✓] Monturas activas\n" else "  [✗] Monturas inactivas\n")
                }
            }
            banner.append("\n")

            if (!hasRoot) {
                banner.append("  Concede permisos root y presiona Enter.\n")
                banner.append("  Si no aparece el diálogo, abre la app SuperUser.\n")
                banner.append("\n")
            } else if (!chrootExists) {
                banner.append("  El chroot no está instalado en /data/local/aarchdroid.\n")
                banner.append("  Abre AArchDroid (app principal) para extraer\n")
                banner.append("  e instalar el sistema base.\n")
                banner.append("\n")
            } else if (!chrootMounted) {
                banner.append("  Las monturas del chroot no están activas.\n")
                banner.append("\n")
            } else {
                banner.append("  Estado OK.\n")
                banner.append("\n")
            }
            banner.append("  Shell de sistema disponible.\n")

            val bannerStr = banner.toString()
            val script = "echo '${bannerStr.replace("'", "'\\''")}'; exec /system/bin/sh"
            Log.d("AArchDroid", "NeoTermActivity: creating recovery session")
            val sessionCallback = TermSessionCallback()
            val viewClient = TermViewClient(this)

            val parameter = ShellParameter()
                .callback(sessionCallback)
                .systemShell(true)
                .arguments(arrayOf("sh", "-c", script))

            val session = termService!!.createTermSession(parameter)
            session.mSessionName = "Recuperación"

            val tab = createTab(session.mSessionName) as TermTab
            tab.termData.initializeSessionWith(session, sessionCallback, viewClient)

            addNewTab(tab, createRevealAnimation())
            switchToSession(tab)
            Handler(Looper.getMainLooper()).postDelayed({
                val currentTab = tabSwitcher.selectedTab
                if (currentTab is TermTab) {
                    currentTab.termData.termView?.let { view ->
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
            }, 300)

            earlyTerminalPlaceholder?.let { placeholder ->
                placeholder.post {
                    val parent = placeholder.parent as? ViewGroup
                    parent?.removeView(placeholder)
                    earlyTerminalPlaceholder = null
                }
            }
        } catch (e: Exception) {
            Log.e("AArchDroid", "NeoTermActivity: createRecoverySession failed — " + e.message)
        }
    }

    fun changehostname(hostnameprovided: String) {
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "hostname $hostnameprovided"))
            val stderrReader = Thread { try { proc.errorStream.use { it.readBytes() } } catch (_: Exception) {} }
            stderrReader.start()
            proc.inputStream.use { it.readBytes() }
            stderrReader.join(1000)
            proc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w("AArchDroid", "changehostname: hostname not set — " + e.message)
        }
    }

    fun get_motherfucker_battery() {

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            val isIgnoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(packageName)

            if (!isIgnoringBatteryOptimizations) {

                val intent = Intent()
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")

            }

        }

    }


    fun isRooted(c:Context): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val finished = process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
            if (finished) {
                val output = process.inputStream.bufferedReader().readText()
                output.contains("uid=0")
            } else {
                process.destroy()
                false
            }
        } catch (e: Exception) {
            false
        }
    }


}
