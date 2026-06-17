package org.aarchdroid;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.aarchdroid.andraxdialogs.Alert;
import org.aarchdroid.codehackide.MainActivityCodeHackIDE;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aarchdroid.ToolDatabase;
import org.aarchdroid.ToolInfo;
import org.aarchdroid.dragonterminal.bridge.Bridge;
import org.aarchdroid.drawer.DrawerAdapter;
import org.aarchdroid.drawer.DrawerItem;
import org.aarchdroid.drawer.DrawerSection;

public class MainActivity extends AppCompatActivity implements DrawerAdapter.OnItemClickListener {
    private static final String TAG = "MainActivity";
    public static final int progressType = 0;
    private ProgressDialog progressDialog;
    private ProgressDialog unpackprogressDialog;
    int install_return = 0;
    int is_debug_build = 0;

    private static final String CHROOT_DIR = "/data/local/aarchdroid";
    private static final String MARKER = CHROOT_DIR + "/.aarchdroid_chroot";
    private static final String BUSYBOX_DST = "/data/data/org.aarchdroid/files/bin/busybox";
    private static final String BUSYBOX_SRC = "arm/static/bin/busybox";
    private static final String DEBUG_LOG = "/sdcard/aarchdroid_debug.log";

    private TextView logText;
    private ScrollView logScroll;
    private Button retryBtn;
    private Button exitBtn;
    private Toolbar toolbar;
    private View logCuadro;
    private DrawerLayout drawerLayout;
    private boolean isFragmentOpen;
    private View gridContainer;
    private final Set<String> processingTools = new HashSet<>();

    ActivityResultLauncher<Intent> install_dialog_result = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
        static final /* synthetic */ boolean $assertionsDisabled = false;

        @Override
        public void onActivityResult(ActivityResult activityResult) {
            Process processExec;
            if (activityResult.getResultCode() == -1) {
                activityResult.getData();
                try {
                    Process processExec2 = Runtime.getRuntime().exec("su -c /data/data/org.aarchdroid/files/bin/busybox test -f /sdcard/Download/" + MainActivity.this.getString(R.string.andraxportablecoreenc));
                    processExec2.waitFor();
                    if (processExec2.exitValue() == 0) {
                        MainActivity.this.startActivity(new Intent(MainActivity.this, (Class<?>) InstallActivity.class));
                        MainActivity.this.finish();
                        return;
                    }
                    try {
                        if (MainActivity.this.is_debug_build == 1) {
                            processExec = Runtime.getRuntime().exec("su -c /data/data/org.aarchdroid/files/bin/busybox test -f /sdcard/Download/arch-rootfs.tar.bz2");
                        } else {
                            processExec = Runtime.getRuntime().exec("su -c /data/data/org.aarchdroid/files/bin/busybox test -f /sdcard/Download/" + MainActivity.this.getString(R.string.andraxportablecorenoenc));
                        }
                        processExec.waitFor();
                        if (processExec.exitValue() == 0) {
                            MainActivity.this.startActivity(new Intent(MainActivity.this, (Class<?>) InstallActivity.class));
                            MainActivity.this.finish();
                            return;
                        }
                        Intent intent = new Intent(MainActivity.this, (Class<?>) Alert.class);
                        intent.putExtra("icon", "afos-ng-not-found");
                        intent.putExtra("title", "CORE NOT FOUND!!!");
                        intent.putExtra("subtitle", "Well... you have a PROBLEM");
                        intent.putExtra("content", "I don't know how you got here...\n\nDid you read the documentation?\n\nIn any case, the CORE file for the installation was not found!\n\nThis is a fatal error!\n\nBye!");
                        intent.putExtra("ok_button", false);
                        intent.putExtra("cancel_button", false);
                        MainActivity.this.startActivity(intent);
                        MainActivity.this.finish();
                        return;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } catch (Exception e2) {
                    throw new RuntimeException(e2);
                }
            }
            if (activityResult.getResultCode() == 0) {
                MainActivity.this.finish();
            }
        }
    });
    ActivityResultLauncher<Intent> uninstall_dialog_result = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
        static final /* synthetic */ boolean $assertionsDisabled = false;

        @Override
        public void onActivityResult(ActivityResult activityResult) {
            if (activityResult.getResultCode() == -1) {
                activityResult.getData();
                MainActivity.this.startActivity(new Intent(MainActivity.this, (Class<?>) UninstallANDRAX.class));
                MainActivity.this.finish();
                return;
            }
            activityResult.getResultCode();
        }
    });

    private void hideSystemUI() {
    }

    static {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }

    public void call_install_dialog() {
        Intent intent = new Intent(this, (Class<?>) Alert.class);
        intent.putExtra("icon", "afos-ng");
        intent.putExtra("title", "Install ANDRAX-NG?");
        intent.putExtra("subtitle", "ANDRAX-NG is not yet installed");
        intent.putExtra("content", "Do you want to install ANDRAX-NG now?\nIf so, press INSTALL to continue...");
        intent.putExtra("ok_button", true);
        intent.putExtra("cancel_button", true);
        this.install_dialog_result.launch(intent);
    }

    public void call_uninstall_dialog() {
        Intent intent = new Intent(this, (Class<?>) Alert.class);
        intent.putExtra("icon", "error");
        intent.putExtra("title", "Uninstall ANDRAX-NG?");
        intent.putExtra("subtitle", "This action can't be undone!");
        intent.putExtra("content", "Are you sure you want to uninstall ANDRAX-NG?\n\nBy clicking the \u201cOK\u201d button, all files in the container will be destroyed!");
        intent.putExtra("ok_button", true);
        intent.putExtra("cancel_button", true);
        this.uninstall_dialog_result.launch(intent);
    }

    @Override
    protected void onCreate(Bundle bundle) {
        Log.d("AArchDroid", "MainActivity: onCreate()");
        super.onCreate(bundle);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(128);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle("AArchDroid");
        toolbar.setTitleTextColor(0xFF00FF00);

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build());

        logCuadro = findViewById(R.id.log_cuadro);
        logText = findViewById(R.id.log_text);
        logScroll = findViewById(R.id.log_scroll);
        retryBtn = findViewById(R.id.btn_retry);
        exitBtn = findViewById(R.id.btn_exit);
        gridContainer = findViewById(R.id.grid_container);

        drawerLayout = findViewById(R.id.drawer_layout);
        toolbar.setNavigationIcon(R.drawable.ic_hamburger);
        toolbar.setNavigationOnClickListener(v -> {
            if (isFragmentOpen) {
                closeFragment();
            } else if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });
        setupDrawer();

        // Hide toolbar until root/install complete
        toolbar.setVisibility(View.GONE);

        // Show log cuadro (visible by default in XML)
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        retryBtn.setOnClickListener(v -> {
            retryBtn.setEnabled(false);
            exitBtn.setEnabled(false);
            appendLog("[*] Verificando root...");
            new Thread(() -> {
                final boolean rooted = checkRoot();
                runOnUiThread(() -> {
                    if (rooted) {
                        appendLog("[+] Root detectado!");
                        showToolbarAnimated();
                        doInstall();
                    } else {
                        appendLog("[-] Root no detectado.");
                        retryBtn.setEnabled(true);
                        exitBtn.setEnabled(true);
                    }
                });
            }).start();
        });

        exitBtn.setOnClickListener(v -> {
            appendLog("[!] Saliendo...");
            finishAffinity();
            finishAndRemoveTask();
        });

        // Auto-check root on startup
        appendLog("[*] Verificando instalacion...");
        new Thread(() -> {
            try {
                if (isInstallComplete()) {
                    runOnUiThread(() -> {
                        showToolbarAnimated();
                        enableLauncherActivities();
                        appendLog("[+] AArchDroid ya instalado.");
                        completeSetup();
                    });
                    return;
                }
                boolean installed = checkRootfsInstalled();
                boolean rooted = checkRoot();
                if (installed) {
                    runOnUiThread(() -> {
                        showToolbarAnimated();
                        enableLauncherActivities();
                        setInstallComplete();
                        appendLog("[+] Rootfs ya instalado.");
                        completeSetup();
                    });
                } else if (rooted) {
                    runOnUiThread(() -> {
                        appendLog("[+] Root detectado.");
                        showToolbarAnimated();
                        doInstall();
                    });
                } else {
                    runOnUiThread(() -> {
                        appendLog("[-] Root no detectado.");
                        appendLog("[!] Se requiere root para instalar.");
                        retryBtn.setVisibility(View.VISIBLE);
                        exitBtn.setVisibility(View.VISIBLE);
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    appendLog("[!] Error: " + e.getMessage());
                });
            }
        }).start();

        // Scroll indicator at bottom of log area
        ProgressBar scrollIndicator = findViewById(R.id.scroll_indicator);
        logScroll.getViewTreeObserver().addOnScrollChangedListener(() -> {
            int max = logScroll.getChildAt(0).getHeight() - logScroll.getHeight();
            if (max <= 0) {
                scrollIndicator.setProgress(scrollIndicator.getMax());
            } else {
                int pct = logScroll.getScrollY() * scrollIndicator.getMax() / max;
                scrollIndicator.setProgress(pct);
            }
        });

        logCuadro.setOnClickListener(v -> expandToGrid());

        findViewById(R.id.btn_terminal_box).setOnClickListener(v ->
            run_hack_cmd("andrax"));

        findViewById(R.id.btn_nvim_box).setOnClickListener(v ->
            startActivity(new Intent(this,
                org.aarchdroid.neovim.NeovimEditorActivity.class)));

        findViewById(R.id.btn_wifi_box).setOnClickListener(v ->
            run_hack_cmd("wifite2"));

        findViewById(R.id.btn_tor_box).setOnClickListener(v ->
            run_hack_cmd("torbrowser"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        processExitFiles();
        refreshDrawerStatuses();
    }

    private void refreshDrawerStatuses() {
        RecyclerView rv = findViewById(R.id.drawer_recycler);
        if (rv == null || rv.getAdapter() == null) return;
        ((DrawerAdapter) rv.getAdapter()).refreshStatuses(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (isFragmentOpen) {
            closeFragment();
        } else if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            finish();
        }
    }

    private void closeFragment() {
        FrameLayout fc = findViewById(R.id.fragment_container);
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (f != null) {
            getSupportFragmentManager().beginTransaction().remove(f).commit();
        }
        fc.setVisibility(View.GONE);
        toolbar.setNavigationIcon(R.drawable.ic_hamburger);
        isFragmentOpen = false;
        invalidateOptionsMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem gear = menu.findItem(R.id.action_settings);
        if (gear != null) gear.setVisible(!isFragmentOpen);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.action_settings) {
            PopupMenu popup = new PopupMenu(this, toolbar, Gravity.END);
            popup.getMenu().add("Uninstall");
            popup.setOnMenuItemClickListener(item -> {
                call_uninstall_dialog();
                return true;
            });
            popup.show();
            return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    public boolean onNavigationItemSelected(MenuItem menuItem) {
        int itemId = menuItem.getItemId();
        if (itemId == R.id.nav_terminal) {
            run_hack_cmd("andrax");
        } else if (itemId == R.id.nav_codehackide) {
            startActivity(new Intent(this, (Class<?>) MainActivityCodeHackIDE.class));
        } else if (itemId == R.id.nav_neovim) {
            startActivity(new Intent(this, (Class<?>) org.aarchdroid.neovim.NeovimEditorActivity.class));
        } else if (itemId == R.id.nav_hid_keyboard) {
            toolbar.post(() -> replaceFragment(new HIDKeyboardFragment()));
        } else if (itemId == R.id.nav_mana) {
            toolbar.post(() -> replaceFragment(new ManaFragment()));
        } else if (itemId == R.id.nav_mitm) {
            toolbar.post(() -> replaceFragment(new MITMFragment()));
        } else if (itemId == R.id.nav_mac_changer) {
            toolbar.post(() -> replaceFragment(new MacChangerFragment()));
        } else if (itemId == R.id.nav_bluetooth) {
            toolbar.post(() -> replaceFragment(new BluetoothFragment()));
        } else if (itemId == R.id.nav_usb_arsenal) {
            toolbar.post(() -> replaceFragment(new USBArsenalFragment()));
        } else if (itemId == R.id.nav_gps) {
            toolbar.post(() -> replaceFragment(new GPSFragment()));
        } else if (itemId == R.id.nav_kali_services) {
            toolbar.post(() -> replaceFragment(new KaliServicesFragment()));
        } else if (itemId == R.id.nav_custom_commands) {
            toolbar.post(() -> replaceFragment(new CustomCommandsFragment()));
        }
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onItemClick(DrawerItem item) {
        if (item.toolKey != null) {
            String status = ToolDatabase.getInstance().getStatus(item.toolKey);
            if ("installed".equals(status)) {
                run_hack_cmd(item.toolKey);
            } else {
                drawerInstallTool(item.toolKey);
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        int itemId = item.id;
        if (itemId == R.id.nav_terminal) {
            run_hack_cmd("andrax");
        } else if (itemId == R.id.nav_codehackide) {
            startActivity(new Intent(this, (Class<?>) MainActivityCodeHackIDE.class));
        } else if (itemId == R.id.nav_neovim) {
            startActivity(new Intent(this, (Class<?>) org.aarchdroid.neovim.NeovimEditorActivity.class));
        } else if (itemId == R.id.nav_hid_keyboard) {
            toolbar.post(() -> replaceFragment(new HIDKeyboardFragment()));
        } else if (itemId == R.id.nav_mana) {
            toolbar.post(() -> replaceFragment(new ManaFragment()));
        } else if (itemId == R.id.nav_mitm) {
            toolbar.post(() -> replaceFragment(new MITMFragment()));
        } else if (itemId == R.id.nav_mac_changer) {
            toolbar.post(() -> replaceFragment(new MacChangerFragment()));
        } else if (itemId == R.id.nav_bluetooth) {
            toolbar.post(() -> replaceFragment(new BluetoothFragment()));
        } else if (itemId == R.id.nav_usb_arsenal) {
            toolbar.post(() -> replaceFragment(new USBArsenalFragment()));
        } else if (itemId == R.id.nav_gps) {
            toolbar.post(() -> replaceFragment(new GPSFragment()));
        } else if (itemId == R.id.nav_kali_services) {
            toolbar.post(() -> replaceFragment(new KaliServicesFragment()));
        } else if (itemId == R.id.nav_custom_commands) {
            toolbar.post(() -> replaceFragment(new CustomCommandsFragment()));
        }
        drawerLayout.closeDrawer(GravityCompat.START);
    }

    private void setupDrawer() {
        RecyclerView rv = findViewById(R.id.drawer_recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setItemAnimator(null);
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);

        Menu menu = new PopupMenu(this, null).getMenu();
        new MenuInflater(this).inflate(R.menu.activity_main_drawer, menu);

        List<DrawerSection> sections = new ArrayList<>();
        for (int i = 0; i < menu.size(); i++) {
            MenuItem cat = menu.getItem(i);
            SubMenu sub = cat.getSubMenu();
            if (sub == null) continue;
            boolean isCollapsed = i >= 6;
            List<DrawerItem> items = new ArrayList<>();
            for (int j = 0; j < sub.size(); j++) {
                MenuItem mi = sub.getItem(j);
                DrawerItem di = new DrawerItem(mi.getItemId(),
                    mi.getIcon(), mi.getTitle());
                if (isCollapsed) {
                    String entryName = getResources().getResourceEntryName(mi.getItemId());
                    if (entryName.startsWith("nav_")) {
                        String tk = entryName.substring(4);
                        ToolInfo info = ToolDatabase.getInstance().getTool(tk);
                        if (info != null) {
                            di.toolKey = tk;
                            di.source = info.source;
                        }
                    }
                }
                items.add(di);
            }
            DrawerSection section = new DrawerSection(cat.getTitle(), items);
            sections.add(section);
        }
        // Collapse sections after Anonymity (index 5)
        for (int i = 6; i < sections.size(); i++) {
            sections.get(i).expanded = false;
        }

        // Process exit files and clean stale installs
        processExitFiles();

        Map<String, String> statuses = new HashMap<>();
        Map<String, Long> sizes = new HashMap<>();
        for (int i = 6; i < sections.size(); i++) {
            for (DrawerItem di : sections.get(i).items) {
                if (di.toolKey != null) {
                    String status = ToolDatabase.getInstance().getStatus(di.toolKey);
                    statuses.put(di.toolKey, status);
                    ToolInfo info = ToolDatabase.getInstance().getTool(di.toolKey);
                    if (info != null && info.actualSizeBytes > 0) {
                        sizes.put(di.toolKey, info.actualSizeBytes);
                    }
                }
            }
        }

        DrawerAdapter adapter = new DrawerAdapter(sections, rv);
        adapter.updateStatuses(statuses, sizes);
        adapter.setOnItemClickListener(this);
        rv.setAdapter(adapter);
    }

    private void replaceFragment(Fragment fragment) {
        FrameLayout fc = findViewById(R.id.fragment_container);
        fc.setVisibility(View.VISIBLE);
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        ft.replace(R.id.fragment_container, fragment);
        ft.commit();
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_green);
        isFragmentOpen = true;
        invalidateOptionsMenu();
    }

    private void processExitFiles() {
        try {
            File stateDir = new File(getFilesDir(), "install-state");
            if (!stateDir.exists()) return;
            File[] files = stateDir.listFiles();
            if (files == null) return;
            boolean changed = false;
            for (File f : files) {
                String name = f.getName();
                if (!name.endsWith(".exit")) continue;
                String toolKey = name.substring(0, name.length() - 5);
                try {
                    String content = new String(new FileInputStream(f).readAllBytes()).trim();
                    int exitCode = Integer.parseInt(content);
                    boolean isUninstall = new File(stateDir, toolKey + ".uninstall").exists();
                    if (isUninstall) {
                        if (exitCode == 0) {
                            ToolDatabase.getInstance().markUninstalled(toolKey);
                        } else {
                            ToolDatabase.getInstance().markInstalled(toolKey);
                        }
                        new File(stateDir, toolKey + ".uninstall").delete();
                    } else if (exitCode == 0) {
                        ToolDatabase.getInstance().markInstalled(toolKey);
                    } else {
                        File logFile = new File(stateDir, toolKey + ".log");
                        String error = "";
                        if (logFile.exists()) {
                            byte[] logBytes = new FileInputStream(logFile).readAllBytes();
                            error = new String(logBytes);
                            if (error.length() > 1000)
                                error = error.substring(error.length() - 1000);
                        }
                        ToolDatabase.getInstance().markFailed(toolKey, error);
                    }
                    f.delete();
                    new File(stateDir, toolKey + ".log").delete();
                    changed = true;
                } catch (Exception e) {
                    Log.e("MainActivity", "processExitFiles: error for " + toolKey, e);
                }
            }
            // Stale: tools stuck in "installing"/"uninstalling" with no .exit or .pending file
            if (changed) {
                List<ToolInfo> allTools = ToolDatabase.getInstance().getAllTools();
                if (allTools != null) {
                    for (ToolInfo ti : allTools) {
                        File exitFile = new File(stateDir, ti.toolKey + ".exit");
                        File pendingFile = new File(stateDir, ti.toolKey + ".pending");
                        if (exitFile.exists() || pendingFile.exists()) continue;
                        if ("installing".equals(ti.status)) {
                            ToolDatabase.getInstance().markFailed(ti.toolKey, "Installation aborted or state lost");
                        } else if ("uninstalling".equals(ti.status)) {
                            ToolDatabase.getInstance().markInstalled(ti.toolKey);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e("MainActivity", "processExitFiles error", e);
        }
    }

    private void drawerInstallTool(String toolKey) {
        String nk = ToolDatabase.normalizeKey(toolKey);
        if (!processingTools.add(nk)) return;
        String installCmd = ToolDatabase.getInstance().getInstallCommand(nk);
        android.util.Log.d("MainActivity", "drawerInstallTool(" + nk + ") cmd=" + installCmd);
        if (installCmd != null) {
            try {
                ToolDatabase.getInstance().markInstalling(nk, installCmd);
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "markInstalling failed", e);
                processingTools.remove(nk);
                return;
            }
            new File(getFilesDir(), "install-state").mkdirs();
            try {
                new File(getFilesDir(), "install-state/" + nk + ".pending").createNewFile();
            } catch (Exception ignored) {}
            run_hack_cmd(buildInstallInline(nk, installCmd));
        } else {
            android.util.Log.d("MainActivity", "No install command for " + nk);
            processingTools.remove(nk);
        }
    }

    private String buildInstallInline(String toolKey, String installCmd) {
        String appDir = "/data/data/" + getPackageName() + "/";
        String stateDir = appDir + "files/install-state";
        String logFile = stateDir + "/" + toolKey + ".log";
        String exitFile = stateDir + "/" + toolKey + ".exit";

        if (installCmd.startsWith("pacman ")) {
            installCmd = installCmd.replaceFirst("^pacman ", "pacman --color always --disable-download-timeout ");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("export PATH=/usr/local/sbin:/usr/local/bin:/usr/bin:/usr/sbin:$PATH && mkdir -p '").append(stateDir).append("' && ");
        sb.append("echo; echo -e \"\\033[1;34m[AArchDroid]\\033[0m \\033[1;33mInstalando\\033[0m: ").append(toolKey).append("\"; echo; ");
        sb.append("(").append(installCmd).append("; echo $? > '").append(exitFile).append("') 2>&1 | tee '").append(logFile).append("'; ");
        sb.append("EC=$(cat '").append(exitFile).append("' 2>/dev/null); ");
        if (installCmd.startsWith("pacman")) {
            sb.append("if [ \"$EC\" != \"0\" ]; then ");
            sb.append("echo -e \"\\033[1;33m  -\\033[0m Sync repos...\"; ");
            sb.append("(pacman --color always --disable-download-timeout -Sy; echo $? > '").append(exitFile).append("') 2>&1 | tee -a '").append(logFile).append("'; ");
            sb.append("EC2=$(cat '").append(exitFile).append("' 2>/dev/null); ");
            sb.append("if [ \"$EC2\" = \"0\" ]; then ");
            sb.append("(").append(installCmd).append("; echo $? > '").append(exitFile).append("') 2>&1 | tee -a '").append(logFile).append("'; ");
            sb.append("EC=$(cat '").append(exitFile).append("' 2>/dev/null); ");
            sb.append("else echo -e \"\\033[1;31m  -\\033[0m Sync failed\"; fi; fi; ");
        }
        sb.append("echo; echo ========================================; ");
        sb.append("if [ \"$EC\" = \"0\" ]; then ");
        sb.append("echo -e \"\\033[1;32m  [AArchDroid] OK\\033[0m\"; ");
        sb.append("else ");
        sb.append("echo -e \"\\033[1;31m  [AArchDroid] FAILED (exit $EC)\\033[0m\"; ");
        sb.append("fi");

        String raw = sb.toString();
        String escaped = raw.replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("$", "\\$");
        return "sh -c \"" + escaped + "\"";
    }

    public void run_hack_cmd(String str) {
        Intent intentCreateExecuteIntent = Bridge.createExecuteIntent(str);
        intentCreateExecuteIntent.setFlags(131072);
        startActivity(intentCreateExecuteIntent);
    }

    @Override
    public void onWindowFocusChanged(boolean z) {
        super.onWindowFocusChanged(z);
        if (z) {
            hideSystemUI();
        }
    }

    private void showSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(1792);
    }

    // ──────────────── Instalación ────────────────

    private void appendLog(final String line) {
        runOnUiThread(() -> {
            SpannableStringBuilder ssb = new SpannableStringBuilder(line + "\n");
            int firstBracket = line.indexOf('[');
            int lastBracket = line.indexOf(']');
            if (firstBracket >= 0 && lastBracket > firstBracket) {
                ssb.setSpan(new ForegroundColorSpan(0xFFFF4444),
                    firstBracket, firstBracket + 1, 0);
                ssb.setSpan(new ForegroundColorSpan(0xFFFF4444),
                    lastBracket, lastBracket + 1, 0);
                if (lastBracket > firstBracket + 1) {
                    char sym = line.charAt(firstBracket + 1);
                    int color;
                    switch (sym) {
                        case '*': color = 0xFF4488FF; break;
                        case '-': color = 0xFFFF4444; break;
                        case '!': color = 0xFFFFFF44; break;
                        default:  color = 0xFF00FF00; break;
                    }
                    ssb.setSpan(new ForegroundColorSpan(color),
                        firstBracket + 1, firstBracket + 2, 0);
                }
            }
            logText.append(ssb);
            logScroll.post(() -> logScroll.fullScroll(ScrollView.FOCUS_DOWN));
        });
        Log.d("AArchDroid", line);
        writeFileLog(line);
    }

    private void writeFileLog(String line) {
        try {
            FileOutputStream fos = new FileOutputStream(DEBUG_LOG, true);
            fos.write((line + "\n").getBytes());
            fos.close();
        } catch (Exception e) {}
    }

    private boolean checkRootfsInstalled() {
        try {
            Process p = Runtime.getRuntime().exec(
                new String[]{"su", "-c", "test -f " + MARKER + " && echo installed"});
            BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            r.close();
            p.waitFor();
            return "installed".equals(line);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkRoot() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            p.waitFor();
            r.close();
            return line != null && line.contains("uid=0");
        } catch (Exception e) {
            appendLog("[-] checkRoot fallo: " + e.getMessage());
            return false;
        }
    }

    private void suCp(InputStream src, String dstPath) throws Exception {
        File tmp = new File(getCacheDir(), "tmp_" + new File(dstPath).getName());
        tmp.getParentFile().mkdirs();
        FileOutputStream fos = new FileOutputStream(tmp);
        byte[] buf = new byte[8192];
        int len;
        while ((len = src.read(buf)) != -1) fos.write(buf, 0, len);
        fos.close();
        src.close();
        String tmpPath = tmp.getAbsolutePath();
        Runtime.getRuntime().exec("su -c mkdir -p " + new File(dstPath).getParent()).waitFor();
        Runtime.getRuntime().exec("su -c cp " + tmpPath + " " + dstPath).waitFor();
        Runtime.getRuntime().exec("su -c chmod 0755 " + dstPath).waitFor();
        tmp.delete();
    }

    private void deployBusybox() {
        try {
            appendLog("[*] Instalando busybox...");
            InputStream in = getAssets().open(BUSYBOX_SRC);
            suCp(in, BUSYBOX_DST);
            appendLog("[+] Busybox instalado.");
        } catch (Exception e) {
            appendLog("[-] Busybox fallo: " + e.getMessage());
        }
    }

    private void deployScripts() {
        try {
            appendLog("[*] Instalando scripts...");
            String[] scripts = {"archdroid.sh", "checkmount.sh", "checkinstall.sh",
                "rootshell", "bashrc-aarchdroid", "andraxshell.sh", "modtar"};
            String dstDir = "/data/data/org.aarchdroid/files/scripts/";
            for (String script : scripts) {
                try {
                    InputStream in = getAssets().open("arm/static/bin/" + script);
                    suCp(in, dstDir + script);
                } catch (Exception e) {
                    appendLog("[-] Script " + script + " fallo: " + e.getMessage());
                }
            }
            appendLog("[+] Scripts instalados.");
        } catch (Exception e) {
            appendLog("[-] Scripts fallo: " + e.getMessage());
        }
    }

    private void showToolbarAnimated() {
        toolbar.setVisibility(View.VISIBLE);
        int h = toolbar.getHeight();
        if (h <= 0) h = 150;
        toolbar.setTranslationY(-h);
        toolbar.animate().translationY(0).setDuration(350).start();
    }

    private void setButtonsInstalling() {
        runOnUiThread(() -> {
            exitBtn.setVisibility(View.GONE);
            retryBtn.setEnabled(false);
            retryBtn.setText("Descomprimiendo...");
            retryBtn.setTextSize(14);
            retryBtn.setBackgroundResource(R.drawable.button_hacker_disabled);
            retryBtn.setTextColor(0xFF003300);
            retryBtn.setVisibility(View.VISIBLE);
        });
    }

    private void setButtonsIniciando() {
        runOnUiThread(() -> {
            exitBtn.setVisibility(View.GONE);
            retryBtn.setEnabled(false);
            retryBtn.setText("Iniciando...");
            retryBtn.setTextSize(14);
            retryBtn.setBackgroundResource(R.drawable.button_hacker_disabled);
            retryBtn.setTextColor(0xFF003300);
            retryBtn.setVisibility(View.VISIBLE);
        });
    }

    private void completeSetup() {
        showToolbarAnimated();
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        logCuadro.postDelayed(this::expandToGrid, 400);
    }

    private void expandToGrid() {
        if (gridContainer.getVisibility() == View.VISIBLE) return;
        View logContent = findViewById(R.id.log_content);
        int toolbarH = toolbar.getHeight() > 0 ? toolbar.getHeight() :
            getResources().getDimensionPixelSize(
                androidx.appcompat.R.attr.actionBarSize);
        logContent.post(() -> {
            int targetY = toolbarH + 24;
            float translateY = -(logContent.getTop() - targetY);
            logContent.animate()
                .translationY(translateY)
                .setDuration(200)
                .start();
        });
        gridContainer.setAlpha(0f);
        gridContainer.setVisibility(View.VISIBLE);
        gridContainer.animate()
            .alpha(1f)
            .setDuration(200)
            .start();
    }

    private void installComplete() {
        runOnUiThread(() -> {
            appendLog("[+] Instalacion completada. Abriendo menu...");
            completeSetup();
        });
    }

    private void doInstall() {
        setButtonsInstalling();
        new Thread(() -> {
            try {
                if (checkRootfsInstalled() || isInstallComplete()) {
                    appendLog("[+] Rootfs ya instalado.");
                    setInstallComplete();
                    enableLauncherActivities();
                    installComplete();
                    return;
                }

                appendLog("[*] Remontando /data con permisos de ejecucion...");
                try {
                    Runtime.getRuntime().exec("su -c toybox mount -o remount,exec,suid,dev,rw /data").waitFor();
                } catch (Exception e) {
                    appendLog("[-] mount fallo: " + e.getMessage());
                }

                appendLog("[*] Comenzando instalacion...");
                deployBusybox();
                deployScripts();

                appendLog("[*] Verificando que rootfs.tgz existe en assets...");
                try {
                    InputStream test = getAssets().open("rootfs.tgz");
                    appendLog("[*] rootfs.tgz disponible en assets");
                    test.close();
                } catch (Exception e) {
                    appendLog("[-] rootfs.tgz NO encontrado en assets: " + e.getMessage());
                    throw e;
                }

                appendLog("[*] Creando directorio " + CHROOT_DIR + "...");
                Runtime.getRuntime().exec("su -c mkdir -p " + CHROOT_DIR).waitFor();

                appendLog("[*] Extrayendo rootfs (~30s)...");
                extractRootfs();

                setInstallComplete();
                enableLauncherActivities();
                runOnUiThread(() -> {
                    appendLog("[+] AArchDroid instalado!");
                    appendLog("[*] Directorio: " + CHROOT_DIR);
                    appendLog("[*] Rootfs extraido correctamente.");
                    installComplete();
                });
            } catch (final Exception e) {
                runOnUiThread(() -> {
                    appendLog("[-] Instalacion fallo: " + e.getMessage());
                    retryBtn.setEnabled(true);
                    exitBtn.setEnabled(true);
                    retryBtn.setBackgroundResource(R.drawable.button_hacker);
                    retryBtn.setTextColor(0xFF00FF00);
                    retryBtn.setText("REINTENTAR");
                    exitBtn.setBackgroundResource(R.drawable.button_hacker);
                    exitBtn.setTextColor(0xFF00FF00);
                    exitBtn.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    private boolean isInstallComplete() {
        return getSharedPreferences(getPackageName(), MODE_PRIVATE)
            .getBoolean("install_complete", false);
    }

    private void setInstallComplete() {
        getSharedPreferences(getPackageName(), MODE_PRIVATE)
            .edit().putBoolean("install_complete", true).apply();
    }

    private String detectTar() {
        String[][] candidates = {
            {"tar", "--version"},
            {BUSYBOX_DST, "tar --version"},
        };
        for (String[] c : candidates) {
            String bin = c[0];
            String arg = c[1];
            try {
                appendLog("[*] Probando: " + bin + " " + arg);
                Process p = Runtime.getRuntime().exec("su -c \"" + bin + " " + arg + " 2>&1\"");
                p.waitFor();
                BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream()));
                StringBuilder out = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) out.append(line).append(" | ");
                r.close();
                boolean ok = p.exitValue() == 0 || out.toString().toLowerCase().contains("tar");
                if (ok) {
                    appendLog("[+] Usando: " + bin);
                    return bin;
                }
            } catch (Exception e) {
                appendLog("[-] " + bin + " fallo: " + e.getMessage());
            }
        }
        appendLog("[*] Usando fallback tar");
        return "tar";
    }

    private void extractRootfs() throws Exception {
        String tarBin = detectTar();
        boolean zSupported = false;
        boolean pSupported = false;

        try {
            appendLog("[*] Probando si tar soporta -z...");
            String testCmd = tarBin + " -xzf /dev/null -C /data 2>&1 || true";
            Process test = Runtime.getRuntime().exec(
                new String[]{"su", "-c", testCmd});
            test.waitFor();
            BufferedReader testOut = new BufferedReader(
                new InputStreamReader(test.getInputStream()));
            BufferedReader testErr = new BufferedReader(
                new InputStreamReader(test.getErrorStream()));
            String stdoutStr = "", stderrStr = "";
            String line;
            while ((line = testOut.readLine()) != null) stdoutStr += line + " | ";
            while ((line = testErr.readLine()) != null) stderrStr += line + " | ";
            testOut.close(); testErr.close();
            String combinedOut = stdoutStr + " " + stderrStr;
            zSupported = test.exitValue() == 0
                && !combinedOut.toLowerCase().contains("unrecognized")
                && !combinedOut.toLowerCase().contains("unknown option")
                && !combinedOut.toLowerCase().contains("not found");
            appendLog("[*] Soporte -z: " + zSupported);
        } catch (Exception e) {
            writeFileLog("[-] Test -z exception: " + Log.getStackTraceString(e));
        }

        try {
            appendLog("[*] Probando si tar soporta -P...");
            String testCmd2 = tarBin + " -P -tf /dev/null 2>&1 || true";
            Process test2 = Runtime.getRuntime().exec(
                new String[]{"su", "-c", testCmd2});
            test2.waitFor();
            BufferedReader r2 = new BufferedReader(
                new InputStreamReader(test2.getInputStream()));
            BufferedReader e2 = new BufferedReader(
                new InputStreamReader(test2.getErrorStream()));
            String s2 = "", eStr2 = "";
            String line;
            while ((line = r2.readLine()) != null) s2 += line + " | ";
            while ((line = e2.readLine()) != null) eStr2 += line + " | ";
            r2.close(); e2.close();
            String combined2 = s2 + " " + eStr2;
            pSupported = test2.exitValue() == 0
                && !combined2.toLowerCase().contains("unrecognized")
                && !combined2.toLowerCase().contains("unknown option")
                && !combined2.toLowerCase().contains("not found");
            appendLog("[*] Soporte -P: " + pSupported);
        } catch (Exception e) {
            writeFileLog("[-] Test -P exception: " + Log.getStackTraceString(e));
        }

        String pFlag = pSupported ? " -P" : "";
        String[] flags;
        if (zSupported) {
            flags = new String[]{
                tarBin + pFlag + " -xzf - -C " + CHROOT_DIR,
                tarBin + " -xzf - -C " + CHROOT_DIR,
                "gunzip -c - | " + tarBin + pFlag + " -xf - -C " + CHROOT_DIR,
                "gunzip -c - | " + tarBin + " -xf - -C " + CHROOT_DIR,
            };
        } else {
            flags = new String[]{
                "gunzip -c - | " + tarBin + pFlag + " -xf - -C " + CHROOT_DIR,
                "gunzip -c - | " + tarBin + " -xf - -C " + CHROOT_DIR,
                tarBin + pFlag + " -xf - -C " + CHROOT_DIR,
                tarBin + " -xf - -C " + CHROOT_DIR,
            };
        }

        boolean ok = false;
        for (int attempt = 0; attempt < flags.length; attempt++) {
            String shellCmd = flags[attempt];
            appendLog("[*] Intento " + (attempt+1) + ": su -c \"" + shellCmd + "\"");

            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", shellCmd});

            OutputStream stdin = p.getOutputStream();
            InputStream in = getAssets().open("rootfs.tgz");
            byte[] buf = new byte[8192];
            int len;
            long total = 0;
            boolean pipeBroken = false;
            try {
                while ((len = in.read(buf)) != -1) {
                    stdin.write(buf, 0, len);
                    total += len;
                }
            } catch (java.io.IOException e) {
                pipeBroken = true;
                appendLog("[!] Pipe roto: " + e.getMessage());
            }
            in.close();
            try { stdin.flush(); } catch (Exception e) {}
            try { stdin.close(); } catch (Exception e) {}

            int exitCode = p.waitFor();
            appendLog("[*] Intento " + (attempt+1) + ": " + total + " bytes, exit=" + exitCode
                + (pipeBroken ? " (pipe roto)" : ""));

            if (exitCode == 0 || (exitCode == 1 && total > 1000000)) {
                ok = true;
                break;
            }
        }

        if (!ok) {
            throw new RuntimeException("tar fallo con todos los metodos");
        }
    }

    private void enableLauncherActivities() {
        try {
            PackageManager pm = getPackageManager();
            ComponentName[][] launchers = {
                {new ComponentName(this, Dco_Information_Gathering.class)},
                {new ComponentName(this, Dco_Scanning.class)},
                {new ComponentName(this, Dco_Packet_Crafting.class)},
                {new ComponentName(this, Dco_network_hacking.class)},
                {new ComponentName(this, Dco_bug_bounty.class)},
                {new ComponentName(this, Dco_website_hacking.class)},
                {new ComponentName(this, Dco_phishing.class)},
                {new ComponentName(this, Dco_exploitation.class)},
                {new ComponentName(this, Dco_c2_rat.class)},
                {new ComponentName(this, Dco_macos_iphone.class)},
                {new ComponentName(this, Dco_Password_Hacking.class)},
                {new ComponentName(this, Dco_phreaking.class)},
                {new ComponentName(this, Dco_ics_scada_iot.class)},
                {new ComponentName(this, Dco_Mainframe.class)},
                {new ComponentName(this, Dco_stress_testing.class)},
                {new ComponentName(this, Dco_Wireless_Hacking.class)},
                {new ComponentName(this, Dco_voip_3g_4g.class)},
                {new ComponentName(this, org.aarchdroid.dragonterminal.ui.term.NeoTermActivity.class)},
                {new ComponentName(this, org.aarchdroid.codehackide.MainActivityCodeHackIDE.class)},
            };
            for (ComponentName[] c : launchers) {
                pm.setComponentEnabledSetting(c[0],
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
            }
            appendLog("[+] Launcher icons activados");
        } catch (Exception e) {
            appendLog("[-] Error activando launchers: " + e.getMessage());
        }
    }
}
