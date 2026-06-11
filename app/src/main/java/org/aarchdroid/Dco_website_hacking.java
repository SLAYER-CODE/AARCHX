package org.aarchdroid;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class Dco_website_hacking extends DcoBaseActivity {
    private static final String TAG = "Dco_website_hacking";

    @Override
    protected void onCreate(Bundle bundle) {
        try {
            requestWindowFeature(1);
            super.onCreate(bundle);
            Log.d(TAG, "onCreate");
            setContentView(R.layout.dco_list_scaffold);
            getWindow().setFlags(1024, 1024);

            TextView titleView = findViewById(R.id.title);
            titleView.setText("Website Hacking");
            ((android.widget.ImageView) findViewById(R.id.banner)).setImageResource(R.drawable.websitehacking);
            ((TextView) findViewById(R.id.stats_tools)).setText("45");

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
                    Dco_website_hacking.this.onUninstallClick(toolKey);
                }

                @Override
                public void onLaunchTool(String toolKey) {
                    Dco_website_hacking.this.onLaunchTool(toolKey);
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
        list.add(makeItem("wafw00f", "WAFw00f", "Identifies and fingerprints WAF", "wafw00f -h", "wafw00f"));
        list.add(makeItem("payloadmask", "PayloadMask", "Bypass WAF using payload editing", "payloadmask", "payloadmask"));
        list.add(makeItem("aron", "Aron", "Find hidden GET/POST", "aron -h", "andraxtool"));
        list.add(makeItem("arjun", "Arjun", "HTTP parameter discovery suite", "arjun -h", "arjun"));
        list.add(makeItem("cansina", "Cansina", "Web Content Discovery", "cansina -h", "cansina"));
        list.add(makeItem("lulzbuster", "Lulzbuster", "Search Web for Fun", "lulzbuster -H", "andraxtool"));
        list.add(makeItem("ffuf", "Ffuf", "Fuzz Faster U Fool", "ffuf -h", "ffuf"));
        list.add(makeItem("monsoon", "Monsoon", "Fast HTTP enumerator", "monsoon --help", "monsoon"));
        list.add(makeItem("wfuzz", "Wfuzz", "Web Application Fuzzer", "wfuzz --help", "wfuzz"));
        list.add(makeItem("gobuster", "Gobuster", "Directory/File, DNS and VHost buster", "gobuster", "andraxtool"));
        list.add(makeItem("httpx", "HttpX", "Multi-purpose HTTP toolkit", "httpx", "httpx"));
        list.add(makeItem("cmseek", "CMSeeK", "CMS Detection and Exploitation suite", "sudo cmseek", "cmseek"));
        list.add(makeItem("wpscan", "WPScan", "WordPress Security Scanner", "wpscan -h", "wpscan"));
        list.add(makeItem("findalllinks", "Find-All-Links", "Find links using Wayback Machine", "find-all-links", "findalllinks"));
        list.add(makeItem("wbk", "WBK", "WayBackURLS Client", "wbk -h", "waybackmachine"));
        list.add(makeItem("whatweb", "WhatWeb", "Next Generation Web Scanner", "whatweb -h", "whatweb"));
        list.add(makeItem("nuclei", "Nuclei", "Advanced targeted scan based on templates", "nuclei", "nuclei"));
        list.add(makeItem("jaeles", "Jaeles", "Next Generation Web Application Testing", "jaeles", "jaeles"));
        list.add(makeItem("zap", "Zap", "Zed Attack Proxy (ZAP)", "zap", "zap"));
        list.add(makeItem("mitmproxy", "Mitmproxy", "SSL-capable proxy with a console interface", "mitmproxy", "mitmproxy"));
        list.add(makeItem("katana", "Katana", "Next GEN Crawling and Spidering", "katana", "katana"));
        list.add(makeItem("s3scanner", "S3scanner", "Scan for open S3 buckets and dump the contents", "s3scanner -h", "s3scanner"));
        list.add(makeItem("cadaver", "Cadaver", "WebDav Command Line", "cadaver", "cadaver"));
        list.add(makeItem("davtest", "DAVTest", "Exploit WebDAV", "davtest", "webdav"));
        list.add(makeItem("sqlmap", "Sqlmap", "", "sqlmap", "sqlmap"));
        list.add(makeItem("commix", "Commix", "All-in-One OS command injection and exploitation tool", "commix", "commix"));
        list.add(makeItem("dotdotpwn", "DotDotPWN", "The Directory Traversal Fuzzer", "dotdotpwn", "dotdotpwn"));
        list.add(makeItem("crlfuzz", "CRLFuzz", "Fast CRLF vulnerability scanner", "crlfuzz -h", "crlfuzz"));
        list.add(makeItem("kadimus", "Kadimus", "LFI Scan &amp; Exploit Tool", "kadimus", "andraxtool"));
        list.add(makeItem("odin", "0d1n", "Customized attacks against web applications", "0d1n", "odin"));
        list.add(makeItem("nodexp", "NodeXP", "Server Side JavaScript Injection", "nodexp --help", "nodexp"));
        list.add(makeItem("jsalert", "JS-Alert", "Find keywords in javascript files and extract the context", "jsalert", "andraxtool"));
        list.add(makeItem("xsstrike", "XSSTrike", "Advanced XSS Detection Suite", "xsstrike", "xsstrike"));
        list.add(makeItem("xspear", "XSpear", "Powerfull XSS Scanning and Parameter analysis", "XSpear -h", "xspear"));
        list.add(makeItem("imagejs", "ImageJS", "Package javascript into a valid image", "imagejs", "andraxtool"));
        list.add(makeItem("xxeinjector", "XXEInjector", "XML External Entity Injector", "xxeinjector", "xxe"));
        list.add(makeItem("xxexploiter", "XXExploiter", "XXE Exploiter", "xxexploiter", "xxe"));
        list.add(makeItem("xxetimes", "XXETimes", "Local File Explorer XXE DTD Entity Expansion", "xxetimes -h", "xxe"));
        list.add(makeItem("phpsploit", "PHPSploit", "Remote control framework for web", "phpsploit", "andraxtool"));
        list.add(makeItem("htshells", "HTShells", "Self contained htaccess shells", "htshells", "andraxtool"));
        list.add(makeItem("jwt_tool", "JWT_Tool", "The JSON Web Token Toolkit", "jwt_tool -h", "jwt"));
        list.add(makeItem("jwtcrack", "JWT-Crack", "JWT Brute Force Cracker", "jwtcrack", "jwt"));
        list.add(makeItem("nomore403", "nomore403", "Bypass 40X response codes", "nomore403", "andraxtool"));
        list.add(makeItem("forbidden", "Forbidden", "Bypass 4xx HTTP response", "forbidden", "forbidden"));
        list.add(makeItem("smuggler", "Smuggler", "HTTP Request Smuggling/Desync", "smuggler", "smuggler"));
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