package org.aarchdroid;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class SplashActivity extends AppCompatActivity {
    private static final String CHROOT_DIR = "/data/local/aarchdroid";
    private static final String MARKER = CHROOT_DIR + "/.aarchdroid_chroot";
    private static final String BUSYBOX_DST = "/data/data/org.aarchdroid/files/bin/busybox";
    private static final String BUSYBOX_SRC = "arm/static/bin/busybox";
    private static final String DEBUG_LOG = "/sdcard/aarchdroid_debug.log";

    private TextView logText;
    private ScrollView logScroll;
    private Button retryBtn;
    private Button rejectBtn;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_splash);

        logText = findViewById(R.id.log_text);
        logScroll = findViewById(R.id.log_scroll);
        retryBtn = findViewById(R.id.btn_retry);
        rejectBtn = findViewById(R.id.btn_reject);

        // Clear debug log on fresh start
        try {
            new java.io.FileOutputStream(DEBUG_LOG).close();
        } catch (Exception e) {}

        appendLog("[*] Iniciando AArchDroid...");
        appendLog("[*] Verificando acceso root...");

        retryBtn.setOnClickListener(v -> {
            retryBtn.setEnabled(false);
            rejectBtn.setEnabled(false);
            appendLog("[*] Reintentando verificacion de root...");
            new Thread(() -> {
                final boolean rooted = isRooted();
                runOnUiThread(() -> {
                    if (rooted) {
                        appendLog("[+] Root detectado!");
                        doInstall();
                    } else {
                        appendLog("[-] Root no detectado.");
                        retryBtn.setEnabled(true);
                        rejectBtn.setEnabled(true);
                    }
                });
            }).start();
        });

        rejectBtn.setOnClickListener(v -> {
            appendLog("[!] Saliendo...");
            finishAffinity();
            finishAndRemoveTask();
        });

        new Thread(() -> {
            try {
                if (isInstallComplete()) {
                    enableLauncherActivities();
                    appendLog("[+] AArchDroid ya instalado.");
                    appendLog("[*] Directorio: " + CHROOT_DIR);
                    appendLog("[*] Marcador: " + MARKER);
                    appendLog("[*] Rootfs listo para usar.");
                    appendLog("[>] Iniciando AArchDroid...");
                    setButtonsIniciando();
                    appendLog("[>] Abriendo panel principal...");
                    proceedToMain();
                    return;
                }
                boolean installed = checkRootfsInstalled();
                boolean rooted = isRooted();
                if (installed) {
                    enableLauncherActivities();
                    appendLog("[+] Rootfs ya instalado.");
                    appendLog("[*] Directorio: " + CHROOT_DIR);
                    appendLog("[*] Marcador: " + MARKER + " encontrado");
                    appendLog("[*] Rootfs listo para usar.");
                    appendLog("[>] Iniciando AArchDroid...");
                    setButtonsIniciando();
                    appendLog("[>] Abriendo panel principal...");
                    proceedToMain();
                } else if (rooted) {
                    runOnUiThread(() -> {
                        appendLog("[+] Root detectado.");
                        doInstall();
                    });
                } else {
                    runOnUiThread(() -> {
                        appendLog("[-] Root no detectado.");
                        appendLog("[!] Se requiere root para instalar.");
                        appendLog("[?] Presiona REINTENTAR cuando tengas root,");
                        appendLog("[?] o RECHAZAR para salir.");
                        retryBtn.setEnabled(true);
                        rejectBtn.setEnabled(true);
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    appendLog("[!] Error: " + e.getMessage());
                    appendLog("[>] Continuando sin root...");
                    proceedToMain();
                });
            }
        }).start();
    }

    private void appendLog(final String line) {
        runOnUiThread(() -> {
            logText.append(line + "\n");
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
        } catch (Exception e) {
            // best effort
        }
    }

    private boolean checkRootfsInstalled() {
        try {
            Process p = Runtime.getRuntime().exec(
                new String[]{"su", "-c", "test -f " + MARKER + " && echo installed"});
            java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            r.close();
            p.waitFor();
            writeFileLog("[*] checkRootfsInstalled: line=[" + line + "] exit=" + p.exitValue());
            return "installed".equals(line);
        } catch (Exception e) {
            writeFileLog("[-] checkRootfsInstalled fallo: " + e.getMessage());
            return false;
        }
    }

    private boolean isRooted() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            p.waitFor();
            r.close();
            return line != null && line.contains("uid=0");
        } catch (Exception e) {
            appendLog("[-] isRooted fallo: " + e.getMessage());
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

    private void setButtonsInstalling() {
        runOnUiThread(() -> {
            rejectBtn.setVisibility(android.view.View.GONE);
            retryBtn.setEnabled(false);
            retryBtn.setText("Descomprimiendo...");
            retryBtn.setTextSize(14);
            retryBtn.setBackgroundResource(R.drawable.button_hacker_disabled);
            retryBtn.setTextColor(0xFF003300);
            retryBtn.setVisibility(android.view.View.VISIBLE);
        });
    }

    private void setButtonsIniciando() {
        runOnUiThread(() -> {
            rejectBtn.setVisibility(android.view.View.GONE);
            retryBtn.setEnabled(false);
            retryBtn.setText("Iniciando...");
            retryBtn.setTextSize(14);
            retryBtn.setBackgroundResource(R.drawable.button_hacker_disabled);
            retryBtn.setTextColor(0xFF003300);
            retryBtn.setVisibility(android.view.View.VISIBLE);
        });
    }

    private void setButtonsGo() {
        runOnUiThread(() -> {
            rejectBtn.setVisibility(android.view.View.GONE);
            retryBtn.setEnabled(true);
            retryBtn.setText("Go >>");
            retryBtn.setTextSize(18);
            retryBtn.setBackgroundResource(R.drawable.button_hacker);
            retryBtn.setTextColor(0xFF00FF00);
            retryBtn.setOnClickListener(v -> {
                appendLog("[>] Reintentando...");
                doInstall();
            });
        });
    }

    private void doInstall() {
        setButtonsInstalling();
        new Thread(() -> {
            try {
                if (checkRootfsInstalled() || isInstallComplete()) {
                    appendLog("[+] Rootfs ya instalado.");
                    appendLog("[>] Iniciando AArchDroid...");
                    setButtonsIniciando();
                    appendLog("[>] Abriendo panel principal...");
                    proceedToMain();
                    return;
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
                    appendLog("[>] Presiona Go >> para iniciar.");
                    setButtonsGo();
                });
            } catch (final Exception e) {
                runOnUiThread(() -> {
                    appendLog("[-] Instalacion fallo: " + e.getMessage());
                    retryBtn.setEnabled(true);
                    rejectBtn.setEnabled(true);
                    retryBtn.setBackgroundResource(R.drawable.button_hacker);
                    retryBtn.setTextColor(0xFF00FF00);
                    retryBtn.setText("REINTENTAR");
                    rejectBtn.setBackgroundResource(R.drawable.button_hacker);
                    rejectBtn.setTextColor(0xFF00FF00);
                    rejectBtn.setText("RECHAZAR");
                    rejectBtn.setVisibility(android.view.View.VISIBLE);
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
        // Try system toybox tar first, then busybox tar
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
                java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()));
                StringBuilder out = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) out.append(line).append(" | ");
                r.close();
                boolean ok = p.exitValue() == 0 || out.toString().toLowerCase().contains("tar");
                appendLog("[*] " + bin + " exit=" + p.exitValue() + " out=" + out.toString().trim());
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
        String[] flags;

        writeFileLog("===== DEBUG EXTRACT ROOTFS =====");
        writeFileLog("tarBin=" + tarBin);
        writeFileLog("CHROOT_DIR=" + CHROOT_DIR);
        writeFileLog("MARKER=" + MARKER);

        // List what's in rootfs.tgz first (list contents)
        try {
            appendLog("[*] Listando contenido de rootfs.tgz...");
            // Read from raw stream first to verify asset
            writeFileLog("[*] Asset rootfs.tgz exists: " + (getAssets().open("rootfs.tgz") != null));
        } catch (Exception e) {
            writeFileLog("[-] Asset rootfs.tgz NOT FOUND: " + e.getMessage());
            throw new RuntimeException("rootfs.tgz no encontrado en assets");
        }

        // Test if tar supports -z (gzip) and -P (absolute-names)
        boolean zSupported = false;
        boolean pSupported = false;
        try {
            appendLog("[*] Probando si tar soporta -z...");
            String testCmd = tarBin + " -xzf /dev/null -C /data 2>&1 || true";
            writeFileLog("[*] Test -z command: " + testCmd);
            Process test = Runtime.getRuntime().exec(
                new String[]{"su", "-c", testCmd});
            test.waitFor();
            java.io.BufferedReader testStdout = new java.io.BufferedReader(
                new java.io.InputStreamReader(test.getInputStream()));
            java.io.BufferedReader testStderr = new java.io.BufferedReader(
                new java.io.InputStreamReader(test.getErrorStream()));
            String stdoutStr = "", stderrStr = "";
            String line;
            while ((line = testStdout.readLine()) != null) stdoutStr += line + " | ";
            while ((line = testStderr.readLine()) != null) stderrStr += line + " | ";
            testStdout.close();
            testStderr.close();
            String combinedOut = stdoutStr + " " + stderrStr;
            writeFileLog("[*] Test -z exit=" + test.exitValue() + " stdout=[" + stdoutStr.trim() + "] stderr=[" + stderrStr.trim() + "]");
            zSupported = test.exitValue() == 0
                && !combinedOut.toLowerCase().contains("unrecognized")
                && !combinedOut.toLowerCase().contains("unknown option")
                && !combinedOut.toLowerCase().contains("not found");
            appendLog("[*] Soporte -z: " + zSupported);
        } catch (Exception e) {
            writeFileLog("[-] Test -z exception: " + Log.getStackTraceString(e));
        }

        // Test -P (--absolute-names)
        try {
            appendLog("[*] Probando si tar soporta -P...");
            String testCmd2 = tarBin + " -P -tf /dev/null 2>&1 || true";
            writeFileLog("[*] Test -P command: " + testCmd2);
            Process test2 = Runtime.getRuntime().exec(
                new String[]{"su", "-c", testCmd2});
            test2.waitFor();
            java.io.BufferedReader r2 = new java.io.BufferedReader(
                new java.io.InputStreamReader(test2.getInputStream()));
            java.io.BufferedReader e2 = new java.io.BufferedReader(
                new java.io.InputStreamReader(test2.getErrorStream()));
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
            writeFileLog("[*] Test -P exit=" + test2.exitValue() + " out=[" + s2.trim() + "] err=[" + eStr2.trim() + "]");
            appendLog("[*] Soporte -P: " + pSupported);
        } catch (Exception e) {
            writeFileLog("[-] Test -P exception: " + Log.getStackTraceString(e));
        }

        String pFlag = pSupported ? " -P" : "";
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

        writeFileLog("[*] Flags a probar:");
        for (int i = 0; i < flags.length; i++) {
            writeFileLog("  [" + i + "] " + flags[i]);
        }

        boolean ok = false;
        for (int attempt = 0; attempt < flags.length; attempt++) {
            String shellCmd = flags[attempt];
            appendLog("[*] Intento " + (attempt+1) + ": su -c \"" + shellCmd + "\"");
            writeFileLog("===== INTENTO " + (attempt+1) + " =====");
            writeFileLog("shellCmd=" + shellCmd);

            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", shellCmd});
            writeFileLog("Process started, pid=" + p.toString());

            // Reader threads for stdout/stderr
            final StringBuilder procStdout = new StringBuilder();
            final StringBuilder procStderr = new StringBuilder();
            Thread stdoutReader = new Thread(() -> {
                try {
                    java.io.BufferedReader r = new java.io.BufferedReader(
                        new java.io.InputStreamReader(p.getInputStream()));
                    String l;
                    while ((l = r.readLine()) != null) procStdout.append(l).append(" | ");
                    r.close();
                } catch (Exception e) {}
            });
            Thread stderrReader = new Thread(() -> {
                try {
                    java.io.BufferedReader r = new java.io.BufferedReader(
                        new java.io.InputStreamReader(p.getErrorStream()));
                    String l;
                    while ((l = r.readLine()) != null) procStderr.append(l).append(" | ");
                    r.close();
                } catch (Exception e) {}
            });
            stdoutReader.start();
            stderrReader.start();

            // Feed data
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
                writeFileLog("[*] Escritos " + total + " bytes al pipe");
            } catch (java.io.IOException e) {
                pipeBroken = true;
                writeFileLog("[!] Pipe roto en " + total + " bytes: " + e.getMessage());
                appendLog("[!] Pipe roto: " + e.getMessage());
            }
            in.close();
            try { stdin.flush(); } catch (Exception e) {
                writeFileLog("[!] stdin.flush exception: " + (e.getMessage() != null ? e.getMessage() : "null"));
            }
            try { stdin.close(); } catch (Exception e) {
                writeFileLog("[!] stdin.close exception: " + (e.getMessage() != null ? e.getMessage() : "null"));
            }

            int exitCode = p.waitFor();
            writeFileLog("[*] waitFor done, exitCode=" + exitCode);

            // Wait for readers
            try { stdoutReader.join(2000); } catch (Exception e) {}
            try { stderrReader.join(2000); } catch (Exception e) {}

            writeFileLog("[*] stdout=[" + procStdout.toString().trim() + "]");
            writeFileLog("[*] stderr=[" + procStderr.toString().trim() + "]");

            appendLog("[*] Intento " + (attempt+1) + ": " + total + " bytes, exit=" + exitCode
                + (pipeBroken ? " (pipe roto)" : ""));
            if (procStderr.length() > 0) appendLog("[-] stderr: " + procStderr.toString().trim());
            if (procStdout.length() > 0) appendLog("[*] stdout: " + procStdout.toString().trim());

            if (exitCode == 0 || (exitCode == 1 && total > 1000000)) {
                ok = true;
                writeFileLog("[+] Intento " + (attempt+1) + " EXITOSO (exit=" + exitCode + ")");
                break;
            } else {
                writeFileLog("[-] Intento " + (attempt+1) + " FALLO");
            }
        }

        if (!ok) {
            writeFileLog("[-] TODOS LOS INTENTOS FALLARON");
            throw new RuntimeException("tar fallo con todos los metodos");
        }
        writeFileLog("[+] extractRootfs EXITOSO");
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

    private void proceedToMain() {
        runOnUiThread(() -> {
            appendLog("[>] Lanzando AArchDroid...");
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
        });
    }
}
