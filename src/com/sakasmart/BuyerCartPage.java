package com.sakasmart;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.List;

/** Embedded, PostgreSQL-backed customer shopping cart. Does not change Main.java. */
final class BuyerCartPage extends JPanel {
    private static final Color INK=new Color(7,75,41), PALE=new Color(242,251,236), LINE=new Color(144,201,152);
    private final JFrame parent;
    private final String buyer;
    private final JPanel items=new JPanel();
    private final JLabel heading=new JLabel("My Shopping Cart");
    private final JLabel summary=new JLabel();
    private final JButton checkout=button("Proceed to Checkout"), refresh=button("Refresh Cart");
    private final Map<Long,JCheckBox> selected=new LinkedHashMap<>();
    private final Map<Long,JSpinner> quantities=new LinkedHashMap<>();
    private List<CommerceService.CartLine> lines=List.of();

    BuyerCartPage(JFrame parent,String buyer) {
        super(new BorderLayout(0,16));
        this.parent=parent; this.buyer=buyer;
        setOpaque(false);setBorder(new EmptyBorder(4,2,6,2));
        heading.setForeground(INK);heading.setFont(font(25,true));
        JLabel sub=new JLabel("Review fresh farm products, choose quantities, and checkout securely.");
        sub.setForeground(new Color(72,111,80));sub.setFont(font(14,false));
        JPanel top=new JPanel(new GridLayout(2,1,0,3));top.setOpaque(false);top.add(heading);top.add(sub);
        add(top,BorderLayout.NORTH);
        JPanel surface=card(new BorderLayout(0,14));surface.setBorder(new EmptyBorder(19,20,19,20));
        items.setOpaque(false);items.setLayout(new BoxLayout(items,BoxLayout.Y_AXIS));
        JScrollPane scroll=new JScrollPane(items);scroll.setBorder(new LineBorder(LINE,1,true));
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setBackground(Color.WHITE);scroll.getVerticalScrollBar().setUnitIncrement(24);
        surface.add(scroll,BorderLayout.CENTER);
        JPanel actions=new JPanel(new BorderLayout(12,4));actions.setOpaque(false);
        summary.setFont(font(15,true));summary.setForeground(INK);actions.add(summary,BorderLayout.CENTER);
        JPanel right=new JPanel(new FlowLayout(FlowLayout.RIGHT,9,0));right.setOpaque(false);
        right.add(refresh);right.add(checkout);actions.add(right,BorderLayout.EAST);
        surface.add(actions,BorderLayout.SOUTH);add(surface,BorderLayout.CENTER);
        refresh.addActionListener(e->reload());
        checkout.addActionListener(e->startCheckout());
        reload();
    }
    private static Font font(int size,boolean bold){return new Font("Segoe UI",bold?Font.BOLD:Font.PLAIN,size);}
    private static JButton button(String value){
        JButton b=MarketplaceVisuals.greenButton(value);b.setFont(font(14,true));
        b.setPreferredSize(new Dimension(Math.max(135,value.length()*9+30),44));return b;
    }
    private static JPanel card(LayoutManager layout){
        JPanel p=MarketplaceVisuals.roundedPanel(layout,new Color(250,255,249),22);return p;
    }
    private static String pesos(BigDecimal n){return "₱"+n.setScale(2,RoundingMode.HALF_UP).toPlainString();}
    private void reload(){
        try{
            lines=new CommerceService().cart(buyer);
            selected.clear();quantities.clear();items.removeAll();
            if(lines.isEmpty()){
                JLabel empty=new JLabel("Your cart is empty. Explore farms to add fresh products.");
                empty.setForeground(INK);empty.setFont(font(15,false));empty.setBorder(new EmptyBorder(35,25,35,25));
                empty.setAlignmentX(Component.LEFT_ALIGNMENT);items.add(empty);
            }else{
                for(CommerceService.CartLine line:lines){
                    JPanel item=card(new BorderLayout(14,8));
                    item.setBorder(BorderFactory.createCompoundBorder(new LineBorder(LINE,1,true),new EmptyBorder(12,14,12,14)));
                    item.setMaximumSize(new Dimension(Integer.MAX_VALUE,105));item.setAlignmentX(Component.LEFT_ALIGNMENT);
                    JCheckBox include=new JCheckBox(line.product(),true);include.setOpaque(false);
                    include.setForeground(INK);include.setFont(font(16,true));selected.put(line.itemId(),include);
                    JPanel information=new JPanel(new GridLayout(2,1,0,3));information.setOpaque(false);
                    information.add(include);
                    JLabel meta=new JLabel(pesos(line.price())+" / "+line.unit()+"  •  "+line.available().stripTrailingZeros().toPlainString()+" available");
                    meta.setFont(font(13,false));meta.setForeground(INK);information.add(meta);
                    item.add(information,BorderLayout.CENTER);
                    JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,9));buttons.setOpaque(false);
                    JSpinner quantity=new JSpinner(new SpinnerNumberModel(line.quantity(),1,100000,1));
                    quantity.setPreferredSize(new Dimension(82,40));quantity.setFont(font(14,true));quantities.put(line.itemId(),quantity);
                    JButton remove=button("Remove");remove.setPreferredSize(new Dimension(95,40));
                    remove.addActionListener(e->remove(line));
                    quantity.addChangeListener(e->updateTotal());include.addActionListener(e->updateTotal());
                    buttons.add(quantity);buttons.add(remove);item.add(buttons,BorderLayout.EAST);
                    items.add(item);items.add(Box.createVerticalStrut(10));
                }
            }
            updateTotal();items.revalidate();items.repaint();
        }catch(Exception e){error(e);}
    }
    private void updateTotal(){
        BigDecimal total=BigDecimal.ZERO;int count=0;
        for(CommerceService.CartLine line:lines){
            JCheckBox check=selected.get(line.itemId());JSpinner q=quantities.get(line.itemId());
            if(check!=null && q!=null && check.isSelected()){
                int quantity=(Integer)q.getValue();
                total=total.add(line.price().multiply(BigDecimal.valueOf(quantity)));count++;
            }
        }
        summary.setText(count+" selected  •  Total: "+pesos(total));checkout.setEnabled(count>0);
    }
    private void remove(CommerceService.CartLine line){
        if(JOptionPane.showConfirmDialog(parent,"Remove "+line.product()+" from your cart?","Remove Product",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        try{new CommerceService().change(buyer,line.itemId(),0);reload();}catch(Exception e){error(e);}
    }
    private void startCheckout(){
        try{
            CommerceService db=new CommerceService();List<Long> ids=new ArrayList<>();
            for(CommerceService.CartLine line:lines){
                JCheckBox check=selected.get(line.itemId());
                if(check!=null && check.isSelected()){
                    int qty=(Integer)quantities.get(line.itemId()).getValue();
                    if(qty<1||BigDecimal.valueOf(qty).compareTo(line.available())>0)throw new IllegalArgumentException("Insufficient stock for "+line.product());
                    db.change(buyer,line.itemId(),qty);ids.add(line.itemId());
                }
            }
            if(ids.isEmpty())return;
            CommerceUI.checkout(parent,buyer,ids);
            reload();
        }catch(Exception e){error(e);}
    }
    private void error(Exception e){JOptionPane.showMessageDialog(parent,e.getMessage(),"Shopping Cart Error",JOptionPane.ERROR_MESSAGE);}
}
