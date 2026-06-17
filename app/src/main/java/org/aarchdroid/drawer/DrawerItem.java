package org.aarchdroid.drawer;

import android.graphics.drawable.Drawable;

public class DrawerItem {
    public int id;
    public Drawable icon;
    public CharSequence title;
    public String toolKey;
    public String status;
    public String source;
    public long actualSizeBytes;

    public DrawerItem(int id, Drawable icon, CharSequence title) {
        this.id = id;
        this.icon = icon;
        this.title = title;
    }
}
