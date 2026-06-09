package org.aarchdroid;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ToolAdapter extends RecyclerView.Adapter<ToolAdapter.ViewHolder> {
    private List<ToolItem> tools;
    private OnToolClickListener listener;
    private ToolDatabase db;

    public interface OnToolClickListener {
        void onToolClick(String cmd);
        void onInstallClick(String toolKey);
    }

    public ToolAdapter(List<ToolItem> tools, OnToolClickListener listener) {
        this.tools = tools;
        this.listener = listener;
        this.db = ToolDatabase.getInstance();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_tool_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder h, int pos) {
        ToolItem t = tools.get(pos);
        h.icon.setImageResource(t.iconResId);
        h.title.setText(t.displayName);
        h.description.setText(t.description);
        h.cardView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onToolClick(t.cmd);
            }
        });
        h.installBtn.setTag(t.key);
        h.installBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onInstallClick(t.key);
            }
        });
        String status = db.getStatus(t.key);
        updateInstallButton(h.installBtn, status);
        updateStatusBar(h.statusBar, status);
    }

    private void updateInstallButton(ImageView btn, String status) {
        if ("installed".equals(status)) {
            btn.setColorFilter(0xFF00FF00);
        } else if ("installing".equals(status)) {
            btn.setColorFilter(0xFFFF8C00);
        } else if ("failed".equals(status)) {
            btn.setColorFilter(0xFFFF0000);
        } else {
            btn.setColorFilter(0xFF006400);
        }
    }

    private void updateStatusBar(View bar, String status) {
        if ("installed".equals(status)) {
            bar.setBackgroundColor(0xFF00FF00);
        } else if ("installing".equals(status)) {
            bar.setBackgroundColor(0xFFFF8C00);
        } else if ("failed".equals(status)) {
            bar.setBackgroundColor(0xFFFF0000);
        } else {
            bar.setBackgroundColor(0xFF006400);
        }
    }

    @Override
    public int getItemCount() {
        return tools.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        ImageView icon;
        TextView title;
        TextView description;
        ImageView installBtn;
        View statusBar;

        ViewHolder(View v) {
            super(v);
            cardView = (CardView) v;
            icon = v.findViewById(R.id.icon);
            title = v.findViewById(R.id.title);
            description = v.findViewById(R.id.description);
            installBtn = v.findViewById(R.id.install_btn);
            statusBar = v.findViewById(R.id.status_bar);
        }
    }
}
