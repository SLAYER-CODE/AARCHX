package org.aarchdroid;

import android.os.Bundle;
import android.view.View;
import androidx.cardview.widget.CardView;
import android.util.Log;

/* JADX INFO: loaded from: classes2.dex */
public class Dco_ics_scada_iot extends DcoBaseActivity {
    private static final String TAG = "Dco_ics_scada_iot";
    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        try {
        requestWindowFeature(1);
        super.onCreate(bundle);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.dco_ics_scada_iot);
        getWindow().setFlags(1024, 1024);
        CardView cardView = (CardView) findViewById(R.id.card_view_plcscan);
        CardView cardView2 = (CardView) findViewById(R.id.card_view_s7scan);
        CardView cardView3 = (CardView) findViewById(R.id.card_view_modscan);
        CardView cardView4 = (CardView) findViewById(R.id.card_view_mbtget);
        CardView cardView5 = (CardView) findViewById(R.id.card_view_smod);
        CardView cardView6 = (CardView) findViewById(R.id.card_view_onthefly);
        CardView cardView7 = (CardView) findViewById(R.id.card_view_homepwn);
        CardView cardView8 = (CardView) findViewById(R.id.card_view_expliot);
        CardView cardView9 = (CardView) findViewById(R.id.card_view_sixnettools);
        CardView cardView10 = (CardView) findViewById(R.id.card_view_scadatools);
        CardView cardView11 = (CardView) findViewById(R.id.card_view_termineter);
        cardView.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_ics_scada_iot.1
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_ics_scada_iot.this.run_hack_cmd("plcscan");
            }
        });
        cardView2.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_ics_scada_iot.2
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_ics_scada_iot.this.run_hack_cmd("s7scan");
            }
        });
        cardView3.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_ics_scada_iot.3
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_ics_scada_iot.this.run_hack_cmd("modscan");
            }
        });
        cardView4.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_ics_scada_iot.4
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_ics_scada_iot.this.run_hack_cmd("mbtget -h");
            }
        });
        cardView5.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_ics_scada_iot.5
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_ics_scada_iot.this.run_hack_cmd("sudo smod");
            }
        });
        cardView6.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_ics_scada_iot.6
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_ics_scada_iot.this.run_hack_cmd("on-the-fly");
            }
        });
        cardView7.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_ics_scada_iot.7
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_ics_scada_iot.this.run_hack_cmd("homePwn");
            }
        });
        cardView8.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_ics_scada_iot.8
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_ics_scada_iot.this.run_hack_cmd("expliot");
            }
        });
        cardView9.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_ics_scada_iot.9
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_ics_scada_iot.this.run_hack_cmd("SIXNET-tools");
            }
        });
        cardView10.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_ics_scada_iot.10
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_ics_scada_iot.this.run_hack_cmd("scada-tools");
            }
        });
        cardView11.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_ics_scada_iot.11
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_ics_scada_iot.this.run_hack_cmd("termineter");
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
