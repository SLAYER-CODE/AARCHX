package org.aarchdroid;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class Dco_c2_rat extends DcoBaseActivity {
    private static final String TAG = "Dco_c2_rat";

    @Override
    protected void onCreate(Bundle bundle) {
        try {
            requestWindowFeature(1);
            super.onCreate(bundle);
            Log.d(TAG, "onCreate");
            setContentView(R.layout.dco_list_scaffold);
            getWindow().setFlags(1024, 1024);

            TextView titleView = findViewById(R.id.title);
            titleView.setText("C2/RAT");
            ((android.widget.ImageView) findViewById(R.id.banner)).setImageResource(R.drawable.c2);
            ((TextView) findViewById(R.id.stats_tools)).setText("6");

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
        list.add(makeItem("merlin", "MerlinC2", "Post-exploitation C2", "merlin-c2", "merlin"));
        list.add(makeItem("nimcrypt", "Nimcrypt2", ".NET, PE, &amp; Raw Shellcode Packer/Loader", "nimcrypt -h", "nimcrypt"));
        list.add(makeItem("godoh", "GoDoH", "DNS-over-HTTPS C2", "godoh -h", "godoh"));
        list.add(makeItem("phpsploit", "PHPSploit", "Remote control framework for web", "phpsploit", "andraxtool_blackbg"));
        list.add(makeItem("evilwinrm", "Evil-WinRM", "The ultimate WinRM shell", "evil-winrm", "evilwinrm"));
        list.add(makeItem("exe2hex", "exe2hex", "Encode Win EXE to HEX", "exe2hex", "exe2hex"));
        return list;
    }

    private ToolItem makeItem(String key, String displayName, String description, String cmd, String drawableName) {
        ToolItem item = new ToolItem();
        item.key = key;
        item.displayName = displayName;
        item.description = description;
        item.cmd = cmd;
        if (drawableName == null || drawableName.isEmpty()) {
            item.iconResId = R.drawable.andraxtool_blackbg;
        } else {
            int id = getResources().getIdentifier(drawableName, "drawable", getPackageName());
            item.iconResId = id != 0 ? id : R.drawable.andraxtool_blackbg;
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