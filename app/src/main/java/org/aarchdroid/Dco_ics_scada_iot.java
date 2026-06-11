package org.aarchdroid;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class Dco_ics_scada_iot extends DcoBaseActivity {
    private static final String TAG = "Dco_ics_scada_iot";

    @Override
    protected void onCreate(Bundle bundle) {
        try {
            requestWindowFeature(1);
            super.onCreate(bundle);
            Log.d(TAG, "onCreate");
            setContentView(R.layout.dco_list_scaffold);
            getWindow().setFlags(1024, 1024);

            TextView titleView = findViewById(R.id.title);
            titleView.setText("ICS/SCADA/IIoT/IoT");
            ((android.widget.ImageView) findViewById(R.id.banner)).setImageResource(R.drawable.ics);
            ((TextView) findViewById(R.id.stats_tools)).setText("11");

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
                    Dco_ics_scada_iot.this.onUninstallClick(toolKey);
                }

                @Override
                public void onLaunchTool(String toolKey) {
                    Dco_ics_scada_iot.this.onLaunchTool(toolKey);
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
        list.add(makeItem("plcscan", "PLCScan", "MODBus and S7COMM PLC Scanner", "plcscan", "plc"));
        list.add(makeItem("s7scan", "S7Scan", "S7 Scanner using LLC and TCT/IP", "s7scan", "plc"));
        list.add(makeItem("modscan", "MODScan", "MODBus Scanner", "modscan", "modbus"));
        list.add(makeItem("mbtget", "MBTGET", "Modbus/TCP client", "mbtget -h", "modbus"));
        list.add(makeItem("sixnettools", "SIXNET-Tools", "Exploit sixnet RTUs", "SIXNET-tools", "andraxtool"));
        list.add(makeItem("scadatools", "SCADA-Tools", "SCADA Scan and Hack tools", "scada-tools", "scadatools"));
        list.add(makeItem("smod", "SMOD", "MODBUS Penetration Test Framework", "sudo smod", "modbus"));
        list.add(makeItem("expliot", "eXplIOT", "IOT Security Testing and Exploitation", "expliot", "iot"));
        list.add(makeItem("homepwn", "HomePWN", "Swiss Army Knife for Pentesting of IoT Devices", "homePwn", "homepwn"));
        list.add(makeItem("onthefly", "On-The-Fly", "Network Pentesting on IT, ICS &amp; IoT Environments", "on-the-fly", "onthefly"));
        list.add(makeItem("termineter", "termineter", "Smart Meter Security Testing Framework", "termineter", "termineter"));
        return list;
    }

    private ToolItem makeItem(String key, String displayName, String description, String cmd, String drawableName) {
        ToolItem item = new ToolItem();
        item.key = key;
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