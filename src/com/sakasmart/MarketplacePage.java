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

/** SAKA-SMART Marketplace page UI. Uses shared actions, storage and styling from the application context. */
final class MarketplacePage extends PageBase {
    MarketplacePage(Main host) { super(host); }
    JComponent build(){
        JPanel p=stack();List<Map<String,String>> harvested=filter(rows("inventory"),x->"HARVEST".equals(v(x,"kind")));
        List<Map<String,String>> sales=rows("sales");
        double income=sales.stream().mapToDouble(x->Storage.num(x,"quantity")*Storage.num(x,"unit_price")).sum();
        long todaySales=sales.stream().filter(x->today().equals(v(x,"date"))).count();
        long repeat=sales.stream().collect(java.util.stream.Collectors.groupingBy(x->v(x,"buyer"),java.util.stream.Collectors.counting())).values().stream().filter(n->n>1).count();
        addBlock(p,horizontal(stat("Available Products",String.valueOf(harvested.size()),"Harvested stock types",LIGHT,"▣"),
            stat("Orders Today",Long.toString(todaySales),"Recorded sales today",new Color(255,242,224),"▤"),
            stat("Recorded Sales",Storage.money(income),"Completed sales total",new Color(231,247,237),"₱"),
            stat("Repeat Buyers",String.valueOf(repeat),"More than one order",new Color(234,242,252),"♟")),115);
        TableParts items=tableView("inventory",harvested,List.of("id","name","quantity","unit","expiry_date"),"Search harvested products...");
        JPanel sell=pane(new BorderLayout(0,10));
        JButton add=btn("+ Record Sale",GREEN);add.setEnabled(editable("sales"));add.addActionListener(e->saleDialog(harvested));
        sell.add(actions(add),BorderLayout.NORTH);sell.add(items.scroll(),BorderLayout.CENTER);
        addBlock(p,section("Product Listings","These products are read directly from harvested inventory",sell),293);
        List<Map<String,String>> display=new ArrayList<>();
        for(Map<String,String> r:sales){Map<String,String> copy=new LinkedHashMap<>(r);
            Map<String,String> item=rows("inventory").stream().filter(x->v(x,"id").equals(v(r,"item_id"))).findFirst().orElse(Map.of());
            copy.put("product_name",v(item,"name"));copy.put("amount",Storage.money(Storage.num(r,"quantity")*Storage.num(r,"unit_price")));display.add(copy);}
        TableParts table=tableView("sales",display,List.of("id","buyer","product_name","quantity","unit_price","amount","date"),"Search sales...");
        JPanel salesPanel=pane(new BorderLayout(0,8));JButton export=secondary("Export Sales CSV");export.addActionListener(e->exportTable("sales",display));
        salesPanel.add(actions(export),BorderLayout.NORTH);salesPanel.add(table.scroll(),BorderLayout.CENTER);
        addBlock(p,section("Orders / Sales Records","Recording a sale automatically reduces product stock and adds accounting income",salesPanel),342);
        return p;
    }
    private void saleDialog(List<Map<String,String>> harvested){
        List<Map<String,String>> available=filter(harvested,x->Storage.num(x,"quantity")>0);
        if(available.isEmpty()){info("Add harvested products to inventory before recording sales.");return;}
        JComboBox<String> product=new JComboBox<>();
        for(Map<String,String> r:available)product.addItem("#"+v(r,"id")+"  "+v(r,"name")+"  ("+v(r,"quantity")+" "+v(r,"unit")+")");
        JTextField buyer=new JTextField(),qty=new JTextField("1"),price=new JTextField("0"),date=new JTextField(today());
        JPanel fields=pane(new GridLayout(0,1,0,6));
        fields.add(label("Harvested Product",13,true,DARK));fields.add(product);
        for(Object[] e:new Object[][]{{"Buyer",buyer},{"Quantity",qty},{"Unit Price (PHP)",price},{"Sale Date (YYYY-MM-DD)",date}}){
            fields.add(label((String)e[0],13,true,DARK));styleInput((JTextField)e[1]);fields.add((JTextField)e[1]);}
        if(StyledDialogs.showConfirmDialog(frame,fields,"Record Product Sale",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        try {
            if(buyer.getText().isBlank())throw new IllegalArgumentException("Enter buyer name.");LocalDate.parse(date.getText().trim());
            Map<String,String> item=available.get(product.getSelectedIndex());
            store.sell(v(item,"id"),buyer.getText().trim(),Double.parseDouble(qty.getText().trim()),Double.parseDouble(price.getText().trim()),date.getText().trim(),username());
            info("Sale recorded! Product stock and accounting income were updated.");renderPage();
        }catch(Exception e){error(e);}
    }


}
