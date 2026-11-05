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

/** SAKA-SMART Accounting page UI. Uses shared actions, storage and styling from the application context. */
final class AccountingPage extends PageBase {
    AccountingPage(Main host) { super(host); }
    JComponent build(){
        JPanel p=stack();List<Map<String,String>> tx=rows("transactions");
        double income=filter(tx,x->"Income".equals(v(x,"type"))).stream().mapToDouble(x->Storage.num(x,"amount")).sum();
        double expenses=filter(tx,x->"Expense".equals(v(x,"type"))).stream().mapToDouble(x->Storage.num(x,"amount")).sum();
        addBlock(p,horizontal(stat("Total Expenses",Storage.money(expenses),"All recorded expenses",new Color(255,237,233),"₱"),
            stat("Total Income",Storage.money(income),"All recorded income",LIGHT,"₱"),
            stat("Net Balance",Storage.money(income-expenses),"Income minus expenses",new Color(233,245,239),"▥"),
            stat("Transactions",String.valueOf(tx.size()),"Total accounting entries",new Color(252,244,224),"▤")),115);
        Map<String,Double> byType=new LinkedHashMap<>();byType.put("Income",income);byType.put("Expenses",expenses);
        Map<String,Double> byCategory=new LinkedHashMap<>();
        for(Map<String,String> r:tx)byCategory.merge(v(r,"category"),Storage.num(r,"amount"),Double::sum);
        addBlock(p,horizontal(section("Income vs Expenses","All recorded transactions",new ChartPanel(byType,"₱")),
            section("Transaction Categories","All amount categories",new ChartPanel(byCategory,"₱"))),267);
        TableParts table=tableView("transactions",tx,List.of("id","date","type","category","amount","notes","sale_id"),"Search transaction records...");
        addBlock(p,section("Accounting & Financial Records","Add income/expenses or export to CSV",tableAndActions("transactions",table,Map.of("type","Expense","date",today(),"amount","0","farm_id","1"))),382);
        return p;
    }


}
