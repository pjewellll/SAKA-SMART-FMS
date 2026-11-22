package com.sakasmart;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

/** Farm-shop and product publishing dialogs. Existing PostgreSQL storage actions are unchanged. */
final class MarketSellerEditor {
    private static final Color INK=new Color(10,68,35), SECONDARY=new Color(45,82,57);
    private static final Color PALE=new Color(243,252,242), BORDER=new Color(139,201,151);
    private static final Font BODY=new Font("Segoe UI",Font.PLAIN,15);
    private static final Font TITLE=new Font("Segoe UI",Font.BOLD,17);
    private static String v(Map<String,String> row,String key){return row.getOrDefault(key,"");}
    private static JLabel text(String value,Font font,Color color){
        JLabel l=new JLabel(value);l.setFont(font);l.setForeground(color);return l;
    }
    private static JPanel transparent(LayoutManager layout){JPanel p=new JPanel(layout);p.setOpaque(false);return p;}
    private static JPanel stack(){JPanel p=transparent(null);p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));return p;}
    private static void stretch(JComponent component){component.setAlignmentX(Component.LEFT_ALIGNMENT);}
    private static JPanel panel(LayoutManager layout){return MarketplaceVisuals.roundedPanel(layout,PALE,22);}
    private static JButton green(String caption){return MarketplaceVisuals.greenButton(caption);}
    private static JTextField input(String value){
        JTextField f=new JTextField(value,25);f.setFont(BODY);Main.styleInput(f);
        f.setPreferredSize(new Dimension(250,40));return f;
    }
    private static JTextArea area(String value,int rows){
        JTextArea a=new JTextArea(value,rows,32);a.setFont(BODY);a.setLineWrap(true);a.setWrapStyleWord(true);
        a.setMargin(new Insets(9,11,9,11));a.setForeground(INK);a.setBackground(Color.WHITE);return a;
    }
    private static void separator(JPanel parent){parent.add(Box.createVerticalStrut(13));}
    private static JPanel form(){
        JPanel p=stack();p.setBorder(new EmptyBorder(12,13,14,13));return p;
    }
    private static JComponent section(String name,String helper,JComponent component){
        JPanel outer=panel(new BorderLayout(0,10));outer.setBorder(new EmptyBorder(15,17,17,17));
        JPanel heading=stack();
        JLabel title=text(name,TITLE,INK);stretch(title);heading.add(title);
        if(helper!=null&&!helper.isBlank()){
            heading.add(Box.createVerticalStrut(4));
            JLabel subtitle=text(helper,new Font("Segoe UI",Font.PLAIN,13),SECONDARY);stretch(subtitle);heading.add(subtitle);
        }
        outer.add(heading,BorderLayout.NORTH);outer.add(component,BorderLayout.CENTER);
        stretch(outer);outer.setMaximumSize(new Dimension(Integer.MAX_VALUE,Integer.MAX_VALUE));return outer;
    }
    private static void addSection(JPanel form,JComponent section,int height){
        stretch(section);section.setPreferredSize(new Dimension(680,height));
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE,height));
        form.add(section);separator(form);
    }
    private static JScrollPane scrollForm(JPanel content){
        JScrollPane sc=new JScrollPane(content,ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        MarketplaceVisuals.scrollbar(sc);
        sc.getViewport().setBackground(new Color(249,254,248));
        sc.setPreferredSize(new Dimension(840,575));
        sc.setMinimumSize(new Dimension(550,330));
        sc.getVerticalScrollBar().setUnitIncrement(24);
        SwingUtilities.invokeLater(()->sc.getViewport().setViewPosition(new Point(0,0)));
        return sc;
    }
    private static JPanel intro(String sentence){
        JPanel p=panel(new BorderLayout());p.setBorder(new EmptyBorder(12,17,12,17));
        JLabel label=text(sentence,new Font("Segoe UI",Font.BOLD,15),INK);
        p.add(label,BorderLayout.CENTER);return p;
    }
    private static JComponent photoPlaceholder(int width,int height,boolean circle,String ref){
        return new JComponent(){
            {setPreferredSize(new Dimension(width,height));setMinimumSize(new Dimension(width,height));}
            @Override protected void paintComponent(Graphics graphics){
                Graphics2D g=(Graphics2D)graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(225,246,225));g.fillRoundRect(1,1,getWidth()-3,getHeight()-3,17,17);
                g.setStroke(new BasicStroke(1.4f));g.setColor(BORDER);
                g.drawRoundRect(1,1,getWidth()-3,getHeight()-3,17,17);
                ImageIcon icon=MarketPhotoAssets.icon(ref,Math.max(60,width-14),Math.max(55,height-12));
                if(icon!=null){
                    int x=(getWidth()-icon.getIconWidth())/2,y=(getHeight()-icon.getIconHeight())/2;
                    if(circle){Shape old=g.getClip();int diameter=Math.min(icon.getIconWidth(),icon.getIconHeight());
                        g.setClip(new Ellipse2D.Float((getWidth()-diameter)/2f,(getHeight()-diameter)/2f,diameter,diameter));
                        g.drawImage(icon.getImage(),x,y,null);g.setClip(old);
                    }else{g.drawImage(icon.getImage(),x,y,null);}
                }else{
                    g.setFont(new Font("Segoe UI",Font.BOLD,13));g.setColor(SECONDARY);
                    String display=ref.startsWith("https://")?"Online photo saved":"No photo selected";
                    FontMetrics fm=g.getFontMetrics();g.drawString(display,Math.max(8,(getWidth()-fm.stringWidth(display))/2),(getHeight()+fm.getAscent())/2);
                }
                g.dispose();
            }
        };
    }
    private static final class PhotoField {
        private final JPanel holder=transparent(new BorderLayout(12,0));
        private final JPanel previewHolder=transparent(new BorderLayout());
        private final JTextField url;
        private final Component parent;
        private final int maxSide;
        private final boolean circle;
        private String value;
        PhotoField(Component parent,String saved,int maxSide,boolean circle){
            this.parent=parent;this.maxSide=maxSide;this.circle=circle;
            this.value=saved==null?"":saved;
            url=input(value.startsWith("https://")?value:"");
            url.setToolTipText("Paste an HTTPS photo address, or use Upload Photo.");
            JButton upload=green("Upload Photo"),link=green("Use URL"),remove=green("Remove Photo");
            upload.addActionListener(e->{String next=MarketPhotoAssets.chooseAndEncode(parent,maxSide);
                if(next!=null){value=next;url.setText("");refresh();}});
            link.addActionListener(e->{try{value=MarketPhotoAssets.validateReference(url.getText());refresh();}
                catch(Exception ex){MarketplaceVisuals.notice(parent,"Invalid Photo URL",ex.getMessage(),JOptionPane.ERROR_MESSAGE);}});
            remove.addActionListener(e->{value="";url.setText("");refresh();});
            JPanel right=transparent(new BorderLayout(0,9));
            JPanel buttons=transparent(new FlowLayout(FlowLayout.LEFT,8,0));
            buttons.add(upload);buttons.add(link);buttons.add(remove);
            JLabel hint=text("Choose a photo or paste a secure HTTPS image link.",new Font("Segoe UI",Font.PLAIN,12),SECONDARY);
            JPanel lower=transparent(new BorderLayout(0,6));lower.add(url,BorderLayout.NORTH);lower.add(hint,BorderLayout.SOUTH);
            right.add(buttons,BorderLayout.NORTH);right.add(lower,BorderLayout.CENTER);
            holder.add(previewHolder,BorderLayout.WEST);holder.add(right,BorderLayout.CENTER);
            refresh();
        }
        JComponent component(){return holder;}
        String result(){
            String typed=url.getText().trim();
            if(!typed.isEmpty()&&!typed.equals(value))return MarketPhotoAssets.validateReference(typed);
            return MarketPhotoAssets.validateReference(value);
        }
        private void refresh(){
            previewHolder.removeAll();previewHolder.add(photoPlaceholder(circle?140:190,112,circle,value),BorderLayout.CENTER);
            previewHolder.revalidate();previewHolder.repaint();
        }
    }
    private static JCheckBox publishCheckbox(String value,boolean selected){
        JCheckBox c=new JCheckBox(value,selected);c.setOpaque(false);
        c.setFont(new Font("Segoe UI",Font.BOLD,15));c.setForeground(INK);
        c.setFocusPainted(false);return c;
    }
    private static JPanel publication(JCheckBox checkbox,String hint){
        JPanel p=transparent(new BorderLayout(0,4));p.add(checkbox,BorderLayout.NORTH);
        p.add(text(hint,new Font("Segoe UI",Font.PLAIN,13),SECONDARY),BorderLayout.SOUTH);return p;
    }
    static void editFarm(Main host){
        if(!"FARM_OWNER".equals(host.role())){host.info("Only Farm Owners can edit farm shop details.");return;}
        try{
            List<Map<String,String>> farms=host.store.all("farms");
            if(farms.isEmpty()){host.info("No farm profile found.");return;}
            Map<String,String> farm=farms.get(0);
            JTextArea desc=area(v(farm,"market_description"),3);
            JScrollPane descScroll=new JScrollPane(desc);descScroll.setBorder(BorderFactory.createLineBorder(BORDER,1,true));
            PhotoField logo=new PhotoField(host.frame,v(farm,"market_logo_url"),500,true);
            PhotoField cover=new PhotoField(host.frame,v(farm,"market_banner_url"),1100,false);
            JCheckBox publish=publishCheckbox("Publish my farm in the customer directory",
                Boolean.parseBoolean(v(farm,"market_published")));
            JPanel fields=form();
            addSection(fields,intro("Edit the farm details that buyers can see."),59);
            addSection(fields,section("Short Farm Description","Tell customers what makes your farm special.",descScroll),166);
            addSection(fields,section("Farm Logo / Photo","Choose a circular logo or picture representing your farm.",logo.component()),207);
            addSection(fields,section("Farm Shop Cover Photo","Choose a wide photo to showcase your farm.",cover.component()),207);
            addSection(fields,section("Farm Visibility","Control whether buyers can discover your farm shop.",
                publication(publish,"Only published farms appear in the customer directory.")),138);
            if(MarketplaceVisuals.confirm(host.frame,"Farm Shop Profile","Review your public farm details below.",scrollForm(fields))!=JOptionPane.OK_OPTION)return;
            String description=desc.getText().trim();
            if(description.length()>700)throw new IllegalArgumentException("Description cannot exceed 700 characters.");
            host.store.edit("farms",v(farm,"id"),Map.of(
                "market_description",description,"market_logo_url",logo.result(),
                "market_banner_url",cover.result(),"market_published",Boolean.toString(publish.isSelected())));
            host.store.log(host.username(),"Updated public farm shop");
            MarketplaceVisuals.notice(host.frame,"Farm Shop Updated Successfully",
                "Your farm shop details have been saved. Published details are available to customers.",JOptionPane.INFORMATION_MESSAGE);
            host.renderPage();
        }catch(Exception e){host.error(e);}
    }
    static void editProduct(Main host){
        if(!"FARM_OWNER".equals(host.role())){host.info("Only Farm Owners can publish products.");return;}
        try{
            List<Map<String,String>> harvests=host.store.all("inventory").stream()
                .filter(row->"HARVEST".equalsIgnoreCase(v(row,"kind"))).toList();
            if(harvests.isEmpty()){host.info("Add harvested items to Inventory before publishing.");return;}
            JComboBox<ProductOption> choice=new JComboBox<>();
            for(Map<String,String> row:harvests)choice.addItem(new ProductOption(row));
            choice.setFont(BODY);choice.setPreferredSize(new Dimension(650,40));
            JTextField price=input("");JTextArea description=area("",3);
            JScrollPane descScroll=new JScrollPane(description);descScroll.setBorder(BorderFactory.createLineBorder(BORDER,1,true));
            JPanel photoHolder=transparent(new BorderLayout());PhotoField[] photo={null};
            JCheckBox publish=publishCheckbox("Publish this product in my farm shop",false);
            Runnable fill=()->{
                ProductOption item=(ProductOption)choice.getSelectedItem();if(item==null)return;
                Map<String,String> row=item.item;
                price.setText(v(row,"market_price"));description.setText(v(row,"market_description"));
                publish.setSelected(Boolean.parseBoolean(v(row,"market_published")));
                photo[0]=new PhotoField(host.frame,v(row,"market_image_url"),850,false);
                photoHolder.removeAll();photoHolder.add(photo[0].component(),BorderLayout.CENTER);
                photoHolder.revalidate();photoHolder.repaint();
            };
            choice.addActionListener(e->fill.run());fill.run();
            JPanel fields=form();
            addSection(fields,intro("Choose a harvested item, set its price, and control visibility."),59);
            addSection(fields,section("Harvested Product (from Inventory)",
                "Select an existing harvested product to offer to buyers.",choice),138);
            addSection(fields,section("Selling Price per Unit (PHP)",
                "This is the price customers will see for one unit.",price),138);
            addSection(fields,section("Product Description",
                "Describe the freshness, quality, and details of your product.",descScroll),170);
            addSection(fields,section("Product Photo",
                "Upload a photo or use a secure HTTPS link.",photoHolder),210);
            addSection(fields,section("Visibility / Publish Settings",
                "Choose whether buyers can see this item in the marketplace.",
                publication(publish,"Stock quantities remain linked to Harvested Inventory.")),138);
            if(MarketplaceVisuals.confirm(host.frame,"Manage Product Listings",
                "Update the information displayed in your farm shop.",scrollForm(fields))!=JOptionPane.OK_OPTION)return;
            ProductOption item=(ProductOption)choice.getSelectedItem();if(item==null)return;
            double amount=price.getText().trim().isEmpty()?0:Double.parseDouble(price.getText().trim());
            if(!Double.isFinite(amount)||amount<0||amount>100000000)
                throw new IllegalArgumentException("Enter a valid product price.");
            if(publish.isSelected()&&amount<=0)throw new IllegalArgumentException("Published products need a positive price.");
            if(description.getText().length()>700)throw new IllegalArgumentException("Product description cannot exceed 700 characters.");
            host.store.edit("inventory",v(item.item,"id"),Map.of(
                "market_price",Double.toString(amount),"market_description",description.getText().trim(),
                "market_image_url",photo[0]==null?"":photo[0].result(),
                "market_published",Boolean.toString(publish.isSelected())));
            host.store.log(host.username(),"Updated public listing for item "+v(item.item,"id"));
            MarketplaceVisuals.notice(host.frame,"Product Listing Saved",
                "Product details, selling price, photo, and visibility were saved successfully.",
                JOptionPane.INFORMATION_MESSAGE);host.renderPage();
        }catch(Exception e){host.error(e);}
    }
    private static final class ProductOption {
        final Map<String,String> item;
        ProductOption(Map<String,String> item){this.item=item;}
        @Override public String toString(){return v(item,"name")+" · "+v(item,"quantity")+" "+v(item,"unit");}
    }
    private MarketSellerEditor(){}
}
