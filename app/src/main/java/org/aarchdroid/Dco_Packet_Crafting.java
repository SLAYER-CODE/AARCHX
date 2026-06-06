package org.aarchdroid;

import android.os.Bundle;
import android.view.View;
import androidx.cardview.widget.CardView;
import android.util.Log;

/* JADX INFO: loaded from: classes2.dex */
public class Dco_Packet_Crafting extends DcoBaseActivity {
    private static final String TAG = "Dco_Packet_Crafting";
    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        try {
        requestWindowFeature(1);
        super.onCreate(bundle);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.dco_packet_crafting);
        getWindow().setFlags(1024, 1024);
        CardView cardView = (CardView) findViewById(R.id.card_view_nping);
        CardView cardView2 = (CardView) findViewById(R.id.card_view_scapy);
        CardView cardView3 = (CardView) findViewById(R.id.card_view_hexinject);
        CardView cardView4 = (CardView) findViewById(R.id.card_view_nemesis);
        CardView cardView5 = (CardView) findViewById(R.id.card_view_arping);
        CardView cardView6 = (CardView) findViewById(R.id.card_view_thcping6);
        CardView cardView7 = (CardView) findViewById(R.id.card_view_bittwist);
        cardView6.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Packet_Crafting.1
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Packet_Crafting.this.run_hack_cmd("thcping6");
            }
        });
        cardView.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Packet_Crafting.2
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Packet_Crafting.this.run_hack_cmd("nping");
            }
        });
        cardView2.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Packet_Crafting.3
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Packet_Crafting.this.run_hack_cmd("sudo scapy");
            }
        });
        cardView3.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Packet_Crafting.4
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Packet_Crafting.this.run_hack_cmd("hexinject -h");
            }
        });
        cardView4.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Packet_Crafting.5
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Packet_Crafting.this.run_hack_cmd("nemesis");
            }
        });
        cardView5.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Packet_Crafting.6
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Packet_Crafting.this.run_hack_cmd("arping");
            }
        });
        cardView7.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Packet_Crafting.7
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Packet_Crafting.this.run_hack_cmd("bittwist -h");
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
