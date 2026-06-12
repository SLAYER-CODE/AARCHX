package org.aarchdroid;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolAdapter extends RecyclerView.Adapter<ToolAdapter.ViewHolder> {
    private List<ToolItem> tools;
    private OnToolClickListener listener;
    private Map<String, String> statusCache = new HashMap<>();
    private Map<String, ToolInfo> infoCache = new HashMap<>();

    public interface OnToolClickListener {
        void onToolClick(ToolItem item);
        void onInstallClick(String toolKey);
        void onUninstallClick(String toolKey);
        void onLaunchTool(String toolKey);
    }

    public ToolAdapter(List<ToolItem> tools, OnToolClickListener listener) {
        this.tools = tools;
        this.listener = listener;
    }

    public void updateCache(Map<String, String> statuses, Map<String, ToolInfo> infos) {
        this.statusCache = statuses;
        this.infoCache = infos;
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
        String nkey = ToolDatabase.normalizeKey(t.key);
        String status = statusCache.getOrDefault(nkey, "not_installed");
        boolean installed = "installed".equals(status);
        boolean isSystem = installed && "local".equals(t.source);
        h.cardView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (installed || "local".equals(t.source)) {
                    if (listener != null) listener.onToolClick(t);
                } else {
                    if (listener != null) listener.onInstallClick(t.key);
                }
            }
        });
        h.installBtn.setTag(t.key);
        String source = t.source;
        if (source != null && !source.isEmpty()) {
            h.sourceBadge.setText(source.toUpperCase());
            int color;
            switch (source.toLowerCase()) {
                case "local":
                    color = 0xFF00FFFF;
                    break;
                case "github":
                    color = 0xFFFFFFFF;
                    break;
                case "url":
                    color = 0xFF808080;
                    break;
                case "pacman":
                default:
                    color = 0xFFFFDD00;
                    break;
            }
            h.sourceBadge.setTextColor(color);
        } else {
            h.sourceBadge.setText("");
        }
        h.installBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (installed || "local".equals(t.source)) {
                    if (listener != null) listener.onLaunchTool(t.key);
                } else {
                    if (listener != null) listener.onInstallClick(t.key);
                }
            }
        });
        h.uninstallBadge.setTag(t.key);
        if (isSystem) {
            h.uninstallBadge.setVisibility(View.GONE);
        } else {
            h.uninstallBadge.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) listener.onUninstallClick(t.key);
                }
            });
        }
        updateCardForStatus(h, status, isSystem);
        updateBadge(h, nkey);
    }

    private void updateBadge(ViewHolder h, String toolKey) {
        ToolInfo t = infoCache.get(toolKey);
        if (t == null || !"installed".equals(t.status)) return;
        long bytes = t.actualSizeBytes > 0 ? t.actualSizeBytes : t.estimatedSizeBytes;
        if (bytes > 0) {
            String txt;
            if (bytes >= 1024 * 1024) {
                txt = (bytes / (1024 * 1024)) + "MB";
            } else if (bytes >= 1024) {
                txt = (bytes / 1024) + "KB";
            } else {
                txt = bytes + "B";
            }
            h.badgeSize.setText(txt);
            h.badgeSize.setVisibility(View.VISIBLE);
        } else {
            h.badgeSize.setVisibility(View.GONE);
        }
    }

    private void updateCardForStatus(ViewHolder h, String status, boolean isSystem) {
        int iconRes;
        int tint;
        int textColor;
        int statusBg;

        if (isSystem) {
            iconRes = R.drawable.ic_check;
            tint = 0xFF00FFFF;
            textColor = 0xFF00FF00;
            statusBg = 0xFF00FFFF;
        } else if ("installed".equals(status)) {
            iconRes = R.drawable.ic_install;
            tint = 0xFFFF8C00;
            textColor = 0xFFFF8C00;
            statusBg = 0xFFFF8C00;
        } else if ("failed".equals(status)) {
            iconRes = R.drawable.ic_install;
            tint = 0xFFFF0000;
            textColor = 0xFFFF0000;
            statusBg = 0xFFFF0000;
        } else {
            iconRes = R.drawable.ic_install;
            tint = 0xFF006400;
            textColor = 0xFF888888;
            statusBg = 0xFF006400;
        }

        h.installBtn.setImageResource(iconRes);
        h.installBtn.setColorFilter(tint);
        h.statusBar.setBackgroundColor(statusBg);
        h.title.setTextColor(textColor);
        h.description.setTextColor(textColor);
        boolean installed = "installed".equals(status);
        h.uninstallBadge.setVisibility(!isSystem && installed ? View.VISIBLE : View.GONE);
        h.badgeSize.setVisibility(installed ? View.VISIBLE : View.GONE);
        h.badgeRow.setVisibility(View.VISIBLE);
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
        TextView uninstallBadge;
        TextView badgeSize;
        TextView sourceBadge;
        View statusBar;
        View badgeRow;

        ViewHolder(View v) {
            super(v);
            cardView = (CardView) v;
            icon = v.findViewById(R.id.icon);
            title = v.findViewById(R.id.title);
            description = v.findViewById(R.id.description);
            installBtn = v.findViewById(R.id.install_btn);
            uninstallBadge = v.findViewById(R.id.uninstall_badge);
            badgeSize = v.findViewById(R.id.badge_size);
            sourceBadge = v.findViewById(R.id.source_badge);
            statusBar = v.findViewById(R.id.status_bar);
            badgeRow = v.findViewById(R.id.badge_row);
        }
    }
}
