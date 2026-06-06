package org.aarchdroid;

import android.os.Bundle;
import android.view.View;
import androidx.cardview.widget.CardView;
import android.util.Log;

/* JADX INFO: loaded from: classes2.dex */
public class Dco_Password_Hacking extends DcoBaseActivity {
    private static final String TAG = "Dco_Password_Hacking";
    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        try {
        requestWindowFeature(1);
        super.onCreate(bundle);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.dco_password_hacking);
        getWindow().setFlags(1024, 1024);
        CardView cardView = (CardView) findViewById(R.id.card_view_hydra);
        CardView cardView2 = (CardView) findViewById(R.id.card_view_ncrack);
        CardView cardView3 = (CardView) findViewById(R.id.card_view_john);
        CardView cardView4 = (CardView) findViewById(R.id.card_view_hashcat);
        CardView cardView5 = (CardView) findViewById(R.id.card_view_hashboy);
        CardView cardView6 = (CardView) findViewById(R.id.card_view_crunch);
        CardView cardView7 = (CardView) findViewById(R.id.card_view_maskprocessor);
        CardView cardView8 = (CardView) findViewById(R.id.card_view_cewl);
        CardView cardView9 = (CardView) findViewById(R.id.card_view_ssh_auditor);
        CardView cardView10 = (CardView) findViewById(R.id.card_view_bopscrk);
        CardView cardView11 = (CardView) findViewById(R.id.card_view_pskcrack);
        CardView cardView12 = (CardView) findViewById(R.id.card_view_bgpmd5crack);
        CardView cardView13 = (CardView) findViewById(R.id.card_view_narthex);
        CardView cardView14 = (CardView) findViewById(R.id.card_view_medusa);
        CardView cardView15 = (CardView) findViewById(R.id.card_view_mfoc);
        CardView cardView16 = (CardView) findViewById(R.id.card_view_pipal);
        CardView cardView17 = (CardView) findViewById(R.id.card_view_eapmd5pass);
        CardView cardView18 = (CardView) findViewById(R.id.card_view_patator);
        cardView12.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Password_Hacking.1
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Password_Hacking.this.run_hack_cmd("bgp_md5crack -h");
            }
        });
        cardView.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Password_Hacking.2
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Password_Hacking.this.run_hack_cmd("hydra");
            }
        });
        cardView2.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Password_Hacking.3
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Password_Hacking.this.run_hack_cmd("ncrack");
            }
        });
        cardView3.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Password_Hacking.4
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Password_Hacking.this.run_hack_cmd("john");
            }
        });
        cardView4.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Password_Hacking.5
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Password_Hacking.this.run_hack_cmd("hashcat -h");
            }
        });
        cardView5.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Password_Hacking.6
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Password_Hacking.this.run_hack_cmd("hashboy");
            }
        });
        cardView6.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Password_Hacking.7
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Password_Hacking.this.run_hack_cmd("crunch");
            }
        });
        cardView7.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Password_Hacking.8
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Password_Hacking.this.run_hack_cmd("maskprocessor --help");
            }
        });
        cardView8.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Password_Hacking.9
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Password_Hacking.this.run_hack_cmd("cewl -h");
            }
        });
        cardView9.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Password_Hacking.10
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Password_Hacking.this.run_hack_cmd("ssh-auditor");
            }
        });
        cardView10.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Password_Hacking.11
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Password_Hacking.this.run_hack_cmd("bopscrk");
            }
        });
        cardView11.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Password_Hacking.12
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Password_Hacking.this.run_hack_cmd("psk-crack");
            }
        });
        cardView13.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Password_Hacking.13
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Password_Hacking.this.run_hack_cmd("nwiz");
            }
        });
        cardView14.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Password_Hacking.14
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Password_Hacking.this.run_hack_cmd("medusa");
            }
        });
        cardView15.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Password_Hacking.15
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Password_Hacking.this.run_hack_cmd("mfoc -h");
            }
        });
        cardView16.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Password_Hacking.16
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Password_Hacking.this.run_hack_cmd("pipal -h");
            }
        });
        cardView17.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Password_Hacking.17
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Password_Hacking.this.run_hack_cmd("eapmd5pass");
            }
        });
        cardView18.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Password_Hacking.18
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Password_Hacking.this.run_hack_cmd("patator");
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
