package org.aarchdroid;

import android.content.Context;
import android.content.res.AssetManager;
import org.aarchdroid.dragonterminal.framework.NeoTermDatabase;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

public class ToolDatabase {
    private static ToolDatabase instance;
    private final NeoTermDatabase db;

    private ToolDatabase() {
        ensureDatabase();
        db = NeoTermDatabase.instance("tools");
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
        if (dbFile.exists()) return;
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
        } catch (Exception e) {
            android.util.Log.e("ToolDatabase", "Failed to copy prebuilt DB", e);
        }
    }

    public ToolInfo getTool(String toolKey) {
        return db.findOneBeanByWhere(ToolInfo.class, "toolKey = '" + toolKey + "'");
    }

    public String getStatus(String toolKey) {
        ToolInfo t = getTool(toolKey);
        return t != null ? t.status : "not_installed";
    }

    public void updateStatus(String toolKey, String status) {
        ToolInfo t = getTool(toolKey);
        if (t == null) return;
        t.status = status;
        db.updateByWhere(ToolInfo.class, "toolKey = '" + toolKey + "'", t);
    }

    public void updateInstallCommand(String toolKey, String command) {
        ToolInfo t = getTool(toolKey);
        if (t == null) return;
        t.installCommand = command;
        db.updateByWhere(ToolInfo.class, "toolKey = '" + toolKey + "'", t);
    }

    public void markInstalling(String toolKey, String installCommand) {
        ToolInfo t = getTool(toolKey);
        if (t == null) return;
        t.status = "installing";
        t.installCommand = installCommand;
        t.errorLog = null;
        db.updateByWhere(ToolInfo.class, "toolKey = '" + toolKey + "'", t);
    }

    public void markInstalled(String toolKey, String installPath) {
        ToolInfo t = getTool(toolKey);
        if (t == null) return;
        t.status = "installed";
        t.installPath = installPath;
        t.installedAt = System.currentTimeMillis() / 1000;
        t.errorLog = null;
        db.updateByWhere(ToolInfo.class, "toolKey = '" + toolKey + "'", t);
        incrementCategoryInstalled(t.category, t.actualSizeBytes > 0 ? t.actualSizeBytes : t.estimatedSizeBytes);
    }

    public void markFailed(String toolKey, String error) {
        ToolInfo t = getTool(toolKey);
        if (t == null) return;
        t.status = "failed";
        t.errorLog = error;
        db.updateByWhere(ToolInfo.class, "toolKey = '" + toolKey + "'", t);
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

    public List<ToolInfo> getInstalledTools() {
        return db.findBeanByWhere(ToolInfo.class, "status = 'installed'");
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
