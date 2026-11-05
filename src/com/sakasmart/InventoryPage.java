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

/** SAKA-SMART Inventory page UI. Uses shared actions, storage and styling from the application context. */
final class InventoryPage extends PageBase {
    InventoryPage(Main host) { super(host); }
    JComponent build(){
        JPanel p=stack();List<Map<String,String>> data=rows("inventory");
        long low=data.stream().filter(x->Storage.num(x,"quantity")<=Storage.num(x,"min_stock")).count();
        long exp=data.stream().filter(Main::isExpiring).count();
        addBlock(p,horizontal(stat("Inventory Items",String.valueOf(data.size()),"All farm inventory",LIGHT,"▣"),
            stat("Low Stock Items",String.valueOf(low),"Below reorder limit",new Color(255,239,222),"!"),
            stat("Expiring Within 30 Days",String.valueOf(exp),"Including expired items",new Color(253,236,233),"◷"),
            stat("Harvested Products",String.valueOf(filter(data,x->"HARVEST".equals(v(x,"kind"))).size()),"Available stock types",new Color(231,247,234),"❧")),112);
        JButton inputs=btn("Farm Inputs / Supplies",host.inventoryHarvest?new Color(112,140,122):GREEN), harvest=btn("Harvested Products",host.inventoryHarvest?GREEN:new Color(112,140,122));
        inputs.addActionListener(e->{host.inventoryHarvest=false;renderPage();});harvest.addActionListener(e->{host.inventoryHarvest=true;renderPage();});
        addBlock(p,section("Inventory Categories","Switch between supplies and stored farm products",actions(inputs,harvest)),143);
        List<Map<String,String>> selected=filter(data,x->(host.inventoryHarvest?"HARVEST":"SUPPLY").equals(v(x,"kind")));
        addBlock(p,section(host.inventoryHarvest?"Harvested Products":"Farm Inputs / Supplies","Add, search, adjust and monitor stock",inventoryTable(selected)),485);
        return p;
    }
    private JComponent inventoryTable(List<Map<String,String>> data){
        JPanel block=pane(new BorderLayout(0,10));
        TableParts table=tableView("inventory",data,List.of("id","name","category","quantity","unit","supplier","expiry_date","min_stock"),"Search items or supplier...");
        JPanel bar=pane(new FlowLayout(FlowLayout.RIGHT,8,0));
        if(editable("inventory")){
            JButton add=btn("+ Add Item",GREEN),edit=secondary("Edit"),move=secondary("Stock In / Out"),delete=btn("Delete",RED);
            add.addActionListener(e->form("inventory",null,Map.of("kind",host.inventoryHarvest?"HARVEST":"SUPPLY","quantity","0","min_stock","0","farm_id","1"),Set.of("kind")));
            edit.addActionListener(e->selectedEdit("inventory",table));
            move.addActionListener(e->stockMove(table));delete.addActionListener(e->selectedDelete("inventory",table));
            bar.add(add);bar.add(edit);bar.add(move);bar.add(delete);
        }
        JButton export=secondary("Export CSV");export.addActionListener(e->exportTable("inventory",data));bar.add(export);
        block.add(bar,BorderLayout.NORTH);block.add(table.scroll(),BorderLayout.CENTER);return block;
    }
    private void stockMove(TableParts part){
        Map<String,String> selected=part.selected();if(selected==null){info("Select an inventory row first.");return;}
        String value=StyledDialogs.showInputDialog(frame,"Enter stock change for "+v(selected,"name")+"\nExamples: 20 = Stock In, -5 = Stock Out","0");
        if(value==null)return;
        try {
            double delta=Double.parseDouble(value.trim());double quantity=Storage.num(selected,"quantity")+delta;
            if(quantity<0)throw new IllegalArgumentException("Stock cannot be negative.");
            store.edit("inventory",v(selected,"id"),Map.of("quantity",Double.toString(quantity)));
            store.log(username(),"Stock change "+delta+" for "+v(selected,"name"));renderPage();
        }catch(Exception e){error(e);}
    }


}
