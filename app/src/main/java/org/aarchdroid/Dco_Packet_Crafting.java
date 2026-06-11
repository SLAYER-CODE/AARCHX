package org.aarchdroid;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class Dco_Packet_Crafting extends DcoBaseActivity {
    private static final String TAG = "Dco_Packet_Crafting";

    @Override
    protected void onCreate(Bundle bundle) {
        try {
            requestWindowFeature(1);
            super.onCreate(bundle);
            Log.d(TAG, "onCreate");
            setContentView(R.layout.dco_list_scaffold);
            getWindow().setFlags(1024, 1024);

            TextView titleView = findViewById(R.id.title);
            titleView.setText("Packet Crafting");
            ((android.widget.ImageView) findViewById(R.id.banner)).setImageResource(R.drawable.packet_crafting);
            ((TextView) findViewById(R.id.stats_tools)).setText("7");

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
                    Dco_Packet_Crafting.this.onUninstallClick(toolKey);
                }

                @Override
                public void onLaunchTool(String toolKey) {
                    Dco_Packet_Crafting.this.onLaunchTool(toolKey);
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
        list.add(makeItem("arping", "Arping", "Ping destination by ARP", "arping", "arping"));
        list.add(makeItem("thcping6", "THCPing6", "icmpv6 echo request", "thcping6", "andraxtool"));
        list.add(makeItem("nping", "Nping", "Universal network packet generator", "nping", "nmap"));
        list.add(makeItem("hexinject", "HexInject", "Versatile packet injector and sniffer", "hexinject -h", "hexinject"));
        list.add(makeItem("bittwist", "Bit-Twist", "Ethernet packet generator", "bittwist -h", "bittwist"));
        list.add(makeItem("scapy", "Scapy", "Powerful interactive packet manipulation", "sudo scapy", "scapy"));
        list.add(makeItem("nemesis", "Nemesis", "CMD-Line Packet Crafting and Injection Tool", "nemesis", "nemesis"));
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