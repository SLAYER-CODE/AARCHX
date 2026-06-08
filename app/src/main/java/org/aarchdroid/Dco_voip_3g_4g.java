package org.aarchdroid;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class Dco_voip_3g_4g extends DcoBaseActivity {
    private static final String TAG = "Dco_voip_3g_4g";

    @Override
    protected void onCreate(Bundle bundle) {
        try {
            requestWindowFeature(1);
            super.onCreate(bundle);
            Log.d(TAG, "onCreate");
            setContentView(R.layout.dco_list_scaffold);
            getWindow().setFlags(1024, 1024);

            TextView titleView = findViewById(R.id.title);
            titleView.setText("VoIP/3G/4G Hacking");
            ((android.widget.ImageView) findViewById(R.id.banner)).setImageResource(R.drawable.voip_banner);
            ((TextView) findViewById(R.id.stats_tools)).setText("21");

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
        list.add(makeItem("enodebhack", "eNodeB", "Evolved Node B for LTE Hacking and 5G Downgrade", "eNodeB-HACK -h", "lte"));
        list.add(makeItem("mmeenodebhack", "MME-eNodeB", "MME enhanced with eNodeB", "mme-eNodeB-HACK -h", "lte"));
        list.add(makeItem("sgwhack", "SGW", "Serving Gateway", "SGW-HACK -h", "cellphonetower"));
        list.add(makeItem("pgwhack", "PGW", "Packet Data Network Gateway", "PGW-HACK -h", "cellphonetower"));
        list.add(makeItem("diameterenum", "Diameter-Enum", "Diameter Scanner", "diameter_enum -h", "diametertower"));
        list.add(makeItem("s1apenum", "S1AP-Enum", "S1SetupRequest PDU to MME", "s1ap_enum", "diametertower"));
        list.add(makeItem("gtpscan", "GTP-Scan", "GTP Scanner", "gtp_scan -h", "diametertower"));
        list.add(makeItem("cryptomobile", "CryptoMobile", "Rape mobile Crypto", "cryptomobile", "cryptomobile"));
        list.add(makeItem("enumiax", "ENUMIAX", "Asterisk Exchange protocol", "enumiax", "andraxicon_svg"));
        list.add(makeItem("svmap", "SVMap", "SIP Scanner", "sipvicious_svmap", "sipvicious"));
        list.add(makeItem("isip", "iSIP", "Interactive sip toolkit for packet manipulations", "sudo isip", "sip"));
        list.add(makeItem("sipsak", "SIPSAK", "For developers and administrators of SIP", "sipsak", "andraxicon_svg"));
        list.add(makeItem("vsaudit", "VSAudit", "Perform attacks to general voip services", "vsaudit", "andraxicon_svg"));
        list.add(makeItem("protostestsuite", "PROTOS Test Suite", "Fuck SIP protocol like a motherfucker", "protos-test-suite", "sip"));
        list.add(makeItem("iaxflood", "IAXFLOOD", "A UDP Inter-Asterisk_eXchange", "iaxflood", "andraxicon_svg"));
        list.add(makeItem("inviteflood", "INVITEFLOOD", "Perform SIP/SDP INVITE flooding over UDP/IP", "inviteflood", "andraxicon_svg"));
        list.add(makeItem("rtpflood", "RTPFLOOD", "Flood any device that is processing RTP", "rtpflood", "andraxicon_svg"));
        list.add(makeItem("udpfloodVLAN", "UDPFloodVLAN", "Flood UDP VLAN packets", "udpfloodVLAN", "andraxicon_svg"));
        list.add(makeItem("rtpbreak", "RTPBREAK", "Detects, reconstructs, and analyzes RTP sessions", "rtpbreak", "andraxicon_svg"));
        list.add(makeItem("sipcracker", "SipCRACKER", "Remote password cracker for SIP", "sipcracker", "sip"));
        list.add(makeItem("rtpinsertsound", "RTPINSERTSOUND", "Insert audio into a specified audio RTP stream", "rtpinsertsound", "andraxicon_svg"));
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