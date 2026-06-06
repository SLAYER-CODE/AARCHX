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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import org.aarchdroid.AArchDroidApp
import org.aarchdroid.R
import android.content.DialogInterface
import org.aarchdroid.dragonterminal.backend.TerminalSession
import org.aarchdroid.dragonterminal.component.profile.ProfileComponent
import org.aarchdroid.dragonterminal.frontend.component.ComponentManager
import org.aarchdroid.dragonterminal.frontend.config.NeoPermission
import org.aarchdroid.dragonterminal.frontend.config.NeoPreference
import org.aarchdroid.dragonterminal.frontend.session.shell.ShellParameter
import org.aarchdroid.dragonterminal.frontend.session.shell.ShellProfile
import org.aarchdroid.dragonterminal.frontend.session.shell.client.TermSessionCallback
import org.aarchdroid.dragonterminal.frontend.session.shell.client.TermViewClient
import org.aarchdroid.dragonterminal.frontend.session.shell.client.event.*
import org.aarchdroid.dragonterminal.frontend.session.xorg.XParameter
import org.aarchdroid.dragonterminal.frontend.session.xorg.XSession
import org.aarchdroid.dragonterminal.services.NeoTermService
import org.aarchdroid.dragonterminal.ui.settings.SettingActivity
import org.aarchdroid.dragonterminal.ui.term.tab.NeoTab
import org.aarchdroid.dragonterminal.ui.term.tab.NeoTabDecorator
import org.aarchdroid.dragonterminal.ui.term.tab.TermTab
import org.aarchdroid.dragonterminal.ui.term.tab.XSessionTab
import org.aarchdroid.dragonterminal.utils.AssetsUtils
import org.aarchdroid.dragonterminal.utils.FullScreenHelper
import org.aarchdroid.dragonterminal.utils.RangedInt
import de.mrapp.android.tabswitcher.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.*
import java.lang.Process
import java.nio.charset.Charset
import java.util.ArrayList


class NeoTermActivity : AppCompatActivity(), ServiceConnection, SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        const val KEY_NO_RESTORE = "no_restore"
        const val REQUEST_SETUP = 22313
    }

    private lateinit var errorDialog: Dialog

    lateinit var tabSwitcher: TabSwitcher
    private lateinit var fullScreenHelper: FullScreenHelper
    lateinit var toolbar: Toolbar

    var addSessionListener = createAddSessionListener()
    private var termService: NeoTermService? = null

    val fullscreen = NeoPreference.isFullScreenEnabled()
    var tshow = false

    @Volatile
    var rootAvailable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("AArchDroid", "NeoTermActivity: onCreate() — entering terminal activity")
        Log.d("AArchDroid", "NeoTermActivity: intent action=" + (intent?.action ?: "null") +
                " extras=" + (intent?.extras?.keySet()?.joinToString() ?: "null"))

        Log.d("AArchDroid", "NeoTermActivity: checking root access")
        rootAvailable = isRooted(this)
        if (!rootAvailable) {
            Log.w("AArchDroid", "NeoTermActivity: root not detected — showing dialog")
            showNoRootDialog()
        }

        changehostname("AARCHX")

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

        setContentView(R.layout.ui_main)

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

                if (method == "recfromshortcut") {
                    Log.d("AArchDroid", "NeoTermActivity: launched from shortcut — setting temp shell")
                    NeoPreference.setLoginShellName("/system/bin/sh")

                    Handler().postDelayed({
                        if (tabSwitcher.count > 0) {
                            Log.d("AArchDroid", "NeoTermActivity: switching to archdroid.sh shell")
                            NeoPreference.setLoginShellName(AArchDroidApp.get().filesDir.absolutePath + "/bin/archdroid.sh")
                        }
                    }, 1000)

                } else {
                    val shellPath = AArchDroidApp.get().filesDir.absolutePath + "/bin/archdroid.sh"
                    Log.d("AArchDroid", "NeoTermActivity: setting login shell to " + shellPath)
                    NeoPreference.setLoginShellName(shellPath)
                }
            } else {
                Log.d("AArchDroid", "NeoTermActivity: no extras — will use default login shell")
            }
        } else {
            Log.d("AArchDroid", "NeoTermActivity: restoring from saved state")
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
                val target = "/data/local/aarchdroid/root/Interna"
                if (isMounted(target)) {
                    Toast.makeText(this, "ya se monto", Toast.LENGTH_SHORT).show()
                } else {
                    suRun("/data/data/org.aarchdroid/files/bin/busybox mkdir -p $target")
                    suRun("/data/data/org.aarchdroid/files/bin/busybox mount -o bind /sdcard $target")
                }
                true
            }

            R.id.menu_item_mount_external -> {
                val target = "/data/local/aarchdroid/root/Externa"
                val extSd = findExternalSd()
                if (extSd == null) {
                    Toast.makeText(this, "no se detecto tarjeta externa", Toast.LENGTH_SHORT).show()
                } else if (isMounted(target)) {
                    Toast.makeText(this, "ya se monto", Toast.LENGTH_SHORT).show()
                } else {
                    suRun("/data/data/org.aarchdroid/files/bin/busybox mkdir -p $target")
                    suRun("/data/data/org.aarchdroid/files/bin/busybox mount -o bind $extSd $target")
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

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showToolsPopup(anchor: View) {
        data class ToolItem(val name: String, val icon: Int, val activityClass: String)

        val tools = listOf(
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

        val wrapped = androidx.appcompat.view.ContextThemeWrapper(this, R.style.Theme_CompactGreenPopup)
        val popup = PopupMenu(wrapped, anchor, Gravity.CENTER_HORIZONTAL, 0, R.style.Widget_GreenBorder_PopupMenu)
        val menu = popup.menu

        tools.forEachIndexed { index, tool ->
            menu.add(0, index, 0, tool.name).setIcon(tool.icon)
        }

        popup.setForceShowIcon(true)
        popup.setOnMenuItemClickListener { item ->
            try {
                startActivity(Intent(this, Class.forName(tools[item.itemId].activityClass)))
            } catch (e: Exception) {
                Log.e("AArchDroid", "showToolsPopup: cannot start " + tools[item.itemId].name + " — " + e.message)
            }
            true
        }

        popup.show()
    }

    override fun onPause() {
        super.onPause()
        val tab = tabSwitcher.selectedTab as NeoTab?
        tab?.onPause()
    }

    override fun onResume() {
        super.onResume()

        try {

            PreferenceManager.getDefaultSharedPreferences(this)
                    .registerOnSharedPreferenceChangeListener(this)
            tabSwitcher.addListener(object : TabSwitcherListener {
                override fun onSwitcherShown(tabSwitcher: TabSwitcher) {
                    toolbar.setBackgroundResource(android.R.color.black)
                }

                override fun onSwitcherHidden(tabSwitcher: TabSwitcher) {
                    toolbar.setBackgroundResource(R.color.black_fuck)
                }

                override fun onSelectionChanged(tabSwitcher: TabSwitcher, selectedTabIndex: Int, selectedTab: Tab?) {
                    if (selectedTab is TermTab && selectedTab.termData.termSession != null) {
                        NeoPreference.storeCurrentSession(selectedTab.termData.termSession!!)
                    }
                }

                override fun onTabAdded(tabSwitcher: TabSwitcher, index: Int, tab: Tab, animation: Animation) {
                    update_colors()
                }

                override fun onTabRemoved(tabSwitcher: TabSwitcher, index: Int, tab: Tab, animation: Animation) {
                    if (tab is TermTab) {
                        SessionRemover.removeSession(termService, tab)
                    } else if (tab is XSessionTab) {
                        SessionRemover.removeXSession(termService, tab)
                    }
                }

                override fun onAllTabsRemoved(tabSwitcher: TabSwitcher, tabs: Array<out Tab>, animation: Animation) {
                }
            })
            val tab = tabSwitcher.selectedTab as NeoTab?
            tab?.onResume()

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

        if (termService != null) {
            if (termService!!.sessions.isEmpty()) {
                termService!!.stopSelf()
            }
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
            Log.d("AArchDroid", "NeoTermActivity: first launch — extracting assets")

            var reader: BufferedReader? = null
            var testmsg=""
            var result = false
            var stdin: OutputStream? = null
            val stdout: InputStream
            val params = ArrayList<String>()

            try {
                Log.d("AArchDroid", "NeoTermActivity: testing root via `su`")
                val pb = ProcessBuilder("su")
                pb.directory(File(AArchDroidApp.get().applicationInfo.dataDir))
                pb.redirectErrorStream(true)
                val process: Process = pb.start()
                stdin = process.outputStream
                stdout = process.inputStream
                params.add(0, "PATH=" + AArchDroidApp.get().filesDir.absolutePath + "/bin:\$PATH")
                params.add("exit 0")
                var os: DataOutputStream? = null

                try {
                    os = DataOutputStream(stdin)
                    for (cmd in params) {
                        os.writeBytes(cmd + "\n")
                    }
                    os.flush()
                } catch (e: IOException) {
                    Log.e("AArchDroid", "NeoTermActivity: su stdin write failed — " + e.message)
                } finally {
                    os?.close()
                }

                reader = BufferedReader(InputStreamReader(stdout))
                var n: Int
                val buffer = CharArray(1024)
                while (reader.read(buffer).also { n = it } != -1) {
                    val msg = String(buffer, 0, n)
                    testmsg += msg
                }

                if (process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS) && process.exitValue() == 0) result = true
                Log.d("AArchDroid", "NeoTermActivity: su test result=" + result + " output=" + testmsg.take(200))
            } catch (e: Exception) {
                result = false
                Log.e("AArchDroid", "NeoTermActivity: su test threw — " + e.message)
            } finally {
                reader?.close()
                stdin?.close()

                val sharedPref = this.getSharedPreferences(this.packageName, Context.MODE_PRIVATE)
                val issafepts = sharedPref.getBoolean("issafepts", false)
                Log.d("AArchDroid", "NeoTermActivity: issafepts=" + issafepts)

                if(issafepts) {
                    Log.d("AArchDroid", "NeoTermActivity: assets already extracted — skipping")
                    val AllDir = File(filesDir.absolutePath+"/scripts")
                    AllDir.mkdirs()
                    val BinDir = File(filesDir.absolutePath+"/bin")
                    BinDir.mkdirs()

                    AssetsUtils.extractAssetsDir(this, "all/scripts", this.filesDir.absolutePath+"/scripts")
                    setPermissions(AllDir)

                    AssetsUtils.extractAssetsDir(this, "arm/static/bin", this.filesDir.absolutePath+"/bin")
                    setPermissions(BinDir)

                } else {
                    Log.d("AArchDroid", "NeoTermActivity: first run — extracting assets from APK")
                    suRun("rm -rf " + this.filesDir.absolutePath + "/bin")
                    suRun("rm -rf " + this.filesDir.absolutePath + "/scripts")

                    val AllDir = File(filesDir.absolutePath+"/scripts")
                    AllDir.mkdirs()
                    val BinDir = File(filesDir.absolutePath+"/bin")
                    BinDir.mkdirs()

                    AssetsUtils.extractAssetsDir(this, "all/scripts", this.filesDir.absolutePath+"/scripts")
                    setPermissions(AllDir)

                    AssetsUtils.extractAssetsDir(this, "arm/static/bin", this.filesDir.absolutePath+"/bin")
                    setPermissions(BinDir)

                    with (sharedPref.edit()) {
                        putBoolean("issafepts", true)
                        commit()
                    }
                    Log.d("AArchDroid", "NeoTermActivity: issafepts set to true")
                }

                checkinstallterm()

                enterMain()

                update_colors()

                get_motherfucker_battery()

            }

            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                Log.d("AArchDroid", "NeoTermActivity: notifications disabled — continuing anyway")
            }

        } else {
            Log.d("AArchDroid", "NeoTermActivity: onServiceConnected but recreating — skipping asset extraction")
        }
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
                addNewSessionFromExisting(session)
            }

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

        } else {
            Log.d("AArchDroid", "NeoTermActivity: no existing sessions — creating first session")
            toggleSwitcher(showSwitcher = true, easterEgg = false)

            try {

                addNewSession(null, false, createRevealAnimation())
                Log.d("AArchDroid", "NeoTermActivity: first session created successfully")

            } catch (e: Exception) {
                Log.e("AArchDroid", "NeoTermActivity: addNewSession failed — " + e.message)
                val intent = Intent(AArchDroidApp.get(), NeoTermActivity::class.java)
                startActivity(intent)
                finish()
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

    private fun addNewSessionWithProfile(profile: ShellProfile) {
        addNewSessionWithProfile(null, getSystemShellMode(),
                createRevealAnimation(), profile)
    }

    private fun addNewSessionWithProfile(sessionName: String?, systemShell: Boolean,
                                         animation: Animation, profile: ShellProfile) {
        Log.d("AArchDroid", "NeoTermActivity: addNewSessionWithProfile — systemShell=" + systemShell +
                " profile=" + profile.profileName)

        val sessionCallback = TermSessionCallback()
        val viewClient = TermViewClient(this)

        val parameter = ShellParameter()
                .callback(sessionCallback)
                .systemShell(systemShell)
                .profile(profile)

        // SELinux Enforcing blocks execvp() of app_data_file by untrusted_app.
        // Also, chroot operations (mount, chroot, write under /data/local/) need root.
        // Run archdroid.sh via su so it runs as root (ksu domain).
        val defaultScript = AArchDroidApp.get().filesDir.absolutePath + "/bin/archdroid.sh"
        if (!systemShell && profile.loginShell == defaultScript) {
            parameter.executablePath("su")
            parameter.arguments(arrayOf("su", "-c", "/system/bin/sh " + defaultScript))
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

        val tab = createTab(session.mSessionName) as TermTab
        tab.termData.initializeSessionWith(session, sessionCallback, viewClient)

        addNewTab(tab, animation)
        switchToSession(tab)
        Log.d("AArchDroid", "NeoTermActivity: tab added and switched")
    }

    private fun addNewSessionFromExisting(session: TerminalSession?) {
        if (session == null) {
            return
        }

        // Do not add the same session again
        // Or app will crash when rotate
        val tabCount = tabSwitcher.count
        (0..(tabCount - 1))
                .map { tabSwitcher.getTab(it) }
                .filter { it is TermTab && it.termData.termSession == session }
                .forEach { return }

        val sessionCallback = session.sessionChangedCallback as TermSessionCallback
        val viewClient = TermViewClient(this)

        val tab = createTab(session.title) as TermTab
        tab.termData.initializeSessionWith(session, sessionCallback, viewClient)

        addNewTab(tab, createRevealAnimation())
        switchToSession(tab)
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
        toggleSwitcher(showSwitcher = true, easterEgg = false)
        tabSwitcher.removeTab(tab)

        if (tabSwitcher.count > 1) {
            var index = tabSwitcher.indexOf(tab)
            if (NeoPreference.isNextTabEnabled()) {
                // 关闭当前窗口后，向下一个窗口切换
                if (--index < 0) index = tabSwitcher.count - 1
            } else {
                // 关闭当前窗口后，向上一个窗口切换
                if (++index >= tabSwitcher.count) index = 0
            }
            switchToSession(tabSwitcher.getTab(index))
        }
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


    fun checkinstallterm() {
        val scriptPath = AArchDroidApp.get().filesDir.absolutePath + "/bin/checkmount.sh"
        Log.d("AArchDroid", "NeoTermActivity: checkinstallterm — running " + scriptPath)
        try {
            val tempcmd = Runtime.getRuntime().exec(arrayOf("su", "-c", scriptPath + " " + AArchDroidApp.get().applicationInfo.dataDir))
            val stderrReader = Thread { try { tempcmd.errorStream.use { it.readBytes() } } catch (_: Exception) {} }
            stderrReader.start()
            tempcmd.inputStream.use { it.readBytes() }
            stderrReader.join(1000)
            tempcmd.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
            val exitVal = tempcmd.exitValue()
            Log.d("AArchDroid", "NeoTermActivity: checkmount.sh exit code = " + exitVal)

            if (exitVal != 0) {
                Log.d("AArchDroid", "NeoTermActivity: chroot not found — extracting embedded rootfs")
                extractEmbeddedRootfs()
            } else {
                Log.d("AArchDroid", "NeoTermActivity: chroot is mounted")
                AArchDroidApp.get().checkcoreversion()
            }
        } catch (e: Exception) {
            Log.e("AArchDroid", "NeoTermActivity: checkinstallterm failed — " + e.message)
        }
    }

    private fun extractEmbeddedRootfs() {
        Thread {
            try {
                val CHROOT_DIR = "/data/local/aarchdroid"
                val BUSYBOX_DST = "/data/data/org.aarchdroid/files/bin/busybox"

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

    fun isMounted(path: String): Boolean {
        try {
            return File("/proc/mounts").readText().contains(path)
        } catch (e: Exception) {
            return false
        }
    }

    fun findExternalSd(): String? {
        val mounts = suRunOutput("cat /proc/mounts 2>/dev/null") ?: return null
        // Look for block device mounts under /storage/ or /mnt/ (external SD)
        for (line in mounts.lines()) {
            val parts = line.split(" ")
            if (parts.size < 2) continue
            val dev = parts[0]
            val path = parts[1]
            if (dev.startsWith("/dev/block/") && !path.contains("emulated")) {
                if (path.startsWith("/storage/") || path.startsWith("/mnt/")) return path
            }
        }
        // Fallback: scan known paths
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

    fun showNoRootDialog() {
        runOnUiThread {
            val dialog = Dialog(this)
            dialog.setContentView(R.layout.dialog_root_required)
            dialog.setCancelable(false)

            val title = dialog.findViewById<android.widget.TextView>(R.id.dialog_title)
            val message = dialog.findViewById<android.widget.TextView>(R.id.dialog_message)
            val retryBtn = dialog.findViewById<android.widget.Button>(R.id.btn_retry)
            val rejectBtn = dialog.findViewById<android.widget.Button>(R.id.btn_reject)

            title.text = "Se requiere acceso Root"
            message.text = "AArchDroid Terminal necesita acceso root " +
                    "para funcionar completamente.\n\n" +
                    "No se detectó root en este dispositivo."
            retryBtn.text = "REINTENTAR"
            rejectBtn.text = "RECHAZAR"

            retryBtn.setOnClickListener {
                dialog.dismiss()
                Thread {
                    val ok = isRooted(this@NeoTermActivity)
                    runOnUiThread {
                        rootAvailable = ok
                        if (ok) {
                            Log.d("AArchDroid", "NeoTermActivity: root granted — continuing")
                        } else {
                            showNoRootDialog()
                        }
                    }
                }.start()
            }

            rejectBtn.setOnClickListener {
                Log.d("AArchDroid", "NeoTermActivity: REJECT — exiting")
                finishAffinity()
                finishAndRemoveTask()
            }

            dialog.show()
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


    fun setPermissions(path: File?) {

        if (path == null) return
        if (path.exists()) {
            path.setReadable(true, false)
            path.setExecutable(true, false)
            val list = path.listFiles() ?: return
            for (f in list) {
                if (f.isDirectory) setPermissions(f)
                f.setReadable(true, false)
                f.setExecutable(true, false)
            }
        }


        var reader: BufferedReader? = null
        var testmsg=""
        var result = false
        var stdin: OutputStream? = null
        val stdout: InputStream
        val params = ArrayList<String>()

        try {
            val pb = ProcessBuilder("su")
            pb.directory(File(AArchDroidApp.get().applicationInfo.dataDir))
            pb.redirectErrorStream(true)
            val process: Process = pb.start()
            stdin = process.outputStream
            stdout = process.inputStream
            params.add(0, "chmod -R 777 " + AArchDroidApp.get().filesDir.absolutePath)
            params.add(1, "rm -rf" + AArchDroidApp.get().filesDir.absolutePath + "/bin/su")
            params.add("exit 0")

            var os: DataOutputStream? = null

            try {
                os = DataOutputStream(stdin)
                for (cmd in params) {
                    os.writeBytes(cmd + "\n")
                }
                os.flush()
            } catch (e: IOException) {
                //e.printStackTrace()
            } finally {
                os?.close()
            }

            reader = BufferedReader(InputStreamReader(stdout))
            var n: Int
            val buffer = CharArray(1024)
            while (reader.read(buffer).also { n = it } != -1) {
                val msg = String(buffer, 0, n)

                testmsg += msg

            }

            if (process.waitFor() == 0) result = true
        } catch (e: Exception) {
            result = false
            //e.printStackTrace()
        } finally {
            reader?.close()
            stdin?.close()


        }
    }


    fun isRooted(c:Context):Boolean {
        var result = false
        var stdin: OutputStream? = null
        var stdout: InputStream? = null
        var process: Process? = null

        try {
            process = Runtime.getRuntime().exec("su")
            stdin = process.getOutputStream()
            stdout = process.getInputStream()
            var os: DataOutputStream? = null

            try {
                os = DataOutputStream(stdin)
                os.writeBytes("ls /data\n")
                os.writeBytes("exit\n")
                os.flush()
            }

            catch (e:IOException) {
                e.printStackTrace()
            }

            finally {
                os?.close()
            }

            var n = 0
            var reader: BufferedReader? = null

            try {
                reader = BufferedReader(InputStreamReader(stdout))
                while (reader.readLine() != null) {
                    n++
                }
            }

            catch (e:IOException) {
                e.printStackTrace()
            }

            finally {
                reader?.close()
            }

            if (n > 0) {
                result = true
            }
        }
        catch (e:IOException) {
            e.printStackTrace()
        }

        finally {
            stdout?.close()
            stdin?.close()
            process?.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
            process?.destroy()
        }

        if (!result) {
            android.util.Log.w("NeoTerm", "Root check failed — no root access")
        }

        return result
    }


}
