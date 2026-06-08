package org.aarchdroid;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class Dco_stress_testing extends DcoBaseActivity {
    private static final String TAG = "Dco_stress_testing";

    @Override
    protected void onCreate(Bundle bundle) {
        try {
            requestWindowFeature(1);
            super.onCreate(bundle);
            Log.d(TAG, "onCreate");
            setContentView(R.layout.dco_list_scaffold);
            getWindow().setFlags(1024, 1024);

            TextView titleView = findViewById(R.id.title);
            titleView.setText("Stress Testing");
            ((android.widget.ImageView) findViewById(R.id.banner)).setImageResource(R.drawable.stress_testing);
            ((TextView) findViewById(R.id.stats_tools)).setText("18");

            RecyclerView list = findViewById(R.id.tool_list);
            list.setLayoutManager(new LinearLayoutManager(this));

            List<ToolItem> tools = buildToolList();
            ToolAdapter adapter = new ToolAdapter(tools, new ToolAdapter.OnToolClickListener() {
                @Override
                public void onToolClick(String cmd) {
                    run_hack_cmd(cmd);
                }

                @Override
                public void onInstallClick(String toolKey) {
                    String cmd = buildInstallCommandForKey(toolKey);
                    if (cmd != null) {
                        run_hack_cmd(cmd);
                    }
                }
            });
            list.setAdapter(adapter);
            list.setHasFixedSize(true);
        } catch (Exception e) {
            Log.e(TAG, "onCreate failed", e);
            finish();
        }
    }

    private List<ToolItem> buildToolList() {
        List<ToolItem> list = new ArrayList<>();
        list.add(makeItem("dnsdrdos", "dnsdrdos", "Distributed DNS reflection DoS", "dnsdrdos -H", "andraxtool"));
        list.add(makeItem("slowhttptest", "SlowHTTPTest", "highly configurable stress testing simulator", "slowhttptest", "andraxtool"));
        list.add(makeItem("fuzzip6", "Fuzz_ip6", "Fuzzes an IPv6 packet", "fuzz_ip6", "andraxtool"));
        list.add(makeItem("denial6", "Denial6", "Perform various DoS attacks", "denial6", "andraxtool"));
        list.add(makeItem("flooddhcpc6", "Flood_dhcpc6", "DHCP Client Flooder", "flood_dhcpc6", "andraxtool"));
        list.add(makeItem("floodadvertise6", "Flood_advertise6", "Flood network with advertise", "flood_advertise6", "andraxtool"));
        list.add(makeItem("floodmld6", "Flood_mld6", "Flood local network with MLD", "flood_mld6", "andraxtool"));
        list.add(makeItem("floodmld26", "Flood_mld26", "Flood local network with MLDv2", "flood_mld26", "andraxtool"));
        list.add(makeItem("floodmldrouter6", "Flood_mldrouter6", "Flood MLD router with advertisements", "flood_mldrouter6", "andraxtool"));
        list.add(makeItem("floodredir6", "Flood_redir6", "Flood with ICMPv6 redirect", "flood_redir6", "andraxtool"));
        list.add(makeItem("floodrouter6", "Flood_router6", "Flood with router advertisements", "flood_router6", "andraxtool"));
        list.add(makeItem("floodrouter26", "Flood_router26", "Flood with router advertisements", "flood_router26", "andraxtool"));
        list.add(makeItem("floodrs6", "Flood_rs6", "Flood with ICMPv6 Router Solicitation", "flood_rs6", "andraxtool"));
        list.add(makeItem("floodsolicitate6", "Flood_solicitate6", "Flood with neighbor solicitations", "flood_solicitate6", "andraxtool"));
        list.add(makeItem("floodunreach6", "Flood_unreach6", "Flood with ICMPv6 unreachable packets", "flood_unreach6", "andraxtool"));
        list.add(makeItem("rsmurf6", "Rsmurf6", "Smurfs the vctim&apos;s local network", "rsmurf6", "andraxtool"));
        list.add(makeItem("dosnewip6", "Dos-new-ip6", "DoS new IPv6 on LAN", "dos-new-ip6", "andraxtool"));
        list.add(makeItem("randicmp6", "Randicmp6", "Send all ICMPv6 types to a destination", "randicmp6", "andraxtool"));
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