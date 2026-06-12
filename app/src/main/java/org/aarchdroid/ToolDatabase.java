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

    private static final Map<String, String> KEY_NORMALIZATION = new HashMap<>();
    static {
        // irregular: java_key -> db_key
        KEY_NORMALIZATION.put("aircrack", "aircrack-ng");
        KEY_NORMALIZATION.put("applebleee", "apple-bleee");
        KEY_NORMALIZATION.put("bgpcli", "bgp-cli");
        KEY_NORMALIZATION.put("bgpleak", "bgp-leak");
        KEY_NORMALIZATION.put("bgpmd5crack", "bgp-md5crack");
        KEY_NORMALIZATION.put("bittwist", "bit-twist");
        KEY_NORMALIZATION.put("detctsniffer6", "detect-sniffer6");
        KEY_NORMALIZATION.put("diameterenum", "diameter-enum");
        KEY_NORMALIZATION.put("dosnewip6", "dos-new-ip6");
        KEY_NORMALIZATION.put("eapmd5pass", "eap-md5-pass");
        KEY_NORMALIZATION.put("eigrpcli", "eigrp-cli");
        KEY_NORMALIZATION.put("enodebhack", "enodeb");
        KEY_NORMALIZATION.put("evilwinrm", "evil-winrm");
        KEY_NORMALIZATION.put("fakeadvertise6", "fake-advertise6");
        KEY_NORMALIZATION.put("fakedhcps6", "fake-dhcps6");
        KEY_NORMALIZATION.put("fakedns6d", "fake-dns6d");
        KEY_NORMALIZATION.put("fakednsupdate6", "fake-dnsupdate6");
        KEY_NORMALIZATION.put("fakemld6", "fake-mld6");
        KEY_NORMALIZATION.put("fakemld26", "fake-mld26");
        KEY_NORMALIZATION.put("fakemldrouter6", "fake-mldrouter6");
        KEY_NORMALIZATION.put("fakerouter6", "fake-router6");
        KEY_NORMALIZATION.put("fakerouter26", "fake-router26");
        KEY_NORMALIZATION.put("fakesolicitate6", "fake-solicitate6");
        KEY_NORMALIZATION.put("findalllinks", "find-all-links");
        KEY_NORMALIZATION.put("floodadvertise6", "flood-advertise6");
        KEY_NORMALIZATION.put("flooddhcpc6", "flood-dhcpc6");
        KEY_NORMALIZATION.put("floodmld6", "flood-mld6");
        KEY_NORMALIZATION.put("floodmld26", "flood-mld26");
        KEY_NORMALIZATION.put("floodmldrouter6", "flood-mldrouter6");
        KEY_NORMALIZATION.put("floodredir6", "flood-redir6");
        KEY_NORMALIZATION.put("floodrouter6", "flood-router6");
        KEY_NORMALIZATION.put("floodrouter26", "flood-router26");
        KEY_NORMALIZATION.put("floodrs6", "flood-rs6");
        KEY_NORMALIZATION.put("floodsolicitate6", "flood-solicitate6");
        KEY_NORMALIZATION.put("floodunreach6", "flood-unreach6");
        KEY_NORMALIZATION.put("fuzzip6", "fuzz-ip6");
        KEY_NORMALIZATION.put("gtpscan", "gtp-scan");
        KEY_NORMALIZATION.put("ikescan", "ike-scan");
        KEY_NORMALIZATION.put("inverselookup6", "inverse-lookup6");
        KEY_NORMALIZATION.put("john", "john-the-ripper");
        KEY_NORMALIZATION.put("jsalert", "js-alert");
        KEY_NORMALIZATION.put("jwtcrack", "jwt-crack");
        KEY_NORMALIZATION.put("jwt_tool", "jwt-tool");
        KEY_NORMALIZATION.put("killrouter6", "kill-router6");
        KEY_NORMALIZATION.put("ldpcli", "ldp-cli");
        KEY_NORMALIZATION.put("merlin", "merlinc2");
        KEY_NORMALIZATION.put("ligolong", "ligolo-ng");
        KEY_NORMALIZATION.put("mainframe_bruter", "mainframe-bruter");
        KEY_NORMALIZATION.put("mmeenodebhack", "mme-enodeb");
        KEY_NORMALIZATION.put("mplsredirect", "mpls-redirect");
        KEY_NORMALIZATION.put("mplstun", "mpls-tun");
        KEY_NORMALIZATION.put("netEBCDICat", "netebcdicat");
        KEY_NORMALIZATION.put("nimcrypt", "nimcrypt2");
        KEY_NORMALIZATION.put("odin", "0d1n");
        KEY_NORMALIZATION.put("onthefly", "on-the-fly");
        KEY_NORMALIZATION.put("pgwhack", "pgw");
        KEY_NORMALIZATION.put("protostestsuite", "protos-test-suite");
        KEY_NORMALIZATION.put("pskcrack", "psk-crack");
        KEY_NORMALIZATION.put("s1apenum", "s1ap-enum");
        KEY_NORMALIZATION.put("scadatools", "scada-tools");
        KEY_NORMALIZATION.put("sgwhack", "sgw");
        KEY_NORMALIZATION.put("sixnettools", "sixnet-tools");
        KEY_NORMALIZATION.put("smtpuserenum", "smtp-user-enum");
        KEY_NORMALIZATION.put("TShOcker", "tshocker");
        KEY_NORMALIZATION.put("ssh_auditor", "ssh-auditor");
        KEY_NORMALIZATION.put("tpxbrute", "tpx-brute");
        KEY_NORMALIZATION.put("udpfloodVLAN", "udpfloodvlan");
        KEY_NORMALIZATION.put("upnptools", "upnp-tools");
        KEY_NORMALIZATION.put("zosprivesc", "zos-privesc");
    }

    public static String normalizeKey(String key) {
        if (key == null) return null;
        String mapped = KEY_NORMALIZATION.get(key);
        if (mapped != null) return mapped;
        return key.replace('_', '-');
    }

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
        String nk = normalizeKey(toolKey);
        return db.findOneBeanByWhere(ToolInfo.class, "toolKey = '" + nk + "'");
    }

    public String getStatus(String toolKey) {
        String nk = normalizeKey(toolKey);
        ToolInfo t = getTool(nk);
        String s = t != null ? t.status : "not_installed";
        if ("crunch".equals(toolKey)) {
            Log.d("ToolDatabase", "getStatus(crunch) = " + s);
        }
        return s;
    }

    public void setStatus(String toolKey, String status) {
        String nk = normalizeKey(toolKey);
        ToolInfo t = getTool(nk);
        if (t == null) return;
        t.status = status;
        db.updateByWhere(ToolInfo.class, "toolKey = '" + nk + "'", t);
    }

    public void markInstalling(String toolKey, String installCommand) {
        try {
            String nk = normalizeKey(toolKey);
            ToolInfo t = getTool(nk);
            if (t == null) {
                Log.d("ToolDatabase", "markInstalling(" + toolKey + "): tool not found in DB");
                return;
            }
            Log.d("ToolDatabase", "markInstalling(" + toolKey + "): old status=" + t.status + " cmd=" + installCommand);
            t.status = "installing";
            t.installCommand = installCommand;
            t.errorLog = null;
            db.updateByWhere(ToolInfo.class, "toolKey = '" + nk + "'", t);
            Log.d("ToolDatabase", "markInstalling(" + toolKey + "): status set to installing in DB");
        } catch (Exception e) {
            Log.e("ToolDatabase", "markInstalling(" + toolKey + ") failed", e);
            throw e;
        }
    }

    public void markUninstalled(String toolKey) {
        String nk = normalizeKey(toolKey);
        ToolInfo t = getTool(nk);
        if (t == null) return;
        t.status = "not_installed";
        t.installPath = null;
        t.installedAt = 0;
        t.errorLog = null;
        db.updateByWhere(ToolInfo.class, "toolKey = '" + nk + "'", t);
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
        ToolInfo t = getTool(normalizeKey(toolKey));
        return t != null ? t.installCommand : null;
    }

    public String getUninstallCommand(String toolKey) {
        ToolInfo t = getTool(normalizeKey(toolKey));
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
