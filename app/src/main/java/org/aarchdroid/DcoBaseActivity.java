package org.aarchdroid;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import android.util.DisplayMetrics;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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
    private boolean scrollListenerAttached = false;

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
            final int maxW = (int)(dm.widthPixels * 0.90);
            final int maxH = (int)(dm.heightPixels * 0.85);
            getWindow().setLayout(maxW, ViewGroup.LayoutParams.WRAP_CONTENT);

            final View decorView = getWindow().getDecorView();
            decorView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    private boolean done = false;
                    @Override
                    public void onGlobalLayout() {
                        if (done) return;
                        RecyclerView rv = findViewById(R.id.tool_list);
                        if (rv == null || rv.getAdapter() == null) return;
                        if (rv.getHeight() == 0) return;
                        done = true;

                        float density = getResources().getDisplayMetrics().density;
                        int occupied = (int)(56 * density + 2 * density + 12 * density + 16 * density);
                        int rvMax = maxH - occupied;
                        if (rvMax < 0) rvMax = 0;

                        if (rv.getHeight() > rvMax) {
                            ViewGroup.LayoutParams lp = rv.getLayoutParams();
                            lp.height = rvMax;
                            rv.setLayoutParams(lp);
                        }

                        setupScrollIndicator(rv);
                        updateStatsSize();

                        decorView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                });
        }
    }

    private String getCurrentCategory() {
        String className = getClass().getSimpleName();
        if (className.startsWith("Dco_")) {
            return className.substring(4).toLowerCase();
        }
        return "";
    }

    private void updateStatsSize() {
        TextView sizeView = findViewById(R.id.stats_size);
        TextView toolsView = findViewById(R.id.stats_tools);
        TextView compactView = findViewById(R.id.stats_compact);
        TextView downView = findViewById(R.id.stats_downloaded);
        TextView updView = findViewById(R.id.stats_updated);
        if (sizeView == null || toolsView == null) return;
        String category = getCurrentCategory();
        CategoryInfo stats = ToolDatabase.getInstance().getCategoryStats(category);
        long totalSizeMb = stats != null ? stats.installedSizeMb : 0;
        if (totalSizeMb == 0) totalSizeMb = stats != null ? 0 : 25;
        sizeView.setText(totalSizeMb + "mb");
        if (totalSizeMb == 0) {
            sizeView.setTextColor(Color.parseColor("#3D6B3D"));
        } else if (totalSizeMb < 500) {
            sizeView.setTextColor(Color.parseColor("#B87333"));
        } else {
            sizeView.setTextColor(Color.parseColor("#8B0000"));
        }
        if (toolsView != null && stats != null) {
            toolsView.setText(String.valueOf(stats.totalTools));
        }
        if (compactView != null) {
            String h = toolsView != null ? toolsView.getText().toString() : "0";
            String d = downView != null ? downView.getText().toString() : "0";
            String a = updView != null ? updView.getText().toString() : "-";
            int neon = Color.parseColor("#39FF14");
            int cyan = Color.parseColor("#00FFFF");
            int green = Color.parseColor("#90EE90");
            int orange = Color.parseColor("#FF8C00");
            SpannableStringBuilder ssb = new SpannableStringBuilder();
            int start = ssb.length(); ssb.append("H:"); ssb.setSpan(new ForegroundColorSpan(neon), start, ssb.length(), 0);
            start = ssb.length(); ssb.append(h);      ssb.setSpan(new ForegroundColorSpan(cyan), start, ssb.length(), 0);
            start = ssb.length(); ssb.append(" D:");  ssb.setSpan(new ForegroundColorSpan(neon), start, ssb.length(), 0);
            start = ssb.length(); ssb.append(d);      ssb.setSpan(new ForegroundColorSpan(green), start, ssb.length(), 0);
            start = ssb.length(); ssb.append(" A:");  ssb.setSpan(new ForegroundColorSpan(neon), start, ssb.length(), 0);
            start = ssb.length(); ssb.append(a);      ssb.setSpan(new ForegroundColorSpan(orange), start, ssb.length(), 0);
            compactView.setText(ssb);
        }
    }

    public void onInstallClick(View v) {
        Object tag = v.getTag();
        String toolKey = tag instanceof String ? (String) tag : "";
        if (toolKey.isEmpty()) return;
        String cmd = buildInstallCommandForKey(toolKey);
        if (cmd != null) {
            ToolDatabase.getInstance().markInstalling(toolKey, cmd);
            run_hack_cmd(cmd);
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

    private void setupScrollIndicator(RecyclerView rv) {
        if (scrollListenerAttached || rv == null) return;
        scrollListenerAttached = true;

        final View indicator = findViewById(R.id.scroll_indicator);
        final View thumb = findViewById(R.id.scroll_thumb);
        if (indicator == null || thumb == null) return;

        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                updateScrollThumb(recyclerView, indicator, thumb);
            }
        });

        rv.post(() -> updateScrollThumb(rv, indicator, thumb));
    }

    private void updateScrollThumb(RecyclerView rv, View indicator, View thumb) {
        LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
        if (lm == null) return;

        int totalItems = lm.getItemCount();
        int firstVisible = lm.findFirstCompletelyVisibleItemPosition();
        int lastVisible = lm.findLastCompletelyVisibleItemPosition();

        if (firstVisible == -1 || lastVisible == -1) {
            firstVisible = lm.findFirstVisibleItemPosition();
            lastVisible = lm.findLastVisibleItemPosition();
        }

        int visibleItems = lastVisible - firstVisible + 1;
        if (visibleItems <= 0) visibleItems = 1;
        if (totalItems <= 0) return;

        if (visibleItems >= totalItems) {
            indicator.setVisibility(View.GONE);
            return;
        }
        indicator.setVisibility(View.VISIBLE);

        float progress = (float) firstVisible / Math.max(1, totalItems - visibleItems);
        progress = Math.max(0, Math.min(1, progress));

        float visibleRatio = (float) visibleItems / totalItems;

        int trackWidth = indicator.getWidth();
        if (trackWidth <= 0) return;

        thumb.setPivotX(0);
        thumb.setScaleX(Math.max(visibleRatio, (float) dpToPx(8) / trackWidth));

        float available = trackWidth * (1 - visibleRatio);
        thumb.setTranslationX(available * progress);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
