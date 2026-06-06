package org.aarchdroid;

import android.os.Bundle;
import android.view.View;
import androidx.cardview.widget.CardView;
import android.util.Log;

/* JADX INFO: loaded from: classes2.dex */
public class Dco_stress_testing extends DcoBaseActivity {
    private static final String TAG = "Dco_stress_testing";
    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        try {
        requestWindowFeature(1);
        super.onCreate(bundle);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.dco_stress_testing);
        getWindow().setFlags(1024, 1024);
        CardView cardView = (CardView) findViewById(R.id.card_view_fuzzip6);
        CardView cardView2 = (CardView) findViewById(R.id.card_view_denial6);
        CardView cardView3 = (CardView) findViewById(R.id.card_view_flooddhcpc6);
        CardView cardView4 = (CardView) findViewById(R.id.card_view_floodadvertise6);
        CardView cardView5 = (CardView) findViewById(R.id.card_view_floodmld6);
        CardView cardView6 = (CardView) findViewById(R.id.card_view_floodmld26);
        CardView cardView7 = (CardView) findViewById(R.id.card_view_floodmldrouter6);
        CardView cardView8 = (CardView) findViewById(R.id.card_view_floodredir6);
        CardView cardView9 = (CardView) findViewById(R.id.card_view_floodrouter6);
        CardView cardView10 = (CardView) findViewById(R.id.card_view_floodrouter26);
        CardView cardView11 = (CardView) findViewById(R.id.card_view_floodrs6);
        CardView cardView12 = (CardView) findViewById(R.id.card_view_floodsolicitate6);
        CardView cardView13 = (CardView) findViewById(R.id.card_view_floodunreach6);
        CardView cardView14 = (CardView) findViewById(R.id.card_view_rsmurf6);
        CardView cardView15 = (CardView) findViewById(R.id.card_view_dosnewip6);
        CardView cardView16 = (CardView) findViewById(R.id.card_view_randicmp6);
        CardView cardView17 = (CardView) findViewById(R.id.card_view_slowhttptest);
        CardView cardView18 = (CardView) findViewById(R.id.card_view_dnsdrdos);
        cardView.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_stress_testing.1
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_stress_testing.this.run_hack_cmd("fuzz_ip6");
            }
        });
        cardView2.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_stress_testing.2
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_stress_testing.this.run_hack_cmd("denial6");
            }
        });
        cardView3.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_stress_testing.3
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_stress_testing.this.run_hack_cmd("flood_dhcpc6");
            }
        });
        cardView4.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_stress_testing.4
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_stress_testing.this.run_hack_cmd("flood_advertise6");
            }
        });
        cardView5.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_stress_testing.5
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_stress_testing.this.run_hack_cmd("flood_mld6");
            }
        });
        cardView6.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_stress_testing.6
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_stress_testing.this.run_hack_cmd("flood_mld26");
            }
        });
        cardView7.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_stress_testing.7
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_stress_testing.this.run_hack_cmd("flood_mldrouter6");
            }
        });
        cardView8.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_stress_testing.8
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_stress_testing.this.run_hack_cmd("flood_redir6");
            }
        });
        cardView9.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_stress_testing.9
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_stress_testing.this.run_hack_cmd("flood_router6");
            }
        });
        cardView10.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_stress_testing.10
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_stress_testing.this.run_hack_cmd("flood_router26");
            }
        });
        cardView11.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_stress_testing.11
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_stress_testing.this.run_hack_cmd("flood_rs6");
            }
        });
        cardView12.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_stress_testing.12
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_stress_testing.this.run_hack_cmd("flood_solicitate6");
            }
        });
        cardView13.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_stress_testing.13
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_stress_testing.this.run_hack_cmd("flood_unreach6");
            }
        });
        cardView14.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_stress_testing.14
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_stress_testing.this.run_hack_cmd("rsmurf6");
            }
        });
        cardView15.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_stress_testing.15
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_stress_testing.this.run_hack_cmd("dos-new-ip6");
            }
        });
        cardView16.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_stress_testing.16
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_stress_testing.this.run_hack_cmd("randicmp6");
            }
        });
        cardView17.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_stress_testing.17
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_stress_testing.this.run_hack_cmd("slowhttptest");
            }
        });
        cardView18.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_stress_testing.18
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_stress_testing.this.run_hack_cmd("dnsdrdos -H");
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
