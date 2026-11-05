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

/** SAKA-SMART Calendar page UI. Uses shared actions, storage and styling from the application context. */
final class CalendarPage extends PageBase {
    CalendarPage(Main host) { super(host); }
    JComponent build(){
        JPanel p=stack();List<Map<String,String>> all=rows("events");
        long overdue=all.stream().filter(x->"Upcoming".equals(v(x,"status"))&&v(x,"date").compareTo(today())<0).count();
        long completed=all.stream().filter(x->"Completed".equals(v(x,"status"))).count();
        addBlock(p,horizontal(stat("Scheduled Tasks",String.valueOf(all.size()),"All farm events",LIGHT,"▦"),
            stat("Overdue Tasks",String.valueOf(overdue),"Still incomplete",new Color(255,237,233),"!"),
            stat("Completed Tasks",String.valueOf(completed),"Recorded as complete",new Color(232,240,253),"✓"),
            stat("Upcoming Events",String.valueOf(filter(all,x->"Upcoming".equals(v(x,"status"))).size()),"Planned activities",new Color(255,244,223),"◷")),115);
        JButton crop=btn("Crop Schedule",host.calendarLivestock?new Color(104,132,115):GREEN);
        JButton live=btn("Livestock Schedule",host.calendarLivestock?GREEN:new Color(104,132,115));
        crop.addActionListener(e->{host.calendarLivestock=false;renderPage();});live.addActionListener(e->{host.calendarLivestock=true;renderPage();});
        JButton add=btn("+ Add Task",GREEN);add.addActionListener(e->form("events",null,Map.of("kind",host.calendarLivestock?"LIVESTOCK":"CROP","date",today(),"time","08:00","status","Upcoming","farm_id","1"),Set.of()));
        JPanel toolbar=pane(new BorderLayout());toolbar.add(actions(crop,live),BorderLayout.WEST);if(editable("events"))toolbar.add(add,BorderLayout.EAST);
        addBlock(p,section("Field & Livestock Calendar","Choose a schedule and manage upcoming activities",toolbar),141);
        List<Map<String,String>> selected=filter(all,x->(host.calendarLivestock?"LIVESTOCK":"CROP").equals(v(x,"kind")));
        JPanel monthPanel=card(new BorderLayout(0,10));pad(monthPanel,15,16,14,16);
        JPanel nav=pane(new FlowLayout(FlowLayout.LEFT,10,0));
        JButton prev=secondary("‹"),next=secondary("›"),todayBtn=secondary("Today");
        prev.addActionListener(e->{host.calendarMonth=host.calendarMonth.minusMonths(1);renderPage();});next.addActionListener(e->{host.calendarMonth=host.calendarMonth.plusMonths(1);renderPage();});
        todayBtn.addActionListener(e->{host.calendarMonth=YearMonth.now();renderPage();});
        nav.add(prev);nav.add(label(host.calendarMonth.getMonth().getDisplayName(java.time.format.TextStyle.FULL,Locale.ENGLISH)+" "+host.calendarMonth.getYear(),19,true,DARK));nav.add(next);nav.add(todayBtn);
        monthPanel.add(nav,BorderLayout.NORTH);
        JPanel grid=pane(new GridLayout(0,7,4,4));
        for(String day:List.of("Mon","Tue","Wed","Thu","Fri","Sat","Sun")){JLabel d=label(day,12,true,GREEN);d.setHorizontalAlignment(SwingConstants.CENTER);grid.add(d);}
        int offset=host.calendarMonth.atDay(1).getDayOfWeek().getValue()-1;
        for(int i=0;i<offset;i++)grid.add(new JLabel());
        for(int day=1;day<=host.calendarMonth.lengthOfMonth();day++){
            LocalDate date=host.calendarMonth.atDay(day);
            long count=selected.stream().filter(x->date.toString().equals(v(x,"date"))).count();
            String text="<html><center>"+day+(count>0?"<br><font color='#167948'>● "+count+" event(s)</font>":"")+"</center></html>";
            JButton cell=new JButton(text);cell.setFont(FONT);cell.setBackground(count>0?LIGHT:Color.WHITE);
            cell.setFocusPainted(false);cell.setBorder(BorderFactory.createLineBorder(date.equals(LocalDate.now())?GREEN:new Color(219,232,222)));
            cell.addActionListener(e->{
                StringBuilder msg=new StringBuilder(date.toString()+"\n\n");
                for(Map<String,String> event:selected)if(date.toString().equals(v(event,"date")))msg.append("• ").append(v(event,"time")).append(" ").append(v(event,"title")).append("\n");
                if(count==0)msg.append("No scheduled tasks.");info(msg.toString());
            });grid.add(cell);
        }
        monthPanel.add(grid,BorderLayout.CENTER);
        List<Map<String,String>> upcoming=selected.stream().sorted(Comparator.comparing(x->v(x,"date"))).toList();
        JPanel right=pane(new BorderLayout(0,10));
        right.add(section("Upcoming & Recorded Tasks","Select a row to edit or complete",tableView("events",upcoming,List.of("title","date","time","target","status"),null).scroll()),BorderLayout.CENTER);
        TableParts table=tableView("events",upcoming,List.of("id","title","kind","date","status","target"),null);
        JPanel buttons=pane(new BorderLayout(0,7));buttons.add(table.scroll(),BorderLayout.CENTER);
        if(editable("events")){
            JButton edit=secondary("Edit Task"),done=btn("Mark Completed",GREEN),delete=btn("Delete",RED);
            edit.addActionListener(e->selectedEdit("events",table));delete.addActionListener(e->selectedDelete("events",table));
            done.addActionListener(e->{Map<String,String> r=table.selected();if(r==null){info("Select a task first.");return;}
                try{store.edit("events",v(r,"id"),Map.of("status","Completed"));store.log(username(),"Completed task: "+v(r,"title"));renderPage();}catch(Exception ex){error(ex);}});
            buttons.add(actions(edit,done,delete),BorderLayout.SOUTH);
        }
        JPanel combined=pane(new GridLayout(1,2,13,0));combined.add(monthPanel);combined.add(section("Task Records","Select dates on the calendar to view events",buttons));
        addBlock(p,combined,514);return p;
    }


}
