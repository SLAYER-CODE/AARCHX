package org.aarchdroid.drawer;

import java.util.List;

public class DrawerSection {
    public CharSequence title;
    public List<DrawerItem> items;
    public boolean expanded = true;

    public DrawerSection(CharSequence title, List<DrawerItem> items) {
        this.title = title;
        this.items = items;
    }
}
