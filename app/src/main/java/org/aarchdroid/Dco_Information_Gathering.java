package org.aarchdroid;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class Dco_Information_Gathering extends DcoBaseActivity {
    private static final String TAG = "Dco_Information_Gathering";

    @Override
    protected void onCreate(Bundle bundle) {
        try {
            requestWindowFeature(1);
            super.onCreate(bundle);
            Log.d(TAG, "onCreate");
            setContentView(R.layout.dco_list_scaffold);
            getWindow().setFlags(1024, 1024);

            TextView titleView = findViewById(R.id.title);
            titleView.setText("Information Gathering");
            ((android.widget.ImageView) findViewById(R.id.banner)).setImageResource(R.drawable.information_gathering);
            ((TextView) findViewById(R.id.stats_tools)).setText("19");

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
                    Dco_Information_Gathering.this.onUninstallClick(toolKey);
                }

                @Override
                public void onLaunchTool(String toolKey) {
                    Dco_Information_Gathering.this.onLaunchTool(toolKey);
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
        list.add(makeItem("whois", "Whois", "Domain/IP Information", "whois", "andraxtool"));
        list.add(makeItem("dig", "DIG", "Domain Information Groper", "dig -h", "andraxtool"));
        list.add(makeItem("dnsx", "dnsX", "Fast multi-purpose DNS toolkit", "dnsx", "dnsx"));
        list.add(makeItem("shuffledns", "ShuffleDNS", "Mass Subdomain Enumeration", "shuffledns", "shuffledns"));
        list.add(makeItem("massdns", "MassDNS", "High-performance DNS stub resolver", "massdns", "andraxtool"));
        list.add(makeItem("dnsmap", "DNSMap", "DNS Network Mapper", "dnsmap", "andraxtool"));
        list.add(makeItem("subfinder", "Subfinder", "Passive subdomain discovery", "subfinder", "subfinder"));
        list.add(makeItem("bgpleak", "BGP-Leak", "Exposing your Motherfucker Organization", "bgp-leak -h", "bgpleak"));
        list.add(makeItem("uncover", "Uncover", "Discover exposed hosts on the internet", "uncover", "andraxtool"));
        list.add(makeItem("trace6", "Trace6", "Traceroute for IPv6", "trace6", "andraxtool"));
        list.add(makeItem("intrace", "InTrace", "Enumerate IP hops using TCP", "intrace", "andraxtool"));
        list.add(makeItem("amass", "Amass", "In-depth Attack Surface Mapping and Asset Discovery", "amass", "amass"));
        list.add(makeItem("spiderfoot", "SpiderFoot", "Threat Intelligence and Attack Mapping", "sfcli", "spiderfoot"));
        list.add(makeItem("onesixtyone", "Onesixtyone", "Fast SNMP Scanner", "onesixtyone", "onesixtyone"));
        list.add(makeItem("braa", "Braa", "Mass snmp scanner", "braa", "braa"));
        list.add(makeItem("snmpwn", "SNMPwn", "SNMPv3 user enumerator and attack tool", "snmpwn --help", "andraxtool"));
        list.add(makeItem("swaks", "Swaks", "Swiss Army Knife for SMTP", "swaks", "swaks"));
        list.add(makeItem("ismtp", "iSMTP", "SMTP Server Tester", "iSMTP", "ismtp"));
        list.add(makeItem("smtpuserenum", "SMTP-User-Enum", "SMTP User Enumeration", "smtp-user-enum", "smtpuserenum"));
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