package org.aarchdroid.drawer;

import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.aarchdroid.R;

import java.util.List;
import java.util.Map;

public class DrawerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(DrawerItem item);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CATEGORY = 1;
    private static final int TYPE_ITEM = 2;

    private static final int NOT_INSTALLED = 0xFF08FF00;
    private static final int INSTALLED = 0xFFFF8C00;
    private static final int INSTALLING = 0xFFFFEB3B;
    private static final int FAILED = 0xFFFF4444;
    private static final int LOCAL = 0xFF00FFFF;
    private static final int TEXT_DEFAULT = 0xFFfefefe;
    private static final int TEXT_GRAY = 0xFF888888;

    private final List<DrawerSection> sections;
    private final RecyclerView recyclerView;
    private OnItemClickListener listener;
    private Map<String, String> statusCache = new java.util.HashMap<>();
    private Map<String, Long> sizeCache = new java.util.HashMap<>();

    public DrawerAdapter(List<DrawerSection> sections, RecyclerView recyclerView) {
        this.sections = sections;
        this.recyclerView = recyclerView;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void updateStatuses(Map<String, String> statuses, Map<String, Long> sizes) {
        this.statusCache = statuses;
        this.sizeCache = sizes;
    }

    public void refreshStatuses(android.content.Context context) {
        org.aarchdroid.ToolDatabase db = org.aarchdroid.ToolDatabase.getInstance();
        Map<String, String> st = new java.util.HashMap<>();
        Map<String, Long> sz = new java.util.HashMap<>();
        for (DrawerSection sec : sections) {
            for (DrawerItem di : sec.items) {
                if (di.toolKey == null) continue;
                String s = db.getStatus(di.toolKey);
                if ("installing".equals(s)) {
                    java.io.File pidFile = new java.io.File(
                        context.getFilesDir(), "install-state/" + di.toolKey + ".pid");
                    if (!pidFile.exists()) {
                        db.markUninstalled(di.toolKey);
                        s = "not_installed";
                    }
                }
                st.put(di.toolKey, s);
                org.aarchdroid.ToolInfo info = db.getTool(di.toolKey);
                if (info != null && info.actualSizeBytes > 0) {
                    sz.put(di.toolKey, info.actualSizeBytes);
                }
            }
        }
        this.statusCache = st;
        this.sizeCache = sz;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        int count = 1;
        for (DrawerSection sec : sections) {
            count++;
            if (sec.expanded) count += sec.items.size();
        }
        return count;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) return TYPE_HEADER;
        int pos = 1;
        for (DrawerSection sec : sections) {
            if (pos == position) return TYPE_CATEGORY;
            pos++;
            if (sec.expanded) {
                if (pos + sec.items.size() > position) return TYPE_ITEM;
                pos += sec.items.size();
            }
        }
        return TYPE_ITEM;
    }

    private Object resolve(int position) {
        if (position == 0) return null;
        int pos = 1;
        for (DrawerSection sec : sections) {
            if (pos == position) return sec;
            pos++;
            if (sec.expanded) {
                int idx = position - pos;
                if (idx >= 0 && idx < sec.items.size()) return sec.items.get(idx);
                pos += sec.items.size();
            }
        }
        return null;
    }

    private int statusColor(String status) {
        if ("installed".equals(status)) return INSTALLED;
        if ("installing".equals(status)) return INSTALLING;
        if ("failed".equals(status)) return FAILED;
        return NOT_INSTALLED;
    }

    private int textColor(String status) {
        if ("installed".equals(status)) return INSTALLED;
        if ("installing".equals(status)) return INSTALLING;
        if ("failed".equals(status)) return FAILED;
        return TEXT_GRAY;
    }

    private String badgeText(String status, long sizeBytes) {
        if ("installing".equals(status)) return "INSTALLING...";
        if ("failed".equals(status)) return "FAILED";
        if ("installed".equals(status) && sizeBytes > 0) {
            if (sizeBytes >= 1024 * 1024) return (sizeBytes / (1024 * 1024)) + "MB";
            if (sizeBytes >= 1024) return (sizeBytes / 1024) + "KB";
            return sizeBytes + "B";
        }
        if ("installed".equals(status)) return "INSTALLED";
        return null;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderViewHolder(inflater.inflate(R.layout.nav_header_main, parent, false));
        } else if (viewType == TYPE_CATEGORY) {
            return new CategoryViewHolder(inflater.inflate(R.layout.drawer_category_header, parent, false));
        } else {
            return new ItemViewHolder(inflater.inflate(R.layout.drawer_item, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object obj = resolve(position);
        if (holder instanceof CategoryViewHolder) {
            DrawerSection sec = (DrawerSection) obj;
            CategoryViewHolder vh = (CategoryViewHolder) holder;
            vh.title.setText(sec.title);
            vh.arrow.setText(sec.expanded ? "\u25BC" : "\u25B6");
            vh.title.setTextColor(sec.expanded ? 0xFFFF4444 : 0xFF08FF00);
            vh.arrow.setTextColor(sec.expanded ? 0xFFFF4444 : 0xFF08FF00);
            vh.itemView.setOnClickListener(v -> {
                recyclerView.setLayoutFrozen(true);
                sec.expanded = !sec.expanded;
                notifyDataSetChanged();
                recyclerView.setLayoutFrozen(false);
            });
        } else if (holder instanceof ItemViewHolder) {
            DrawerItem item = (DrawerItem) obj;
            ItemViewHolder vh = (ItemViewHolder) holder;
            vh.title.setText(item.title);

            if (item.toolKey != null) {
                String status = statusCache != null ? statusCache.get(item.toolKey) : null;
                if (status == null) status = "not_installed";
                boolean localSource = "local".equals(item.source);
                int color = localSource ? LOCAL : statusColor(status);
                int tColor = localSource ? LOCAL : textColor(status);

                if (item.icon != null) {
                    vh.icon.setImageDrawable(item.icon.mutate());
                    vh.icon.setColorFilter(tColor, PorterDuff.Mode.SRC_IN);
                }
                vh.title.setTextColor(tColor);

                long size = sizeCache != null ? sizeCache.getOrDefault(item.toolKey, 0L) : 0;
                String badge = badgeText(status, size);
                if (badge != null) {
                    vh.badge.setText(badge);
                    vh.badge.setTextColor(color);
                    vh.badge.setVisibility(View.VISIBLE);
                } else {
                    vh.badge.setVisibility(View.GONE);
                }
            } else {
                if (item.icon != null) {
                    vh.icon.setImageDrawable(item.icon.mutate());
                    vh.icon.setColorFilter(NOT_INSTALLED, PorterDuff.Mode.SRC_IN);
                }
                vh.title.setTextColor(TEXT_DEFAULT);
                vh.badge.setVisibility(View.GONE);
            }

            vh.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(item);
            });
        }
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        HeaderViewHolder(View v) { super(v); }
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        TextView arrow;
        CategoryViewHolder(View v) {
            super(v);
            title = v.findViewById(R.id.title);
            arrow = v.findViewById(R.id.arrow);
        }
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView title;
        TextView badge;
        ItemViewHolder(View v) {
            super(v);
            icon = v.findViewById(R.id.icon);
            title = v.findViewById(R.id.title);
            badge = v.findViewById(R.id.badge);
        }
    }
}
