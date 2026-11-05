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

/** SAKA-SMART SystemRecords page UI. Uses shared actions, storage and styling from the application context. */
final class SystemRecordsPage extends PageBase {
    SystemRecordsPage(Main host) { super(host); }
    JComponent build(){
        JPanel p=stack();List<Map<String,String>> logs=rows("logs");
        addBlock(p,horizontal(stat("System Records",String.valueOf(logs.size()),"Activity log entries",LIGHT,"≡"),
            stat("Accounts",String.valueOf(rows("users").size()),"Known users",new Color(234,244,254),"♟")),115);
        addBlock(p,section("Recent System Activity","Record changes, sign-ins, and actions",tableView("logs",logs,List.of("id","date","actor","action"),"Search activity...").scroll()),480);
        return p;
    }

}
