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

import java.util.ArrayList;
import java.util.List;

public class DrawerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(DrawerItem item);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CATEGORY = 1;
    private static final int TYPE_ITEM = 2;

    private final List<Object> flatItems = new ArrayList<>();
    private OnItemClickListener listener;

    public DrawerAdapter(List<DrawerSection> sections) {
        flatItems.add(new Object()); // header placeholder
        for (DrawerSection sec : sections) {
            flatItems.add(sec);
            flatItems.addAll(sec.items);
        }
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        Object obj = flatItems.get(position);
        if (obj instanceof DrawerSection) return TYPE_CATEGORY;
        if (obj instanceof DrawerItem) return TYPE_ITEM;
        return TYPE_HEADER;
    }

    @Override
    public int getItemCount() {
        return flatItems.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            View v = inflater.inflate(R.layout.nav_header_main, parent, false);
            return new HeaderViewHolder(v);
        } else if (viewType == TYPE_CATEGORY) {
            View v = inflater.inflate(R.layout.drawer_category_header, parent, false);
            return new CategoryViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.drawer_item, parent, false);
            return new ItemViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object obj = flatItems.get(position);
        if (holder instanceof CategoryViewHolder) {
            DrawerSection sec = (DrawerSection) obj;
            ((CategoryViewHolder) holder).title.setText(sec.title);
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
        CategoryViewHolder(View v) {
            super(v);
            title = (TextView) v;
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
