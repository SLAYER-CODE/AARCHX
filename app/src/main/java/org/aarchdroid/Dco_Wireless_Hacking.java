package org.aarchdroid;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class Dco_Wireless_Hacking extends DcoBaseActivity {
    private static final String TAG = "Dco_Wireless_Hacking";

    @Override
    protected void onCreate(Bundle bundle) {
        try {
            requestWindowFeature(1);
            super.onCreate(bundle);
            Log.d(TAG, "onCreate");
            setContentView(R.layout.dco_list_scaffold);
            getWindow().setFlags(1024, 1024);

            TextView titleView = findViewById(R.id.title);
            titleView.setText("Wireless Hacking");
            ((android.widget.ImageView) findViewById(R.id.banner)).setImageResource(R.drawable.wirelesshacking);
            ((TextView) findViewById(R.id.stats_tools)).setText("18");

            RecyclerView list = findViewById(R.id.tool_list);
            list.setLayoutManager(new LinearLayoutManager(this));

            createAdapter(list, buildToolList(), new ToolAdapter.OnToolClickListener() {
                @Override
                public void onToolClick(ToolItem item) {
                    run_hack_cmd(item.cmd, item.iconResId);
                }

                @Override
                public void onInstallClick(String toolKey) {
                    processInstallTool(toolKey);
                }

                @Override
                public void onUninstallClick(String toolKey) {
                    Dco_Wireless_Hacking.this.onUninstallClick(toolKey);
                }

                @Override
                public void onLaunchTool(String toolKey) {
                    Dco_Wireless_Hacking.this.onLaunchTool(toolKey);
                }
            });
            list.setHasFixedSize(true);
        } catch (Exception e) {
            Log.e(TAG, "onCreate failed", e);
            finish();
        }
    }

    private List<ToolItem> buildToolList() {
        List<ToolItem> list = new ArrayList<>();
        list.add(makeItem("aircrack", "AIRCRACK-NG", "Suite of tools to assess WiFi network security", "aircrack-ng", "aircrackng"));
        list.add(makeItem("eapmd5pass", "EAP-MD5-Pass", "Offline EAP-MD5 dictionary attack", "eapmd5pass", "andraxtool"));
        list.add(makeItem("wacker", "wacker", "", "wacker -h", "wacker"));
        list.add(makeItem("cowpatty", "COWPATTY", "Offline dictionary attack against WPA/WPA2", "cowpatty", "cowpatty"));
        list.add(makeItem("mdk4", "MDK4", "Exploit common IEEE 802.11 weaknesses", "mdk4", "mdk4"));
        list.add(makeItem("bully", "BULLY", "A reaver better than reaver", "bully", "bully"));
        list.add(makeItem("wash", "Wash", "WiFi Protected Setup Scan Tool", "wash", "reaver"));
        list.add(makeItem("reaver", "REAVER", "WPS Attack Testing Tool", "reaver", "reaver"));
        list.add(makeItem("pixiewps", "PixieWPS", "WPS pixie-dust attack tool", "pixiewps", "pixiewps"));
        list.add(makeItem("hcxdumptool", "HCXDumpTool", "Capture packets from wlan devices", "hcxdumptool", "andraxtool"));
        list.add(makeItem("blueranger", "BlueRanger", "", "blueranger", "andraxtool"));
        list.add(makeItem("bluesnarfer", "BLUESnarfer", "", "bluesnarfer", "andraxtool"));
        list.add(makeItem("blescan", "BLEScan", "", "blescan -h", "andraxtool"));
        list.add(makeItem("btscanner", "BTScanner", "", "btscanner -h", "andraxtool"));
        list.add(makeItem("spooftooph", "SpoofTooph", "", "spooftooph", "spooftooph"));
        list.add(makeItem("btlejack", "BTLEJack", "", "btlejack", "microbit"));
        list.add(makeItem("crackle", "CrackLE", "", "crackle", "crackle"));
        list.add(makeItem("mfterm", "mfterm", "", "mfterm -h", "mfterm"));
        return list;
    }

    private ToolItem makeItem(String key, String displayName, String description, String cmd, String drawableName) {
        ToolItem item = new ToolItem();
        item.key = key;
        item.source = resolveSource(key);
        item.displayName = displayName;
        item.description = description;
        item.cmd = cmd;
        if (drawableName == null || drawableName.isEmpty()) {
            item.iconResId = R.drawable.andraxtool;
        } else {
            int id = getResources().getIdentifier(drawableName, "drawable", getPackageName());
            item.iconResId = id != 0 ? id : R.drawable.andraxtool;
        }
        return item;
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        finish();
    }
}