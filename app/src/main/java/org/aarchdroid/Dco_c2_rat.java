package org.aarchdroid;

import android.os.Bundle;
import android.view.View;
import androidx.cardview.widget.CardView;
import android.util.Log;

/* JADX INFO: loaded from: classes2.dex */
public class Dco_c2_rat extends DcoBaseActivity {
    private static final String TAG = "Dco_c2_rat";
    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        try {
        requestWindowFeature(1);
        super.onCreate(bundle);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.dco_c2_rat);
        getWindow().setFlags(1024, 1024);
        CardView cardView = (CardView) findViewById(R.id.card_view_merlin);
        CardView cardView2 = (CardView) findViewById(R.id.card_view_nimcrypt);
        CardView cardView3 = (CardView) findViewById(R.id.card_view_godoh);
        CardView cardView4 = (CardView) findViewById(R.id.card_view_phpsploit);
        CardView cardView5 = (CardView) findViewById(R.id.card_view_evilwinrm);
        CardView cardView6 = (CardView) findViewById(R.id.card_view_exe2hex);
        cardView.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_c2_rat.1
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_c2_rat.this.run_hack_cmd("merlin-c2");
            }
        });
        cardView2.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_c2_rat.2
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_c2_rat.this.run_hack_cmd("nimcrypt -h");
            }
        });
        cardView3.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_c2_rat.3
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_c2_rat.this.run_hack_cmd("godoh -h");
            }
        });
        cardView4.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_c2_rat.4
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_c2_rat.this.run_hack_cmd("phpsploit");
            }
        });
        cardView5.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_c2_rat.5
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_c2_rat.this.run_hack_cmd("evil-winrm");
            }
        });
        cardView6.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_c2_rat.6
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_c2_rat.this.run_hack_cmd("exe2hex");
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
