package org.aarchdroid;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class Dco_Password_Hacking extends DcoBaseActivity {
    private static final String TAG = "Dco_Password_Hacking";

    @Override
    protected void onCreate(Bundle bundle) {
        try {
            requestWindowFeature(1);
            super.onCreate(bundle);
            Log.d(TAG, "onCreate");
            setContentView(R.layout.dco_list_scaffold);
            getWindow().setFlags(1024, 1024);

            TextView titleView = findViewById(R.id.title);
            titleView.setText("Password Cracking");
            ((android.widget.ImageView) findViewById(R.id.banner)).setImageResource(R.drawable.passwordhacking);
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
                    Dco_Password_Hacking.this.onUninstallClick(toolKey);
                }

                @Override
                public void onLaunchTool(String toolKey) {
                    Dco_Password_Hacking.this.onLaunchTool(toolKey);
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
        list.add(makeItem("maskprocessor", "MaskProcessor", "High-Performance Word Generator", "maskprocessor --help", "maskprocessor"));
        list.add(makeItem("cewl", "CeWL", "Custom Word List generator", "cewl -h", "cewl"));
        list.add(makeItem("bopscrk", "Bopscrk", "Generates smart and powerful wordlists", "bopscrk", "bopscrk"));
        list.add(makeItem("narthex", "narthex", "Modular Personalized Dictionary Generator", "nwiz", "narthex"));
        list.add(makeItem("crunch", "CRUNCH", "Wordlist Generator", "crunch", "crunch"));
        list.add(makeItem("pipal", "pipal", "Password analyser", "pipal -h", "pipal"));
        list.add(makeItem("hashboy", "HASHBoy", "Passive HASH Craker", "hashboy", "hashboy"));
        list.add(makeItem("ssh_auditor", "SSH-AUDITOR", "The best way to scan for weak ssh passwords", "ssh-auditor", "sshauditor"));
        list.add(makeItem("ncrack", "NCRACK", "High-SPEED Network AUTH Cracking", "ncrack", "ncrack"));
        list.add(makeItem("hydra", "HYDRA", "Tool to brute force protocols", "hydra", "hydra"));
        list.add(makeItem("medusa", "medusa", "Parallel Network Login Auditor", "medusa", "medusa"));
        list.add(makeItem("patator", "patator", "Multi-purpose brute-forcer", "patator", "patator"));
        list.add(makeItem("hashcat", "HASHCAT", "World&apos;s fastest password cracker", "hashcat -h", "hashcat"));
        list.add(makeItem("john", "John The Ripper", "Fast password cracker", "john", "john"));
        list.add(makeItem("bgpmd5crack", "BGP_MD5Crack", "RFC2385 password cracker", "bgp_md5crack -h", "andraxtool"));
        list.add(makeItem("pskcrack", "PSK-Crack", "Pre-Shared Key Cracking", "psk-crack", "ikescan"));
        list.add(makeItem("mfoc", "mfoc", "Mifare Classic Offline Cracker", "mfoc -h", "mfoc"));
        list.add(makeItem("eapmd5pass", "EAP-MD5-Pass", "Offline EAP-MD5 dictionary attack", "eapmd5pass", "andraxtool"));
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