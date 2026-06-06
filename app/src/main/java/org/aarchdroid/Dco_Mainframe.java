package org.aarchdroid;

import android.os.Bundle;
import android.view.View;
import androidx.cardview.widget.CardView;
import android.util.Log;

/* JADX INFO: loaded from: classes2.dex */
public class Dco_Mainframe extends DcoBaseActivity {
    private static final String TAG = "Dco_Mainframe";
    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        try {
        requestWindowFeature(1);
        super.onCreate(bundle);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.dco_mainframe);
        getWindow().setFlags(1024, 1024);
        CardView cardView = (CardView) findViewById(R.id.card_view_tpxbrute);
        CardView cardView2 = (CardView) findViewById(R.id.card_view_cicspwn);
        CardView cardView3 = (CardView) findViewById(R.id.card_view_cicsshot);
        CardView cardView4 = (CardView) findViewById(R.id.card_view_netEBCDICat);
        CardView cardView5 = (CardView) findViewById(R.id.card_view_TShOcker);
        CardView cardView6 = (CardView) findViewById(R.id.card_view_phatso);
        CardView cardView7 = (CardView) findViewById(R.id.card_view_mfsniffer);
        CardView cardView8 = (CardView) findViewById(R.id.card_view_psikotik);
        CardView cardView9 = (CardView) findViewById(R.id.card_view_birp);
        CardView cardView10 = (CardView) findViewById(R.id.card_view_maintp);
        CardView cardView11 = (CardView) findViewById(R.id.card_view_mainframe_bruter);
        CardView cardView12 = (CardView) findViewById(R.id.card_view_mfdos);
        CardView cardView13 = (CardView) findViewById(R.id.card_view_zosprivesc);
        cardView.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Mainframe.1
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Mainframe.this.run_hack_cmd("TPX_Brute -h");
            }
        });
        cardView2.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Mainframe.2
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Mainframe.this.run_hack_cmd("cicspwn -h");
            }
        });
        cardView3.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Mainframe.3
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Mainframe.this.run_hack_cmd("cicsshot -h");
            }
        });
        cardView4.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Mainframe.4
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Mainframe.this.run_hack_cmd("netEBCDICat -h");
            }
        });
        cardView5.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Mainframe.5
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Mainframe.this.run_hack_cmd("TShOcker -h");
            }
        });
        cardView6.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Mainframe.6
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Mainframe.this.run_hack_cmd("phatso -h");
            }
        });
        cardView7.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Mainframe.7
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Mainframe.this.run_hack_cmd("MFSniffer -h");
            }
        });
        cardView8.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Mainframe.8
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Mainframe.this.run_hack_cmd("psikotik -h");
            }
        });
        cardView9.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Mainframe.9
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Mainframe.this.run_hack_cmd("birp -h");
            }
        });
        cardView10.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Mainframe.10
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Mainframe.this.run_hack_cmd("MainTP -h");
            }
        });
        cardView11.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Mainframe.11
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Mainframe.this.run_hack_cmd("mainframe_bruter -h");
            }
        });
        cardView12.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Mainframe.12
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Mainframe.this.run_hack_cmd("MFDoS -h");
            }
        });
        cardView13.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Mainframe.13
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Mainframe.this.run_hack_cmd("zos-privesc");
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
