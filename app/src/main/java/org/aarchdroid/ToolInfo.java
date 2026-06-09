package org.aarchdroid;

import org.aarchdroid.dragonterminal.framework.database.annotation.ID;
import org.aarchdroid.dragonterminal.framework.database.annotation.Table;

@Table(name = "tools")
public class ToolInfo {
    @ID
    public String toolKey;
    public String displayName;
    public String description;
    public String source;
    public String category;
    public String installCommand;
    public String uninstallCommand;
    public long estimatedSizeBytes;
    public long actualSizeBytes;
    public String status;
    public String installPath;
    public long installedAt;
    public String errorLog;

    public ToolInfo() {
    }
}
