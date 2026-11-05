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

/** SAKA-SMART Production page UI. Uses shared actions, storage and styling from the application context. */
final class ProductionPage extends PageBase {
    ProductionPage(Main host) { super(host); }
    JComponent build(){
        JPanel p=stack();List<Map<String,String>> logs=rows("production");
        double avg=logs.stream().filter(x->Storage.num(x,"target")>0).mapToDouble(x->100*Storage.num(x,"actual")/Storage.num(x,"target")).average().orElse(0);
        long behind=logs.stream().filter(x->Storage.num(x,"actual")<Storage.num(x,"target")).count();
        addBlock(p,horizontal(stat("Production Logs",Integer.toString(logs.size()),"Crop and livestock",LIGHT,"❧"),
            stat("Crop Records",String.valueOf(filter(logs,x->"CROP".equals(v(x,"kind"))).size()),"Logged field output",new Color(252,242,223),"▥"),
            stat("Avg Target Achievement",String.format(Locale.US,"%.1f%%",avg),"Average of per-log ratios",new Color(230,243,255),"%"),
            stat("Below Target",Long.toString(behind),"Needs review",new Color(253,238,233),"!")),115);
        Map<String,Double> chart=new LinkedHashMap<>();
        for(Map<String,String> r:logs)chart.put(v(r,"item")+" (#"+v(r,"id")+")",Storage.num(r,"target")<=0?0:100*Storage.num(r,"actual")/Storage.num(r,"target"));
        addBlock(p,section("Target vs Actual Output","Percent of target achieved per production record (units are not added together)",new ChartPanel(chart,"%")),278);
        TableParts table=tableView("production",logs,List.of("id","item","kind","target","actual","unit","date","notes"),"Search production logs...");
        addBlock(p,section("Crop & Livestock Production Logs","Actual output and targets",tableAndActions("production",table,Map.of("kind","CROP","date",today(),"farm_id","1","target","0","actual","0"))),382);
        return p;
    }


}
