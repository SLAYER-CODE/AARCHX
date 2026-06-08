package org.aarchdroid;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class Dco_phishing extends DcoBaseActivity {
    private static final String TAG = "Dco_phishing";

    @Override
    protected void onCreate(Bundle bundle) {
        try {
            requestWindowFeature(1);
            super.onCreate(bundle);
            Log.d(TAG, "onCreate");
            setContentView(R.layout.dco_list_scaffold);
            getWindow().setFlags(1024, 1024);

            TextView titleView = findViewById(R.id.title);
            titleView.setText("Phishing");
            ((android.widget.ImageView) findViewById(R.id.banner)).setImageResource(R.drawable.phishing);
            ((TextView) findViewById(R.id.stats_tools)).setText("5");

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
        list.add(makeItem("evilginx2", "EVILGINX2", "MITM Attack Framework for Credential Harvesting", "sudo evilginx", "evilginx2"));
        list.add(makeItem("modlishka", "Modlishka", "Reverse proxy for next level phishing", "sudo modlishka", "andraxtool"));
        list.add(makeItem("gophish", "GOPhish", "Open-Source Phishing Toolkit", "sudo gophish", "gophish"));
        list.add(makeItem("bitb", "BITB", "Browser In The Browser Templates", "bitb", "bitb"));
        list.add(makeItem("urlcrazy", "URLCrazy", "URL hijacking, Phishing, and Corporate Espionage", "urlcrazy", "urlcrazy"));
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