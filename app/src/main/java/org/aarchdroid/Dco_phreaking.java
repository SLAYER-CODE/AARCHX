package org.aarchdroid;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class Dco_phreaking extends DcoBaseActivity {
    private static final String TAG = "Dco_phreaking";

    @Override
    protected void onCreate(Bundle bundle) {
        try {
            requestWindowFeature(1);
            super.onCreate(bundle);
            Log.d(TAG, "onCreate");
            setContentView(R.layout.dco_list_scaffold);
            getWindow().setFlags(1024, 1024);

            TextView titleView = findViewById(R.id.title);
            titleView.setText("Phreaking");
            ((android.widget.ImageView) findViewById(R.id.banner)).setImageResource(R.drawable.phreaking);
            ((TextView) findViewById(R.id.stats_tools)).setText("24");

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
                    Dco_phreaking.this.onUninstallClick(toolKey);
                }

                @Override
                public void onLaunchTool(String toolKey) {
                    Dco_phreaking.this.onLaunchTool(toolKey);
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
        list.add(makeItem("sippts", "Sippts", "", "sippts", "sippts"));
        list.add(makeItem("svmap", "svmap", "SIP Scanner", "sipvicious_svmap", "sipvicious"));
        list.add(makeItem("svwar", "svwar", "Identifies working lines on a PBX", "sipvicious_svwar", "sipvicious"));
        list.add(makeItem("rtpbreak", "RTPBREAK", "Detects, reconstructs, and analyzes RTP sessions", "rtpbreak", "andraxtool_blackbg"));
        list.add(makeItem("svcrack", "svcrack", "Crack PBX passwords", "sipvicious_svcrack", "sipvicious"));
        list.add(makeItem("enumiax", "ENUMIAX", "IAX Protocol Enumerator", "enumiax", "andraxtool_blackbg"));
        list.add(makeItem("rtpinsertsound", "RTPInsertSOUND", "Insert audio into RTP stream", "rtpinsertsound", "andraxtool_blackbg"));
        list.add(makeItem("iaxflood", "IAXFLOOD", "Inter-Asterisk_eXchange Flooder", "iaxflood", "andraxtool_blackbg"));
        list.add(makeItem("inviteflood", "INVITEFLOOD", "SIP/SDP INVITE flooding over UDP/IP", "inviteflood", "andraxtool_blackbg"));
        list.add(makeItem("rtpflood", "RTPFLOOD", "Flood any device that is processing RTP", "rtpflood", "andraxtool_blackbg"));
        list.add(makeItem("udpfloodVLAN", "UDPFloodVLAN", "UDP Flood with VLAN Support", "udpfloodVLAN", "andraxtool_blackbg"));
        list.add(makeItem("voiphopper", "VOIPHopper", "VoIP Hopper Network Penetration Testing", "voiphopper -h", "voiphopper"));
        list.add(makeItem("vsaudit", "VSAudit", "VOIP Security Audit Framework", "vsaudit", "andraxtool_blackbg"));
        list.add(makeItem("sipsak", "SIPSAK", "SIP swiss army knife", "sipsak", "andraxtool_blackbg"));
        list.add(makeItem("isip", "iSIP", "Interactive sip toolkit for packet manipulations", "sudo isip", "sip"));
        list.add(makeItem("sctpscan", "SCTPScan", "SCTP Network Scanner", "sctpscan", "sctpscan"));
        list.add(makeItem("gtpscan", "GTP-Scan", "GTP Scanner", "gtp_scan -h", "gtp_scan"));
        list.add(makeItem("diameterenum", "Diameter-Enum", "Diameter Scanner", "diameter_enum -h", "ltetower"));
        list.add(makeItem("s1apenum", "S1AP_Enum", "S1AP Enumerator", "s1ap_enum", "ltetower"));
        list.add(makeItem("cryptomobile", "CryptoMobile", "Rape mobile Crypto", "cryptomobile", "cryptomobile"));
        list.add(makeItem("enodebhack", "eNodeB", "Evolved Node B for LTE Hacking and 5G Downgrade", "eNodeB", "lte"));
        list.add(makeItem("mme", "Mme", "", "mme", "lte"));
        list.add(makeItem("sgwhack", "SGW", "Serving Gateway", "sgw", "cellphonetower"));
        list.add(makeItem("pgwhack", "PGW", "Packet Data Network Gateway", "pgw", "cellphonetower"));
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