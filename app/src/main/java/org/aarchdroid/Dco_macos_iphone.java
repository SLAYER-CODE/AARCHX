package org.aarchdroid;

import android.os.Bundle;
import android.view.View;
import androidx.cardview.widget.CardView;
import android.util.Log;

/* JADX INFO: loaded from: classes2.dex */
public class Dco_macos_iphone extends DcoBaseActivity {
    private static final String TAG = "Dco_macos_iphone";
    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        try {
        requestWindowFeature(1);
        super.onCreate(bundle);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.dco_macos_iphone);
        getWindow().setFlags(1024, 1024);
        CardView cardView = (CardView) findViewById(R.id.card_view_owl);
        CardView cardView2 = (CardView) findViewById(R.id.card_view_applebleee);
        CardView cardView3 = (CardView) findViewById(R.id.card_view_iblessing);
        CardView cardView4 = (CardView) findViewById(R.id.card_view_frida);
        CardView cardView5 = (CardView) findViewById(R.id.card_view_objection);
        CardView cardView6 = (CardView) findViewById(R.id.card_view_merlin);
        CardView cardView7 = (CardView) findViewById(R.id.card_view_godoh);
        cardView.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_macos_iphone.1
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_macos_iphone.this.run_hack_cmd("owl");
            }
        });
        cardView2.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_macos_iphone.2
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_macos_iphone.this.run_hack_cmd("apple-bleee");
            }
        });
        cardView3.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_macos_iphone.3
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_macos_iphone.this.run_hack_cmd("iblessing");
            }
        });
        cardView4.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_macos_iphone.4
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_macos_iphone.this.run_hack_cmd("frida");
            }
        });
        cardView5.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_macos_iphone.5
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_macos_iphone.this.run_hack_cmd("objection");
            }
        });
        cardView6.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_macos_iphone.6
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_macos_iphone.this.run_hack_cmd("merlin-c2");
            }
        });
        cardView7.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.Dco_macos_iphone.7
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Dco_macos_iphone.this.run_hack_cmd("godoh -h");
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
