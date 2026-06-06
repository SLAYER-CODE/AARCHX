package org.aarchdroid;

import android.os.Bundle;
import android.view.View;
import androidx.cardview.widget.CardView;
import android.util.Log;

/* JADX INFO: loaded from: classes2.dex */
public class Dco_phishing extends DcoBaseActivity {
    private static final String TAG = "Dco_phishing";
    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        try {
        requestWindowFeature(1);
        super.onCreate(bundle);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.dco_phishing);
        getWindow().setFlags(1024, 1024);
        CardView cardView = (CardView) findViewById(R.id.card_view_gophish);
        CardView cardView2 = (CardView) findViewById(R.id.card_view_evilginx2);
        CardView cardView3 = (CardView) findViewById(R.id.card_view_modlishka);
        CardView cardView4 = (CardView) findViewById(R.id.card_view_urlcrazy);
        CardView cardView5 = (CardView) findViewById(R.id.card_view_bitb);
        cardView.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phishing.1
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phishing.this.run_hack_cmd("sudo gophish");
            }
        });
        cardView2.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phishing.2
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phishing.this.run_hack_cmd("sudo evilginx");
            }
        });
        cardView3.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phishing.3
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phishing.this.run_hack_cmd("sudo modlishka");
            }
        });
        cardView4.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phishing.4
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phishing.this.run_hack_cmd("urlcrazy");
            }
        });
        cardView5.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_phishing.5
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_phishing.this.run_hack_cmd("bitb");
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
