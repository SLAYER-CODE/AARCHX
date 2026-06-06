package org.aarchdroid;

import android.os.Bundle;
import android.view.View;
import androidx.cardview.widget.CardView;
import android.util.Log;

/* JADX INFO: loaded from: classes2.dex */
public class Dco_website_hacking extends DcoBaseActivity {
    private static final String TAG = "Dco_website_hacking";
    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        try {
        requestWindowFeature(1);
        super.onCreate(bundle);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.dco_website_hacking);
        getWindow().setFlags(1024, 1024);
        CardView cardView = (CardView) findViewById(R.id.card_view_odin);
        CardView cardView2 = (CardView) findViewById(R.id.card_view_dotdotpwn);
        CardView cardView3 = (CardView) findViewById(R.id.card_view_nodexp);
        CardView cardView4 = (CardView) findViewById(R.id.card_view_xxeinjector);
        CardView cardView5 = (CardView) findViewById(R.id.card_view_xxexploiter);
        CardView cardView6 = (CardView) findViewById(R.id.card_view_xxetimes);
        CardView cardView7 = (CardView) findViewById(R.id.card_view_mitmproxy);
        CardView cardView8 = (CardView) findViewById(R.id.card_view_zap);
        CardView cardView9 = (CardView) findViewById(R.id.card_view_phpsploit);
        CardView cardView10 = (CardView) findViewById(R.id.card_view_xsstrike);
        CardView cardView11 = (CardView) findViewById(R.id.card_view_commix);
        CardView cardView12 = (CardView) findViewById(R.id.card_view_sqlmap);
        CardView cardView13 = (CardView) findViewById(R.id.card_view_payloadmask);
        CardView cardView14 = (CardView) findViewById(R.id.card_view_arjun);
        CardView cardView15 = (CardView) findViewById(R.id.card_view_whatweb);
        CardView cardView16 = (CardView) findViewById(R.id.card_view_wafw00f);
        CardView cardView17 = (CardView) findViewById(R.id.card_view_jaeles);
        CardView cardView18 = (CardView) findViewById(R.id.card_view_nuclei);
        CardView cardView19 = (CardView) findViewById(R.id.card_view_httpx);
        CardView cardView20 = (CardView) findViewById(R.id.card_view_wpscan);
        CardView cardView21 = (CardView) findViewById(R.id.card_view_cmseek);
        CardView cardView22 = (CardView) findViewById(R.id.card_view_aron);
        CardView cardView23 = (CardView) findViewById(R.id.card_view_jwtcrack);
        CardView cardView24 = (CardView) findViewById(R.id.card_view_jwt_tool);
        CardView cardView25 = (CardView) findViewById(R.id.card_view_wfuzz);
        CardView cardView26 = (CardView) findViewById(R.id.card_view_monsoon);
        CardView cardView27 = (CardView) findViewById(R.id.card_view_cadaver);
        CardView cardView28 = (CardView) findViewById(R.id.card_view_xspear);
        CardView cardView29 = (CardView) findViewById(R.id.card_view_imagejs);
        CardView cardView30 = (CardView) findViewById(R.id.card_view_findalllinks);
        CardView cardView31 = (CardView) findViewById(R.id.card_view_jsalert);
        CardView cardView32 = (CardView) findViewById(R.id.card_view_wbk);
        CardView cardView33 = (CardView) findViewById(R.id.card_view_cansina);
        CardView cardView34 = (CardView) findViewById(R.id.card_view_crlfuzz);
        CardView cardView35 = (CardView) findViewById(R.id.card_view_davtest);
        CardView cardView36 = (CardView) findViewById(R.id.card_view_ffuf);
        CardView cardView37 = (CardView) findViewById(R.id.card_view_nomore403);
        CardView cardView38 = (CardView) findViewById(R.id.card_view_kadimus);
        CardView cardView39 = (CardView) findViewById(R.id.card_view_gobuster);
        CardView cardView40 = (CardView) findViewById(R.id.card_view_lulzbuster);
        CardView cardView41 = (CardView) findViewById(R.id.card_view_s3scanner);
        CardView cardView42 = (CardView) findViewById(R.id.card_view_htshells);
        CardView cardView43 = (CardView) findViewById(R.id.card_view_katana);
        CardView cardView44 = (CardView) findViewById(R.id.card_view_forbidden);
        CardView cardView45 = (CardView) findViewById(R.id.card_view_smuggler);
        cardView.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.1
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("0d1n");
            }
        });
        cardView2.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.2
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("dotdotpwn");
            }
        });
        cardView7.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.3
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("mitmproxy");
            }
        });
        cardView8.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.4
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("zap");
            }
        });
        cardView9.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.5
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("phpsploit");
            }
        });
        cardView10.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.6
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("xsstrike");
            }
        });
        cardView11.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.7
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("commix");
            }
        });
        cardView12.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.8
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("sqlmap");
            }
        });
        cardView13.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.9
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("payloadmask");
            }
        });
        cardView14.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.10
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("arjun -h");
            }
        });
        cardView15.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.11
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("whatweb -h");
            }
        });
        cardView16.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.12
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("wafw00f -h");
            }
        });
        cardView17.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.13
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("jaeles");
            }
        });
        cardView18.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.14
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("nuclei");
            }
        });
        cardView19.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.15
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("httpx");
            }
        });
        cardView20.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.16
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("wpscan -h");
            }
        });
        cardView21.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.17
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("sudo cmseek");
            }
        });
        cardView22.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.18
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("aron -h");
            }
        });
        cardView23.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.19
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("jwtcrack");
            }
        });
        cardView24.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.20
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("jwt_tool -h");
            }
        });
        cardView3.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.21
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("nodexp --help");
            }
        });
        cardView4.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.22
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("xxeinjector");
            }
        });
        cardView5.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.23
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("xxexploiter");
            }
        });
        cardView6.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.24
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("xxetimes -h");
            }
        });
        cardView25.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.25
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("wfuzz --help");
            }
        });
        cardView26.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.26
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("monsoon --help");
            }
        });
        cardView27.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.27
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("cadaver");
            }
        });
        cardView28.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.28
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("XSpear -h");
            }
        });
        cardView29.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.29
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("imagejs");
            }
        });
        cardView30.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.30
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("find-all-links");
            }
        });
        cardView31.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.31
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("jsalert");
            }
        });
        cardView32.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.32
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("wbk -h");
            }
        });
        cardView33.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.33
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("cansina -h");
            }
        });
        cardView34.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.34
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("crlfuzz -h");
            }
        });
        cardView35.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.35
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("davtest");
            }
        });
        cardView36.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.36
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("ffuf -h");
            }
        });
        cardView37.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.37
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("nomore403");
            }
        });
        cardView38.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.38
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("kadimus");
            }
        });
        cardView39.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.39
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("gobuster");
            }
        });
        cardView40.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.40
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("lulzbuster -H");
            }
        });
        cardView41.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.41
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("s3scanner -h");
            }
        });
        cardView42.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.42
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("htshells");
            }
        });
        cardView43.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.43
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("katana");
            }
        });
        cardView44.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.44
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("forbidden");
            }
        });
        cardView45.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_website_hacking.45
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_website_hacking.this.run_hack_cmd("smuggler");
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
