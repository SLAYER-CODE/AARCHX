package org.aarchdroid;

import android.os.Bundle;
import android.view.View;
import androidx.cardview.widget.CardView;
import android.util.Log;

/* JADX INFO: loaded from: classes2.dex */
public class Dco_phreaking extends DcoBaseActivity {
    private static final String TAG = "Dco_phreaking";
    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        try {
        requestWindowFeature(1);
        super.onCreate(bundle);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.dco_phreaking);
        getWindow().setFlags(1024, 1024);
        CardView cardView = (CardView) findViewById(R.id.card_view_sctpscan);
        CardView cardView2 = (CardView) findViewById(R.id.card_view_sipsak);
        CardView cardView3 = (CardView) findViewById(R.id.card_view_enodebhack);
        CardView cardView4 = (CardView) findViewById(R.id.card_view_mme);
        CardView cardView5 = (CardView) findViewById(R.id.card_view_pgwhack);
        CardView cardView6 = (CardView) findViewById(R.id.card_view_diameterenum);
        CardView cardView7 = (CardView) findViewById(R.id.card_view_s1apenum);
        CardView cardView8 = (CardView) findViewById(R.id.card_view_gtpscan);
        CardView cardView9 = (CardView) findViewById(R.id.card_view_sgwhack);
        CardView cardView10 = (CardView) findViewById(R.id.card_view_cryptomobile);
        CardView cardView11 = (CardView) findViewById(R.id.card_view_enumiax);
        CardView cardView12 = (CardView) findViewById(R.id.card_view_svmap);
        CardView cardView13 = (CardView) findViewById(R.id.card_view_svwar);
        CardView cardView14 = (CardView) findViewById(R.id.card_view_svcrack);
        CardView cardView15 = (CardView) findViewById(R.id.card_view_isip);
        CardView cardView16 = (CardView) findViewById(R.id.card_view_vsaudit);
        CardView cardView17 = (CardView) findViewById(R.id.card_view_iaxflood);
        CardView cardView18 = (CardView) findViewById(R.id.card_view_inviteflood);
        CardView cardView19 = (CardView) findViewById(R.id.card_view_rtpflood);
        CardView cardView20 = (CardView) findViewById(R.id.card_view_udpfloodVLAN);
        CardView cardView21 = (CardView) findViewById(R.id.card_view_rtpbreak);
        CardView cardView22 = (CardView) findViewById(R.id.card_view_rtpinsertsound);
        CardView cardView23 = (CardView) findViewById(R.id.card_view_sippts);
        CardView cardView24 = (CardView) findViewById(R.id.card_view_voiphopper);
        cardView.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phreaking.1
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phreaking.this.run_hack_cmd("sctpscan");
            }
        });
        cardView3.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phreaking.2
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phreaking.this.run_hack_cmd("eNodeB");
            }
        });
        cardView4.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phreaking.3
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phreaking.this.run_hack_cmd("mme");
            }
        });
        cardView5.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phreaking.4
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phreaking.this.run_hack_cmd("pgw");
            }
        });
        cardView6.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phreaking.5
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phreaking.this.run_hack_cmd("diameter_enum -h");
            }
        });
        cardView7.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phreaking.6
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phreaking.this.run_hack_cmd("s1ap_enum");
            }
        });
        cardView8.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phreaking.7
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phreaking.this.run_hack_cmd("gtp_scan -h");
            }
        });
        cardView10.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phreaking.8
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phreaking.this.run_hack_cmd("cryptomobile");
            }
        });
        cardView9.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phreaking.9
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phreaking.this.run_hack_cmd("sgw");
            }
        });
        cardView11.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phreaking.10
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phreaking.this.run_hack_cmd("enumiax");
            }
        });
        cardView12.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phreaking.11
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phreaking.this.run_hack_cmd("sipvicious_svmap");
            }
        });
        cardView13.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phreaking.12
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phreaking.this.run_hack_cmd("sipvicious_svwar");
            }
        });
        cardView14.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phreaking.13
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phreaking.this.run_hack_cmd("sipvicious_svcrack");
            }
        });
        cardView15.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phreaking.14
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phreaking.this.run_hack_cmd("sudo isip");
            }
        });
        cardView2.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phreaking.15
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phreaking.this.run_hack_cmd("sipsak");
            }
        });
        cardView16.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phreaking.16
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phreaking.this.run_hack_cmd("vsaudit");
            }
        });
        cardView17.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phreaking.17
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phreaking.this.run_hack_cmd("iaxflood");
            }
        });
        cardView18.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phreaking.18
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phreaking.this.run_hack_cmd("inviteflood");
            }
        });
        cardView19.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phreaking.19
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phreaking.this.run_hack_cmd("rtpflood");
            }
        });
        cardView20.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phreaking.20
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phreaking.this.run_hack_cmd("udpfloodVLAN");
            }
        });
        cardView21.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phreaking.21
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phreaking.this.run_hack_cmd("rtpbreak");
            }
        });
        cardView22.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phreaking.22
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phreaking.this.run_hack_cmd("rtpinsertsound");
            }
        });
        cardView23.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phreaking.23
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phreaking.this.run_hack_cmd("sippts");
            }
        });
        cardView24.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phreaking.24
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phreaking.this.run_hack_cmd("voiphopper -h");
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
