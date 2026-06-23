package org.aarchdroid;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class Dco_voip_3g_4g extends DcoBaseActivity {
    private static final String TAG = "Dco_voip_3g_4g";

    @Override
    protected void onCreate(Bundle bundle) {
        try {
            requestWindowFeature(1);
            super.onCreate(bundle);
            Log.d(TAG, "onCreate");
            setContentView(R.layout.dco_list_scaffold);
            getWindow().setFlags(1024, 1024);

            ((TextView) findViewById(R.id.title)).setText(getCategoryDisplayName());
            ((ImageView) findViewById(R.id.banner)).setImageResource(getCategoryBannerResId());
            ((TextView) findViewById(R.id.stats_tools)).setText(String.valueOf(getCategoryToolCount()));

            RecyclerView list = findViewById(R.id.tool_list);
            list.setLayoutManager(new LinearLayoutManager(this));

            createAdapter(list, loadToolsFromDb(), new ToolAdapter.OnToolClickListener() {
                @Override
                public void onToolClick(ToolItem item) { handleCardClick(item); }
                @Override
                public void onInstallClick(String toolKey) { processInstallTool(toolKey); }
                @Override
                public void onUninstallClick(String toolKey) { Dco_voip_3g_4g.this.onUninstallClick(toolKey); }
                @Override
                public void onLaunchTool(String toolKey) { Dco_voip_3g_4g.this.onLaunchTool(toolKey); }
            });
            list.setHasFixedSize(true);
        } catch (Exception e) {
            Log.e(TAG, "onCreate failed", e);
            finish();
        }
    }
}
