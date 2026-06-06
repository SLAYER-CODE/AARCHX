package org.aarchdroid;

import android.os.Bundle;
import android.view.View;
import androidx.cardview.widget.CardView;
import android.util.Log;

/* JADX INFO: loaded from: classes2.dex */
public class Dco_bug_bounty extends DcoBaseActivity {
    private static final String TAG = "Dco_bug_bounty";
    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        try {
        requestWindowFeature(1);
        super.onCreate(bundle);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.dco_bug_bounty);
        getWindow().setFlags(1024, 1024);
        CardView cardView = (CardView) findViewById(R.id.card_view_bgpleak);
        CardView cardView2 = (CardView) findViewById(R.id.card_view_amass);
        CardView cardView3 = (CardView) findViewById(R.id.card_view_subfinder);
        CardView cardView4 = (CardView) findViewById(R.id.card_view_uncover);
        CardView cardView5 = (CardView) findViewById(R.id.card_view_aron);
        CardView cardView6 = (CardView) findViewById(R.id.card_view_arjun);
        CardView cardView7 = (CardView) findViewById(R.id.card_view_whatweb);
        CardView cardView8 = (CardView) findViewById(R.id.card_view_katana);
        CardView cardView9 = (CardView) findViewById(R.id.card_view_nuclei);
        CardView cardView10 = (CardView) findViewById(R.id.card_view_httpx);
        CardView cardView11 = (CardView) findViewById(R.id.card_view_jaeles);
        CardView cardView12 = (CardView) findViewById(R.id.card_view_wbk);
        CardView cardView13 = (CardView) findViewById(R.id.card_view_findalllinks);
        CardView cardView14 = (CardView) findViewById(R.id.card_view_wfuzz);
        CardView cardView15 = (CardView) findViewById(R.id.card_view_commix);
        CardView cardView16 = (CardView) findViewById(R.id.card_view_sqlmap);
        CardView cardView17 = (CardView) findViewById(R.id.card_view_dotdotpwn);
        CardView cardView18 = (CardView) findViewById(R.id.card_view_nodexp);
        CardView cardView19 = (CardView) findViewById(R.id.card_view_jsalert);
        CardView cardView20 = (CardView) findViewById(R.id.card_view_xsstrike);
        CardView cardView21 = (CardView) findViewById(R.id.card_view_xspear);
        CardView cardView22 = (CardView) findViewById(R.id.card_view_payloadmask);
        CardView cardView23 = (CardView) findViewById(R.id.card_view_crlfuzz);
        CardView cardView24 = (CardView) findViewById(R.id.card_view_kadimus);
        CardView cardView25 = (CardView) findViewById(R.id.card_view_xxeinjector);
        CardView cardView26 = (CardView) findViewById(R.id.card_view_xxexploiter);
        CardView cardView27 = (CardView) findViewById(R.id.card_view_xxetimes);
        CardView cardView28 = (CardView) findViewById(R.id.card_view_phpsploit);
        CardView cardView29 = (CardView) findViewById(R.id.card_view_jwt_tool);
        CardView cardView30 = (CardView) findViewById(R.id.card_view_jwtcrack);
        CardView cardView31 = (CardView) findViewById(R.id.card_view_nomore403);
        CardView cardView32 = (CardView) findViewById(R.id.card_view_forbidden);
        CardView cardView33 = (CardView) findViewById(R.id.card_view_smuggler);
        cardView.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.1
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("bgp-leak -h");
            }
        });
        cardView2.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.2
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("amass");
            }
        });
        cardView3.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.3
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("subfinder");
            }
        });
        cardView4.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.4
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("uncover");
            }
        });
        cardView5.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.5
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("aron -h");
            }
        });
        cardView6.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.6
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("arjun -h");
            }
        });
        cardView7.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.7
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("whatweb -h");
            }
        });
        cardView8.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.8
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("katana");
            }
        });
        cardView9.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.9
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("nuclei");
            }
        });
        cardView10.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.10
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("httpx");
            }
        });
        cardView11.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.11
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("jaeles");
            }
        });
        cardView12.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.12
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("wbk -h");
            }
        });
        cardView13.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.13
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("find-all-links");
            }
        });
        cardView14.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.14
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("wfuzz --help");
            }
        });
        cardView15.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.15
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("commix");
            }
        });
        cardView16.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.16
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("sqlmap");
            }
        });
        cardView17.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.17
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("dotdotpwn");
            }
        });
        cardView18.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.18
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("nodexp --help");
            }
        });
        cardView19.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.19
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("jsalert");
            }
        });
        cardView20.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.20
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("xsstrike");
            }
        });
        cardView21.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.21
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("XSpear -h");
            }
        });
        cardView22.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.22
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("payloadmask");
            }
        });
        cardView23.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.23
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("crlfuzz -h");
            }
        });
        cardView24.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.24
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("kadimus");
            }
        });
        cardView25.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.25
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("xxeinjector");
            }
        });
        cardView26.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.26
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("xxexploiter");
            }
        });
        cardView27.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.27
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("xxetimes -h");
            }
        });
        cardView28.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.28
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("phpsploit");
            }
        });
        cardView29.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.29
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("jwt_tool -h");
            }
        });
        cardView30.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.30
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("jwtcrack");
            }
        });
        cardView31.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.31
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("nomore403");
            }
        });
        cardView32.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.32
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("forbidden");
            }
        });
        cardView33.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_bug_bounty.33
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_bug_bounty.this.run_hack_cmd("smuggler");
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
