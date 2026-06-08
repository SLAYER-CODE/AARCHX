package org.aarchdroid;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class Dco_network_hacking extends DcoBaseActivity {
    private static final String TAG = "Dco_network_hacking";

    @Override
    protected void onCreate(Bundle bundle) {
        try {
            requestWindowFeature(1);
            super.onCreate(bundle);
            Log.d(TAG, "onCreate");
            setContentView(R.layout.dco_list_scaffold);
            getWindow().setFlags(1024, 1024);

            TextView titleView = findViewById(R.id.title);
            titleView.setText("Network Hacking");
            ((android.widget.ImageView) findViewById(R.id.banner)).setImageResource(R.drawable.networkhacking);
            ((TextView) findViewById(R.id.stats_tools)).setText("47");

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
        list.add(makeItem("ipcalc", "IPCalc", "IPv4/IPv6 calculation", "ipcalc", "ipcalc"));
        list.add(makeItem("netmask", "Netmask", "Netmask helping tool", "netmask --help", "netmask"));
        list.add(makeItem("tshark", "Tshark", "Network protocol analyzer", "tshark --help", "wireshark"));
        list.add(makeItem("dhcping", "DHCPing", "DHCP test and autiting tool", "sudo dhcping", "andraxtool"));
        list.add(makeItem("dnsdict6", "DNSDict6", "Parallized DNS IPv6 dictionary bruteforcer", "dnsdict6", "andraxtool"));
        list.add(makeItem("netdiscover", "Netdiscover", "Active/Passive ARP Reconnaissance", "sudo netdiscover", "netdiscover"));
        list.add(makeItem("implementation6", "Implementation6", "Perform some IPv6 checks", "implementation6", "andraxtool"));
        list.add(makeItem("cdpsnarf", "CDPSnarf", "Cisco Discovery Protocol Sniffer", "cdpsnarf", "cdpsnarf"));
        list.add(makeItem("ipdecap", "IPDecap", "Decapsulate GRE, IPIP, 6in4 and ESP traffic", "ipdecap", "andraxtool"));
        list.add(makeItem("fragmentation6", "Fragmentation6", "Perform fragment firewall tests", "fragmentation6", "andraxtool"));
        list.add(makeItem("parasite6", "Parasite6", "&quot;ARP spoofer&quot; for IPv6", "parasite6", "andraxtool"));
        list.add(makeItem("inverselookup6", "Inverse_lookup6", "Inverse address query", "inverse_lookup6", "andraxtool"));
        list.add(makeItem("mitm6", "Mitm6", "Pwning IPv4 via IPv6", "mitm6 --help", "mitm6"));
        list.add(makeItem("dnschef", "DNSChef", "DNS proxy for Penetration Test", "dnschef -h", "dnschef"));
        list.add(makeItem("fakesolicitate6", "Fake_solicitate6", "Solicitate IPv6 on the network", "fake_solicitate6", "andraxtool"));
        list.add(makeItem("detctsniffer6", "Detect_sniffer6", "Check LAN sniffers", "detect_sniffer6", "andraxtool"));
        list.add(makeItem("fakeadvertise6", "Fake_advertise6", "Advertise IPv6 on the network", "fake_advertise6", "andraxtool"));
        list.add(makeItem("rsmurf6", "Rsmurf6", "Smurfs the vctim&apos;s local network", "rsmurf6", "andraxtool"));
        list.add(makeItem("smurf6", "Smurf6", "Smurf the target with ICMPv6", "smurf6", "andraxtool"));
        list.add(makeItem("fakedhcps6", "Fake_dhcps6", "Fake DHCPv6 Server", "fake_dhcps6", "andraxtool"));
        list.add(makeItem("fakedns6d", "Fake_dns6d", "Fake DNS IPv6 server", "fake_dns6d", "andraxtool"));
        list.add(makeItem("fakednsupdate6", "Fake_dnsupdate6", "Fake DNS update requests", "fake_dnsupdate6", "andraxtool"));
        list.add(makeItem("fakemld6", "Fake_mld6", "Announce yourself in a multicast using MLD", "fake_mld6", "andraxtool"));
        list.add(makeItem("fakemld26", "Fake_mld26", "Announce yourself in a multicast using MLDv2", "fake_mld26", "andraxtool"));
        list.add(makeItem("fakemldrouter6", "Fake_mldrouter6", "Announce, delete or soliciated MLD route", "fake_mldrouter6", "andraxtool"));
        list.add(makeItem("fakerouter6", "Fake_router6", "Try create a fake IPv6 router", "fake_router6", "andraxtool"));
        list.add(makeItem("fakerouter26", "Fake_router26", "Try create a fake IPv6 router 2", "fake_router26", "andraxtool"));
        list.add(makeItem("redir6", "Redir6", "Implante a route into victim-ip", "redir6", "andraxtool"));
        list.add(makeItem("killrouter6", "Kill_router6", "Down router on routeing table", "kill_router6", "andraxtool"));
        list.add(makeItem("delorean", "Delorean", "NTP server written in python", "sudo delorean -h", "delorean"));
        list.add(makeItem("responder", "Responder", "NBT-NS, LLMNR and MDNS Responder", "sudo responder -h", "responder"));
        list.add(makeItem("ncat", "Ncat", "Feature-packed networking utility ", "ncat -h", "nmap"));
        list.add(makeItem("bettercap", "Bettercap", "Swiss Army knife for networks recon and attacks", "sudo bettercap", "bettercap"));
        list.add(makeItem("yersinia", "Yersinia", "A framework for layer 2 attacks", "sudo yersinia -h", "yersinia"));
        list.add(makeItem("miranda", "Miranda", "Interactive UPnP Client", "sudo miranda", "miranda"));
        list.add(makeItem("upnptools", "UPnP-Tools", "UPnP Utilities", "upnp_tools", "andraxtool"));
        list.add(makeItem("bgpcli", "BGP-Cli", "Border Gateway Protocol CLI", "bgp_cli -h", "andraxtool"));
        list.add(makeItem("eigrpcli", "EIGRP-Cli", "EIGRP Client", "eigrp_cli", "andraxtool"));
        list.add(makeItem("ldpcli", "LDP_Cli", "Label Distribution Protocol CLI", "ldp_cli", "andraxtool"));
        list.add(makeItem("sdnpwn", "SDNPwn", "SDN Penetration Testing Toolkit", "sdnpwn", "sdnpwn"));
        list.add(makeItem("mplstun", "MPLS_Tun", "MPLS L2 and L3 tunnel", "mpls_tun -h", "andraxtool"));
        list.add(makeItem("mplsredirect", "MPLS_Redirect", "On-the-fly MPLS Redirector", "mpls_redirect -h", "andraxtool"));
        list.add(makeItem("fiked", "Fiked", "FakeIKEd, fake IKE daemon", "fiked", "andraxtool"));
        list.add(makeItem("socat", "Socat", "Relay for bidirectional data transfer", "socat -h", "andraxtool"));
        list.add(makeItem("dns2tcp", "DNS2TCP", "Designed to relay TCP connections through DNS", "dns2tcpc", "dns2tcp"));
        list.add(makeItem("chisel", "Chisel", "A fast TCP tunnel over HTTP", "chisel", "chisel"));
        list.add(makeItem("ligolong", "Ligolo-NG", "Tunneling like a VPN", "ligolo-ng", "ligolong"));
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