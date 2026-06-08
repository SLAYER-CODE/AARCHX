import json, os, re

JAVA_DIR = "app/src/main/java/org/aarchdroid"
LAYOUT_DIR = "app/src/main/res/layout"

with open("dco_tool_data.json") as f:
    data = json.load(f)

banner_map = {
    "bug_bounty": "bugbounty",
    "c2_rat": "c2",
    "exploitation": "exploit",
    "ics_scada_iot": "ics",
    "information_gathering": "information_gathering",
    "macos_iphone": "evilapple",
    "mainframe": "mainframe",
    "network_hacking": "networkhacking",
    "packet_crafting": "packet_crafting",
    "password_hacking": "passwordhacking",
    "phishing": "phishing",
    "phreaking": "phreaking",
    "scanning": "scanning",
    "stress_testing": "stress_testing",
    "voip_3g_4g": "andrax_banner",
    "website_hacking": "websitehacking",
    "wireless_hacking": "wirelesshacking"
}

title_map = {
    "bug_bounty": "Bug Bounty",
    "c2_rat": "C2/RAT",
    "exploitation": "Exploitation",
    "ics_scada_iot": "ICS/SCADA/IIoT/IoT",
    "information_gathering": "Information Gathering",
    "macos_iphone": "MacOS/iPhone",
    "mainframe": "Mainframe",
    "network_hacking": "Network Hacking",
    "packet_crafting": "Packet Crafting",
    "password_hacking": "Password Cracking",
    "phishing": "Phishing",
    "phreaking": "Phreaking",
    "scanning": "Scanning",
    "stress_testing": "Stress Testing",
    "voip_3g_4g": "VoIP/3G/4G Hacking",
    "website_hacking": "Website Hacking",
    "wireless_hacking": "Wireless Hacking"
}

# Handle mainframe vs mainframes - Dco_Mainframe.java has XML dco_mainframe.xml (also dco_mainframes.xml)
# The data has both "mainframe" (13 tools) and "mainframes" (12 tools)
# Dco_Mainframe.java references both... actually let me check
# Let's use "mainframe" as the primary

# Map data key -> actual DCO class name
dco_to_class = {
    "bug_bounty": "Dco_bug_bounty",
    "c2_rat": "Dco_c2_rat",
    "exploitation": "Dco_exploitation",
    "ics_scada_iot": "Dco_ics_scada_iot",
    "information_gathering": "Dco_Information_Gathering",
    "macos_iphone": "Dco_macos_iphone",
    "mainframe": "Dco_Mainframe",
    "network_hacking": "Dco_network_hacking",
    "packet_crafting": "Dco_Packet_Crafting",
    "password_hacking": "Dco_Password_Hacking",
    "phishing": "Dco_phishing",
    "phreaking": "Dco_phreaking",
    "scanning": "Dco_Scanning",
    "stress_testing": "Dco_stress_testing",
    "voip_3g_4g": "Dco_voip_3g_4g",
    "website_hacking": "Dco_website_hacking",
    "wireless_hacking": "Dco_Wireless_Hacking"
}

for dco_key, class_name in dco_to_class.items():
    if dco_key == "exploitation":
        continue  # already done
    
    if dco_key not in data:
        print(f"SKIP {class_name}: no data for '{dco_key}'")
        continue
    
    entry = data[dco_key]
    cards = entry["cards"]
    
    header_title = title_map.get(dco_key, dco_key.replace("_", " ").title())
    banner_drawable = banner_map.get(dco_key, "andraxtool")
    
    java_file = os.path.join(JAVA_DIR, f"{class_name}.java")
    
    lines = []
    lines.append(f'package org.aarchdroid;')
    lines.append('')
    lines.append('import android.os.Bundle;')
    lines.append('import android.util.Log;')
    lines.append('import android.widget.ImageView;')
    lines.append('import android.widget.TextView;')
    lines.append('import androidx.recyclerview.widget.LinearLayoutManager;')
    lines.append('import androidx.recyclerview.widget.RecyclerView;')
    lines.append('import java.util.ArrayList;')
    lines.append('import java.util.List;')
    lines.append('')
    lines.append(f'public class {class_name} extends DcoBaseActivity {{')
    lines.append(f'    private static final String TAG = "{class_name}";')
    lines.append('')
    lines.append('    @Override')
    lines.append('    protected void onCreate(Bundle bundle) {')
    lines.append('        try {')
    lines.append('            requestWindowFeature(1);')
    lines.append('            super.onCreate(bundle);')
    lines.append('            Log.d(TAG, "onCreate");')
    lines.append('            setContentView(R.layout.dco_list_scaffold);')
    lines.append('            getWindow().setFlags(1024, 1024);')
    lines.append('')
    lines.append('            TextView titleView = findViewById(R.id.title);')
    lines.append(f'            titleView.setText("{header_title}");')
    lines.append(f'            ((android.widget.ImageView) findViewById(R.id.banner)).setImageResource({"R.drawable." + banner_drawable if banner_drawable else "R.drawable.andraxtool"});')
    lines.append(f'            ((TextView) findViewById(R.id.stats_tools)).setText("{len(cards)}");')
    lines.append('')
    lines.append('            RecyclerView list = findViewById(R.id.tool_list);')
    lines.append('            list.setLayoutManager(new LinearLayoutManager(this));')
    lines.append('')
    lines.append('            List<ToolItem> tools = buildToolList();')
    lines.append('            ToolAdapter adapter = new ToolAdapter(tools, new ToolAdapter.OnToolClickListener() {')
    lines.append('                @Override')
    lines.append('                public void onToolClick(String cmd) {')
    lines.append('                    run_hack_cmd(cmd);')
    lines.append('                }')
    lines.append('')
    lines.append('                @Override')
    lines.append('                public void onInstallClick(String toolKey) {')
    lines.append('                    String cmd = buildInstallCommandForKey(toolKey);')
    lines.append('                    if (cmd != null) {')
    lines.append('                        run_hack_cmd(cmd);')
    lines.append('                    }')
    lines.append('                }')
    lines.append('            });')
    lines.append('            list.setAdapter(adapter);')
    lines.append('            list.setHasFixedSize(true);')
    lines.append('        } catch (Exception e) {')
    lines.append('            Log.e(TAG, "onCreate failed", e);')
    lines.append('            finish();')
    lines.append('        }')
    lines.append('    }')
    lines.append('')
    lines.append('    private List<ToolItem> buildToolList() {')
    lines.append('        List<ToolItem> list = new ArrayList<>();')
    
    for card in cards:
        key = card["key"]
        display = card.get("displayName") or key.replace("-", " ").title()
        desc = card.get("description") or ""
        cmd = card.get("cmd") or key
        drawable = card.get("drawable") or ""
        
        # Escape special chars for Java
        display_j = display.replace('"', '\\"').replace("'", "\\'")
        desc_j = desc.replace('"', '\\"').replace("'", "\\'")
        cmd_j = cmd.replace('"', '\\"')
        
        lines.append(f'        list.add(makeItem("{key}", "{display_j}", "{desc_j}", "{cmd_j}", "{drawable}"));')
    
    lines.append('        return list;')
    lines.append('    }')
    lines.append('')
    lines.append('    private ToolItem makeItem(String key, String displayName, String description, String cmd, String drawableName) {')
    lines.append('        ToolItem item = new ToolItem();')
    lines.append('        item.key = key;')
    lines.append('        item.displayName = displayName;')
    lines.append('        item.description = description;')
    lines.append('        item.cmd = cmd;')
    lines.append('        if (drawableName == null || drawableName.isEmpty()) {')
    lines.append('            item.iconResId = R.drawable.andraxtool;')
    lines.append('        } else {')
    lines.append('            int id = getResources().getIdentifier(drawableName, "drawable", getPackageName());')
    lines.append('            item.iconResId = id != 0 ? id : R.drawable.andraxtool;')
    lines.append('        }')
    lines.append('        return item;')
    lines.append('    }')
    lines.append('')
    lines.append('    @Override')
    lines.append('    public void onPause() {')
    lines.append('        super.onPause();')
    lines.append('        Log.d(TAG, "onPause");')
    lines.append('        finish();')
    lines.append('    }')
    lines.append('}')
    
    with open(java_file, "w") as f:
        f.write("\n".join(lines))
    
    print(f"Generated {class_name}.java ({len(cards)} tools)")

print("Done!")
