package org.aarchdroid;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import androidx.fragment.app.Fragment;
import org.aarchdroid.dragonterminal.bridge.Bridge;

/* JADX INFO: loaded from: classes2.dex */
public class MainFragment extends Fragment implements View.OnClickListener {
    @Override // android.view.View.OnClickListener
    public void onClick(View view) {
    }

    @Override // androidx.fragment.app.Fragment
    public View onCreateView(LayoutInflater layoutInflater, ViewGroup viewGroup, Bundle bundle) {
        View viewInflate = layoutInflater.inflate(R.layout.fragment_main, viewGroup, false);
        ImageButton imageButton = (ImageButton) viewInflate.findViewById(R.id.btnrun);
        ImageButton imageButton3 = (ImageButton) viewInflate.findViewById(R.id.btntelegram);
        ImageButton imageButton4 = (ImageButton) viewInflate.findViewById(R.id.btncontact);
        ImageButton imageButton5 = (ImageButton) viewInflate.findViewById(R.id.btnmanual);
        imageButton.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.MainFragment.1
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                MainFragment.this.run_hack_cmd("andrax");
            }
        });
        imageButton3.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.MainFragment.3
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                MainFragment.this.startActivity(new Intent("android.intent.action.VIEW", Uri.parse("https://t.me/snakesecurityofficial")));
            }
        });
        imageButton4.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.MainFragment.4
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                String str = "mailto:weidsom@snakesecurity.org?subject=" + Uri.encode("About ANDRAX-NG");
                Intent intent = new Intent("android.intent.action.SENDTO");
                intent.setData(Uri.parse(str));
                try {
                    MainFragment.this.startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        imageButton5.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andrax.MainFragment.5
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                MainFragment.this.startActivity(new Intent("android.intent.action.VIEW", Uri.parse("https://snakesecurity.org/andrax-documentation/")));
            }
        });
        return viewInflate;
    }

    @Override // androidx.fragment.app.Fragment
    public void onPause() {
        super.onPause();
    }

    @Override // androidx.fragment.app.Fragment
    public void onResume() {
        super.onResume();
    }

    @Override // androidx.fragment.app.Fragment
    public void onDestroy() {
        super.onDestroy();
    }

    public void run_hack_cmd(String str) {
        Intent intentCreateExecuteIntent = Bridge.createExecuteIntent(str);
        intentCreateExecuteIntent.setFlags(131072);
        startActivity(intentCreateExecuteIntent);
    }
}
