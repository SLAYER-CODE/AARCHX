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

public class DrawerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(DrawerItem item);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CATEGORY = 1;
    private static final int TYPE_ITEM = 2;

    private final List<DrawerSection> sections;
    private final RecyclerView recyclerView;
    private OnItemClickListener listener;

    public DrawerAdapter(List<DrawerSection> sections, RecyclerView recyclerView) {
        this.sections = sections;
        this.recyclerView = recyclerView;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @Override
    public int getItemCount() {
        int count = 1; // header
        for (DrawerSection sec : sections) {
            count++; // category header
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
        if (position == 0) return null; // header
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
            if (item.icon != null) {
                vh.icon.setImageDrawable(item.icon.mutate());
                vh.icon.setColorFilter(0xFF08FF00, PorterDuff.Mode.SRC_IN);
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
        ItemViewHolder(View v) {
            super(v);
            icon = v.findViewById(R.id.icon);
            title = v.findViewById(R.id.title);
        }
    }
}
