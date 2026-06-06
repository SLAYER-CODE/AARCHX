package org.aarchdroid;

import android.os.Bundle;
import android.view.View;
import androidx.cardview.widget.CardView;
import android.util.Log;

/* JADX INFO: loaded from: classes2.dex */
public class Dco_network_hacking extends DcoBaseActivity {
    private static final String TAG = "Dco_network_hacking";
    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        try {
        requestWindowFeature(1);
        super.onCreate(bundle);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.dco_network_hacking);
        getWindow().setFlags(1024, 1024);
        CardView cardView = (CardView) findViewById(R.id.card_view_eigrpcli);
        CardView cardView2 = (CardView) findViewById(R.id.card_view_sdnpwn);
        CardView cardView3 = (CardView) findViewById(R.id.card_view_cdpsnarf);
        CardView cardView4 = (CardView) findViewById(R.id.card_view_bgpcli);
        CardView cardView5 = (CardView) findViewById(R.id.card_view_netmask);
        CardView cardView6 = (CardView) findViewById(R.id.card_view_responder);
        CardView cardView7 = (CardView) findViewById(R.id.card_view_bettercap);
        CardView cardView8 = (CardView) findViewById(R.id.card_view_mitm6);
        CardView cardView9 = (CardView) findViewById(R.id.card_view_socat);
        CardView cardView10 = (CardView) findViewById(R.id.card_view_chisel);
        CardView cardView11 = (CardView) findViewById(R.id.card_view_dns2tcp);
        CardView cardView12 = (CardView) findViewById(R.id.card_view_dnschef);
        CardView cardView13 = (CardView) findViewById(R.id.card_view_tshark);
        CardView cardView14 = (CardView) findViewById(R.id.card_view_yersinia);
        CardView cardView15 = (CardView) findViewById(R.id.card_view_miranda);
        CardView cardView16 = (CardView) findViewById(R.id.card_view_upnptools);
        CardView cardView17 = (CardView) findViewById(R.id.card_view_killrouter6);
        CardView cardView18 = (CardView) findViewById(R.id.card_view_detctsniffer6);
        CardView cardView19 = (CardView) findViewById(R.id.card_view_fakeadvertise6);
        CardView cardView20 = (CardView) findViewById(R.id.card_view_fakedhcps6);
        CardView cardView21 = (CardView) findViewById(R.id.card_view_fakedns6d);
        CardView cardView22 = (CardView) findViewById(R.id.card_view_fakednsupdate6);
        CardView cardView23 = (CardView) findViewById(R.id.card_view_fakemld26);
        CardView cardView24 = (CardView) findViewById(R.id.card_view_fakemld6);
        CardView cardView25 = (CardView) findViewById(R.id.card_view_fakemldrouter6);
        CardView cardView26 = (CardView) findViewById(R.id.card_view_fakerouter26);
        CardView cardView27 = (CardView) findViewById(R.id.card_view_fakerouter6);
        CardView cardView28 = (CardView) findViewById(R.id.card_view_fakesolicitate6);
        CardView cardView29 = (CardView) findViewById(R.id.card_view_implementation6);
        CardView cardView30 = (CardView) findViewById(R.id.card_view_parasite6);
        CardView cardView31 = (CardView) findViewById(R.id.card_view_redir6);
        CardView cardView32 = (CardView) findViewById(R.id.card_view_smurf6);
        CardView cardView33 = (CardView) findViewById(R.id.card_view_delorean);
        CardView cardView34 = (CardView) findViewById(R.id.card_view_fiked);
        CardView cardView35 = (CardView) findViewById(R.id.card_view_dhcping);
        CardView cardView36 = (CardView) findViewById(R.id.card_view_ipdecap);
        CardView cardView37 = (CardView) findViewById(R.id.card_view_ldpcli);
        CardView cardView38 = (CardView) findViewById(R.id.card_view_mplsredirect);
        CardView cardView39 = (CardView) findViewById(R.id.card_view_mplstun);
        CardView cardView40 = (CardView) findViewById(R.id.card_view_ligolong);
        CardView cardView41 = (CardView) findViewById(R.id.card_view_dnsdict6);
        CardView cardView42 = (CardView) findViewById(R.id.card_view_netdiscover);
        CardView cardView43 = (CardView) findViewById(R.id.card_view_fragmentation6);
        CardView cardView44 = (CardView) findViewById(R.id.card_view_inverselookup6);
        CardView cardView45 = (CardView) findViewById(R.id.card_view_rsmurf6);
        CardView cardView46 = (CardView) findViewById(R.id.card_view_ncat);
        CardView cardView47 = (CardView) findViewById(R.id.card_view_ipcalc);
        cardView5.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.1
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("netmask --help");
            }
        });
        cardView6.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.2
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("sudo responder -h");
            }
        });
        cardView7.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.3
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("sudo bettercap");
            }
        });
        cardView8.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.4
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("mitm6 --help");
            }
        });
        cardView9.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.5
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("socat -h");
            }
        });
        cardView10.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.6
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("chisel");
            }
        });
        cardView11.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.7
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("dns2tcpc");
            }
        });
        cardView12.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.8
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("dnschef -h");
            }
        });
        cardView13.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.9
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("tshark --help");
            }
        });
        cardView.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.10
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("eigrp_cli");
            }
        });
        cardView2.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.11
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("sdnpwn");
            }
        });
        cardView3.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.12
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("cdpsnarf");
            }
        });
        cardView4.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.13
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("bgp_cli -h");
            }
        });
        cardView14.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.14
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("sudo yersinia -h");
            }
        });
        cardView15.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.15
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("sudo miranda");
            }
        });
        cardView16.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.16
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("upnp_tools");
            }
        });
        cardView17.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.17
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("kill_router6");
            }
        });
        cardView18.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.18
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("detect_sniffer6");
            }
        });
        cardView19.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.19
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("fake_advertise6");
            }
        });
        cardView20.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.20
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("fake_dhcps6");
            }
        });
        cardView21.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.21
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("fake_dns6d");
            }
        });
        cardView22.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.22
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("fake_dnsupdate6");
            }
        });
        cardView23.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.23
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("fake_mld26");
            }
        });
        cardView24.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.24
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("fake_mld6");
            }
        });
        cardView25.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.25
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("fake_mldrouter6");
            }
        });
        cardView26.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.26
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("fake_router26");
            }
        });
        cardView27.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.27
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("fake_router6");
            }
        });
        cardView28.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.28
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("fake_solicitate6");
            }
        });
        cardView29.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.29
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("implementation6");
            }
        });
        cardView30.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.30
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("parasite6");
            }
        });
        cardView31.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.31
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("redir6");
            }
        });
        cardView32.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.32
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("smurf6");
            }
        });
        cardView33.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.33
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("sudo delorean -h");
            }
        });
        cardView34.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.34
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("fiked");
            }
        });
        cardView35.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.35
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("sudo dhcping");
            }
        });
        cardView36.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.36
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("ipdecap");
            }
        });
        cardView37.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.37
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("ldp_cli");
            }
        });
        cardView38.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.38
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("mpls_redirect -h");
            }
        });
        cardView39.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.39
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("mpls_tun -h");
            }
        });
        cardView40.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.40
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("ligolo-ng");
            }
        });
        cardView41.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.41
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("dnsdict6");
            }
        });
        cardView42.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.42
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("sudo netdiscover");
            }
        });
        cardView43.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.43
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("fragmentation6");
            }
        });
        cardView44.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.44
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("inverse_lookup6");
            }
        });
        cardView45.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.45
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("rsmurf6");
            }
        });
        cardView46.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.46
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("ncat -h");
            }
        });
        cardView47.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_network_hacking.47
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_network_hacking.this.run_hack_cmd("ipcalc");
            }
        });
        } catch (Exception e) {
            Log.e(TAG, "onCreate failed", e);
            finish();
        }
    }

    
@Override // android.app.Activity
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        finish();
    }
}
