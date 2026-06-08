package org.aarchdroid;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class Dco_bug_bounty extends DcoBaseActivity {
    private static final String TAG = "Dco_bug_bounty";

    @Override
    protected void onCreate(Bundle bundle) {
        try {
            requestWindowFeature(1);
            super.onCreate(bundle);
            Log.d(TAG, "onCreate");
            setContentView(R.layout.dco_list_scaffold);
            getWindow().setFlags(1024, 1024);

            TextView titleView = findViewById(R.id.title);
            titleView.setText("Bug Bounty");
            ((android.widget.ImageView) findViewById(R.id.banner)).setImageResource(R.drawable.bugbounty);
            ((TextView) findViewById(R.id.stats_tools)).setText("33");

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
        list.add(makeItem("bgpleak", "BGP-Leak", "Exposing your Motherfucker Organization", "bgp-leak -h", "bgpleak"));
        list.add(makeItem("amass", "Amass", "In-depth Attack Surface Mapping and Asset Discovery", "amass", "amass"));
        list.add(makeItem("subfinder", "Subfinder", "Passive subdomain discovery", "subfinder", "subfinder"));
        list.add(makeItem("uncover", "Uncover", "Discover exposed hosts on the internet", "uncover", "andraxtool"));
        list.add(makeItem("aron", "Aron", "Find hidden GET/POST", "aron -h", "andraxtool"));
        list.add(makeItem("arjun", "Arjun", "HTTP parameter discovery suite", "arjun -h", "arjun"));
        list.add(makeItem("whatweb", "WhatWeb", "Next Generation Web Scanner", "whatweb -h", "whatweb"));
        list.add(makeItem("katana", "Katana", "Next GEN Crawling and Spidering", "katana", "katana"));
        list.add(makeItem("nuclei", "Nuclei", "Advanced targeted scan based on templates", "nuclei", "nuclei"));
        list.add(makeItem("httpx", "HttpX", "Multi-purpose HTTP toolkit", "httpx", "httpx"));
        list.add(makeItem("jaeles", "Jaeles", "Next Generation Web Application Testing", "jaeles", "jaeles"));
        list.add(makeItem("wbk", "WBK", "WayBackURLS Client", "wbk -h", "waybackmachine"));
        list.add(makeItem("findalllinks", "Find-All-Links", "Find links using Wayback Machine", "find-all-links", "findalllinks"));
        list.add(makeItem("wfuzz", "Wfuzz", "Web Application Fuzzer", "wfuzz --help", "wfuzz"));
        list.add(makeItem("commix", "COMMIX", "All-in-One OS command injection and exploitation tool", "commix", "commix"));
        list.add(makeItem("sqlmap", "Sqlmap", "", "sqlmap", "sqlmap"));
        list.add(makeItem("dotdotpwn", "DotDotPWN", "The Directory Traversal Fuzzer", "dotdotpwn", "dotdotpwn"));
        list.add(makeItem("nodexp", "NodeXP", "Server Side JavaScript Injection", "nodexp --help", "nodexp"));
        list.add(makeItem("jsalert", "JS-Alert", "Find keywords in javascript files and extract the context", "jsalert", "andraxtool"));
        list.add(makeItem("xsstrike", "XSSTrike", "Advanced XSS Detection Suite", "xsstrike", "xsstrike"));
        list.add(makeItem("xspear", "XSpear", "Powerfull XSS Scanning and Parameter analysis", "XSpear -h", "xspear"));
        list.add(makeItem("payloadmask", "PayloadMask", "Bypass WAF using payload editing", "payloadmask", "payloadmask"));
        list.add(makeItem("crlfuzz", "CRLFuzz", "Fast CRLF vulnerability scanner", "crlfuzz -h", "crlfuzz"));
        list.add(makeItem("kadimus", "Kadimus", "LFI Scan &amp; Exploit Tool", "kadimus", "andraxtool"));
        list.add(makeItem("xxeinjector", "XXEInjector", "XML External Entity Injector", "xxeinjector", "xxe"));
        list.add(makeItem("xxexploiter", "XXExploiter", "XXE Exploiter", "xxexploiter", "xxe"));
        list.add(makeItem("xxetimes", "XXETimes", "Local File Explorer XXE DTD Entity Expansion", "xxetimes -h", "xxe"));
        list.add(makeItem("phpsploit", "PHPSploit", "Remote control framework for web", "phpsploit", "andraxtool"));
        list.add(makeItem("jwt_tool", "JWT_Tool", "The JSON Web Token Toolkit", "jwt_tool -h", "jwt"));
        list.add(makeItem("jwtcrack", "JWT-Crack", "JWT Brute Force Cracker", "jwtcrack", "jwt"));
        list.add(makeItem("nomore403", "nomore403", "Bypass 40X response codes", "nomore403", "nomore403"));
        list.add(makeItem("forbidden", "Forbidden", "Bypass 4xx HTTP response", "forbidden", "forbidden"));
        list.add(makeItem("smuggler", "Smuggler", "HTTP Request Smuggling/Desync", "smuggler", "smuggler"));
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