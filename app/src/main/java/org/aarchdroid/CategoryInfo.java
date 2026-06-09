package org.aarchdroid;

import org.aarchdroid.dragonterminal.framework.database.annotation.ID;
import org.aarchdroid.dragonterminal.framework.database.annotation.Table;

@Table(name = "categories")
public class CategoryInfo {
    @ID
    public String name;
    public int totalTools;
    public int installedTools;
    public long totalSizeMb;
    public long installedSizeMb;

    public CategoryInfo() {
    }
}
