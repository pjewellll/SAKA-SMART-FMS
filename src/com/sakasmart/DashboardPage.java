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

/** SAKA-SMART Dashboard page UI. Uses shared actions, storage and styling from the application context. */
final class DashboardPage extends PageBase {
    DashboardPage(Main host) { super(host); }
    JComponent build(){
        JPanel body=stack();
        List<Map<String,String>> items=rows("inventory"),events=rows("events"),tx=rows("transactions"),logs=rows("logs");
        long low=items.stream().filter(x->Storage.num(x,"quantity")<=Storage.num(x,"min_stock")).count();
        long upcoming=events.stream().filter(x->"Upcoming".equals(v(x,"status"))&&v(x,"date").compareTo(today())>=0).count();
        double incomes=tx.stream().filter(x->"Income".equals(v(x,"type"))&&v(x,"date").startsWith(month())).mapToDouble(x->Storage.num(x,"amount")).sum();
        if(admin()){
            long active=rows("users").stream().filter(x->"true".equalsIgnoreCase(v(x,"active"))).count();
            long pending=rows("users").stream().filter(x->"false".equalsIgnoreCase(v(x,"active"))).count();
            addBlock(body,horizontal(dashboardStat("Registered Users",Integer.toString(rows("users").size()),"All system accounts",LIGHT,"♟"),
                dashboardStat("Active Users",Long.toString(active),"Can sign in",new Color(234,244,253),"✓"),
                dashboardStat("Pending Approvals",Long.toString(pending),"Inactive registrations",new Color(253,242,223),"!"),
                dashboardStat("Activity Records",Integer.toString(logs.size()),"Logged system actions",new Color(240,233,253),"≡")),116);
            JButton bu=dashboardButton("Manage Users"),bc=dashboardButton("Learning Content"),bf=dashboardButton("Farm Profile");
            bu.addActionListener(e->go("Users"));bc.addActionListener(e->go("Learning Center"));bf.addActionListener(e->go("Farm Profile"));
            addBlock(body,dashboardSection("Quick Actions","Administrative tools",actions(bu,bc,bf)),145);
            addBlock(body,horizontal(dashboardSection("Recent Users","Latest registered accounts",dashboardTable("users",rows("users"),List.of("username","full_name","role","active"))),
                dashboardSection("System Activity","Recent changes",dashboardTable("logs",logs.stream().limit(9).toList(),List.of("actor","action","date")))),355);
        }else{
            addBlock(body,horizontal(dashboardStat("Total Inventory Items",Integer.toString(items.size()),"Registered stock entries",LIGHT,"▣"),
                dashboardStat("Upcoming Tasks",Long.toString(upcoming),"Scheduled and pending",new Color(254,244,224),"▦"),
                dashboardStat("This Month Income",Storage.money(incomes),"Recorded income",new Color(235,247,241),"₱"),
                dashboardStat("Low Stock Alerts",Long.toString(low),"Items need attention",new Color(255,237,233),"!")),116);
            List<Map<String,String>> urgent=filter(items,x->Storage.num(x,"quantity")<=Storage.num(x,"min_stock")||isExpiring(x));
            List<Map<String,String>> soon=filter(events,x->"Upcoming".equals(v(x,"status"))).stream().sorted(Comparator.comparing(x->v(x,"date"))).limit(7).toList();
            Map<String,String> farm=rows("farms").stream().findFirst().orElse(Map.of());
            JPanel farmOverview=pane(new BorderLayout(0,10));
            farmOverview.add(label(v(farm,"name"),21,true,DARK),BorderLayout.NORTH);
            JPanel overviewDetails=pane(new GridLayout(4,1,0,7));
            overviewDetails.add(label("📍 "+v(farm,"location"),14,false,TEXT));
            overviewDetails.add(label("Land area: "+v(farm,"area_ha")+" ha",14,false,TEXT));
            overviewDetails.add(label("Crops: "+v(farm,"crops"),13,false,TEXT));
            overviewDetails.add(label("Livestock: "+v(farm,"livestock"),13,false,TEXT));farmOverview.add(overviewDetails,BorderLayout.CENTER);
            JButton profile=dashboardButton("Farm Profile");profile.addActionListener(e->go("Settings"));farmOverview.add(profile,BorderLayout.SOUTH);
            addBlock(body,horizontal(dashboardSection("Farm Overview","Your farm at a glance",farmOverview),
                dashboardSection("Upcoming Calendar Events","Scheduled farming tasks",dashboardTable("events",soon,List.of("title","date","target","status"))),
                dashboardSection("Low Stock & Expiry Alerts","Monitor supplies",dashboardTable("inventory",urgent,List.of("name","quantity","unit","expiry_date")))),295);
            List<Map<String,String>> production=rows("production");
            Map<String,Double> productionMap=new LinkedHashMap<>();
            for(Map<String,String> row:production)productionMap.put(v(row,"item"),Storage.num(row,"target")<=0?0:Math.min(120,100*Storage.num(row,"actual")/Storage.num(row,"target")));
            Map<String,Double> financial=new LinkedHashMap<>();
            financial.put("Income",tx.stream().filter(x->"Income".equals(v(x,"type"))).mapToDouble(x->Storage.num(x,"amount")).sum());
            financial.put("Expenses",tx.stream().filter(x->"Expense".equals(v(x,"type"))).mapToDouble(x->Storage.num(x,"amount")).sum());
            addBlock(body,horizontal(dashboardSection("Financial Snapshot","Recorded transactions",new ChartPanel(financial,"₱")),
                dashboardSection("Production Summary","Target achievement (%)",new ChartPanel(productionMap,"%"))),295);
            addBlock(body,dashboardTip("🌱  SAKA-SMART TIP","Your charts and totals are calculated from saved records, not from fixed mockup numbers."),95);
        }
        return body;
    }

}
