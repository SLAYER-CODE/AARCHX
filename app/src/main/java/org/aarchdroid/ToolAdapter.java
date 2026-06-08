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

    public interface OnToolClickListener {
        void onToolClick(String cmd);
        void onInstallClick(String toolKey);
    }

    public ToolAdapter(List<ToolItem> tools, OnToolClickListener listener) {
        this.tools = tools;
        this.listener = listener;
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

        ViewHolder(View v) {
            super(v);
            cardView = (CardView) v;
            icon = v.findViewById(R.id.icon);
            title = v.findViewById(R.id.title);
            description = v.findViewById(R.id.description);
            installBtn = v.findViewById(R.id.install_btn);
        }
    }
}
