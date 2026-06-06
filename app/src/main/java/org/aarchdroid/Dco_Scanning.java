package org.aarchdroid;

import android.os.Bundle;
import android.view.View;
import androidx.cardview.widget.CardView;
import android.util.Log;

/* JADX INFO: loaded from: classes2.dex */
public class Dco_Scanning extends DcoBaseActivity {
    private static final String TAG = "Dco_Scanning";
    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        try {
        requestWindowFeature(1);
        super.onCreate(bundle);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.dco_scanning);
        getWindow().setFlags(1024, 1024);
        CardView cardView = (CardView) findViewById(R.id.card_view_nmap);
        CardView cardView2 = (CardView) findViewById(R.id.card_view_masscan);
        CardView cardView3 = (CardView) findViewById(R.id.card_view_ssh_auditor);
        CardView cardView4 = (CardView) findViewById(R.id.card_view_nbtscan);
        CardView cardView5 = (CardView) findViewById(R.id.card_view_ikescan);
        CardView cardView6 = (CardView) findViewById(R.id.card_view_sctpscan);
        cardView.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Scanning.1
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Scanning.this.run_hack_cmd("nmap");
            }
        });
        cardView2.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Scanning.2
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Scanning.this.run_hack_cmd("masscan --help");
            }
        });
        cardView3.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Scanning.3
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Scanning.this.run_hack_cmd("ssh-auditor");
            }
        });
        cardView4.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Scanning.4
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Scanning.this.run_hack_cmd("nbtscan");
            }
        });
        cardView5.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Scanning.5
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Scanning.this.run_hack_cmd("sudo ike-scan");
            }
        });
        cardView6.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_Scanning.6
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_Scanning.this.run_hack_cmd("sctpscan");
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
