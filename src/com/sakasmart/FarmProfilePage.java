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

/** SAKA-SMART FarmProfile page UI. Uses shared actions, storage and styling from the application context. */
final class FarmProfilePage extends PageBase {
    FarmProfilePage(Main host) { super(host); }
    JComponent build(){
        JPanel p=stack();Map<String,String> f=rows("farms").stream().findFirst().orElse(Map.of());
        JButton edit=btn("Edit Farm Profile",GREEN);edit.addActionListener(e->editFarm());
        addBlock(p,section("Farm Administration","The starter currently has one farm profile",actions(edit)),110);
        addBlock(p,section("Farm Details","Edit the current farm record in the database",tableView("farms",List.of(f),List.of("name","location","owner","area_ha","crops","livestock"),null).scroll()),263);
        addBlock(p,blank("Multi-Farm Expansion","The mockup includes multiple farms. Registration approvals and multi-farm isolation are planned for a later stage."),110);
        return p;
    }

}
