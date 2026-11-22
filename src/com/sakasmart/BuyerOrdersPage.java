package com.sakasmart;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.math.*;
import java.util.*;
import java.util.List;

/** Embedded PostgreSQL customer orders list (tracking/detail popups only). */
final class BuyerOrdersPage extends JPanel {
    private static final Color INK=new Color(7,75,41), GREEN=new Color(0,111,47), LINE=new Color(143,201,151), PALE=new Color(242,251,236);
    private final JFrame parent; private final String buyer;
    private final JLabel count=new JLabel("Loading orders...");
    private final DefaultTableModel model=new DefaultTableModel(new String[]{"Order #","Farm","Status","Fulfillment","Total","Placed"},0){
        @Override public boolean isCellEditable(int row,int col){return false;}
    };
    private final JTable table=new JTable(model);
    private final JButton track=button("Track Order"),detail=button("View Details"),cancel=button("Cancel Pending Order"),refresh=button("Refresh");
    private List<CommerceService.Order> orders=List.of();

    BuyerOrdersPage(JFrame parent,String buyer){
        super(new BorderLayout(0,16));this.parent=parent;this.buyer=buyer;setOpaque(false);
        setBorder(new EmptyBorder(4,2,6,2));
        JLabel title=new JLabel("My Orders");title.setFont(font(25,true));title.setForeground(INK);
        JLabel subtitle=new JLabel("Track your purchases and review order details from local farms.");
        subtitle.setFont(font(14,false));subtitle.setForeground(new Color(73,113,81));
        JPanel top=new JPanel(new GridLayout(2,1,0,3));top.setOpaque(false);top.add(title);top.add(subtitle);add(top,BorderLayout.NORTH);
        JPanel surface=MarketplaceVisuals.roundedPanel(new BorderLayout(0,14),Color.WHITE,22);
        surface.setBorder(new EmptyBorder(20,20,20,20));
        count.setFont(font(14,true));count.setForeground(INK);surface.add(count,BorderLayout.NORTH);
        table.setFont(font(14,false));table.setRowHeight(50);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setShowGrid(true);table.setGridColor(LINE);table.setIntercellSpacing(new Dimension(1,1));
        table.setSelectionBackground(new Color(205,241,204));table.setSelectionForeground(INK);table.setFillsViewportHeight(true);
        JTableHeader th=table.getTableHeader();th.setPreferredSize(new Dimension(100,46));th.setReorderingAllowed(false);
        th.setDefaultRenderer(new DefaultTableCellRenderer(){
            @Override public Component getTableCellRendererComponent(JTable t,Object value,boolean selected,boolean focus,int row,int col){
                JLabel l=(JLabel)super.getTableCellRendererComponent(t,value,selected,focus,row,col);
                l.setHorizontalAlignment(SwingConstants.CENTER);l.setFont(font(14,true));l.setBackground(GREEN);
                l.setForeground(Color.WHITE);l.setOpaque(true);l.setBorder(BorderFactory.createMatteBorder(0,0,1,1,LINE));return l;
            }
        });
        DefaultTableCellRenderer body=new DefaultTableCellRenderer(){
            @Override public Component getTableCellRendererComponent(JTable t,Object value,boolean selected,boolean focus,int row,int col){
                JLabel l=(JLabel)super.getTableCellRendererComponent(t,value,selected,focus,row,col);
                String raw=String.valueOf(value==null?"":value);
                l.setText(col==5&&raw.length()>16?raw.substring(0,16):raw);l.setToolTipText(raw);
                l.setFont(font(14,col==2||col==4));
                l.setForeground(col==2&&(raw.equals("REJECTED")||raw.equals("CANCELLED"))?new Color(175,59,44):INK);
                l.setBackground(selected?new Color(202,240,200):row%2==0?Color.WHITE:PALE);l.setOpaque(true);
                l.setHorizontalAlignment(col==1?SwingConstants.LEFT:SwingConstants.CENTER);
                l.setBorder(new EmptyBorder(8,9,8,9));return l;
            }
        };
        int[] width={75,180,155,132,110,160};for(int i=0;i<6;i++){
            table.getColumnModel().getColumn(i).setCellRenderer(body);table.getColumnModel().getColumn(i).setPreferredWidth(width[i]);
        }
        JScrollPane scroll=new JScrollPane(table);scroll.setBorder(new LineBorder(LINE,1,true));
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(22);surface.add(scroll,BorderLayout.CENTER);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,9,4));actions.setOpaque(false);
        for(JButton b:new JButton[]{track,detail,cancel,refresh})actions.add(b);surface.add(actions,BorderLayout.SOUTH);
        add(surface,BorderLayout.CENTER);
        table.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting())updateActions();});
        track.addActionListener(e->withSelected(o->CommerceUI.trackOrder(parent,buyer,o.id(),false)));
        detail.addActionListener(e->withSelected(this::showDetails));cancel.addActionListener(e->withSelected(this::cancelOrder));
        refresh.addActionListener(e->reload());reload();
    }
    private static Font font(int s,boolean b){return new Font("Segoe UI",b?Font.BOLD:Font.PLAIN,s);}
    private static JButton button(String s){JButton b=MarketplaceVisuals.greenButton(s);b.setFont(font(14,true));
        b.setPreferredSize(new Dimension(Math.max(105,s.length()*9+28),44));return b;}
    private void reload(){try{
        long current=selectedId();orders=new CommerceService().orders(buyer,false);model.setRowCount(0);
        for(CommerceService.Order o:orders)model.addRow(new Object[]{o.id(),o.farm(),o.status(),o.method(),pesos(o.total()),o.placed()});
        count.setText(orders.isEmpty()?"No purchases yet. Browse fresh products to place an order.":orders.size()+" order(s)  •  Select an order to track or view details.");
        if(current>0)for(int i=0;i<table.getRowCount();i++)if(Long.parseLong(String.valueOf(table.getValueAt(i,0)))==current){table.setRowSelectionInterval(i,i);break;}
        updateActions();
    }catch(Exception e){count.setText("Unable to load orders.");error(e);}}
    private static String pesos(BigDecimal n){return "₱"+n.setScale(2,RoundingMode.HALF_UP).toPlainString();}
    private long selectedId(){int row=table.getSelectedRow();return row<0?-1:Long.parseLong(String.valueOf(table.getValueAt(row,0)));}
    private CommerceService.Order selected(){long id=selectedId();for(CommerceService.Order o:orders)if(o.id()==id)return o;return null;}
    private void updateActions(){CommerceService.Order o=selected();track.setEnabled(o!=null);detail.setEnabled(o!=null);
        cancel.setEnabled(o!=null&&"PENDING".equals(o.status()));}
    private interface Action{void apply(CommerceService.Order o) throws Exception;}
    private void withSelected(Action action){CommerceService.Order o=selected();if(o==null)return;
        try{action.apply(o);}catch(Exception e){error(e);}}
    private void showDetails(CommerceService.Order o) throws Exception{
        String products=new CommerceService().details(buyer,o.id(),false);
        JTextArea t=new JTextArea("Order #"+o.id()+"\nFarm: "+o.farm()+"\nStatus: "+o.status()+"\nPayment: "+o.paymentStatus()+
          "\nTotal: "+pesos(o.total())+"\nContact: "+o.contact()+"\nFulfillment: "+o.method()+"\nAddress: "+o.address()+"\n\nProducts:\n"+products);
        t.setFont(font(14,false));t.setEditable(false);t.setLineWrap(true);t.setWrapStyleWord(true);
        t.setBorder(new EmptyBorder(14,14,14,14));JScrollPane s=new JScrollPane(t);s.setPreferredSize(new Dimension(570,350));
        JOptionPane.showMessageDialog(parent,s,"Order Details — #"+o.id(),JOptionPane.PLAIN_MESSAGE);
    }
    private void cancelOrder(CommerceService.Order o) throws Exception{
        if(!"PENDING".equals(o.status()))return;
        if(JOptionPane.showConfirmDialog(parent,"Cancel pending Order #"+o.id()+"?","Confirm Cancellation",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.YES_OPTION)return;
        new CommerceService().status(buyer,o.id(),"CANCELLED",false);reload();
    }
    private void error(Exception e){JOptionPane.showMessageDialog(parent,e.getMessage(),"My Orders Error",JOptionPane.ERROR_MESSAGE);}
}
