package org.aarchdroid;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import android.util.DisplayMetrics;
import org.aarchdroid.dragonterminal.bridge.Bridge;
import java.io.DataOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class DcoBaseActivity extends Activity {
    private static final String TAG = "DcoBaseActivity";
    private static Boolean hasRoot = null;
    private boolean layoutSet = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setGravity(Gravity.CENTER);
        getWindow().setDimAmount(0.25f);
        setFinishOnTouchOutside(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!layoutSet) {
            layoutSet = true;
            DisplayMetrics dm = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(dm);
            getWindow().setLayout((int)(dm.widthPixels * 0.85),
                (int)(dm.heightPixels * 0.65));
        }
    }

    public void onInstallClick(View v) {
        ViewGroup cardContent = (ViewGroup) v.getParent();
        String tool = "";
        for (int i = 0; i < cardContent.getChildCount(); i++) {
            View child = cardContent.getChildAt(i);
            if (child instanceof TextView) {
                String text = ((TextView) child).getText().toString().trim();
                if (!text.isEmpty()) {
                    tool = text.toLowerCase().replaceAll("[^a-z0-9-]", "").replaceAll("-+", "-");
                    break;
                }
            }
        }
        if (!tool.isEmpty()) {
            run_hack_cmd("pacman -S " + tool);
        }
    }

    public void run_hack_cmd(String cmd) {
        Log.d(TAG, "run_hack_cmd: " + cmd);

        if (hasRoot == null) {
            hasRoot = checkRoot();
        }

        if (!hasRoot) {
            Toast.makeText(this, "Root no detectado", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = Bridge.createExecuteIntent(cmd);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try {
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "run_hack_cmd failed: " + e.getMessage(), e);
        }
    }

    private static boolean checkRoot() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            os.writeBytes("id\n");
            os.writeBytes("exit\n");
            os.flush();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("uid=0")) {
                    process.waitFor();
                    return true;
                }
            }
            process.waitFor();
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Root check failed", e);
            return false;
        }
    }
}
