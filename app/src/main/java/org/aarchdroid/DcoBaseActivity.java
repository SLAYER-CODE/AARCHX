package org.aarchdroid;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import android.util.DisplayMetrics;
import org.aarchdroid.dragonterminal.bridge.Bridge;
import org.json.JSONObject;
import java.io.DataOutputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class DcoBaseActivity extends Activity {
    private static final String TAG = "DcoBaseActivity";
    private static Boolean hasRoot = null;
    private static JSONObject toolManifest = null;
    private boolean layoutSet = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        getWindow().setGravity(Gravity.CENTER);
        getWindow().setDimAmount(0.25f);
        setFinishOnTouchOutside(true);
        loadManifest();
    }

    private void loadManifest() {
        if (toolManifest != null) return;
        try {
            AssetManager am = getAssets();
            InputStream is = am.open("tool-manifest.json");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String json = new String(buffer, StandardCharsets.UTF_8);
            toolManifest = new JSONObject(json);
            Log.d(TAG, "Manifest loaded: " + toolManifest.length() + " tools");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load manifest", e);
            toolManifest = new JSONObject();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!layoutSet) {
            layoutSet = true;
            DisplayMetrics dm = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(dm);
            getWindow().setLayout((int)(dm.widthPixels * 0.85),
                (int)(dm.heightPixels * 0.90));
        }
    }

    public void onInstallClick(View v) {
        ViewGroup cardContent = (ViewGroup) v.getParent();
        String toolDisplayName = "";
        for (int i = 0; i < cardContent.getChildCount(); i++) {
            View child = cardContent.getChildAt(i);
            if (child instanceof TextView) {
                String text = ((TextView) child).getText().toString().trim();
                if (!text.isEmpty()) {
                    toolDisplayName = text;
                    break;
                }
            }
        }
        if (!toolDisplayName.isEmpty()) {
            String cmd = buildInstallCommand(toolDisplayName);
            if (cmd != null) {
                run_hack_cmd(cmd);
            }
        }
    }

    public String buildInstallCommandForKey(String key) {
        try {
            if (toolManifest != null && toolManifest.has(key)) {
                JSONObject entry = toolManifest.getJSONObject(key);
                String source = entry.optString("source", "blackarch");
                String pkg = entry.optString("pkg", key);
                String url = entry.optString("url", "");
                String note = entry.optString("note", "");
                switch (source) {
                    case "blackarch":
                    case "arch":
                        return "pacman -Sy --noconfirm " + pkg;
                    case "pip":
                        return "pip install " + pkg;
                    case "gem":
                        return "gem install " + pkg;
                    case "go":
                        return "go install " + pkg;
                    case "github":
                    case "local":
                        return "sh /data/data/org.aarchdroid/files/scripts/install-tool.sh " + key;
                    case "url":
                        if (!url.isEmpty()) {
                            return "mkdir -p /opt/" + key + " && wget -q \"" + url + "\" -O /opt/" + key + "/" + key + " && chmod +x /opt/" + key + "/" + key;
                        }
                        return "pacman -Sy --noconfirm " + key;
                    case "ubuntu_only":
                        Toast.makeText(this, note.isEmpty() ? "Solo disponible en Ubuntu" : note, Toast.LENGTH_LONG).show();
                        return null;
                    default:
                        return "pacman -Sy --noconfirm " + key;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error building install command for key", e);
        }
        return "pacman -Sy --noconfirm " + key;
    }

    private String buildInstallCommand(String displayName) {
        String normalized = displayName.toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        try {
            if (toolManifest.has(normalized)) {
                JSONObject entry = toolManifest.getJSONObject(normalized);
                String source = entry.optString("source", "blackarch");
                String pkg = entry.optString("pkg", normalized);
                String url = entry.optString("url", "");
                String repo = entry.optString("repo", "");
                String note = entry.optString("note", "");

                switch (source) {
                    case "blackarch":
                    case "arch":
                        return "pacman -Sy --noconfirm " + pkg;
                    case "pip":
                        return "pip install " + pkg;
                    case "gem":
                        return "gem install " + pkg;
                    case "go":
                        return "go install " + pkg;
                    case "github":
                    case "local":
                        return "sh /data/data/org.aarchdroid/files/scripts/install-tool.sh " + normalized;
                    case "url":
                        if (!url.isEmpty()) {
                            return "mkdir -p /opt/" + normalized + " && wget -q \"" + url + "\" -O /opt/" + normalized + "/" + normalized + " && chmod +x /opt/" + normalized + "/" + normalized;
                        }
                        return "pacman -Sy --noconfirm " + normalized;
                    case "ubuntu_only":
                        Toast.makeText(this, note.isEmpty() ? "Solo disponible en Ubuntu" : note, Toast.LENGTH_LONG).show();
                        return null;
                    default:
                        return "pacman -Sy --noconfirm " + normalized;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error building install command", e);
        }
        return "pacman -Sy --noconfirm " + normalized;
    }

    public void run_hack_cmd(String cmd) {
        Log.d(TAG, "run_hack_cmd: " + cmd);

        if (hasRoot == null) {
            hasRoot = checkRoot();
        }

        if (!hasRoot) {
            Toast.makeText(this, "Root no detectado", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = Bridge.createExecuteIntent(cmd);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try {
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "run_hack_cmd failed: " + e.getMessage(), e);
        }
    }

    private static boolean checkRoot() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            os.writeBytes("id\n");
            os.writeBytes("exit\n");
            os.flush();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("uid=0")) {
                    process.waitFor();
                    return true;
                }
            }
            process.waitFor();
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Root check failed", e);
            return false;
        }
    }
}
