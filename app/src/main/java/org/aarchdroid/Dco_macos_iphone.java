package org.aarchdroid;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class Dco_macos_iphone extends DcoBaseActivity {
    private static final String TAG = "Dco_macos_iphone";

    @Override
    protected void onCreate(Bundle bundle) {
        try {
            requestWindowFeature(1);
            super.onCreate(bundle);
            Log.d(TAG, "onCreate");
            setContentView(R.layout.dco_list_scaffold);
            getWindow().setFlags(1024, 1024);

            TextView titleView = findViewById(R.id.title);
            titleView.setText("MacOS/iPhone");
            ((android.widget.ImageView) findViewById(R.id.banner)).setImageResource(R.drawable.evilapple);
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
                    Dco_macos_iphone.this.onUninstallClick(toolKey);
                }

                @Override
                public void onLaunchTool(String toolKey) {
                    Dco_macos_iphone.this.onLaunchTool(toolKey);
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
        list.add(makeItem("merlin", "MerlinC2", "Post-exploitation C2", "merlin-c2", "merlin"));
        list.add(makeItem("godoh", "GoDoH", "DNS-over-HTTPS C2", "godoh -h", "godoh"));
        list.add(makeItem("owl", "owl", "Apple Wireless Direct Link", "owl", "owl"));
        list.add(makeItem("applebleee", "Apple-BLEEE", "Apple BLE Sniffing", "apple-bleee", "applebleee"));
        list.add(makeItem("iblessing", "iblessing", "iOS Security Exploiting Toolkit", "iblessing", "evilapplecolor"));
        list.add(makeItem("frida", "Frida", "Dynamic instrumentation for Reverse-Engineers", "frida", "frida"));
        list.add(makeItem("objection", "OBJection", "Runtime Mobile Exploration", "objection", "objection"));
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