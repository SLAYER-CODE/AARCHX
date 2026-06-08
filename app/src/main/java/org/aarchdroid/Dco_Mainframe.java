package org.aarchdroid;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class Dco_Mainframe extends DcoBaseActivity {
    private static final String TAG = "Dco_Mainframe";

    @Override
    protected void onCreate(Bundle bundle) {
        try {
            requestWindowFeature(1);
            super.onCreate(bundle);
            Log.d(TAG, "onCreate");
            setContentView(R.layout.dco_list_scaffold);
            getWindow().setFlags(1024, 1024);

            TextView titleView = findViewById(R.id.title);
            titleView.setText("Mainframe");
            ((android.widget.ImageView) findViewById(R.id.banner)).setImageResource(R.drawable.mainframe);
            ((TextView) findViewById(R.id.stats_tools)).setText("13");

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
        list.add(makeItem("psikotik", "PSIKOTIK", "TSO User Enumerator", "psikotik -h", "ziron"));
        list.add(makeItem("mfsniffer", "MFSniffer", "Capture TSO user ID and password", "MFSniffer -h", "ziron"));
        list.add(makeItem("birp", "BIRP", "Big Iron Recon &amp; Pwnage", "birp -h", "ziron"));
        list.add(makeItem("mfdos", "MFDoS", "Mainframe TN3270 DoS", "MFDoS -h", "ziron"));
        list.add(makeItem("phatso", "PhaTSO", "TSO User Brute Forcer", "phatso -h", "ziron"));
        list.add(makeItem("tpxbrute", "TPX_Brute", "The z/OS TPX logon brute forcer", "TPX_Brute -h", "ziron"));
        list.add(makeItem("mainframe_bruter", "Mainframe_Bruter", "z/OS Mainframe Bruteforcer", "mainframe_bruter -h", "ziron"));
        list.add(makeItem("cicsshot", "CICSSHOT", "Screenshotting CICS transactions", "cicsshot -h", "ziron"));
        list.add(makeItem("cicspwn", "CICSPWN", "Pentest CICS Transaction servers on z/OS", "cicspwn -h", "ziron"));
        list.add(makeItem("TShOcker", "TShOcker", "Meterpreter like TSO reverse shell", "TShOcker -h", "ziron"));
        list.add(makeItem("netEBCDICat", "netEBCDICat", "Accept z/OS EBCDIC Socket Reverse Shells", "netEBCDICat -h", "ziron"));
        list.add(makeItem("maintp", "MainTP", "Mainframe Reverse/Bind Root Shell", "MainTP -h", "ziron"));
        list.add(makeItem("zosprivesc", "zOS-PRIVESC", "Privilege escalation on z/OS", "zos-privesc", "ziron"));
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