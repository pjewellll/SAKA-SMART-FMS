package com.sakasmart;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;

/** Full-sized farm owner order management page. Uses the existing PostgreSQL commerce service. */
final class FarmOrdersPage extends JPanel {
    private static final Color INK=new Color(7,75,41), GREEN=new Color(0,111,47),
        BORDER=new Color(143,201,151), PALE=new Color(241,251,237);
    private final JFrame window;
    private final String username;
    private final JLabel count=new JLabel("Loading orders...");
    private final DefaultTableModel model=new DefaultTableModel(
        new String[]{"Order #","Buyer","Status","Fulfillment","Total","Placed"},0){
        @Override public boolean isCellEditable(int r,int c){return false;}
    };
    private final JTable table=new JTable(model);
    private final JButton track=button("Track Order"), details=button("View Details"),
        status=button("Update Status"), payment=button("Confirm Payment"), refresh=button("Refresh");
    private List<CommerceService.Order> orders=java.util.Collections.emptyList();

    FarmOrdersPage(JFrame window,String username){
        super(new BorderLayout(0,16));
        this.window=window;this.username=username;
        setOpaque(false);
        setBorder(new EmptyBorder(4,2,6,2));
        JLabel heading=new JLabel("Customer Orders");heading.setFont(font(25,true));heading.setForeground(INK);
        JLabel subtitle=new JLabel("Manage incoming orders, tracking, and payments for your farm.");
        subtitle.setFont(font(14,false));subtitle.setForeground(new Color(73,113,81));
        JPanel headingText=new JPanel(new GridLayout(2,1,0,4));headingText.setOpaque(false);
        headingText.add(heading);headingText.add(subtitle);
        add(headingText,BorderLayout.NORTH);

        JPanel content=new RoundedSurface(new BorderLayout(0,14));
        content.setBorder(new EmptyBorder(20,20,20,20));
        count.setFont(font(14,true));count.setForeground(INK);
        content.add(count,BorderLayout.NORTH);

        table.setFont(font(14,false));table.setRowHeight(42);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setGridColor(BORDER);table.setShowGrid(true);table.setIntercellSpacing(new Dimension(1,1));
        table.setSelectionBackground(new Color(205,241,204));table.setSelectionForeground(INK);
        table.setFillsViewportHeight(true);
        JTableHeader th=table.getTableHeader();
        th.setPreferredSize(new Dimension(100,46));th.setReorderingAllowed(false);
        th.setDefaultRenderer(new DefaultTableCellRenderer(){
            @Override public Component getTableCellRendererComponent(JTable t,Object value,boolean sel,boolean focus,int row,int col){
                JLabel l=(JLabel)super.getTableCellRendererComponent(t,value,sel,focus,row,col);
                l.setHorizontalAlignment(SwingConstants.CENTER);l.setFont(font(14,true));
                l.setBackground(GREEN);l.setForeground(Color.WHITE);l.setOpaque(true);
                l.setBorder(BorderFactory.createMatteBorder(0,0,1,1,new Color(126,183,137)));return l;
            }
        });
        DefaultTableCellRenderer cellRenderer=new DefaultTableCellRenderer(){
            @Override public Component getTableCellRendererComponent(JTable t,Object value,boolean selected,boolean focus,int row,int col){
                JLabel l=(JLabel)super.getTableCellRendererComponent(t,value,selected,focus,row,col);
                String raw=String.valueOf(value==null?"":value);
                l.setText(col==5&&raw.length()>16?raw.substring(0,16):raw);
                l.setToolTipText(raw);l.setFont(font(14,col==2||col==4));
                l.setForeground(col==2&&(raw.equals("REJECTED")||raw.equals("CANCELLED"))?new Color(171,60,45):INK);
                l.setBackground(selected?new Color(202,240,200):row%2==0?Color.WHITE:PALE);
                l.setOpaque(true);l.setBorder(new EmptyBorder(8,8,8,8));
                l.setHorizontalAlignment(col==1?SwingConstants.LEFT:SwingConstants.CENTER);
                return l;
            }
        };
        int[] widths={76,172,151,135,115,173};
        for(int i=0;i<6;i++){
            table.getColumnModel().getColumn(i).setCellRenderer(cellRenderer);
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            table.getColumnModel().getColumn(i).setMinWidth(i==0?52:80);
        }
        JScrollPane sc=new JScrollPane(table);
        // Keep the order list inside a predictable viewport so the action bar
        // remains visible on normal laptop/desktop screens. Long lists scroll
        // inside this table instead of pushing the buttons below the window.
        sc.setPreferredSize(new Dimension(1000, 330));
        sc.setMinimumSize(new Dimension(420, 220));
        sc.setBorder(new LineBorder(BORDER,1,true));
        sc.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sc.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        sc.getViewport().setBackground(Color.WHITE);
        sc.getVerticalScrollBar().setUnitIncrement(20);
        content.add(sc,BorderLayout.CENTER);

        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,9,4));actions.setOpaque(false);
        for(JButton b:new JButton[]{track,details,status,payment,refresh})actions.add(b);
        content.add(actions,BorderLayout.SOUTH);
        add(content,BorderLayout.CENTER);

        table.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting())updateActions();});
        track.addActionListener(e->withSelected(o->CommerceUI.trackOrder(window,username,o.id(),true)));
        details.addActionListener(e->withSelected(this::showDetails));
        status.addActionListener(e->withSelected(this::changeStatus));
        payment.addActionListener(e->withSelected(this::confirmPayment));
        refresh.addActionListener(e->load());
        load();
    }
    private static Font font(int size,boolean bold){return new Font("Segoe UI",bold?Font.BOLD:Font.PLAIN,size);}
    private static JButton button(String title){
        JButton b=MarketplaceVisuals.greenButton(title);
        b.setFont(font(14,true));b.setPreferredSize(new Dimension(Math.max(112,title.length()*9+34),44));return b;
    }
    private static final class RoundedSurface extends JPanel {
        RoundedSurface(LayoutManager m){super(m);setOpaque(false);}
        @Override protected void paintComponent(Graphics original){
            Graphics2D g=(Graphics2D)original.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(251,255,250));g.fillRoundRect(1,1,getWidth()-3,getHeight()-3,22,22);
            g.setColor(BORDER);g.setStroke(new BasicStroke(1.5f));g.drawRoundRect(1,1,getWidth()-3,getHeight()-3,22,22);g.dispose();
            super.paintComponent(original);
        }
    }
    private void load(){
        try{
            long selectedId=selectedId();
            orders=new CommerceService().orders(username,true);
            model.setRowCount(0);
            for(CommerceService.Order o:orders)model.addRow(new Object[]{o.id(),o.buyer(),o.status(),o.method(),formatMoney(o.total()),o.placed()});
            count.setText(orders.isEmpty()?"No customer orders yet. New orders will appear here.":
                orders.size()+" order"+(orders.size()==1?"":"s")+"  •  Select an order to view details or update its status.");
            if(selectedId>0){for(int i=0;i<table.getRowCount();i++){
                if(Long.parseLong(String.valueOf(table.getValueAt(i,0)))==selectedId){table.setRowSelectionInterval(i,i);break;}
            }}
            updateActions();
        }catch(Exception ex){
            count.setText("Unable to load orders from PostgreSQL.");
            JOptionPane.showMessageDialog(window,ex.getMessage(),"Customer Orders Error",JOptionPane.ERROR_MESSAGE);
        }
    }
    private static String formatMoney(BigDecimal v){return "₱"+v.setScale(2,java.math.RoundingMode.HALF_UP).toPlainString();}
    private long selectedId(){int row=table.getSelectedRow();return row<0?-1:Long.parseLong(String.valueOf(table.getValueAt(row,0)));}
    private CommerceService.Order selected(){long id=selectedId();for(CommerceService.Order o:orders)if(o.id()==id)return o;return null;}
    private void updateActions(){
        CommerceService.Order o=selected();boolean present=o!=null;
        track.setEnabled(present);details.setEnabled(present);
        status.setEnabled(present&&Set.of("PENDING","ACCEPTED","PREPARING","READY","OUT_FOR_DELIVERY","DELIVERED","PICKED_UP").contains(o.status()));
        payment.setEnabled(present&&"UNPAID".equalsIgnoreCase(o.paymentStatus())&&Set.of("DELIVERED","PICKED_UP","COMPLETED").contains(o.status()));
    }
    private interface OrderAction {void run(CommerceService.Order order) throws Exception;}
    private void withSelected(OrderAction action){
        CommerceService.Order o=selected();if(o==null)return;
        try{action.run(o);}catch(Exception ex){JOptionPane.showMessageDialog(window,ex.getMessage(),"Order Error",JOptionPane.ERROR_MESSAGE);}
    }
    private void showDetails(CommerceService.Order o) throws Exception {
        String details=new CommerceService().details(username,o.id(),true);
        JTextArea info=new JTextArea("Order #"+o.id()+"\nBuyer: "+o.buyer()+"\nStatus: "+o.status()+
            "\nPayment: "+o.paymentStatus()+"\nTotal: "+formatMoney(o.total())+"\nContact: "+o.contact()+
            "\nFulfillment: "+o.method()+"\nAddress: "+o.address()+"\n\nProducts:\n"+details);
        info.setFont(font(14,false));info.setEditable(false);info.setLineWrap(true);info.setWrapStyleWord(true);
        info.setBorder(new EmptyBorder(14,14,14,14));
        JScrollPane scroll=new JScrollPane(info);scroll.setPreferredSize(new Dimension(590,350));
        JOptionPane.showMessageDialog(window,scroll,"Order Details — #"+o.id(),JOptionPane.PLAIN_MESSAGE);
    }
    private void changeStatus(CommerceService.Order o) throws Exception {
        String[] candidates=switch(o.status()){
            case "PENDING" -> new String[]{"ACCEPTED","REJECTED"};
            case "ACCEPTED" -> new String[]{"PREPARING","REJECTED"};
            case "PREPARING" -> new String[]{"READY"};
            case "READY" -> "DELIVERY".equals(o.method())?new String[]{"OUT_FOR_DELIVERY"}:new String[]{"PICKED_UP"};
            case "OUT_FOR_DELIVERY" -> new String[]{"DELIVERED"};
            case "DELIVERED","PICKED_UP" -> new String[]{"COMPLETED"};
            default -> new String[0];
        };
        if(candidates.length==0)return;
        JComboBox<String> choice=new JComboBox<>(candidates);choice.setFont(font(14,true));
        if(JOptionPane.showConfirmDialog(window,choice,"Next Status — Order #"+o.id(),JOptionPane.OK_CANCEL_OPTION,JOptionPane.QUESTION_MESSAGE)!=JOptionPane.OK_OPTION)return;
        String next=(String)choice.getSelectedItem();if(next==null)return;
        if(JOptionPane.showConfirmDialog(window,"Change Order #"+o.id()+" to "+next+"?", "Confirm Status",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        new CommerceService().status(username,o.id(),next,true);load();
    }
    private void confirmPayment(CommerceService.Order o) throws Exception {
        if(JOptionPane.showConfirmDialog(window,"Confirm cash payment was actually received for Order #"+o.id()+"?",
            "Confirm Payment Received",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.YES_OPTION)return;
        new CommerceService().confirmPayment(username,o.id());load();
    }
}
