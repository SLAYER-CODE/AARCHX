package org.aarchdroid;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import org.aarchdroid.dragonterminal.bridge.Bridge;

public class chtsh extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        /**
         *
         * Help me, i'm dying...
         *
         **/

        run_hack_cmd("cht.sh --shell");

    }

    @Override
    protected void onPause() {
        super.onPause();
        finish();
    }

    public void run_hack_cmd(String cmd) {

        Intent intent = Bridge.createExecuteIntent(cmd);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);

    }

}
