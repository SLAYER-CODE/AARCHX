package org.aarchdroid;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.navigation.NavigationView;
import org.aarchdroid.andraxdialogs.Alert;
import org.aarchdroid.codehackide.MainActivityCodeHackIDE;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import org.aarchdroid.dragonterminal.bridge.Bridge;

/* JADX INFO: loaded from: classes2.dex */
public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    public static final int progressType = 0;
    private ProgressDialog progressDialog;
    private ProgressDialog unpackprogressDialog;
    int install_return = 0;
    int is_debug_build = 0;
    ActivityResultLauncher<Intent> install_dialog_result = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() { // from class: org.snakesecurity.andrax.MainActivity.1
        static final /* synthetic */ boolean $assertionsDisabled = false;

        @Override // androidx.activity.result.ActivityResultCallback
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
    ActivityResultLauncher<Intent> uninstall_dialog_result = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() { // from class: org.snakesecurity.andrax.MainActivity.2
        static final /* synthetic */ boolean $assertionsDisabled = false;

        @Override // androidx.activity.result.ActivityResultCallback
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
        intent.putExtra("content", "Are you sure you want to uninstall ANDRAX-NG?\n\nBy clicking the “OK” button, all files in the container will be destroyed!");
        intent.putExtra("ok_button", true);
        intent.putExtra("cancel_button", true);
        this.uninstall_dialog_result.launch(intent);
    }

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        Log.d("AArchDroid", "MainActivity: onCreate() — starting main dashboard");
        super.onCreate(bundle);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(128);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build());
        try {
            Log.d("AArchDroid", "MainActivity: remounting /data as exec,suid,dev,rw");
            Runtime.getRuntime().exec("su -c toybox mount -o remount,exec,suid,dev,rw /data").waitFor();
        } catch (Exception e) {
            Log.e("AArchDroid", "MainActivity: remount failed — " + e.getMessage());
            e.printStackTrace();
        }
        Log.d("AArchDroid", "MainActivity: loading MainFragment");
        MainFragment mainFragment = new MainFragment();
        FragmentTransaction fragmentTransactionBeginTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransactionBeginTransaction.replace(R.id.fragment_container, mainFragment);
        fragmentTransactionBeginTransaction.commit();
        DrawerLayout drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle actionBarDrawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.setDrawerListener(actionBarDrawerToggle);
        actionBarDrawerToggle.syncState();
        ((NavigationView) findViewById(R.id.nav_view)).setNavigationItemSelectedListener(this);
        new Handler().postDelayed(new Runnable() { // from class: org.snakesecurity.andrax.MainActivity.3
            @Override // java.lang.Runnable
            public void run() {
                try {
                    Log.d("AArchDroid", "MainActivity: running checkmount.sh");
                    Process processExec = Runtime.getRuntime().exec("su -c /data/data/org.aarchdroid/files/scripts/checkmount.sh");
                    processExec.waitFor();
                    MainActivity.this.install_return = processExec.exitValue();
                    Log.d("AArchDroid", "MainActivity: checkmount.sh exit code = " + MainActivity.this.install_return);
                } catch (Exception e2) {
                    Log.e("AArchDroid", "MainActivity: checkmount.sh failed — " + e2.getMessage());
                    e2.printStackTrace();
                }
                if (MainActivity.this.install_return != 0) {
                    Log.d("AArchDroid", "MainActivity: core not mounted — calling install dialog");
                    MainActivity.this.call_install_dialog();
                }
            }
        }, 1000L);
        isRooted(this);
        if (this.is_debug_build == 1) {
            Log.d("AArchDroid", "MainActivity: debug build — launching InstallActivity");
            startActivity(new Intent(this, (Class<?>) InstallActivity.class));
        }
    }

    @Override // androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onResume() {
        super.onResume();
    }

    @Override // androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onPause() {
        super.onPause();
    }

    @Override // androidx.activity.ComponentActivity, android.app.Activity
    public void onBackPressed() {
        DrawerLayout drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            finish();
        }
    }

    @Override // android.app.Activity
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override // android.app.Activity
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        int itemId = menuItem.getItemId();
        if (itemId == R.id.action_help) {
            String str = "mailto:weidsom@snakesecurity.org?subject=" + Uri.encode("About ANDRAX-NG");
            Intent intent = new Intent("android.intent.action.SENDTO");
            intent.setData(Uri.parse(str));
            try {
                startActivity(intent);
            } catch (Exception unused) {
            }
        } else if (itemId == R.id.action_about) {
            startActivity(new Intent(this, (Class<?>) AboutANDRAX.class));
        } else if (itemId == R.id.action_uninstall) {
            call_uninstall_dialog();
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override // com.google.android.material.navigation.NavigationView.OnNavigationItemSelectedListener
    public boolean onNavigationItemSelected(MenuItem menuItem) {
        int itemId = menuItem.getItemId();
        if (itemId == R.id.nav_terminal) {
            run_hack_cmd("andrax");
        } else if (itemId == R.id.nav_codehackide) {
            startActivity(new Intent(this, (Class<?>) MainActivityCodeHackIDE.class));
        } else if (itemId == R.id.nav_hidrastrike) {
            HIDraStrikeFragment hIDraStrikeFragment = new HIDraStrikeFragment();
            FragmentTransaction fragmentTransactionBeginTransaction = getSupportFragmentManager().beginTransaction();
            fragmentTransactionBeginTransaction.replace(R.id.fragment_container, hIDraStrikeFragment);
            fragmentTransactionBeginTransaction.commit();
        } else if (itemId == R.id.nav_telegram) {
            startActivity(new Intent("android.intent.action.VIEW", Uri.parse("https://t.me/snakesecurityofficial")));
        } else if (itemId == R.id.nav_x) {
            startActivity(new Intent("android.intent.action.VIEW", Uri.parse("https://x.com/snakesecurityOF")));
        } else if (itemId == R.id.nav_github) {
            startActivity(new Intent("android.intent.action.VIEW", Uri.parse("https://github.com/snakesec")));
        }
        ((DrawerLayout) findViewById(R.id.drawer_layout)).closeDrawer(GravityCompat.START);
        return true;
    }

    public void run_hack_cmd(String str) {
        Log.d("AArchDroid", "MainActivity: run_hack_cmd(\"" + str + "\") — creating Bridge execute intent");
        Intent intentCreateExecuteIntent = Bridge.createExecuteIntent(str);
        intentCreateExecuteIntent.setFlags(131072);
        Log.d("AArchDroid", "MainActivity: starting activity with intent action=" + intentCreateExecuteIntent.getAction());
        startActivity(intentCreateExecuteIntent);
    }

    public boolean isRooted(Context context) {
        try {
            Process process = Runtime.getRuntime().exec("su -c id");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            process.waitFor();
            boolean rooted = line != null && line.contains("uid=0");
            reader.close();
            if (!rooted) {
                startActivity(new Intent(this, RootIt.class));
                finish();
            }
            return rooted;
        } catch (Exception e) {
            e.printStackTrace();
            startActivity(new Intent(this, RootIt.class));
            finish();
            return false;
        }
    }

    @Override // android.app.Activity, android.view.Window.Callback
    public void onWindowFocusChanged(boolean z) {
        super.onWindowFocusChanged(z);
        if (z) {
            hideSystemUI();
        }
    }

    private void showSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(1792);
    }
}
