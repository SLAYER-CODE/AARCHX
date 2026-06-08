package org.aarchdroid;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public abstract class DcoListActivity extends DcoBaseActivity {
    private static final String TAG = "DcoListActivity";

    protected abstract List<ToolItem> buildToolList();
    protected abstract String getHeaderTitle();
    protected abstract int getBannerResId();
    protected abstract int getToolCount();

    @Override
    protected void onCreate(Bundle bundle) {
        try {
            requestWindowFeature(1);
            super.onCreate(bundle);
            setContentView(R.layout.dco_list_scaffold);
            getWindow().setFlags(1024, 1024);

            RecyclerView list = findViewById(R.id.tool_list);
            list.setLayoutManager(new LinearLayoutManager(this));

            List<ToolItem> tools = buildToolList();
            ToolAdapter adapter = new ToolAdapter(tools, new ToolAdapter.OnToolClickListener() {
                @Override
                public void onToolClick(String cmd) {
                    run_hack_cmd(cmd);
                }

                @Override
                public void onInstallClick(String toolKey) {
                    String cmd = buildInstallCommandForKey(toolKey);
                    if (cmd != null) {
                        run_hack_cmd(cmd);
                    }
                }
            });
            list.setAdapter(adapter);
            list.setHasFixedSize(true);
        } catch (Exception e) {
            Log.e(TAG, "onCreate failed", e);
            finish();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        finish();
    }
}
