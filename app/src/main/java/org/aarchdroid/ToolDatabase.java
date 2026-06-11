package org.aarchdroid;

import android.content.Context;
import android.content.res.AssetManager;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import org.aarchdroid.dragonterminal.framework.NeoTermDatabase;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolDatabase {
    private static final int PREBUILT_DB_VERSION = 2;
    private static ToolDatabase instance;
    private final NeoTermDatabase db;

    private ToolDatabase() {
        ensureDatabase();
        db = NeoTermDatabase.instance("tools.db", PREBUILT_DB_VERSION);
    }

    public static synchronized ToolDatabase getInstance() {
        if (instance == null) {
            instance = new ToolDatabase();
        }
        return instance;
    }

    private void ensureDatabase() {
        Context ctx = AArchDroidApp.Companion.get();
        File dbFile = ctx.getDatabasePath("tools.db");
        if (dbFile.exists()) {
            try {
                SQLiteDatabase existing = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, 0);
                int ver = existing.getVersion();
                existing.close();
                if (ver >= PREBUILT_DB_VERSION) return;
                Log.d("ToolDatabase", "ensureDatabase: existing DB version " + ver
                    + " < " + PREBUILT_DB_VERSION + ", recopying");
                dbFile.delete();
            } catch (Exception e) {
                android.util.Log.e("ToolDatabase", "ensureDatabase: error checking existing DB, recopying", e);
                dbFile.delete();
            }
        }
        dbFile.getParentFile().mkdirs();
        try {
            AssetManager am = ctx.getAssets();
            InputStream in = am.open("databases/tools.db");
            FileOutputStream out = new FileOutputStream(dbFile);
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.close();
            in.close();
            Log.d("ToolDatabase", "ensureDatabase: copied prebuilt DB (version " + PREBUILT_DB_VERSION + ")");
        } catch (Exception e) {
            Log.e("ToolDatabase", "Failed to copy prebuilt DB", e);
        }
    }

    public ToolInfo getTool(String toolKey) {
        return db.findOneBeanByWhere(ToolInfo.class, "toolKey = '" + toolKey + "'");
    }

    public String getStatus(String toolKey) {
        ToolInfo t = getTool(toolKey);
        String s = t != null ? t.status : "not_installed";
        if ("crunch".equals(toolKey)) {
            Log.d("ToolDatabase", "getStatus(crunch) = " + s);
        }
        return s;
    }

    public void setStatus(String toolKey, String status) {
        ToolInfo t = getTool(toolKey);
        if (t == null) return;
        t.status = status;
        db.updateByWhere(ToolInfo.class, "toolKey = '" + toolKey + "'", t);
    }

    public void markInstalling(String toolKey, String installCommand) {
        try {
            ToolInfo t = getTool(toolKey);
            if (t == null) {
                Log.d("ToolDatabase", "markInstalling(" + toolKey + "): tool not found in DB");
                return;
            }
            Log.d("ToolDatabase", "markInstalling(" + toolKey + "): old status=" + t.status + " cmd=" + installCommand);
            t.status = "installing";
            t.installCommand = installCommand;
            t.errorLog = null;
            db.updateByWhere(ToolInfo.class, "toolKey = '" + toolKey + "'", t);
            Log.d("ToolDatabase", "markInstalling(" + toolKey + "): status set to installing in DB");
        } catch (Exception e) {
            Log.e("ToolDatabase", "markInstalling(" + toolKey + ") failed", e);
            throw e;
        }
    }

    public void markUninstalled(String toolKey) {
        ToolInfo t = getTool(toolKey);
        if (t == null) return;
        t.status = "not_installed";
        t.installPath = null;
        t.installedAt = 0;
        t.errorLog = null;
        db.updateByWhere(ToolInfo.class, "toolKey = '" + toolKey + "'", t);
        decrementCategoryInstalled(t.category, t.actualSizeBytes > 0 ? t.actualSizeBytes : t.estimatedSizeBytes);
    }

    public CategoryInfo getCategoryStats(String category) {
        return db.findOneBeanByWhere(CategoryInfo.class, "name = '" + category + "'");
    }

    private void incrementCategoryInstalled(String category, long sizeBytes) {
        CategoryInfo c = getCategoryStats(category);
        if (c == null) return;
        c.installedTools++;
        c.installedSizeMb += sizeBytes / (1024 * 1024);
        db.updateByWhere(CategoryInfo.class, "name = '" + category + "'", c);
    }

    private void decrementCategoryInstalled(String category, long sizeBytes) {
        CategoryInfo c = getCategoryStats(category);
        if (c == null) return;
        c.installedTools = Math.max(0, c.installedTools - 1);
        c.installedSizeMb = Math.max(0, c.installedSizeMb - sizeBytes / (1024 * 1024));
        db.updateByWhere(CategoryInfo.class, "name = '" + category + "'", c);
    }

    public String getInstallCommand(String toolKey) {
        ToolInfo t = getTool(toolKey);
        return t != null ? t.installCommand : null;
    }

    public String getUninstallCommand(String toolKey) {
        ToolInfo t = getTool(toolKey);
        return t != null ? t.uninstallCommand : null;
    }

    public Map<String, String> getStatusMap(String category) {
        List<ToolInfo> tools = getToolsByCategory(category);
        Map<String, String> map = new HashMap<>();
        for (ToolInfo t : tools) {
            map.put(t.toolKey, t.status != null ? t.status : "not_installed");
        }
        return map;
    }

    public Map<String, ToolInfo> getToolInfoMap(String category) {
        List<ToolInfo> tools = getToolsByCategory(category);
        Map<String, ToolInfo> map = new HashMap<>();
        for (ToolInfo t : tools) {
            map.put(t.toolKey, t);
        }
        return map;
    }

    public List<ToolInfo> getInstalledTools() {
        return db.findBeanByWhere(ToolInfo.class, "status = 'installed'");
    }

    public List<ToolInfo> getInstallingTools() {
        return db.findBeanByWhere(ToolInfo.class, "status = 'installing'");
    }

    public int getInstalledCount() {
        return getInstalledTools().size();
    }

    public long getInstalledTotalBytes() {
        List<ToolInfo> list = getInstalledTools();
        long total = 0;
        for (ToolInfo t : list) {
            total += t.actualSizeBytes > 0 ? t.actualSizeBytes : t.estimatedSizeBytes;
        }
        return total;
    }

    public List<ToolInfo> getToolsByCategory(String category) {
        return db.findBeanByWhere(ToolInfo.class, "category = '" + category + "'");
    }

    public List<ToolInfo> getAllTools() {
        return db.findAll(ToolInfo.class);
    }
}
