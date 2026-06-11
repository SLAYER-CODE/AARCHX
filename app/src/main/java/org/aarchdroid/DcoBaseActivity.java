package org.aarchdroid;

import android.app.Activity;
import android.content.Intent;
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
import java.io.DataOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DcoBaseActivity extends Activity {
    private static final String TAG = "DcoBaseActivity";
    private static Boolean hasRoot = null;
    private boolean layoutSet = false;
    private boolean scrollListenerAttached = false;
    protected ToolAdapter adapter;
    private final Set<String> processingTools = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        getWindow().setWindowAnimations(0);
        getWindow().setGravity(Gravity.CENTER);
        getWindow().setDimAmount(0.25f);
        setFinishOnTouchOutside(true);
    }

    protected void createAdapter(RecyclerView rv, List<ToolItem> tools, ToolAdapter.OnToolClickListener listener) {
        adapter = new ToolAdapter(tools, listener);
        rv.setAdapter(adapter);
    }

    protected String resolveSource(String toolKey) {
        try {
            ToolInfo info = ToolDatabase.getInstance().getTool(toolKey);
            if (info != null && info.source != null) {
                String src = info.source;
                if ("arch".equals(src) || "blackarch".equals(src)) {
                    return "pacman";
                }
                return src;
            }
        } catch (Exception e) {
            Log.d(TAG, "resolveSource: error for " + toolKey, e);
        }
        return "";
    }

    private void refreshStatusesAsync() {
        new Thread(() -> {
            String category = getCurrentCategory();
            Map<String, String> statuses = ToolDatabase.getInstance().getStatusMap(category);
            Map<String, ToolInfo> toolInfos = ToolDatabase.getInstance().getToolInfoMap(category);
            runOnUiThread(() -> {
                if (adapter != null) {
                    adapter.updateCache(statuses, toolInfos);
                    adapter.notifyDataSetChanged();
                }
                updateStatsSize();
            });
        }).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatusesAsync();
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
        processInstallTool(toolKey);
    }

    public void processInstallTool(String toolKey) {
        if (!processingTools.add(toolKey)) {
            Log.d(TAG, "processInstallTool(" + toolKey + ") already processing — ignored");
            return;
        }
        Log.d(TAG, "processInstallTool(" + toolKey + ") called");
        String installCmd = ToolDatabase.getInstance().getInstallCommand(toolKey);
        Log.d(TAG, "processInstallTool: installCmd=" + installCmd);
        if (installCmd != null) {
            try {
                ToolDatabase.getInstance().markInstalling(toolKey, installCmd);
            } catch (Exception e) {
                Log.e(TAG, "markInstalling failed", e);
                processingTools.remove(toolKey);
                return;
            }
            createPendingMarker(toolKey);
            boolean wrapperOk = createInstallWrapper(toolKey, installCmd);
            Log.d(TAG, "processInstallTool: wrapperOk=" + wrapperOk);
            if (wrapperOk) {
                run_hack_cmd("sh /data/data/org.aarchdroid/files/install-wrappers/" + toolKey + ".sh", 0, toolKey);
            } else {
                run_hack_cmd(installCmd, 0, toolKey);
            }
        } else {
            Log.d(TAG, "processInstallTool: no install command found for " + toolKey);
            Toast.makeText(this, "No install command for " + toolKey, Toast.LENGTH_SHORT).show();
            processingTools.remove(toolKey);
        }
    }

    public void onUninstallClick(String toolKey) {
        if (!processingTools.add(toolKey)) {
            Log.d(TAG, "onUninstallClick(" + toolKey + ") already processing — ignored");
            return;
        }
        String cmd = ToolDatabase.getInstance().getUninstallCommand(toolKey);
        if (cmd != null) {
            try {
                ToolDatabase.getInstance().setStatus(toolKey, "installing");
            } catch (Exception e) {
                Log.e(TAG, "setStatus failed", e);
                processingTools.remove(toolKey);
                return;
            }
            createPendingMarker(toolKey);
            boolean wrapperOk = createUninstallWrapper(toolKey, cmd);
            if (wrapperOk) {
                run_hack_cmd("sh /data/data/org.aarchdroid/files/install-wrappers/uninstall-" + toolKey + ".sh", 0, toolKey);
            } else {
                ToolDatabase.getInstance().markUninstalled(toolKey);
                run_hack_cmd(cmd, 0, toolKey);
            }
        } else {
            Log.d(TAG, "onUninstallClick: no uninstall command for " + toolKey);
            Toast.makeText(this, "No uninstall command for " + toolKey, Toast.LENGTH_SHORT).show();
            processingTools.remove(toolKey);
        }
    }

    public void onLaunchTool(String toolKey) {
        run_hack_cmd(toolKey + " -h");
    }

    private boolean createInstallWrapper(String toolKey, String installCmd) {
        try {
            File wrappersDir = new File(getFilesDir(), "install-wrappers");
            wrappersDir.mkdirs();
            File script = new File(wrappersDir, toolKey + ".sh");
            Log.d(TAG, "createInstallWrapper: script=" + script.getAbsolutePath());

            String dbPath = "/data/data/org.aarchdroid/databases/tools.db";

            StringBuilder sb = new StringBuilder();
            sb.append("#!/bin/sh\n");
            sb.append("TOOLKEY='").append(toolKey).append("'\n");
            sb.append("DB='").append(dbPath).append("'\n");
            sb.append("STATE_DIR=/data/data/org.aarchdroid/files/install-state\n");
            sb.append("PID_FILE=$STATE_DIR/$TOOLKEY.pid\n");
            sb.append("INSTALL_LOG=$STATE_DIR/$TOOLKEY.log\n");
            sb.append("\n");
            sb.append("mkdir -p $STATE_DIR 2>/dev/null || true\n");
            sb.append("echo \"$$\" > $PID_FILE\n");
            sb.append("trap 'rm -f $PID_FILE $INSTALL_LOG; s=$(sqlite3 \"$DB\" \"SELECT status FROM tools WHERE toolKey=\\\"$TOOLKEY\\\"\" 2>/dev/null); if [ \"$s\" = \"installing\" ]; then sqlite3 \"$DB\" \"UPDATE tools SET status=\\\"failed\\\", errorLog=\\\"Interrumpido\\\" WHERE toolKey=\\\"$TOOLKEY\\\"\"; fi' EXIT\n");
            sb.append("rm -f $STATE_DIR/$TOOLKEY.pending\n");
            sb.append("\n");
            sb.append("retry_sqlite() {\n");
            sb.append("  local n=0\n");
            sb.append("  while [ $n -lt 10 ]; do\n");
            sb.append("    sqlite3 \"$DB\" \"$1\" 2>/dev/null && return 0\n");
            sb.append("    n=$((n+1))\n");
            sb.append("    sleep 0.2 2>/dev/null || usleep 200000 2>/dev/null || :\n");
            sb.append("  done\n");
            sb.append("  sqlite3 \"$DB\" \"$1\"\n");
            sb.append("}\n");
            sb.append("\n");
            sb.append(installCmd).append(" > $INSTALL_LOG 2>&1\n");
            sb.append("EXIT_CODE=$?\n");
            sb.append("cat $INSTALL_LOG\n");
            sb.append("\n");
            sb.append("echo \"\"\n");
            sb.append("echo \"==========================================\"\n");
            sb.append("if [ $EXIT_CODE -eq 0 ]; then\n");
            sb.append("  echo \"  Instalacion completada: Exitoso\"\n");
            sb.append("else\n");
            sb.append("  echo \"  Instalacion completada: Fallido\"\n");
            sb.append("fi\n");
            sb.append("echo \"==========================================\"\n");
            sb.append("\n");
            sb.append("if [ $EXIT_CODE -eq 0 ]; then\n");
            sb.append("    BINARY=$(command -v $TOOLKEY 2>/dev/null || echo \"\")\n");
            sb.append("    if [ -z \"$BINARY\" ]; then\n");
            sb.append("        for d in /usr/bin /bin /data/data/com.termux/files/usr/bin /data/data/org.aarchdroid/files/usr/bin; do\n");
            sb.append("            [ -f \"$d/$TOOLKEY\" ] && BINARY=\"$d/$TOOLKEY\" && break\n");
            sb.append("        done\n");
            sb.append("    fi\n");
            sb.append("    SIZE=0\n");
            sb.append("    [ -n \"$BINARY\" ] && SIZE=$(stat -c%s \"$BINARY\" 2>/dev/null || echo 0)\n");
            sb.append("    NOW=$(date +%s)\n");
            sb.append("    retry_sqlite \"UPDATE tools SET status='installed', installPath='$BINARY', actualSizeBytes=$SIZE, installedAt=$NOW, errorLog=NULL WHERE toolKey='$TOOLKEY'\"\n");
            sb.append("    CATEGORY=$(sqlite3 \"$DB\" \"SELECT category FROM tools WHERE toolKey='$TOOLKEY'\")\n");
            sb.append("    retry_sqlite \"UPDATE categories SET installedTools=(SELECT COUNT(*) FROM tools WHERE category='$CATEGORY' AND status='installed'), installedSizeMb=(SELECT COALESCE(SUM(actualSizeBytes)/(1024*1024),0) FROM tools WHERE category='$CATEGORY' AND status='installed') WHERE name='$CATEGORY'\"\n");
            sb.append("else\n");
            sb.append("    ERROR=$(tail -5 $INSTALL_LOG 2>/dev/null | tr '\\n' ' ' | sed \"s/'/''/g\")\n");
            sb.append("    retry_sqlite \"UPDATE tools SET status='failed', errorLog='$ERROR' WHERE toolKey='$TOOLKEY'\"\n");
            sb.append("fi\n");
            sb.append("rm -f $INSTALL_LOG\n");

            FileOutputStream fos = new FileOutputStream(script);
            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            fos.close();
            script.setExecutable(true, false);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create install wrapper", e);
            return false;
        }
    }

    private boolean createUninstallWrapper(String toolKey, String uninstallCmd) {
        try {
            File wrappersDir = new File(getFilesDir(), "install-wrappers");
            wrappersDir.mkdirs();
            File script = new File(wrappersDir, "uninstall-" + toolKey + ".sh");

            String dbPath = "/data/data/org.aarchdroid/databases/tools.db";

            StringBuilder sb = new StringBuilder();
            sb.append("#!/bin/sh\n");
            sb.append("TOOLKEY='").append(toolKey).append("'\n");
            sb.append("DB='").append(dbPath).append("'\n");
            sb.append("STATE_DIR=/data/data/org.aarchdroid/files/install-state\n");
            sb.append("PID_FILE=$STATE_DIR/$TOOLKEY.pid\n");
            sb.append("INSTALL_LOG=$STATE_DIR/$TOOLKEY.log\n");
            sb.append("\n");
            sb.append("mkdir -p $STATE_DIR 2>/dev/null || true\n");
            sb.append("echo \"$$\" > $PID_FILE\n");
            sb.append("trap 'rm -f $PID_FILE $INSTALL_LOG; s=$(sqlite3 \"$DB\" \"SELECT status FROM tools WHERE toolKey=\\\"$TOOLKEY\\\"\" 2>/dev/null); if [ \"$s\" = \"installing\" ]; then sqlite3 \"$DB\" \"UPDATE tools SET status=\\\"failed\\\", errorLog=\\\"Interrumpido\\\" WHERE toolKey=\\\"$TOOLKEY\\\"\"; fi' EXIT\n");
            sb.append("rm -f $STATE_DIR/$TOOLKEY.pending\n");
            sb.append("\n");
            sb.append("retry_sqlite() {\n");
            sb.append("  local n=0\n");
            sb.append("  while [ $n -lt 10 ]; do\n");
            sb.append("    sqlite3 \"$DB\" \"$1\" 2>/dev/null && return 0\n");
            sb.append("    n=$((n+1))\n");
            sb.append("    sleep 0.2 2>/dev/null || usleep 200000 2>/dev/null || :\n");
            sb.append("  done\n");
            sb.append("  sqlite3 \"$DB\" \"$1\"\n");
            sb.append("}\n");
            sb.append("\n");
            sb.append(uninstallCmd).append(" > $INSTALL_LOG 2>&1\n");
            sb.append("EXIT_CODE=$?\n");
            sb.append("cat $INSTALL_LOG\n");
            sb.append("\n");
            sb.append("echo \"\"\n");
            sb.append("echo \"==========================================\"\n");
            sb.append("if [ $EXIT_CODE -eq 0 ]; then\n");
            sb.append("  echo \"  Desinstalacion completada: Exitoso\"\n");
            sb.append("else\n");
            sb.append("  echo \"  Desinstalacion completada: Fallido\"\n");
            sb.append("fi\n");
            sb.append("echo \"==========================================\"\n");
            sb.append("\n");
            sb.append("if [ $EXIT_CODE -eq 0 ]; then\n");
            sb.append("    CATEGORY=$(sqlite3 \"$DB\" \"SELECT category FROM tools WHERE toolKey='$TOOLKEY'\")\n");
            sb.append("    retry_sqlite \"UPDATE tools SET status='not_installed', installPath=NULL, actualSizeBytes=0, installedAt=0, errorLog=NULL WHERE toolKey='$TOOLKEY'\"\n");
            sb.append("    retry_sqlite \"UPDATE categories SET installedTools=(SELECT COUNT(*) FROM tools WHERE category='$CATEGORY' AND status='installed'), installedSizeMb=(SELECT COALESCE(SUM(actualSizeBytes)/(1024*1024),0) FROM tools WHERE category='$CATEGORY' AND status='installed') WHERE name='$CATEGORY'\"\n");
            sb.append("else\n");
            sb.append("    ERROR=$(tail -5 $INSTALL_LOG 2>/dev/null | tr '\\n' ' ' | sed \"s/'/''/g\")\n");
            sb.append("    retry_sqlite \"UPDATE tools SET status='installed', errorLog='$ERROR' WHERE toolKey='$TOOLKEY'\"\n");
            sb.append("fi\n");
            sb.append("rm -f $INSTALL_LOG\n");

            FileOutputStream fos = new FileOutputStream(script);
            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            fos.close();
            script.setExecutable(true, false);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create uninstall wrapper", e);
            return false;
        }
    }

    private void createPendingMarker(String toolKey) {
        try {
            File dir = new File(getFilesDir(), "install-state");
            dir.mkdirs();
            new File(dir, toolKey + ".pending").createNewFile();
        } catch (Exception e) {
            Log.e(TAG, "Failed to create pending marker for " + toolKey, e);
        }
    }

    public void run_hack_cmd(String cmd) {
        run_hack_cmd(cmd, 0, null);
    }

    public void run_hack_cmd(String cmd, int iconResId) {
        run_hack_cmd(cmd, iconResId, null);
    }

    public void run_hack_cmd(String cmd, int iconResId, String toolKey) {
        Log.d(TAG, "run_hack_cmd: " + cmd + " icon=" + iconResId + " toolKey=" + toolKey);

        if (hasRoot == null) {
            hasRoot = checkRoot();
        }

        if (!hasRoot) {
            Toast.makeText(this, "Root no detectado", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = Bridge.createExecuteIntent(cmd, iconResId);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        if (toolKey != null && !toolKey.isEmpty()) {
            intent.putExtra("tool_key", toolKey);
        }
        try {
            startActivity(intent);
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
