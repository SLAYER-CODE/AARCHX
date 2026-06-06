package org.aarchdroid;

import android.os.Bundle;
import android.view.View;
import androidx.cardview.widget.CardView;
import android.util.Log;

/* JADX INFO: loaded from: classes2.dex */
public class Dco_Wireless_Hacking extends DcoBaseActivity {
    private static final String TAG = "Dco_Wireless_Hacking";
    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        try {
        requestWindowFeature(1);
        super.onCreate(bundle);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.dco_wireless_hacking);
        getWindow().setFlags(1024, 1024);
        CardView cardView = (CardView) findViewById(R.id.card_view_aircrack);
        CardView cardView2 = (CardView) findViewById(R.id.card_view_cowpatty);
        CardView cardView3 = (CardView) findViewById(R.id.card_view_mdk4);
        CardView cardView4 = (CardView) findViewById(R.id.card_view_bully);
        CardView cardView5 = (CardView) findViewById(R.id.card_view_reaver);
        CardView cardView6 = (CardView) findViewById(R.id.card_view_wash);
        CardView cardView7 = (CardView) findViewById(R.id.card_view_blueranger);
        CardView cardView8 = (CardView) findViewById(R.id.card_view_bluesnarfer);
        CardView cardView9 = (CardView) findViewById(R.id.card_view_crackle);
        CardView cardView10 = (CardView) findViewById(R.id.card_view_blescan);
        CardView cardView11 = (CardView) findViewById(R.id.card_view_btlejack);
        CardView cardView12 = (CardView) findViewById(R.id.card_view_btscanner);
        CardView cardView13 = (CardView) findViewById(R.id.card_view_spooftooph);
        CardView cardView14 = (CardView) findViewById(R.id.card_view_hcxdumptool);
        CardView cardView15 = (CardView) findViewById(R.id.card_view_eapmd5pass);
        CardView cardView16 = (CardView) findViewById(R.id.card_view_pixiewps);
        CardView cardView17 = (CardView) findViewById(R.id.card_view_mfterm);
        CardView cardView18 = (CardView) findViewById(R.id.card_view_wacker);
        cardView.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Wireless_Hacking.1
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Wireless_Hacking.this.run_hack_cmd("aircrack-ng");
            }
        });
        cardView2.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Wireless_Hacking.2
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Wireless_Hacking.this.run_hack_cmd("cowpatty");
            }
        });
        cardView3.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Wireless_Hacking.3
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Wireless_Hacking.this.run_hack_cmd("mdk4");
            }
        });
        cardView4.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Wireless_Hacking.4
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Wireless_Hacking.this.run_hack_cmd("bully");
            }
        });
        cardView5.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Wireless_Hacking.5
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Wireless_Hacking.this.run_hack_cmd("reaver");
            }
        });
        cardView6.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Wireless_Hacking.6
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Wireless_Hacking.this.run_hack_cmd("wash");
            }
        });
        cardView7.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Wireless_Hacking.7
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Wireless_Hacking.this.run_hack_cmd("blueranger");
            }
        });
        cardView8.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Wireless_Hacking.8
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Wireless_Hacking.this.run_hack_cmd("bluesnarfer");
            }
        });
        cardView9.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Wireless_Hacking.9
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Wireless_Hacking.this.run_hack_cmd("crackle");
            }
        });
        cardView10.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Wireless_Hacking.10
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Wireless_Hacking.this.run_hack_cmd("blescan -h");
            }
        });
        cardView11.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Wireless_Hacking.11
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Wireless_Hacking.this.run_hack_cmd("btlejack");
            }
        });
        cardView12.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Wireless_Hacking.12
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Wireless_Hacking.this.run_hack_cmd("btscanner -h");
            }
        });
        cardView13.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Wireless_Hacking.13
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Wireless_Hacking.this.run_hack_cmd("spooftooph");
            }
        });
        cardView14.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Wireless_Hacking.14
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Wireless_Hacking.this.run_hack_cmd("hcxdumptool");
            }
        });
        cardView15.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Wireless_Hacking.15
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Wireless_Hacking.this.run_hack_cmd("eapmd5pass");
            }
        });
        cardView16.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Wireless_Hacking.16
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Wireless_Hacking.this.run_hack_cmd("pixiewps");
            }
        });
        cardView17.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Wireless_Hacking.17
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Wireless_Hacking.this.run_hack_cmd("mfterm -h");
            }
        });
        cardView18.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Wireless_Hacking.18
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Wireless_Hacking.this.run_hack_cmd("wacker -h");
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
