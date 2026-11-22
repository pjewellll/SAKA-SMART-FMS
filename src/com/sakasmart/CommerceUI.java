package com.sakasmart;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.border.LineBorder;
import java.awt.geom.RoundRectangle2D;
import java.awt.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;

/** Buyer cart/checkout and farm-owner order management, using CommerceService transactions. */
final class CommerceUI {
    private CommerceUI(){}
    private static final Color INK=new Color(8,74,39), SOFT=new Color(240,251,238),
        LINE=new Color(151,209,159), WHITE=new Color(253,255,252), MINT=new Color(226,246,224),
        GREEN=new Color(0,119,49), MUTED=new Color(52,102,66);
    private static Font font(int size,boolean bold){return new Font("Segoe UI",bold?Font.BOLD:Font.PLAIN,size);}
    private static JLabel label(String s){JLabel l=new JLabel(s);l.setForeground(INK);l.setFont(font(14,true));return l;}
    private static JButton button(String s){JButton b=MarketplaceVisuals.greenButton(s);b.setFont(font(14,true));b.setPreferredSize(new Dimension(Math.max(110,s.length()*9+36),43));return b;}
    private static JButton secondary(String title){
        JButton b=new JButton(title);b.setFont(font(14,true));b.setForeground(INK);
        b.setBackground(new Color(218,245,219));b.setOpaque(true);b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createCompoundBorder(new LineBorder(new Color(111,184,129),1,true),new EmptyBorder(10,20,10,20)));
        return b;
    }
    private static JPanel panel(LayoutManager l){JPanel p=MarketplaceVisuals.roundedPanel(l,WHITE,22);p.setBorder(new EmptyBorder(16,18,16,18));return p;}
    private static JPanel transparent(LayoutManager l){JPanel p=new JPanel(l);p.setOpaque(false);return p;}
    private static JTextField field(String value){
        JTextField f=new JTextField(value,28);f.setFont(font(15,false));f.setForeground(INK);f.setBackground(WHITE);
        f.setPreferredSize(new Dimension(280,43));f.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(127,189,142),1,true),new EmptyBorder(8,12,8,12)));return f;
    }
    /** Prevent non-numeric phone characters and more than eleven digits even on paste. */
    private static void restrictPhilippineMobile(JTextField phone){
        phone.setToolTipText("Use an 11-digit Philippine mobile number starting with 09, e.g. 09123456789.");
        ((AbstractDocument)phone.getDocument()).setDocumentFilter(new DocumentFilter(){
            @Override public void insertString(FilterBypass fb,int offset,String text,AttributeSet attrs) throws BadLocationException {
                replace(fb,offset,0,text,attrs);
            }
            @Override public void replace(FilterBypass fb,int offset,int length,String text,AttributeSet attrs) throws BadLocationException {
                if(text==null)text="";
                String previous=fb.getDocument().getText(0,fb.getDocument().getLength());
                String next=previous.substring(0,offset)+text+previous.substring(offset+length);
                if(next.length()<=11 && next.matches("[0-9]*"))super.replace(fb,offset,length,text,attrs);
            }
        });
    }
    private static boolean validPhilippineMobile(String phone){return phone!=null && phone.matches("09[0-9]{9}");}
    private static JTextArea area(String initial,int rows){
        JTextArea a=new JTextArea(initial,rows,30);a.setFont(font(14,false));a.setForeground(INK);
        a.setLineWrap(true);a.setWrapStyleWord(true);a.setBackground(WHITE);
        a.setBorder(new EmptyBorder(10,12,10,12));return a;
    }
    private static JPanel formRow(String heading,String help,JComponent control){
        JPanel row=transparent(new BorderLayout(0,5));
        JLabel title=label(heading);title.setFont(font(14,true));row.add(title,BorderLayout.NORTH);
        JPanel center=transparent(new BorderLayout(0,5));
        if(help!=null&&!help.isBlank()){
            JLabel hint=new JLabel(help);hint.setForeground(MUTED);hint.setFont(font(12,false));center.add(hint,BorderLayout.NORTH);
        }
        center.add(control,BorderLayout.CENTER);row.add(center,BorderLayout.CENTER);return row;
    }
    private static JPanel stack(){JPanel p=transparent(null);p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));return p;}
    private static void stackAdd(JPanel p,JComponent c){c.setAlignmentX(Component.LEFT_ALIGNMENT);c.setMaximumSize(new Dimension(Integer.MAX_VALUE,c.getPreferredSize().height));p.add(c);p.add(Box.createVerticalStrut(13));}
    private static String money(BigDecimal n){return "₱"+n.setScale(2,java.math.RoundingMode.HALF_UP).toPlainString();}
    private static CommerceService service(){try{return new CommerceService();}catch(Exception e){throw new IllegalStateException("PostgreSQL marketplace could not initialize: "+e.getMessage(),e);}}
    private static final class VectorIcon extends JComponent {
        private final int type;
        VectorIcon(int type){this.type=type;setPreferredSize(new Dimension(44,44));}
        protected void paintComponent(Graphics original){
            Graphics2D g=(Graphics2D)original.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            Color tone=type==2?new Color(204,65,69):type==1?new Color(214,151,30):new Color(0,145,59);
            g.setPaint(new GradientPaint(0,0,tone.brighter(),0,42,tone.darker()));g.fillOval(1,1,41,41);
            g.setColor(Color.WHITE);g.setStroke(new BasicStroke(3.8f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            if(type==2){g.drawLine(14,14,30,30);g.drawLine(30,14,14,30);}
            else if(type==1){g.drawLine(22,10,22,25);g.fillOval(20,31,4,4);}
            else {g.drawLine(11,22,19,30);g.drawLine(19,30,33,14);}g.dispose();
        }
    }
    private static JPanel header(String title,String subtitle,int type){
        JPanel bar=transparent(new BorderLayout(15,0));bar.setBorder(new EmptyBorder(4,4,10,4));
        bar.add(new VectorIcon(type),BorderLayout.WEST);
        JPanel text=transparent(new GridLayout(0,1,0,4));
        JLabel h=label(title);h.setFont(font(22,true));text.add(h);
        if(subtitle!=null&&!subtitle.isBlank()){
            JLabel sub=new JLabel(subtitle);sub.setFont(font(12,false));sub.setForeground(MUTED);text.add(sub);
        }
        bar.add(text,BorderLayout.CENTER);return bar;
    }
    private static final class DialogSurface extends JPanel {
        DialogSurface(LayoutManager layout){super(layout);setOpaque(false);setBorder(new EmptyBorder(20,24,20,24));}
        protected void paintComponent(Graphics original){
            Graphics2D g=(Graphics2D)original.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth()-2,h=getHeight()-2;
            g.setPaint(new GradientPaint(0,0,WHITE,0,Math.max(1,h),new Color(220,246,220)));
            g.fill(new RoundRectangle2D.Float(1,1,w,h,27,27));
            g.setColor(LINE);g.setStroke(new BasicStroke(1.7f));g.draw(new RoundRectangle2D.Float(1,1,w,h,27,27));
            g.dispose();super.paintComponent(original);
        }
    }
    private static JSpinner spinner(int initial){
        JSpinner sp=new JSpinner(new SpinnerNumberModel(initial,1,100000,1));sp.setFont(font(16,true));
        sp.setPreferredSize(new Dimension(122,43));
        JComponent editor=sp.getEditor();if(editor instanceof JSpinner.DefaultEditor d){
            d.getTextField().setHorizontalAlignment(SwingConstants.CENTER);
            d.getTextField().setFont(font(17,true));d.getTextField().setForeground(INK);
        }return sp;
    }
    /** Reusable glossy modal. No new dependencies, no persistence changes. */
    private static boolean confirm(Component parent,JComponent content,String title){
        return confirm(parent,content,title,null);
    }
    private static boolean confirm(Component parent,JComponent content,String title,java.util.function.BooleanSupplier validator){
        Window owner=SwingUtilities.getWindowAncestor(parent);
        JDialog dialog=new JDialog(owner,title,Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel shell=new DialogSurface(new BorderLayout(0,12));
        shell.add(header(title,"SAKA-SMART  /  SECURE MARKETPLACE",title.matches("(?i).*(cancel|reject).*")?1:0),BorderLayout.NORTH);
        shell.add(content,BorderLayout.CENTER);
        JPanel actions=transparent(new FlowLayout(FlowLayout.RIGHT,10,0));
        JButton cancel=secondary("Cancel"),okay=button("Continue");
        final boolean[] approved={false};
        cancel.addActionListener(e->dialog.dispose());
        okay.addActionListener(e->{if(validator!=null&&!validator.getAsBoolean())return;approved[0]=true;dialog.dispose();});
        actions.add(cancel);actions.add(okay);shell.add(actions,BorderLayout.SOUTH);
        dialog.setContentPane(shell);dialog.pack();
        Dimension screen=Toolkit.getDefaultToolkit().getScreenSize();
        dialog.setSize(Math.min(Math.max(540,dialog.getWidth()),Math.min(930,screen.width-60)),
            Math.min(Math.max(285,dialog.getHeight()),Math.min(790,screen.height-70)));
        dialog.setLocationRelativeTo(parent);dialog.getRootPane().setDefaultButton(okay);
        dialog.getRootPane().registerKeyboardAction(e->dialog.dispose(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setVisible(true);return approved[0];
    }
    private static void failure(Component parent,Exception e){MarketplaceVisuals.notice(parent,"Marketplace Error",e.getMessage()==null?"An unexpected error occurred.":e.getMessage(),JOptionPane.ERROR_MESSAGE);}
    private static void message(Component parent,String msg){MarketplaceVisuals.notice(parent,"SAKA-SMART",msg,JOptionPane.INFORMATION_MESSAGE);}
    static void add(JFrame parent,String username,Map<String,String> item,boolean immediate){
        try{
            CommerceService db=service();JSpinner qty=spinner(1);
            JPanel form=panel(new BorderLayout(0,14));
            JLabel product=label(item.getOrDefault("name","Product"));product.setFont(font(19,true));
            JLabel price=label("Price: "+money(new BigDecimal(item.get("market_price")))+" / "+item.getOrDefault("unit","unit"));
            price.setForeground(new Color(204,76,40));price.setFont(font(16,true));
            JPanel name=transparent(new GridLayout(2,1,0,7));name.add(product);name.add(price);
            form.add(name,BorderLayout.NORTH);
            form.add(formRow(immediate?"Quantity to buy":"Quantity to add to cart",
                "Select the number of units you need.",qty),BorderLayout.CENTER);
            if(!confirm(parent,form,immediate?"Buy Now":"Add to Cart"))return;
            long id=Long.parseLong(item.get("id"));int count=((Number)qty.getValue()).intValue();
            if(immediate)checkout(parent,username,List.of(id),Map.of(id,count));
            else {db.add(username,id,count);message(parent,"Added to My Cart! Open My Cart to review your products or continue shopping.");}
        }catch(Exception e){failure(parent,e);}
    }
    /** One persistent cart per buyer. Quantity updates are saved only on Checkout Selected
     * or via explicit Remove; the cart is never cleared merely by closing the dialog. */
    static void cart(JFrame parent,String buyer){
        try{
            CommerceService db=service();List<CommerceService.CartLine> lines=db.cart(buyer);
            if(lines.isEmpty()){message(parent,"Your cart is empty. Browse farms to add fresh products.");return;}
            JPanel body=new JPanel();body.setLayout(new BoxLayout(body,BoxLayout.Y_AXIS));body.setOpaque(false);
            Map<Long,JCheckBox> selections=new LinkedHashMap<>();
            Map<Long,JSpinner> quantities=new LinkedHashMap<>();
            for(CommerceService.CartLine line:lines){
                JPanel row=panel(new BorderLayout(8,8));
                row.setMaximumSize(new Dimension(760,104));
                JCheckBox include=new JCheckBox(line.product(),true);
                include.setFont(font(16,true));include.setForeground(INK);include.setOpaque(false);
                selections.put(line.itemId(),include);
                JPanel left=new JPanel(new GridLayout(2,1,2,2));left.setOpaque(false);
                left.add(include);
                JLabel cost=label(money(line.price())+" / "+line.unit()+"  •  "+line.available().stripTrailingZeros().toPlainString()+" available");
                cost.setFont(font(12,false));left.add(cost);
                row.add(left,BorderLayout.CENTER);
                JPanel right=new JPanel(new FlowLayout(FlowLayout.RIGHT,6,7));right.setOpaque(false);
                SpinnerNumberModel countModel=new SpinnerNumberModel(line.quantity(),1,100000,1);
                JSpinner qty=new JSpinner(countModel);qty.setPreferredSize(new Dimension(66,34));qty.setFont(font(14,true));
                qty.setToolTipText("Quantity");quantities.put(line.itemId(),qty);
                JButton remove=button("Remove");remove.setPreferredSize(new Dimension(90,36));
                remove.addActionListener(e->{
                    JPanel prompt=panel(new BorderLayout());prompt.add(label("Remove "+line.product()+" from your cart?"),BorderLayout.CENTER);
                    if(!confirm(parent,prompt,"Remove Product"))return;
                    try{db.change(buyer,line.itemId(),0);
                        Window active=SwingUtilities.getWindowAncestor(remove);
                        if(active!=null)active.dispose();
                        SwingUtilities.invokeLater(()->cart(parent,buyer));}
                    catch(Exception ex){failure(parent,ex);}
                });
                right.add(qty);right.add(remove);row.add(right,BorderLayout.EAST);
                body.add(row);body.add(Box.createVerticalStrut(9));
            }
            JPanel pane=panel(new BorderLayout(0,10));
            JLabel title=label("MY SHOPPING CART  •  "+lines.size()+" product(s)");title.setFont(font(16,true));
            pane.add(title,BorderLayout.NORTH);
            JScrollPane view=new JScrollPane(body);
            view.setBorder(BorderFactory.createEmptyBorder());view.getViewport().setBackground(SOFT);
            view.setPreferredSize(new Dimension(735,Math.min(370,lines.size()*113+12)));
            view.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            MarketplaceVisuals.scrollbar(view);pane.add(view,BorderLayout.CENTER);
            JLabel hint=label("Select items and set quantities. Checkout creates one order per farm.");
            hint.setFont(font(12,false));pane.add(hint,BorderLayout.SOUTH);
            if(!confirm(parent,pane,"My Cart — Checkout Selected"))return;
            List<Long> selected=new ArrayList<>();
            for(CommerceService.CartLine line:lines){
                if(selections.get(line.itemId()).isSelected()){
                    int q=((Number)quantities.get(line.itemId()).getValue()).intValue();
                    if(q<1||java.math.BigDecimal.valueOf(q).compareTo(line.available())>0)
                        throw new IllegalArgumentException("Insufficient available stock for "+line.product()+".");
                    db.change(buyer,line.itemId(),q);selected.add(line.itemId());
                }
            }
            if(selected.isEmpty()){message(parent,"Select at least one product to checkout.");return;}
            checkout(parent,buyer,selected);
        }catch(Exception e){failure(parent,e);}
    }
    static void checkout(JFrame parent,String buyer,List<Long> ids){checkout(parent,buyer,ids,null);}
    private static void checkout(JFrame parent,String buyer,List<Long> ids,Map<Long,Integer> direct){
        if(ids.isEmpty()){message(parent,"Select at least one product for checkout.");return;}
        final List<CommerceService.PickupLocation> farmLocations;
        try{farmLocations=service().pickupLocations(buyer,ids,direct!=null);}
        catch(Exception e){failure(parent,e);return;}
        JTextField name=field(""),phone=field(""),address=field("");
        restrictPhilippineMobile(phone);
        JComboBox<String> delivery=new JComboBox<>(new String[]{"Farm Pickup","Local Delivery (if arranged with farmer)"});
        delivery.setFont(font(14,true));delivery.setForeground(INK);delivery.setBackground(WHITE);
        delivery.setPreferredSize(new Dimension(310,42));
        JComboBox<String> paymentMethod=new JComboBox<>(new String[]{"Cash on Pickup","Cash on Delivery"});
        paymentMethod.setFont(font(14,true));paymentMethod.setForeground(INK);paymentMethod.setBackground(WHITE);
        paymentMethod.setPreferredSize(new Dimension(310,42));
        // A cart may contain more than one farm; checkout creates a separate order for each.
        StringBuilder pickupDescription=new StringBuilder();
        boolean genericLocation=false;
        for(CommerceService.PickupLocation location:farmLocations){
            if(pickupDescription.length()>0)pickupDescription.append("\n\n");
            pickupDescription.append(location.farmName()).append("\n");
            if(location.location()==null || location.location().isBlank()){
                pickupDescription.append("Pickup address not provided by farm. Ask the farmer for the exact location.");
                genericLocation=true;
            }else{
                pickupDescription.append(location.location());
                if(location.location().trim().equalsIgnoreCase("Philippines")){
                    pickupDescription.append("\n(Only a general location is saved. Confirm the exact pickup address with the farmer.)");
                    genericLocation=true;
                }
            }
        }
        JTextArea pickupDetails=area(pickupDescription.toString(),Math.max(3,Math.min(7,2*farmLocations.size()+1)));
        pickupDetails.setEditable(false);pickupDetails.setFocusable(false);pickupDetails.setForeground(INK);
        pickupDetails.setFont(font(13,true));
        JScrollPane pickupScroll=new JScrollPane(pickupDetails);
        pickupScroll.setBorder(BorderFactory.createLineBorder(LINE,1,true));
        pickupScroll.setPreferredSize(new Dimension(400,farmLocations.size()>1?130:105));
        pickupScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        MarketplaceVisuals.scrollbar(pickupScroll);
        JPanel locationPanel=transparent(new CardLayout());
        locationPanel.add(formRow("Farm pickup location","Location saved by the farm owner. Confirm pickup arrangements before collecting.",pickupScroll),"PICKUP");
        locationPanel.add(formRow("Delivery address *","Enter house/street, barangay, city, province and a nearby landmark.",address),"DELIVERY");
        delivery.addActionListener(e->{
            boolean deliver=delivery.getSelectedIndex()==1;
            ((CardLayout)locationPanel.getLayout()).show(locationPanel,deliver?"DELIVERY":"PICKUP");
            paymentMethod.setSelectedIndex(deliver?1:0);
            locationPanel.revalidate();locationPanel.repaint();
        });
        JTextArea notes=area("",3);
        JScrollPane notesScroll=new JScrollPane(notes);notesScroll.setPreferredSize(new Dimension(400,87));
        MarketplaceVisuals.scrollbar(notesScroll);MarketplaceVisuals.roundedTable(notesScroll);
        JPanel fields=stack();
        stackAdd(fields,formRow("Buyer full name *","Who will receive the order?",name));
        stackAdd(fields,formRow("Contact number *","11 digits starting with 09 (example: 09123456789).",phone));
        stackAdd(fields,formRow("Fulfillment method","Choose how you will receive the products.",delivery));
        stackAdd(fields,locationPanel);
        stackAdd(fields,formRow("Payment method","Cash is confirmed by the farmer after handover.",paymentMethod));
        stackAdd(fields,formRow("Order notes (optional)","Pickup instructions or requests for the farmer.",notesScroll));
        JPanel outer=panel(new BorderLayout(0,10));
        outer.add(label("Complete your details to place this order securely."),BorderLayout.NORTH);
        JScrollPane scroll=new JScrollPane(fields);scroll.setBorder(null);scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);scroll.setPreferredSize(new Dimension(480,470));
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        MarketplaceVisuals.scrollbar(scroll);outer.add(scroll,BorderLayout.CENTER);
        if(!confirm(parent,outer,"Confirm Your Order",()->{
            if(name.getText().trim().isEmpty()){
                failure(parent,new IllegalArgumentException("Enter the buyer's full name."));name.requestFocusInWindow();return false;
            }
            if(!validPhilippineMobile(phone.getText().trim())){
                failure(parent,new IllegalArgumentException("Mobile number must have exactly 11 digits starting with 09 (e.g. 09123456789)."));
                phone.requestFocusInWindow();return false;
            }
            if(delivery.getSelectedIndex()==1 && address.getText().trim().isEmpty()){
                failure(parent,new IllegalArgumentException("Enter a complete delivery address."));
                address.requestFocusInWindow();return false;
            }
            return true;
        }))return;
        try{
            String mode=delivery.getSelectedIndex()==0?"PICKUP":"DELIVERY";
            if(name.getText().trim().isEmpty())throw new IllegalArgumentException("Enter the buyer's full name.");
            String mobile=phone.getText().trim();
            if(!validPhilippineMobile(mobile))throw new IllegalArgumentException("Enter a valid 11-digit Philippine mobile number starting with 09 (e.g. 09123456789).");
            if("DELIVERY".equals(mode)&&address.getText().trim().isEmpty())throw new IllegalArgumentException("Enter a complete delivery address.");
            if("PICKUP".equals(mode)&&genericLocation){
                // A general location is useful for preview but must not be represented as a street address.
                message(parent,"Pickup location is incomplete for one or more farms. Confirm the exact address with the farmer before collecting your order.");
            }
            List<Long> created;
            String pay="PICKUP".equals(mode)?"CASH_ON_PICKUP":"CASH_ON_DELIVERY";
            if(direct!=null){
                long itemId=ids.get(0);
                long order=service().buyNow(buyer,itemId,direct.get(itemId),name.getText(),mobile,mode,address.getText(),notes.getText(),pay);
                created=List.of(order);
            }else{
                created=service().checkout(buyer,ids,name.getText(),mobile,mode,address.getText(),notes.getText(),pay);
            }
            message(parent,"Order placed successfully! Reference(s): "+created+". Await farmer confirmation. Stock reserved.");
            myOrders(parent,buyer);
        }catch(Exception e){failure(parent,e);}
    }
    static void myOrders(JFrame parent,String buyer){showOrders(parent,buyer,false);}
    static void farmOrders(JFrame parent,String owner){showOrders(parent,owner,true);}
    private static void showOrders(JFrame parent,String username,boolean owner){
        try{
            CommerceService db=service();List<CommerceService.Order> orders=db.orders(username,owner);
            if(orders.isEmpty()){message(parent,owner?"No customer orders for your farm yet.":"You have no orders yet.");return;}
            String[] columns=owner?new String[]{"Order #","Buyer","Status","Fulfillment","Total","Placed"}:new String[]{"Order #","Farm","Status","Fulfillment","Total","Placed"};
            DefaultTableModel model=new DefaultTableModel(columns,0){@Override public boolean isCellEditable(int r,int c){return false;}};
            for(CommerceService.Order o:orders)model.addRow(new Object[]{o.id(),owner?o.buyer():o.farm(),o.status(),o.method(),money(o.total()),o.placed()});
            JTable table=new JTable(model);table.setRowHeight(47);table.setFont(font(14,false));
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.setIntercellSpacing(new Dimension(1,1));table.setShowGrid(true);
            table.setGridColor(new Color(171,215,178));table.setForeground(INK);
            table.setSelectionBackground(new Color(210,245,207));table.setSelectionForeground(INK);
            table.setAutoCreateRowSorter(true);
            JTableHeader tableHeader=table.getTableHeader();
            tableHeader.setFont(font(14,true));tableHeader.setPreferredSize(new Dimension(100,45));
            tableHeader.setBackground(new Color(0,109,48));tableHeader.setForeground(Color.WHITE);
            tableHeader.setReorderingAllowed(false);
            DefaultTableCellRenderer headerRenderer=new DefaultTableCellRenderer(){
                @Override public Component getTableCellRendererComponent(JTable t,Object v,boolean selected,boolean focus,int row,int col){
                    JLabel l=(JLabel)super.getTableCellRendererComponent(t,v,selected,focus,row,col);
                    l.setHorizontalAlignment(SwingConstants.CENTER);l.setFont(font(14,true));
                    l.setForeground(Color.WHITE);l.setBackground(new Color(0,105,46));l.setOpaque(true);
                    l.setBorder(BorderFactory.createMatteBorder(0,0,1,1,new Color(100,169,111)));return l;
                }
            };
            tableHeader.setDefaultRenderer(headerRenderer);
            DefaultTableCellRenderer rowRenderer=new DefaultTableCellRenderer(){
                @Override public Component getTableCellRendererComponent(JTable t,Object v,boolean selected,boolean focus,int row,int col){
                    JLabel l=(JLabel)super.getTableCellRendererComponent(t,v,selected,focus,row,col);
                    l.setBorder(new EmptyBorder(7,10,7,10));l.setFont(font(14,col==2||col==4));
                    l.setForeground(INK);l.setBackground(selected?new Color(202,240,201):row%2==0?Color.WHITE:new Color(239,250,236));
                    l.setHorizontalAlignment(col==0||col==2||col==3||col==4?SwingConstants.CENTER:SwingConstants.LEFT);
                    if(col==2){String status=String.valueOf(v);l.setForeground(status.equals("COMPLETED")?GREEN:
                        status.equals("CANCELLED")||status.equals("REJECTED")?new Color(173,62,54):INK);}
                    return l;
                }
            };
            for(int i=0;i<table.getColumnCount();i++)table.getColumnModel().getColumn(i).setCellRenderer(rowRenderer);
            // Fit all columns inside the modal; never force a horizontal scrollbar.
            // Widths are proportions, and Swing distributes extra space when resized.
            int[] widths={72,180,112,126,115,195};
            table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
            for(int i=0;i<widths.length;i++){
                table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
                table.getColumnModel().getColumn(i).setMinWidth(i==0?48:75);
            }
            table.setFillsViewportHeight(true);
            table.setPreferredScrollableViewportSize(new Dimension(800,Math.max(174,Math.min(390,orders.size()*47+12))));
            // Format timestamps in the visible cell, leaving the stored value unchanged.
            table.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer(){
                @Override public Component getTableCellRendererComponent(JTable t,Object value,boolean selected,boolean focus,int row,int col){
                    JLabel l=(JLabel)super.getTableCellRendererComponent(t,value,selected,focus,row,col);
                    String raw=String.valueOf(value==null?"":value).replace('T',' ');
                    l.setText(raw.length()>16?raw.substring(0,16):raw);
                    l.setToolTipText(raw);
                    l.setHorizontalAlignment(SwingConstants.CENTER);
                    l.setFont(font(13,false));l.setForeground(INK);
                    l.setOpaque(true);
                    l.setBackground(selected?new Color(202,240,201):row%2==0?Color.WHITE:new Color(239,250,236));
                    l.setBorder(new EmptyBorder(7,6,7,6));
                    return l;
                }
            });
            JScrollPane sc=new JScrollPane(table);
            sc.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            sc.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
            sc.setPreferredSize(new Dimension(805,Math.max(226,Math.min(440,90+orders.size()*47))));
            sc.getViewport().setBackground(WHITE);
            MarketplaceVisuals.scrollbar(sc);MarketplaceVisuals.roundedTable(sc);
            JPanel overview=panel(new BorderLayout(0,10));
            JLabel tip=label(""+orders.size()+" order"+(orders.size()==1?"":"s")+"  •  Select a row to view details or change its status.");
            tip.setFont(font(13,false));
            overview.add(tip,BorderLayout.NORTH);overview.add(sc,BorderLayout.CENTER);
            JPanel controls=new JPanel(new FlowLayout(FlowLayout.RIGHT,7,0));controls.setOpaque(false);
            JButton trackButton=button("Track Order");
            JButton detailsButton=button("View Details");
            JButton updateButton=button(owner?"Update Status":"Cancel Order");
            JButton paymentButton=button("Confirm Payment");
            JButton refreshButton=secondary("Refresh");
            JButton closeButton=button("Close");
            final int[] result={-1};
            JDialog dlg=new JDialog(parent,owner?"Customer Orders — My Farm":"My Orders",true);
            trackButton.addActionListener(e->{result[0]=2;dlg.dispose();});
            detailsButton.addActionListener(e->{result[0]=0;dlg.dispose();});
            updateButton.addActionListener(e->{result[0]=1;dlg.dispose();});
            paymentButton.addActionListener(e->{result[0]=4;dlg.dispose();});
            refreshButton.addActionListener(e->{result[0]=3;dlg.dispose();});
            closeButton.addActionListener(e->dlg.dispose());
            trackButton.setPreferredSize(new Dimension(126,42));
            detailsButton.setPreferredSize(new Dimension(126,42));
            updateButton.setPreferredSize(new Dimension(owner?148:130,42));
            paymentButton.setPreferredSize(new Dimension(145,42));
            refreshButton.setPreferredSize(new Dimension(100,42));
            closeButton.setPreferredSize(new Dimension(88,42));
            // This is presentation-only; the PostgreSQL service independently enforces
            // PENDING-only customer cancellation and owner transition permissions.
            Runnable updateActions=()->{
                int selectedRow=table.getSelectedRow();
                boolean selected=selectedRow>=0;
                CommerceService.Order current=selected?orders.get(table.convertRowIndexToModel(selectedRow)):null;
                trackButton.setEnabled(selected);
                detailsButton.setEnabled(selected);
                boolean canChange=selected && (owner?switch(current.status()){
                    case "PENDING","ACCEPTED","PREPARING","READY","OUT_FOR_DELIVERY" -> true;
                    case "DELIVERED","PICKED_UP" -> "PAID".equalsIgnoreCase(current.paymentStatus());
                    default -> false;
                }:"PENDING".equals(current.status()));
                updateButton.setEnabled(canChange);
                paymentButton.setVisible(owner);
                paymentButton.setEnabled(owner && selected && "UNPAID".equalsIgnoreCase(current.paymentStatus()) && Set.of("DELIVERED","PICKED_UP","COMPLETED").contains(current.status()));
                paymentButton.setToolTipText("Confirm cash received after handover. Payment is saved in PostgreSQL and cannot be confirmed twice.");
                updateButton.setToolTipText(!selected?"Select an order first":canChange?"Update the selected order":owner&&selected&&Set.of("DELIVERED","PICKED_UP").contains(current.status())?"Confirm cash payment first, then complete the order":owner?"This order has no further transitions":"Only PENDING orders can be cancelled by the buyer");
            };
            table.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting())updateActions.run();});
            updateActions.run();
            controls.add(trackButton);controls.add(detailsButton);controls.add(updateButton);if(owner)controls.add(paymentButton);controls.add(refreshButton);controls.add(closeButton);
            overview.add(controls,BorderLayout.SOUTH);JPanel shell=new DialogSurface(new BorderLayout(0,12));
            shell.add(header(owner?"Customer Orders":"My Orders", "Review orders, view details and manage statuses.",1),BorderLayout.NORTH);
            shell.add(overview,BorderLayout.CENTER);
            dlg.setContentPane(shell);
            dlg.setMinimumSize(new Dimension(740,490));
            dlg.setSize(1000,Math.max(520,Math.min(740,390+orders.size()*47)));
            dlg.setLocationRelativeTo(parent);
            dlg.setVisible(true);
            if(result[0]<0)return;
            if(result[0]==3){SwingUtilities.invokeLater(()->showOrders(parent,username,owner));return;}
            int row=table.getSelectedRow();if(row<0){message(parent,"Select an order row first.");return;}
            CommerceService.Order o=orders.get(table.convertRowIndexToModel(row));
            if(result[0]==4){
                JPanel paymentConfirmPanel=panel(new BorderLayout(0,8));
                paymentConfirmPanel.add(label("Confirm that cash payment for order #"+o.id()+" has actually been received."),BorderLayout.CENTER);
                if(!confirm(parent,paymentConfirmPanel,"Confirm Payment Received"))return;
                db.confirmPayment(username,o.id());message(parent,"Payment confirmed and saved in PostgreSQL.");SwingUtilities.invokeLater(()->showOrders(parent,username,owner));
            }else if(result[0]==2){
                trackOrder(parent,username,o.id(),owner);
            }else if(result[0]==0){
                JTextArea details=area("Order #"+o.id()+"\nStatus: "+o.status()+"\nTotal: "+money(o.total())+"\nContact: "+o.contact()+"\nFulfillment: "+o.method()+"\nAddress: "+o.address()+"\n\nProducts:\n"+db.details(username,o.id(),owner),11);
                details.setEditable(false);details.setFont(font(14,false));details.setLineWrap(true);details.setWrapStyleWord(true);
                JScrollPane detailScroll=new JScrollPane(details);detailScroll.setPreferredSize(new Dimension(550,280));
                JPanel detailPanel=panel(new BorderLayout());detailPanel.add(detailScroll,BorderLayout.CENTER);
                viewOnly(parent,detailPanel,"Order Details — #"+o.id());
            }else{
                String next;
                if(!owner)next="CANCELLED";
                else{String[] candidates=switch(o.status()){
                    case "PENDING" -> new String[]{"ACCEPTED","REJECTED"};
                    case "ACCEPTED" -> new String[]{"PREPARING","REJECTED"};
                    case "PREPARING" -> new String[]{"READY"};
                    case "READY" -> "DELIVERY".equals(o.method())?new String[]{"OUT_FOR_DELIVERY"}:new String[]{"PICKED_UP"};
                    case "OUT_FOR_DELIVERY" -> new String[]{"DELIVERED"};
                    case "DELIVERED", "PICKED_UP" -> new String[]{"COMPLETED"};
                    default -> new String[0];};
                    if(candidates.length==0){message(parent,"This order cannot be updated in its current state.");return;}
                    JComboBox<String> nextBox=new JComboBox<>(candidates);nextBox.setFont(font(15,true));
                    JPanel nextPanel=panel(new BorderLayout(0,10));
                    nextPanel.add(label("Choose the next status for order #"+o.id()),BorderLayout.NORTH);
                    nextPanel.add(nextBox,BorderLayout.CENTER);
                    if(!confirm(parent,nextPanel,"Update Order Status"))return;
                    next=(String)nextBox.getSelectedItem();
                }
                if(next==null)return;
                JPanel confirmation=panel(new BorderLayout());confirmation.add(label("Change Order #"+o.id()+" to "+next+"?"),BorderLayout.CENTER);
                if(!confirm(parent,confirmation,"CANCELLED".equals(next)?"Confirm Cancellation":"REJECTED".equals(next)?"Confirm Rejection":"Confirm Order Update"))return;
                db.status(username,o.id(),next,owner);
                message(parent,"Order #"+o.id()+" updated to "+next+". The buyer can view the latest status in My Orders.");
                SwingUtilities.invokeLater(()->showOrders(parent,username,owner));
            }
        }catch(Exception e){failure(parent,e);}
    }

    /** Shared PostgreSQL status and real, timestamped history for both roles. */
    private static CommerceService.Order freshOrder(CommerceService db,String username,long id,boolean owner) throws Exception {
        for(CommerceService.Order order:db.orders(username,owner))if(order.id()==id)return order;
        throw new SecurityException("Order not found or no longer accessible.");
    }
    private static final class ProgressMark extends JComponent {
        private final boolean reached,active;
        ProgressMark(boolean reached,boolean active){this.reached=reached;this.active=active;setPreferredSize(new Dimension(46,48));}
        @Override protected void paintComponent(Graphics source){
            Graphics2D g=(Graphics2D)source.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(active?new Color(0,119,49):reached?new Color(68,169,91):new Color(211,230,213));
            g.fillOval(7,7,32,32);
            g.setColor(active||reached?Color.WHITE:new Color(109,150,116));
            g.setStroke(new BasicStroke(3,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            if(reached){g.drawLine(16,23,21,28);g.drawLine(21,28,31,17);}
            else g.fillOval(21,21,4,4);
            g.dispose();
        }
    }
    private static String[] trackingStatuses(String method){
        return "DELIVERY".equals(method)
            ? new String[]{"PENDING","ACCEPTED","PREPARING","READY","OUT_FOR_DELIVERY","DELIVERED","COMPLETED"}
            : new String[]{"PENDING","ACCEPTED","PREPARING","READY","PICKED_UP","COMPLETED"};
    }
    private static String trackingTitle(String status){
        return switch(status){
            case "PENDING" -> "Order Placed";
            case "ACCEPTED" -> "Accepted by Farmer";
            case "PREPARING" -> "Preparing Products";
            case "READY" -> "Ready for Handover";
            case "OUT_FOR_DELIVERY" -> "Out for Delivery";
            case "DELIVERED" -> "Delivered to Buyer";
            case "PICKED_UP" -> "Picked Up by Buyer";
            case "COMPLETED" -> "Transaction Completed";
            default -> status.replace('_',' ');
        };
    }
    private static String trackingDetail(String status){
        return switch(status){
            case "PENDING" -> "Order submitted and stock reserved in PostgreSQL.";
            case "ACCEPTED" -> "The farm owner confirmed the order.";
            case "PREPARING" -> "The farmer is preparing the requested items.";
            case "READY" -> "The products are ready for pickup or dispatch.";
            case "OUT_FOR_DELIVERY" -> "The farmer has dispatched the order to the delivery address.";
            case "DELIVERED" -> "The order has been handed over to the buyer.";
            case "PICKED_UP" -> "The buyer has collected the order from the farm.";
            case "COMPLETED" -> "Payment was confirmed and the transaction was completed.";
            default -> "";
        };
    }
    static void trackOrder(JFrame parent,String username,long orderId,boolean owner){
        try{
            CommerceService db=service();
            CommerceService.Order o=freshOrder(db,username,orderId,owner);
            List<CommerceService.OrderEvent> events=db.history(username,orderId,owner);
            Map<String,CommerceService.OrderEvent> firstEvent=new LinkedHashMap<>();
            for(CommerceService.OrderEvent ev:events)firstEvent.putIfAbsent(ev.status(),ev);
            String[] tracking=trackingStatuses(o.method());
            int reached=Arrays.asList(tracking).indexOf(o.status());
            boolean stopped="CANCELLED".equals(o.status())||"REJECTED".equals(o.status());
            JPanel timeline=transparent(new BorderLayout(0,12));
            JPanel summary=panel(new BorderLayout(0,8));
            JLabel heading=label("Order #"+o.id()+"   •   "+o.status());heading.setFont(font(21,true));
            heading.setForeground(stopped?new Color(168,57,51):GREEN);
            summary.add(heading,BorderLayout.NORTH);
            String description=owner?"Buyer: "+o.buyer():"Farm: "+o.farm();
            JLabel facts=label(description+"   |   "+money(o.total())+"   |   "+o.method());
            facts.setFont(font(13,false));summary.add(facts,BorderLayout.CENTER);
            JLabel hint=label("Live database status. Historical events are saved at each transition; older orders show a snapshot.");
            hint.setFont(font(12,false));hint.setForeground(MUTED);summary.add(hint,BorderLayout.SOUTH);
            timeline.add(summary,BorderLayout.NORTH);
            JPanel stages=transparent(new GridLayout(0,1,0,7));
            for(int i=0;i<tracking.length;i++){
                String stepStatus=tracking[i];
                boolean done=!stopped&&reached>=i;
                JPanel row=panel(new BorderLayout(10,0));
                row.setBorder(new EmptyBorder(7,12,7,12));
                row.add(new ProgressMark(done,i==reached&&!stopped),BorderLayout.WEST);
                JPanel text=transparent(new GridLayout(3,1,0,2));
                JLabel title=label(trackingTitle(stepStatus));title.setFont(font(15,true));
                title.setForeground(done?INK:new Color(112,135,116));
                JLabel detail=label(trackingDetail(stepStatus));detail.setFont(font(12,false));
                detail.setForeground(done?MUTED:new Color(136,149,138));
                CommerceService.OrderEvent occurred=firstEvent.get(stepStatus);
                String when=occurred==null?"Not recorded":occurred.changedAt();
                if(occurred!=null&&occurred.note().startsWith("Existing order status imported"))when+=" (imported snapshot)";
                JLabel stamped=label(when);stamped.setFont(font(11,false));
                stamped.setForeground(occurred==null?new Color(136,149,138):MUTED);
                text.add(title);text.add(detail);text.add(stamped);row.add(text,BorderLayout.CENTER);
                stages.add(row);
            }
            JPanel timelineBody=transparent(new BorderLayout(0,10));
            timelineBody.add(stages,BorderLayout.NORTH);
            JPanel audit=panel(new BorderLayout(0,8));
            JLabel auditTitle=label("Order Activity History (Philippine Time)");
            auditTitle.setFont(font(15,true));audit.add(auditTitle,BorderLayout.NORTH);
            JPanel auditRows=transparent(new GridLayout(0,1,0,5));
            for(CommerceService.OrderEvent ev:events){
                String snapshot=ev.note().startsWith("Existing order status imported")?"  ·  Imported snapshot":"";
                JLabel entry=label(ev.changedAt()+"  •  "+ev.status()+"  •  "+ev.actor()+snapshot);
                entry.setFont(font(12,false));entry.setToolTipText(ev.note());auditRows.add(entry);
            }
            audit.add(auditRows,BorderLayout.CENTER);
            timelineBody.add(audit,BorderLayout.CENTER);
            JScrollPane timelineScroll=new JScrollPane(timelineBody);
            timelineScroll.setBorder(null);timelineScroll.setOpaque(false);
            timelineScroll.getViewport().setOpaque(false);
            timelineScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            timelineScroll.getVerticalScrollBar().setUnitIncrement(20);
            MarketplaceVisuals.scrollbar(timelineScroll);
            timeline.add(timelineScroll,BorderLayout.CENTER);
            if(stopped){
                JPanel warning=panel(new BorderLayout());
                JLabel stoppedText=label("Order "+o.status().toLowerCase(Locale.ROOT)+". Reserved stock was released.");
                stoppedText.setForeground(new Color(158,60,50));
                warning.add(stoppedText);timeline.add(warning,BorderLayout.SOUTH);
            }
            JDialog dialog=new JDialog(parent,"Order Tracking — #"+orderId,true);
            JPanel shell=new DialogSurface(new BorderLayout(0,11));
            shell.add(header("Order Tracking","Buyer and farmer see the same saved order status.",0),BorderLayout.NORTH);
            shell.add(timeline,BorderLayout.CENTER);
            JPanel actions=transparent(new FlowLayout(FlowLayout.RIGHT,10,0));
            JButton refresh=button("Refresh Status");
            JButton close=secondary("Close");
            refresh.addActionListener(e->{dialog.dispose();SwingUtilities.invokeLater(()->trackOrder(parent,username,orderId,owner));});
            close.addActionListener(e->dialog.dispose());
            actions.add(refresh);actions.add(close);shell.add(actions,BorderLayout.SOUTH);
            dialog.setContentPane(shell);dialog.setSize(665,690);dialog.setMinimumSize(new Dimension(585,600));
            dialog.setLocationRelativeTo(parent);
            dialog.getRootPane().registerKeyboardAction(e->dialog.dispose(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
            dialog.setVisible(true);
        }catch(Exception e){failure(parent,e);}
    }
    private static void viewOnly(Component parent,JComponent content,String title){
        Window owner=SwingUtilities.getWindowAncestor(parent);
        JDialog dialog=new JDialog(owner,title,Dialog.ModalityType.APPLICATION_MODAL);
        JPanel shell=new DialogSurface(new BorderLayout(0,12));
        shell.add(header(title,"Order information",0),BorderLayout.NORTH);
        shell.add(content,BorderLayout.CENTER);
        JPanel actions=transparent(new FlowLayout(FlowLayout.RIGHT));
        JButton close=button("Close");close.addActionListener(e->dialog.dispose());actions.add(close);
        shell.add(actions,BorderLayout.SOUTH);dialog.setContentPane(shell);
        dialog.setSize(665,470);dialog.setLocationRelativeTo(parent);
        dialog.getRootPane().registerKeyboardAction(e->dialog.dispose(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setVisible(true);
    }

}
