package com.sakasmart;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.List;
import java.util.function.*;
import static com.sakasmart.Main.*;

/** SAKA-SMART Backup page UI. Uses shared actions, storage and styling from the application context. */
final class BackupPage extends PageBase {
    BackupPage(Main host) { super(host); }
    JComponent build(){
        JPanel p=stack();
        JButton export=btn("Export CSV Data Archive (ZIP)",GREEN);export.addActionListener(e->backupArchive());
        addBlock(p,section("Backup & Data Export","Download copies of all tables as CSV files",actions(export)),150);
        addBlock(p,blank("Important: This Is Not a Full Restore Backup","For PostgreSQL, use pgAdmin Backup/Restore (or pg_dump/pg_restore) for a complete database backup. No cloud sync or in-app restore is included in this starter."),130);
        addBlock(p,blank("Local Demo Storage","Demo information is saved in data/saka-smart-demo.dat. Closing and reopening the app preserves your changes on this computer."),130);
        return p;
    }

}
