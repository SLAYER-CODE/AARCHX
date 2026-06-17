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
import java.io.InputStreamReader;
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
            ToolInfo info = ToolDatabase.getInstance().getTool(ToolDatabase.normalizeKey(toolKey));
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
            decorView.getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    private boolean done = false;
                    @Override
                    public boolean onPreDraw() {
                        if (done) return true;
                        RecyclerView rv = findViewById(R.id.tool_list);
                        if (rv == null || rv.getAdapter() == null) return true;
                        if (rv.getHeight() == 0) return true;
                        done = true;

                        float density = getResources().getDisplayMetrics().density;
                        int occupied = (int)(56 * density + 2 * density + 12 * density + 16 * density);
                        int rvMax = maxH - occupied;
                        if (rvMax < 0) rvMax = 0;

                        if (rv.getHeight() > rvMax) {
                            ViewGroup.LayoutParams lp = rv.getLayoutParams();
                            lp.height = rvMax;
                            rv.setLayoutParams(lp);
                            decorView.getViewTreeObserver().removeOnPreDrawListener(this);
                            return false;
                        }

                        setupScrollIndicator(rv);
                        updateStatsSize();

                        decorView.getViewTreeObserver().removeOnPreDrawListener(this);
                        return true;
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
        toolKey = ToolDatabase.normalizeKey(toolKey);
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
            String inline = buildInstallInline(toolKey, installCmd);
            run_hack_cmd(inline, 0, toolKey);
        } else {
            Log.d(TAG, "processInstallTool: no install command found for " + toolKey);
            Toast.makeText(this, "No install command for " + toolKey, Toast.LENGTH_SHORT).show();
            processingTools.remove(toolKey);
        }
    }

    public void onUninstallClick(String toolKey) {
        toolKey = ToolDatabase.normalizeKey(toolKey);
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
            String inline = buildUninstallInline(toolKey, cmd);
            run_hack_cmd(inline, 0, toolKey);
        } else {
            Log.d(TAG, "onUninstallClick: no uninstall command for " + toolKey);
            Toast.makeText(this, "No uninstall command for " + toolKey, Toast.LENGTH_SHORT).show();
            processingTools.remove(toolKey);
        }
    }

    public void handleCardClick(ToolItem item) {
        if ("github".equals(item.source)) {
            run_hack_cmd("cd /Herramientas/" + item.key + " && ls -la", item.iconResId);
        } else {
            run_hack_cmd(item.cmd, item.iconResId);
        }
    }

    public void onLaunchTool(String toolKey) {
        String source = ToolDatabase.getInstance().getSource(toolKey);
        if ("github".equals(source)) {
            run_hack_cmd("cd /Herramientas/" + toolKey + " && ls -la");
        } else {
            run_hack_cmd(toolKey + " -h");
        }
    }

    private String buildInstallInline(String toolKey, String installCmd) {
        String appDir = "/data/data/" + getPackageName() + "/";
        String stateDir = appDir + "files/install-state";
        String dbPath = appDir + "databases/tools.db";

        if (installCmd.startsWith("pacman ")) {
            installCmd = installCmd.replaceFirst("^pacman ", "pacman --color always --disable-download-timeout ");
        }

        String pidFile = stateDir + "/" + toolKey + ".pid";
        String logFile = stateDir + "/" + toolKey + ".log";
        String exitFile = stateDir + "/" + toolKey + ".exit";

        StringBuilder sb = new StringBuilder();
        sb.append("mkdir -p '").append(stateDir).append("' && ");
        sb.append("echo $$ > '").append(pidFile).append("' && ");
        sb.append("trap '");
        sb.append("rm -f \"").append(pidFile).append("\" \"").append(logFile).append("\"; ");
        sb.append("s=$(sqlite3 \"").append(dbPath).append("\" \"SELECT status FROM tools WHERE toolKey='").append(toolKey).append("'\" 2>/dev/null); ");
        sb.append("if [ \"$s\" = \"installing\" ]; then ");
        sb.append("sqlite3 \"").append(dbPath).append("\" \"UPDATE tools SET status='failed',errorLog='Interrumpido' WHERE toolKey='").append(toolKey).append("'\"; ");
        sb.append("fi' EXIT; ");
        sb.append("rm -f '").append(stateDir).append("/").append(toolKey).append(".pending'; ");
        sb.append("echo; echo -e \"\\033[1;34m[AArchDroid]\\033[0m \\033[1;33mInstalando\\033[0m: ").append(toolKey).append("\"; echo; ");
        sb.append("(").append(installCmd).append("; echo $? > '").append(exitFile).append("') 2>&1 | tee '").append(logFile).append("'; ");
        sb.append("read EC < '").append(exitFile).append("' 2>/dev/null || EC=1; ");
        sb.append("rm -f '").append(exitFile).append("'; ");
        if (installCmd.startsWith("pacman")) {
            sb.append("if [ $EC -ne 0 ]; then ");
            sb.append("echo -e \"\\033[1;33m  -\\033[0m Sync repos...\"; ");
            sb.append("(pacman --color always --disable-download-timeout -Sy; echo $? > '").append(exitFile).append("') 2>&1 | tee -a '").append(logFile).append("'; ");
            sb.append("read EC2 < '").append(exitFile).append("' 2>/dev/null || EC2=1; ");
            sb.append("rm -f '").append(exitFile).append("'; ");
            sb.append("if [ $EC2 -eq 0 ]; then ");
            sb.append("(").append(installCmd).append("; echo $? > '").append(exitFile).append("') 2>&1 | tee -a '").append(logFile).append("'; ");
            sb.append("read EC < '").append(exitFile).append("' 2>/dev/null || EC=1; ");
            sb.append("rm -f '").append(exitFile).append("'; ");
            sb.append("else echo -e \"\\033[1;31m  -\\033[0m Sync failed\"; fi; fi; ");
        }
        sb.append("echo; echo ========================================; ");
        sb.append("if [ $EC -eq 0 ]; then ");
        sb.append("echo -e \"\\033[1;32m  [AArchDroid] OK\\033[0m\"; ");
        sb.append("sqlite3 \"").append(dbPath).append("\" \"UPDATE tools SET status='installed',errorLog=NULL WHERE toolKey='").append(toolKey).append("'\"; ");
        sb.append("CATEGORY=$(sqlite3 \"").append(dbPath).append("\" \"SELECT category FROM tools WHERE toolKey='").append(toolKey).append("'\"); ");
        sb.append("sqlite3 \"").append(dbPath).append("\" \"UPDATE categories SET installedTools=(SELECT COUNT(*) FROM tools WHERE category='");
        sb.append("\"$CATEGORY\"");
        sb.append("' AND status='installed'), installedSizeMb=(SELECT COALESCE(SUM(actualSizeBytes)/(1024*1024),0) FROM tools WHERE category='");
        sb.append("\"$CATEGORY\"");
        sb.append("' AND status='installed') WHERE name='");
        sb.append("\"$CATEGORY\"");
        sb.append("'\"; ");
        sb.append("else ");
        sb.append("echo -e \"\\033[1;31m  [AArchDroid] FAILED\\033[0m\"; ");
        sb.append("ERROR=$(tail -5 '").append(logFile).append("' 2>/dev/null | tr '\\n' ' ' | sed \"s/'/''/g\"); ");
        sb.append("sqlite3 \"").append(dbPath).append("\" \"UPDATE tools SET status='failed',errorLog='$ERROR' WHERE toolKey='").append(toolKey).append("'\"; ");
        sb.append("fi; ");
        sb.append("rm -f '").append(pidFile).append("' '").append(logFile).append("'");

        String raw = sb.toString();
        String escaped = raw.replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("$", "\\$");
        return "sh -c \"" + escaped + "\"";
    }

    private String buildUninstallInline(String toolKey, String uninstallCmd) {
        String appDir = "/data/data/" + getPackageName() + "/";
        String stateDir = appDir + "files/install-state";
        String dbPath = appDir + "databases/tools.db";

        String pidFile = stateDir + "/" + toolKey + ".pid";
        String logFile = stateDir + "/" + toolKey + ".log";
        String exitFile = stateDir + "/" + toolKey + ".exit";

        if (uninstallCmd.startsWith("pacman ")) {
            uninstallCmd = uninstallCmd.replaceFirst("^pacman ", "pacman --color always --disable-download-timeout ");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("mkdir -p '").append(stateDir).append("' && ");
        sb.append("echo $$ > '").append(pidFile).append("' && ");
        sb.append("trap '");
        sb.append("rm -f \"").append(pidFile).append("\" \"").append(logFile).append("\"; ");
        sb.append("s=$(sqlite3 \"").append(dbPath).append("\" \"SELECT status FROM tools WHERE toolKey='").append(toolKey).append("'\" 2>/dev/null); ");
        sb.append("if [ \"$s\" = \"installing\" ]; then ");
        sb.append("sqlite3 \"").append(dbPath).append("\" \"UPDATE tools SET status='not_installed',errorLog=NULL WHERE toolKey='").append(toolKey).append("'\"; ");
        sb.append("fi' EXIT; ");
        sb.append("rm -f '").append(stateDir).append("/").append(toolKey).append(".pending'; ");
        sb.append("echo; echo -e \"\\033[1;34m[AArchDroid]\\033[0m \\033[1;33mDesinstalando\\033[0m: ").append(toolKey).append("\"; echo; ");
        sb.append("(").append(uninstallCmd).append("; echo $? > '").append(exitFile).append("') 2>&1 | tee '").append(logFile).append("'; ");
        sb.append("read EC < '").append(exitFile).append("' 2>/dev/null || EC=1; ");
        sb.append("rm -f '").append(exitFile).append("'; ");
        sb.append("echo; echo ========================================; ");
        sb.append("if [ $EC -eq 0 ]; then ");
        sb.append("echo -e \"\\033[1;32m  [AArchDroid] Uninstall OK\\033[0m\"; ");
        sb.append("sqlite3 \"").append(dbPath).append("\" \"UPDATE tools SET status='not_installed',errorLog=NULL WHERE toolKey='").append(toolKey).append("'\"; ");
        sb.append("else ");
        sb.append("echo -e \"\\033[1;31m  [AArchDroid] Uninstall FAILED\\033[0m\"; ");
        sb.append("ERROR=$(tail -5 '").append(logFile).append("' 2>/dev/null | tr '\\n' ' ' | sed \"s/'/''/g\"); ");
        sb.append("sqlite3 \"").append(dbPath).append("\" \"UPDATE tools SET status='failed',errorLog='$ERROR' WHERE toolKey='").append(toolKey).append("'\"; ");
        sb.append("fi; ");
        sb.append("rm -f '").append(pidFile).append("' '").append(logFile).append("'");

        String raw = sb.toString();
        String escaped = raw.replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("$", "\\$");
        return "sh -c \"" + escaped + "\"";
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
