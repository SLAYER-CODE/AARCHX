package org.aarchdroid;

import android.os.Bundle;
import android.view.View;
import androidx.cardview.widget.CardView;
import android.util.Log;

/* JADX INFO: loaded from: classes2.dex */
public class Dco_Information_Gathering extends DcoBaseActivity {
    private static final String TAG = "Dco_Information_Gathering";
    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        try {
        requestWindowFeature(1);
        super.onCreate(bundle);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.dco_information_gathering);
        getWindow().setFlags(1024, 1024);
        CardView cardView = (CardView) findViewById(R.id.card_view_spiderfoot);
        CardView cardView2 = (CardView) findViewById(R.id.card_view_whois);
        CardView cardView3 = (CardView) findViewById(R.id.card_view_bgpleak);
        CardView cardView4 = (CardView) findViewById(R.id.card_view_dig);
        CardView cardView5 = (CardView) findViewById(R.id.card_view_smtpuserenum);
        CardView cardView6 = (CardView) findViewById(R.id.card_view_ismtp);
        CardView cardView7 = (CardView) findViewById(R.id.card_view_braa);
        CardView cardView8 = (CardView) findViewById(R.id.card_view_intrace);
        CardView cardView9 = (CardView) findViewById(R.id.card_view_shuffledns);
        CardView cardView10 = (CardView) findViewById(R.id.card_view_massdns);
        CardView cardView11 = (CardView) findViewById(R.id.card_view_dnsmap);
        CardView cardView12 = (CardView) findViewById(R.id.card_view_amass);
        CardView cardView13 = (CardView) findViewById(R.id.card_view_onesixtyone);
        CardView cardView14 = (CardView) findViewById(R.id.card_view_trace6);
        CardView cardView15 = (CardView) findViewById(R.id.card_view_snmpwn);
        CardView cardView16 = (CardView) findViewById(R.id.card_view_dnsx);
        CardView cardView17 = (CardView) findViewById(R.id.card_view_subfinder);
        CardView cardView18 = (CardView) findViewById(R.id.card_view_swaks);
        CardView cardView19 = (CardView) findViewById(R.id.card_view_uncover);
        cardView.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Information_Gathering.1
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Information_Gathering.this.run_hack_cmd("sfcli");
            }
        });
        cardView2.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Information_Gathering.2
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Information_Gathering.this.run_hack_cmd("whois");
            }
        });
        cardView3.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Information_Gathering.3
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Information_Gathering.this.run_hack_cmd("bgp-leak -h");
            }
        });
        cardView4.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Information_Gathering.4
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Information_Gathering.this.run_hack_cmd("dig -h");
            }
        });
        cardView5.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Information_Gathering.5
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Information_Gathering.this.run_hack_cmd("smtp-user-enum");
            }
        });
        cardView6.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Information_Gathering.6
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Information_Gathering.this.run_hack_cmd("iSMTP");
            }
        });
        cardView13.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Information_Gathering.7
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Information_Gathering.this.run_hack_cmd("onesixtyone");
            }
        });
        cardView7.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Information_Gathering.8
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Information_Gathering.this.run_hack_cmd("braa");
            }
        });
        cardView8.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Information_Gathering.9
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Information_Gathering.this.run_hack_cmd("intrace");
            }
        });
        cardView11.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Information_Gathering.10
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Information_Gathering.this.run_hack_cmd("dnsmap");
            }
        });
        cardView12.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Information_Gathering.11
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Information_Gathering.this.run_hack_cmd("amass");
            }
        });
        cardView9.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Information_Gathering.12
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Information_Gathering.this.run_hack_cmd("shuffledns");
            }
        });
        cardView10.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Information_Gathering.13
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Information_Gathering.this.run_hack_cmd("massdns");
            }
        });
        cardView14.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Information_Gathering.14
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Information_Gathering.this.run_hack_cmd("trace6");
            }
        });
        cardView15.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Information_Gathering.15
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Information_Gathering.this.run_hack_cmd("snmpwn --help");
            }
        });
        cardView16.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Information_Gathering.16
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Information_Gathering.this.run_hack_cmd("dnsx");
            }
        });
        cardView17.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Information_Gathering.17
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Information_Gathering.this.run_hack_cmd("subfinder");
            }
        });
        cardView18.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Information_Gathering.18
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Information_Gathering.this.run_hack_cmd("swaks");
            }
        });
        cardView19.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Information_Gathering.19
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Information_Gathering.this.run_hack_cmd("uncover");
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
