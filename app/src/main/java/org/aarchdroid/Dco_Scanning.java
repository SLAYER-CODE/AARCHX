package org.aarchdroid;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class Dco_Scanning extends DcoBaseActivity {
    private static final String TAG = "Dco_Scanning";

    @Override
    protected void onCreate(Bundle bundle) {
        try {
            requestWindowFeature(1);
            super.onCreate(bundle);
            Log.d(TAG, "onCreate");
            setContentView(R.layout.dco_list_scaffold);
            getWindow().setFlags(1024, 1024);

            TextView titleView = findViewById(R.id.title);
            titleView.setText("Scanning");
            ((android.widget.ImageView) findViewById(R.id.banner)).setImageResource(R.drawable.scanning);
            ((TextView) findViewById(R.id.stats_tools)).setText("6");

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
                    Dco_Scanning.this.onUninstallClick(toolKey);
                }

                @Override
                public void onLaunchTool(String toolKey) {
                    Dco_Scanning.this.onLaunchTool(toolKey);
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
        list.add(makeItem("nmap", "Nmap", "Advanced Network Mapper", "nmap", "nmap"));
        list.add(makeItem("masscan", "MASSCAN", "Mass IP port scanner", "masscan --help", "masscan"));
        list.add(makeItem("nbtscan", "NBTScan", "NETBIOS Scanner", "nbtscan", "nbtscan"));
        list.add(makeItem("sctpscan", "SCTPScan", "SCTP Network Scanner", "sctpscan", "sctpscan"));
        list.add(makeItem("ikescan", "IKE-Scan", "Discover and fingerprint IKE hosts", "sudo ike-scan", "ikescan"));
        list.add(makeItem("ssh_auditor", "SSH-AUDITOR", "The best way to scan for weak ssh passwords", "ssh-auditor", "sshauditor"));
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