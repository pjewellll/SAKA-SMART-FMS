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
            stat("Sales Today",Long.toString(todaySales),"Recorded sales today",new Color(255,242,224),"▤"),
            stat("Recorded Sales",Storage.money(income),"Completed sales total",new Color(231,247,237),"₱"),
            stat("Repeat Buyers",String.valueOf(repeat),"More than one order",new Color(234,242,252),"♟")),115);
        TableParts items=tableView("inventory",harvested,List.of("id","name","quantity","unit","expiry_date"),"Search harvested products...");
        styleMarketTable(items.table(),new String[]{"ID","Product Name","Available Stock","Unit","Expiry Date"});
        JPanel sell=pane(new BorderLayout(0,10));
        JButton add=MarketplaceVisuals.greenButton("+ Record Sale");add.setEnabled(editable("sales"));add.addActionListener(e->saleDialog(harvested));
        JButton publicFarm=MarketplaceVisuals.greenButton("Edit Farm Shop");
        JButton listing=MarketplaceVisuals.greenButton("Manage Product Listings");
        boolean owner="FARM_OWNER".equals(role());publicFarm.setEnabled(owner);listing.setEnabled(owner);
        publicFarm.addActionListener(e->MarketSellerEditor.editFarm(host));
        listing.addActionListener(e->MarketSellerEditor.editProduct(host));
        // A full-height, two-card storefront launcher; the former 175px block
        // clipped the action buttons under the section's own title/subtitle.
        JPanel management=pane(new BorderLayout(0,14));
        JPanel manageHeader=pane(new BorderLayout(0,4));
        manageHeader.add(label("YOUR PUBLIC FARM SHOP",18,true,DARK),BorderLayout.NORTH);
        manageHeader.add(label("Choose what buyers can see in the customer marketplace.",14,true,DARK),BorderLayout.SOUTH);
        management.add(manageHeader,BorderLayout.NORTH);
        JPanel manageActions=pane(new GridLayout(1,2,14,0));
        manageActions.add(storefrontActionCard("01  FARM PROFILE",
            "Update your farm details, logo and cover photo",publicFarm));
        manageActions.add(storefrontActionCard("02  PRODUCT LISTINGS",
            "Add product photos, prices and availability",listing));
        manageActions.setPreferredSize(new Dimension(760,166));
        management.add(manageActions,BorderLayout.CENTER);
        // Section title + section subtitle + content + padding must ALL fit.
        addBlock(p,section("Farm Shop Management",
            "Manage your farm profile and products shown to customers",management),325);
        sell.add(actions(add),BorderLayout.NORTH);sell.add(items.scroll(),BorderLayout.CENTER);
        MarketplaceVisuals.scrollbar(items.scroll());
        MarketplaceVisuals.roundedTable(items.scroll());
        addBlock(p,section("Harvested Products & Inventory","Review harvested stock before publishing items to customers",sell),360);
        List<Map<String,String>> display=new ArrayList<>();
        for(Map<String,String> r:sales){Map<String,String> copy=new LinkedHashMap<>(r);
            Map<String,String> item=rows("inventory").stream().filter(x->v(x,"id").equals(v(r,"item_id"))).findFirst().orElse(Map.of());
            copy.put("product_name",v(item,"name"));copy.put("amount",Storage.money(Storage.num(r,"quantity")*Storage.num(r,"unit_price")));display.add(copy);}
        TableParts table=tableView("sales",display,List.of("id","buyer","product_name","quantity","unit_price","amount","date"),"Search sales...");
        styleMarketTable(table.table(),new String[]{"ID","Buyer","Product","Qty","Unit Price","Total","Date"});
        JPanel salesPanel=pane(new BorderLayout(0,8));JButton export=MarketplaceVisuals.greenButton("↓ Export Sales CSV");export.addActionListener(e->exportTable("sales",display));
        MarketplaceVisuals.scrollbar(table.scroll());
        MarketplaceVisuals.roundedTable(table.scroll());
        MarketplaceVisuals.emptySales(table.scroll(),table.table());
        salesPanel.add(actions(export),BorderLayout.NORTH);salesPanel.add(table.scroll(),BorderLayout.CENTER);
        addBlock(p,section("Recorded Sales","Recording a sale automatically reduces product stock and adds accounting income",salesPanel),390);
        if(owner){JPanel orderPanel=pane(new BorderLayout());JButton customerOrders=MarketplaceVisuals.greenButton("View and Process Customer Orders");
            customerOrders.addActionListener(e->CommerceUI.farmOrders(host.frame,username()));
            orderPanel.add(customerOrders,BorderLayout.WEST);
            addBlock(p,section("Customer Orders","Accept, prepare, complete or reject buyer orders for your farm",orderPanel),155);}
        return p;
    }
    private static JPanel storefrontActionCard(String title,String detail,JButton button){
        JPanel card=MarketplaceVisuals.roundedPanel(new BorderLayout(0,0),new Color(236,250,233),24);
        card.setBorder(new EmptyBorder(14,18,16,18));
        JPanel copy=new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy,BoxLayout.Y_AXIS));
        JLabel heading=label(title,15,true,DARK);
        heading.setFont(new Font("Segoe UI",Font.BOLD,15));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel description=label(detail,13,false,new Color(37,88,52));
        description.setFont(new Font("Segoe UI",Font.PLAIN,13));
        description.setAlignmentX(Component.LEFT_ALIGNMENT);
        description.setToolTipText(detail);
        copy.add(heading);
        copy.add(Box.createVerticalStrut(8));
        copy.add(description);
        card.add(copy,BorderLayout.NORTH);
        JPanel actionRow=new JPanel(new BorderLayout());
        actionRow.setOpaque(false);
        actionRow.setBorder(new EmptyBorder(13,0,0,0));
        button.setPreferredSize(new Dimension(220,45));
        actionRow.add(button,BorderLayout.CENTER);
        card.add(actionRow,BorderLayout.SOUTH);
        return card;
    }

    /** Shared table style: aligned columns, calm body text, visible dividers and a compact header. */
    private static void styleMarketTable(JTable table,String[] headings){
        final Font bodyFont=new Font("Segoe UI",Font.PLAIN,15);
        final Font headFont=new Font("Segoe UI",Font.BOLD,15);
        final Color ink=new Color(18,67,39), grid=new Color(179,215,184);
        table.setRowHeight(51);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setShowGrid(true);
        table.setGridColor(grid);
        table.setIntercellSpacing(new Dimension(1,1));
        table.setFont(bodyFont);
        table.setForeground(ink);
        table.setSelectionBackground(new Color(204,238,207));
        table.setSelectionForeground(ink);
        table.setBackground(Color.WHITE);
        JTableHeader header=table.getTableHeader();
        if(header!=null){
            header.setPreferredSize(new Dimension(header.getPreferredSize().width,45));
            header.setReorderingAllowed(false);
            header.setFont(headFont);
            header.setBackground(new Color(8,112,53));
            header.setForeground(Color.WHITE);
            header.setDefaultRenderer(new DefaultTableCellRenderer(){
                @Override public Component getTableCellRendererComponent(JTable t,Object val,boolean selected,boolean focus,int row,int col){
                    super.getTableCellRendererComponent(t,val,selected,focus,row,col);
                    setOpaque(true);
                    setBackground(new Color(8,112,53));
                    setForeground(Color.WHITE);
                    setFont(headFont);
                    setHorizontalAlignment(SwingConstants.CENTER);
                    setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0,col==0?0:1,1,0,new Color(133,193,146)),
                        new EmptyBorder(7,8,7,8)));
                    return this;
                }
            });
        }
        for(int c=0;c<table.getColumnCount();c++){
            final String title=c<headings.length?headings[c]:table.getColumnName(c);
            table.getColumnModel().getColumn(c).setHeaderValue(title);
            final int alignment=(c==0||"Qty".equals(title)||"Unit".equals(title)||"Expiry Date".equals(title)||"Date".equals(title))
                ?SwingConstants.CENTER:SwingConstants.LEFT;
            table.getColumnModel().getColumn(c).setCellRenderer(new DefaultTableCellRenderer(){
                @Override public Component getTableCellRendererComponent(JTable t,Object value,boolean selected,boolean focus,int row,int column){
                    super.getTableCellRendererComponent(t,value,selected,focus,row,column);
                    setOpaque(true);
                    setFont(bodyFont);
                    setForeground(ink);
                    setHorizontalAlignment(alignment);
                    setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0,0,1,1,grid),
                        new EmptyBorder(0,12,0,12)));
                    if(!selected)setBackground(row%2==0?Color.WHITE:new Color(241,250,241));
                    setToolTipText(value==null?null:value.toString());
                    return this;
                }
            });
        }
        if(header!=null)header.repaint();
    }

    private void saleDialog(List<Map<String,String>> harvested){
        List<Map<String,String>> available=filter(harvested,x->Storage.num(x,"quantity")>0);
        if(available.isEmpty()){
            MarketplaceVisuals.notice(frame,"No Stock Available","Add harvested products with available stock to Inventory before recording a sale.",JOptionPane.WARNING_MESSAGE);
            return;
        }
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
            MarketplaceVisuals.notice(frame,"Sale Recorded Successfully","The transaction was saved. Product stock was reduced and accounting income was updated.",JOptionPane.INFORMATION_MESSAGE);renderPage();
        }catch(Exception e){error(e);}
    }


}
