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

/** SAKA-SMART Learning page UI. Uses shared actions, storage and styling from the application context. */
final class LearningPage extends PageBase {
    LearningPage(Main host) { super(host); }
    JComponent build(){
        JPanel p=stack();List<Map<String,String>> articles=rows("articles");
        addBlock(p,horizontal(stat("Learning Articles",String.valueOf(articles.size()),"Available guides",LIGHT,"▧"),
            stat("Crop Guides",String.valueOf(filter(articles,x->"Crops".equals(v(x,"category"))).size()),"Growing techniques",new Color(240,250,228),"❧"),
            stat("Livestock Guides",String.valueOf(filter(articles,x->"Livestock".equals(v(x,"category"))).size()),"Animal welfare",new Color(254,246,227),"♞")),115);
        JTextField search=new JTextField();styleInput(search);
        DefaultListModel<String> titles=new DefaultListModel<>();JList<String> list=new JList<>(titles);list.setFont(FONT);list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);list.setFixedCellHeight(35);
        JTextPane reader=new JTextPane();reader.setContentType("text/html");reader.setEditable(false);reader.setBackground(Color.WHITE);
        java.util.List<Map<String,String>> filtered=new ArrayList<>(articles);
        Runnable update=()->{
            filtered.clear();titles.clear();String q=search.getText().toLowerCase(Locale.ROOT);
            for(Map<String,String> r:articles)if((v(r,"title")+" "+v(r,"category")+" "+v(r,"content")).toLowerCase(Locale.ROOT).contains(q)){
                filtered.add(r);titles.addElement(v(r,"title")+"   ["+v(r,"category")+"]");
            }
            if(!filtered.isEmpty())list.setSelectedIndex(0);else reader.setText("<html><body style='font-family:sans-serif'>No matching guides.</body></html>");
        };
        list.addListSelectionListener(e->{int i=list.getSelectedIndex();if(i<0||i>=filtered.size())return;Map<String,String> r=filtered.get(i);
            reader.setText("<html><body style='font-family:Segoe UI,sans-serif;padding:15px;color:#153d2d'>"+
                "<h1>"+html(v(r,"title"))+"</h1><p><b>"+html(v(r,"category"))+"</b> · "+html(v(r,"date"))+"</p>"+
                "<p style='font-size:15px;line-height:1.8'>"+html(v(r,"content")).replace("\n","<br>")+"</p></body></html>");reader.setCaretPosition(0);
        });
        watch(search,update);update.run();
        JPanel left=pane(new BorderLayout(0,10));left.add(search,BorderLayout.NORTH);left.add(new JScrollPane(list),BorderLayout.CENTER);
        JPanel buttons=pane(new FlowLayout(FlowLayout.LEFT,7,0));
        if(admin()){
            JButton add=btn("+ Article",GREEN),edit=secondary("Edit"),del=btn("Delete",RED);
            add.addActionListener(e->form("articles",null,Map.of("category","Crops","date",today()),Set.of()));
            edit.addActionListener(e->{int i=list.getSelectedIndex();if(i>=0)form("articles",filtered.get(i),Map.of(),Set.of());else info("Choose an article.");});
            del.addActionListener(e->{int i=list.getSelectedIndex();if(i<0){info("Choose an article.");return;}
                deleteRow("articles",filtered.get(i));});
            buttons.add(add);buttons.add(edit);buttons.add(del);
        }
        left.add(buttons,BorderLayout.SOUTH);
        JPanel content=pane(new GridLayout(1,2,12,0));content.add(section("Explore Learning Resources","Search by topic, crop or keyword",left));
        content.add(section("Article Reader","Farming basics and seasonal guidance",new JScrollPane(reader)));
        addBlock(p,content,560);
        addBlock(p,blank("Learning Center","Content is informational. Always follow locally applicable agricultural and veterinary guidance."),86);
        return p;
    }
    private static String html(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}


}
