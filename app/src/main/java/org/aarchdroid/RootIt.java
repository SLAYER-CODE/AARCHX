package org.aarchdroid;

import android.app.Activity;
import android.os.Bundle;

/* JADX INFO: loaded from: classes2.dex */
public class RootIt extends Activity {
    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        requestWindowFeature(1);
        super.onCreate(bundle);
        setContentView(R.layout.rootit);
    }

    @Override // android.app.Activity
    public void onPause() {
        super.onPause();
        finish();
    }
}
