package com.sakasmart;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/** Customer-facing public marketplace with PostgreSQL cart and ordering actions.
 * All data comes from the existing public-marketplace storage API. */
final class MarketBrowsePage extends JPanel {
    private static final Color DARK=new Color(6,77,42), GREEN=new Color(14,117,55),
        MUTED=new Color(28,82,48), PALE=new Color(240,251,233), OUTLINE=new Color(158,208,165);
    private final Main host;
    private final Storage store;
    private final boolean directory;
    private String currentSearch="", currentCategory="All Products";
    private Map<String,String> currentFarm;
    private JPanel productGrid;
    private JScrollPane scroll;

    MarketBrowsePage(Main host,boolean directory){
        super(new BorderLayout());this.host=host;this.store=host.store;this.directory=directory;
        setOpaque(false);renderDirectory("", "All Products");
    }
    private static String val(Map<String,String> row,String key){return Objects.toString(row.get(key),"");}
    private static String esc(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private static JLabel text(String s,int size,boolean strong){
        JLabel l=new JLabel(s);l.setFont(new Font("Segoe UI",strong?Font.BOLD:Font.PLAIN,size));l.setForeground(DARK);return l;
    }
    private static JLabel wrap(String value,int width,int size){
        JLabel l=new JLabel("<html><div style='width:"+width+"px'>"+esc(value)+"</div></html>");
        l.setFont(new Font("Segoe UI",Font.BOLD,size));l.setForeground(MUTED);l.setToolTipText(value);return l;
    }
    private static JPanel clear(LayoutManager layout){JPanel p=new JPanel(layout);p.setOpaque(false);return p;}
    private static JPanel vstack(){JPanel p=clear(null);p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));return p;}
    /** Prevents the main page from growing beyond its viewport at any DPI scale. */
    private static final class ViewportStack extends JPanel implements Scrollable {
        ViewportStack(){super();setOpaque(false);setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));}
        public Dimension getPreferredScrollableViewportSize(){return new Dimension(930,680);}
        public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction){return 25;}
        public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction){return Math.max(100,r.height-75);}
        public boolean getScrollableTracksViewportWidth(){return true;}
        public boolean getScrollableTracksViewportHeight(){return false;}
    }
    private static void append(JPanel p,JComponent c){c.setAlignmentX(Component.LEFT_ALIGNMENT);c.setMaximumSize(new Dimension(Integer.MAX_VALUE,c.getPreferredSize().height));p.add(c);}
    private static void gap(JPanel p,int h){JPanel sp=clear(new BorderLayout());sp.setPreferredSize(new Dimension(1,h));append(p,sp);}
    private static JButton action(String name){JButton b=GlossyTheme.button(name,GREEN);b.setFont(new Font("Segoe UI",Font.BOLD,13));return b;}
    private static final class GlossCard extends JPanel {
        GlossCard(LayoutManager layout,int top,int side){super(layout);setOpaque(false);setBorder(new EmptyBorder(top,side,top+3,side));}
        @Override protected void paintComponent(Graphics original){
            Graphics2D g=(Graphics2D)original.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();if(w>12&&h>12){
                g.setColor(new Color(13,95,39,25));g.fillRoundRect(3,5,w-7,h-8,24,24);
                g.setPaint(new GradientPaint(0,0,Color.WHITE,0,h,new Color(228,246,223)));g.fillRoundRect(1,1,w-5,h-7,24,24);
                g.setColor(OUTLINE);g.setStroke(new BasicStroke(1.35f));g.drawRoundRect(1,1,w-5,h-7,24,24);
                g.setColor(new Color(255,255,255,190));g.drawRoundRect(5,5,w-13,h-15,19,19);
            }g.dispose();super.paintComponent(original);
        }
    }
    private static JPanel card(LayoutManager layout,int top,int side){return new GlossCard(layout,top,side);}
    private static JLabel sectionHeading(String name){JLabel l=text(name,19,true);l.setBorder(new EmptyBorder(0,2,0,0));return l;}
    private static JPanel titledDivider(String label){JPanel p=clear(new BorderLayout(14,0));p.add(sectionHeading(label),BorderLayout.WEST);
        JSeparator line=new JSeparator();line.setForeground(OUTLINE);p.add(line,BorderLayout.CENTER);return p;}
    private static String shortText(String s,int limit){return s.length()<=limit?s:s.substring(0,limit-1)+"…";}
    private List<Map<String,String>> farms(){try{return store.publicMarketFarms();}catch(Exception e){host.error(e);return List.of();}}
    private List<Map<String,String>> products(){try{return store.publicMarketProducts();}catch(Exception e){host.error(e);return List.of();}}
    private static String category(Map<String,String> item){
        String s=(val(item,"name")+" "+val(item,"category")).toLowerCase(Locale.ROOT);
        if(s.matches(".*(egg|itlog|chicken|poultry|duck).*"))return "Poultry & Eggs";
        if(s.matches(".*(banana|saging|mango|mangga|papaya|pineapple|fruit).*"))return "Fruits";
        if(s.matches(".*(rice|palay|corn|mais|grain).*"))return "Grains";
        if(s.matches(".*(tomato|kamatis|talong|eggplant|pechay|cabbage|vegetable|onion|carrot|okra|lettuce).*"))return "Vegetables";
        return "Other";
    }
    private static boolean matchCategory(Map<String,String> item,String choice){return "All Products".equals(choice)||category(item).equals(choice);}
    private static JLabel pill(String content){JLabel l=text(content,12,true);l.setHorizontalAlignment(SwingConstants.CENTER);
        l.setOpaque(true);l.setBackground(new Color(224,246,210));l.setBorder(new EmptyBorder(9,11,9,11));return l;}
    private void show(JPanel content){
        removeAll();scroll=new JScrollPane(content);scroll.setBorder(null);scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);scroll.getVerticalScrollBar().setUnitIncrement(23);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        MarketplaceVisuals.scrollbar(scroll);
        add(scroll,BorderLayout.CENTER);revalidate();repaint();
        SwingUtilities.invokeLater(()->{
            scroll.getViewport().setViewPosition(new Point(0,0));
            SwingUtilities.invokeLater(()->scroll.getViewport().setViewPosition(new Point(0,0)));
        });
    }
    private String buyerFirstName(){
        String fullName=host.user==null?"":Objects.toString(host.user.get("full_name"),"").trim();
        if(fullName.isBlank())fullName=host.username();
        if(fullName.isBlank()||"Guest".equalsIgnoreCase(fullName))return "there";
        return fullName.split("\\s+")[0];
    }
    private static JTextArea readableParagraph(String value,int size){
        JTextArea area=new JTextArea(value);
        area.setFont(new Font("Segoe UI",Font.PLAIN,size));
        area.setForeground(new Color(27,78,47));
        area.setLineWrap(true);area.setWrapStyleWord(true);
        area.setEditable(false);area.setFocusable(false);area.setOpaque(false);
        area.setMargin(new Insets(0,0,0,0));area.setToolTipText(value);
        return area;
    }
    private static JComponent searchSymbol(){
        JComponent symbol=new JComponent(){
            @Override protected void paintComponent(Graphics original){
                Graphics2D g=(Graphics2D)original.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setStroke(new BasicStroke(2.3f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g.setColor(GREEN);g.drawOval(6,4,14,14);g.drawLine(18,17,26,25);g.dispose();
            }
        };
        symbol.setPreferredSize(new Dimension(31,31));return symbol;
    }
    private void renderDirectory(String filter,String categoryChoice){
        currentFarm=null;currentSearch=filter;currentCategory=categoryChoice;
        List<Map<String,String>> allFarms=farms(),allProducts=products();
        JPanel root=new ViewportStack();root.setBorder(new EmptyBorder(5,6,22,8));
        JPanel hero=card(new BorderLayout(0,5),13,20);
        hero.setPreferredSize(new Dimension(800,105));
        JPanel words=clear(new BorderLayout(0,7));
        JLabel greeting=text("Hi, "+buyerFirstName()+"!",24,true);
        words.add(greeting,BorderLayout.NORTH);
        words.add(text("Welcome back! Ready to discover fresh local farm products?",14,false),BorderLayout.CENTER);
        hero.add(words,BorderLayout.CENTER);
        append(root,hero);gap(root,9);

        JPanel searchCard=card(new BorderLayout(0,8),10,17);
        searchCard.setPreferredSize(new Dimension(800,105));
        JPanel searchLine=clear(new BorderLayout(9,0));
        JTextField input=new JTextField(filter);
        input.setFont(new Font("Segoe UI",Font.PLAIN,14));
        input.setForeground(DARK);input.setBackground(new Color(253,255,251));
        input.setCaretColor(GREEN);input.setPreferredSize(new Dimension(400,39));
        input.setToolTipText("Search farms, products, or categories...");
        JPanel inputPanel=clear(new BorderLayout(7,0));
        inputPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(OUTLINE,1,true),new EmptyBorder(4,12,4,8)));
        inputPanel.add(searchSymbol(),BorderLayout.WEST);inputPanel.add(input,BorderLayout.CENTER);
        searchLine.add(inputPanel,BorderLayout.CENTER);
        JButton search=action("Search");search.setPreferredSize(new Dimension(115,43));
        search.addActionListener(e->renderDirectory(input.getText().trim(),currentCategory));
        input.addActionListener(e->search.doClick());searchLine.add(search,BorderLayout.EAST);
        searchCard.add(searchLine,BorderLayout.NORTH);
        JPanel categories=clear(new FlowLayout(FlowLayout.LEFT,7,0));
        for(String c:List.of("All Products","Vegetables","Fruits","Poultry & Eggs","Grains","Other")){
            JButton b=new JButton(c);b.setFont(new Font("Segoe UI",Font.BOLD,12));
            b.setFocusPainted(false);b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            boolean active=c.equals(categoryChoice);
            b.setForeground(active?Color.WHITE:DARK);
            b.setBackground(active?GREEN:new Color(235,248,231));b.setOpaque(true);
            b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(active?GREEN:OUTLINE,1,true),
                new EmptyBorder(6,12,6,12)));
            b.addActionListener(e->renderDirectory(input.getText().trim(),c));categories.add(b);
        }
        searchCard.add(categories,BorderLayout.SOUTH);
        append(root,searchCard);gap(root,11);
        append(root,titledDivider("Explore Our Farms"));gap(root,7);
        String needle=filter.toLowerCase(Locale.ROOT);int found=0;
        for(Map<String,String> farm:allFarms){
            List<Map<String,String>> listed=new ArrayList<>();
            for(Map<String,String> item:allProducts)
                if(val(item,"farm_id").equals(val(farm,"id"))&&matchCategory(item,categoryChoice))listed.add(item);
            boolean matched=(val(farm,"name")+" "+val(farm,"location")+" "+val(farm,"market_description")).toLowerCase(Locale.ROOT).contains(needle);
            if(!matched)for(Map<String,String> item:listed)
                if(val(item,"name").toLowerCase(Locale.ROOT).contains(needle)){matched=true;break;}
            if(!matched||(listed.isEmpty()&&!categoryChoice.equals("All Products")))continue;
            found++;
            JPanel box=card(new BorderLayout(14,0),10,17);
            box.setPreferredSize(new Dimension(810,114));
            ImageCircle logo=new ImageCircle(val(farm,"market_logo_url"),val(farm,"name"),false);
            logo.setPreferredSize(new Dimension(74,74));box.add(logo,BorderLayout.WEST);
            JPanel info=clear(new BorderLayout(0,5));
            info.add(text(val(farm,"name"),19,true),BorderLayout.NORTH);
            JPanel body=clear(new BorderLayout(0,3));
            body.add(text("Location: "+val(farm,"location")+"  ·  "+listed.size()+" available product"+(listed.size()==1?"":"s"),12,true),BorderLayout.NORTH);
            String desc=val(farm,"market_description");
            if(desc.isBlank())desc="Get to know this farm and explore its available products.";
            JTextArea summary=readableParagraph(shortText(desc,125),12);
            summary.setRows(2);body.add(summary,BorderLayout.CENTER);
            info.add(body,BorderLayout.CENTER);box.add(info,BorderLayout.CENTER);
            JButton visit=action("View Farm");visit.setPreferredSize(new Dimension(123,40));
            visit.addActionListener(e->renderFarm(farm,"All Products"));
            logo.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            logo.addMouseListener(new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){renderFarm(farm,"All Products");}});
            JPanel side=clear(new GridBagLayout());side.add(visit);box.add(side,BorderLayout.EAST);
            append(root,box);gap(root,7);
        }
        if(found==0)append(root,wrap("No published farms match this search or category. Try another keyword or category.",540,13));
        show(root);
    }
    private void renderFarm(Map<String,String> farm,String filter){
        currentFarm=farm;List<Map<String,String>> items=products();
        JPanel root=new ViewportStack();root.setBorder(new EmptyBorder(5,6,26,8));
        JPanel top=clear(new FlowLayout(FlowLayout.LEFT,10,0));JButton back=action("← All Farms");top.add(back);
        back.addActionListener(e->renderDirectory(currentSearch,currentCategory));append(root,top);gap(root,12);
        String banner=val(farm,"market_banner_url");
        if(!banner.isBlank()){
            MarketBanner picture=new MarketBanner(banner);picture.setPreferredSize(new Dimension(800,140));append(root,picture);gap(root,12);
        }
        // Keep the farm description in its own bounded, wrapped area. HTML labels with
        // hardcoded pixel widths could paint outside the card at some Windows DPI scales.
        JPanel profile=card(new BorderLayout(20,0),20,22);
        profile.setPreferredSize(new Dimension(800,132));
        ImageCircle logo=new ImageCircle(val(farm,"market_logo_url"),val(farm,"name"),false);
        logo.setPreferredSize(new Dimension(88,88));profile.add(logo,BorderLayout.WEST);
        JPanel details=clear(new BorderLayout(0,9));
        details.add(text(val(farm,"name"),25,true),BorderLayout.NORTH);
        String description=val(farm,"market_description");
        if(description.isBlank())description="Meet your local farmer and explore freshly listed products.";
        JPanel facts=clear(new BorderLayout(0,7));
        JPanel information=clear(new GridLayout(0,1,0,3));
        information.add(text("Location: "+val(farm,"location"),13,true));
        String specialties=(val(farm,"crops")+", "+val(farm,"livestock")).replaceAll("^, |, $","");
        if(!specialties.isBlank())information.add(text("Specialties: "+shortText(specialties,65),13,true));
        facts.add(information,BorderLayout.NORTH);
        JTextArea descriptionText=new JTextArea(shortText(description,185),2,36);
        descriptionText.setFont(new Font("Segoe UI",Font.PLAIN,14));
        descriptionText.setForeground(new Color(23,77,44));
        descriptionText.setLineWrap(true);descriptionText.setWrapStyleWord(true);
        descriptionText.setEditable(false);descriptionText.setFocusable(false);
        descriptionText.setOpaque(false);descriptionText.setBorder(new EmptyBorder(2,0,0,4));
        descriptionText.setMinimumSize(new Dimension(100,35));
        descriptionText.setToolTipText(description);
        facts.add(descriptionText,BorderLayout.CENTER);
        details.add(facts,BorderLayout.CENTER);profile.add(details,BorderLayout.CENTER);
        append(root,profile);gap(root,9);
        append(root,titledDivider("Available Products  ·  Fresh Picks"));gap(root,7);
        List<Map<String,String>> farmItems=new ArrayList<>();for(Map<String,String> item:items)if(val(item,"farm_id").equals(val(farm,"id")))farmItems.add(item);
        if(farmItems.isEmpty())append(root,wrap("No products are published at the moment.",550,13));
        else{
            JPanel filters=clear(new FlowLayout(FlowLayout.LEFT,7,0));
            for(String category:List.of("All Products","Vegetables","Fruits","Poultry & Eggs","Grains","Other")){
                JButton b=action(category);b.setFont(new Font("Segoe UI",Font.BOLD,11));
                b.addActionListener(e->renderFarm(farm,category));filters.add(b);
            }
            append(root,filters);gap(root,5);
            List<Map<String,String>> visible=new ArrayList<>();for(Map<String,String> item:farmItems)if(matchCategory(item,filter))visible.add(item);
            if(visible.isEmpty())append(root,wrap("No products in this category.",500,13));
            else{
                // A responsive one/two-column grid. Never use zero-width roots to
                // guess number of columns before the window is displayed.
                productGrid=clear(new GridBagLayout());
                for(Map<String,String> item:visible){
                    // Seed the tile list before the first responsive layout.
                    productGrid.add(productCard(item));
                }
                updateProductGrid(productGrid,visible.size(),Math.max(700,getWidth()-55));
                append(root,productGrid);
                SwingUtilities.invokeLater(()->{
                    if(scroll!=null)updateProductGrid(productGrid,visible.size(),Math.max(330,scroll.getViewport().getWidth()-22));
                });
                root.addComponentListener(new ComponentAdapter(){
                    @Override public void componentResized(ComponentEvent e){
                        if(scroll!=null)updateProductGrid(productGrid,visible.size(),Math.max(330,scroll.getViewport().getWidth()-22));
                        root.revalidate();
                    }
                });
            }
        }
        show(root);
    }
    // Compact storefront cards: actions remain visible, even with one product.
    // A single product must remain a compact product tile, never a full-width panel.
    // GridBagLayout also keeps fixed-width cards aligned left when a category contains
    // fewer products than columns. This is what the older GridLayout could not do.
    private static final int PRODUCT_CARD_HEIGHT=290;
    private static final int PRODUCT_CARD_WIDTH=262;
    private static void updateProductGrid(JPanel grid,int count,int availableWidth){
        int width=Math.max(280,availableWidth);
        int columns=Math.max(1,Math.min(4,(width+16)/(PRODUCT_CARD_WIDTH+16)));
        Object previousWidthValue=grid.getClientProperty("market.width");
        int previousWidth=previousWidthValue instanceof Integer n?n:0;
        Object previousColumns=grid.getClientProperty("market.columns");
        if(previousWidth==width && Integer.valueOf(columns).equals(previousColumns))return;
        grid.putClientProperty("market.width",width);
        grid.putClientProperty("market.columns",columns);
        Component[] tiles=grid.getComponents();
        // Do not collect the layout's filler elements when recalculating.
        java.util.List<Component> cards=new ArrayList<>();
        for(Component tile:tiles)if(tile instanceof JComponent jc && Boolean.TRUE.equals(jc.getClientProperty("market.tile")))cards.add(tile);
        grid.removeAll();
        GridBagConstraints c=new GridBagConstraints();
        c.anchor=GridBagConstraints.NORTHWEST;c.fill=GridBagConstraints.NONE;
        c.weightx=0;c.weighty=0;c.insets=new Insets(0,0,16,16);
        for(int i=0;i<cards.size();i++){
            c.gridx=i%columns;c.gridy=i/columns;
            grid.add(cards.get(i),c);
        }
        // Elastic spacer occupies only unused space. Product cards stay 294 px wide.
        GridBagConstraints filler=new GridBagConstraints();
        filler.gridx=columns;filler.gridy=0;filler.gridheight=Math.max(1,(cards.size()+columns-1)/columns);
        filler.weightx=1;filler.weighty=1;filler.fill=GridBagConstraints.BOTH;
        grid.add(Box.createGlue(),filler);
        int rows=Math.max(1,(cards.size()+columns-1)/columns);
        grid.setPreferredSize(new Dimension(width,rows*(PRODUCT_CARD_HEIGHT+16)));
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE,rows*(PRODUCT_CARD_HEIGHT+16)));
        grid.revalidate();grid.repaint();
    }
    /** Bounded, card-first layout: buttons always occupy the bottom 40px.
     * No dynamically sized text area is permitted to consume the footer. */
    private JPanel productCard(Map<String,String> item){
        JPanel box=card(new BorderLayout(0,0),8,8);
        Dimension size=new Dimension(PRODUCT_CARD_WIDTH,PRODUCT_CARD_HEIGHT);
        box.setPreferredSize(size);box.setMinimumSize(size);box.setMaximumSize(size);
        box.putClientProperty("market.tile",Boolean.TRUE);
        JPanel top=clear(new BorderLayout());
        top.setPreferredSize(new Dimension(PRODUCT_CARD_WIDTH-16,93));
        JPanel imageStage=new JPanel(new GridBagLayout()){
            @Override protected void paintComponent(Graphics original){
                Graphics2D g=(Graphics2D)original.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setPaint(new GradientPaint(0,0,new Color(255,242,235),0,getHeight(),new Color(239,250,231)));
                g.fillRoundRect(0,0,getWidth(),getHeight(),16,16);g.dispose();
                super.paintComponent(original);
            }
        };
        imageStage.setOpaque(false);
        ImageCircle picture=new ImageCircle(val(item,"market_image_url"),val(item,"name"),true);
        picture.setPreferredSize(new Dimension(83,83));
        imageStage.add(picture);top.add(imageStage,BorderLayout.CENTER);
        picture.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        picture.addMouseListener(new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){showProductDetails(item);}});
        box.add(top,BorderLayout.NORTH);

        JPanel details=clear(new BorderLayout(0,3));
        details.setBorder(new EmptyBorder(6,5,0,5));
        JPanel titleBlock=clear(new GridLayout(3,1,0,2));
        JLabel name=text(shortText(val(item,"name"),25),16,true);
        name.setToolTipText(val(item,"name"));
        JLabel price=text("₱"+val(item,"market_price")+" / "+val(item,"unit"),17,true);
        price.setForeground(new Color(214,76,43));
        JLabel stock=text("Available: "+val(item,"quantity")+" "+val(item,"unit"),12,true);
        titleBlock.add(name);titleBlock.add(price);titleBlock.add(stock);
        details.add(titleBlock,BorderLayout.NORTH);
        String description=val(item,"market_description");
        if(description.isBlank())description="Fresh locally harvested produce.";
        JLabel excerpt=wrap(shortText(description,62),225,12);
        excerpt.setVerticalAlignment(SwingConstants.TOP);
        excerpt.setToolTipText(description);
        details.add(excerpt,BorderLayout.CENTER);
        box.add(details,BorderLayout.CENTER);

        JPanel actions=clear(new GridLayout(1,2,5,0));
        actions.setPreferredSize(new Dimension(PRODUCT_CARD_WIDTH-16,42));
        actions.setBorder(new EmptyBorder(2,1,1,1));
        JButton add=action("Add to Cart"),buy=action("Buy Now");
        add.setFont(new Font("Segoe UI",Font.BOLD,12));buy.setFont(new Font("Segoe UI",Font.BOLD,12));
        add.setMargin(new Insets(2,3,2,3));buy.setMargin(new Insets(2,3,2,3));
        add.addActionListener(e->CommerceUI.add(host.frame,host.username(),item,false));
        buy.addActionListener(e->CommerceUI.add(host.frame,host.username(),item,true));
        actions.add(add);actions.add(buy);
        box.add(actions,BorderLayout.SOUTH);
        return box;
    }
    private void showProductDetails(Map<String,String> item){
        JDialog dlg=new JDialog(host.frame,"Product Details",true);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel shell=card(new BorderLayout(15,15),22,24);
        JLabel heading=text("Product Details",23,true);
        shell.add(heading,BorderLayout.NORTH);
        JPanel body=clear(new BorderLayout(18,0));
        ImageCircle picture=new ImageCircle(val(item,"market_image_url"),val(item,"name"),true);
        picture.setPreferredSize(new Dimension(170,170));body.add(picture,BorderLayout.WEST);
        JPanel summary=clear(new BorderLayout(0,8));
        JLabel name=text(val(item,"name"),21,true);summary.add(name,BorderLayout.NORTH);
        JTextArea copy=new JTextArea("₱"+val(item,"market_price")+" / "+val(item,"unit")+"\n"+
            "Available: "+val(item,"quantity")+" "+val(item,"unit")+"\n\n"+
            val(item,"market_description"));
        copy.setFont(new Font("Segoe UI",Font.PLAIN,15));copy.setForeground(DARK);
        copy.setOpaque(false);copy.setEditable(false);copy.setLineWrap(true);copy.setWrapStyleWord(true);
        summary.add(copy,BorderLayout.CENTER);body.add(summary,BorderLayout.CENTER);
        shell.add(body,BorderLayout.CENTER);
        JPanel actions=clear(new GridLayout(1,3,12,0));
        JButton add=action("Add to Cart"),buy=action("Buy Now"),close=action("Close");
        add.addActionListener(e->{dlg.dispose();CommerceUI.add(host.frame,host.username(),item,false);});
        buy.addActionListener(e->{dlg.dispose();CommerceUI.add(host.frame,host.username(),item,true);});
        close.addActionListener(e->dlg.dispose());
        actions.add(add);actions.add(buy);actions.add(close);
        shell.add(actions,BorderLayout.SOUTH);
        dlg.setContentPane(shell);dlg.setSize(710,370);dlg.setLocationRelativeTo(host.frame);
        dlg.getRootPane().registerKeyboardAction(e->dlg.dispose(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        dlg.setVisible(true);
    }
    private static final class FarmLandscape extends JComponent {
        FarmLandscape(){setPreferredSize(new Dimension(235,110));}
        @Override protected void paintComponent(Graphics original){Graphics2D g=(Graphics2D)original.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();g.setColor(new Color(255,219,95));g.fillOval(w-76,8,48,48);
            g.setColor(new Color(113,188,100));g.fillOval(4,53,w,110);g.setColor(new Color(49,150,74));g.fillOval(68,73,w,104);
            g.setColor(new Color(200,226,92));for(int i=0;i<9;i++)g.fillPolygon(new int[]{i*28,i*28+16,i*28+22},new int[]{h,74,h},3);
            g.dispose();}
    }
    private static final class MarketBanner extends JComponent {
        private volatile BufferedImage image;
        MarketBanner(String ref){new SwingWorker<BufferedImage,Void>(){
            protected BufferedImage doInBackground(){return MarketPhotoAssets.load(ref);}
            protected void done(){try{image=get();}catch(Exception ignore){}repaint();}
        }.execute();}
        @Override protected void paintComponent(Graphics original){Graphics2D g=(Graphics2D)original.create();
            int w=getWidth(),h=getHeight();g.setPaint(new GradientPaint(0,0,new Color(214,242,193),0,h,new Color(48,157,70)));
            g.fillRoundRect(0,0,w,h,20,20);if(image!=null){g.setClip(new RoundRectangle2D.Float(0,0,w,h,20,20));
                double scale=Math.max((double)w/image.getWidth(),(double)h/image.getHeight());int iw=(int)(image.getWidth()*scale),ih=(int)(image.getHeight()*scale);
                g.drawImage(image,(w-iw)/2,(h-ih)/2,iw,ih,null);}g.dispose();}
    }
    /** Uploaded photos take precedence; if absent, render genuine vector illustration, not fake stock photos. */
    private static final class ImageCircle extends JComponent {
        private volatile BufferedImage picture;private final String name;private final boolean product;
        ImageCircle(String ref,String name,boolean product){this.name=name;this.product=product;setOpaque(false);
            setToolTipText(name);if(ref!=null&&!ref.isBlank())new SwingWorker<BufferedImage,Void>(){
                protected BufferedImage doInBackground(){return MarketPhotoAssets.load(ref);}
                protected void done(){try{picture=get();}catch(Exception ignore){}repaint();}
            }.execute();}
        @Override protected void paintComponent(Graphics original){Graphics2D g=(Graphics2D)original.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int d=Math.min(getWidth(),getHeight())-9,x=(getWidth()-d)/2,y=(getHeight()-d)/2;
            g.setColor(new Color(20,111,46,45));g.fillOval(x+3,y+5,d,d);
            g.setColor(new Color(249,255,245));g.fillOval(x,y,d,d);
            g.setColor(new Color(58,155,75));g.setStroke(new BasicStroke(3));g.drawOval(x+1,y+1,d-2,d-2);
            g.setColor(new Color(186,225,183));g.setStroke(new BasicStroke(1));g.drawOval(x+6,y+6,d-12,d-12);
            if(picture!=null){g.setClip(new Ellipse2D.Double(x+8,y+8,d-16,d-16));
                double scale=Math.min((double)(d-21)/picture.getWidth(),(double)(d-21)/picture.getHeight());
                int iw=(int)Math.ceil(picture.getWidth()*scale),ih=(int)Math.ceil(picture.getHeight()*scale);
                g.drawImage(picture,x+(d-iw)/2,y+(d-ih)/2,iw,ih,null);g.setClip(null);
            }else if(product){
                Graphics2D artwork=(Graphics2D)g.create();
                artwork.setClip(new Ellipse2D.Double(x+9,y+9,d-18,d-18));
                drawProductIcon(artwork,x+10,y+10,d-20,name);
                artwork.dispose();
            }
            else{
                String[] words=name.trim().split("\\s+");StringBuilder initials=new StringBuilder();for(String w:words)if(!w.isBlank()&&initials.length()<2)initials.append(Character.toUpperCase(w.charAt(0)));
                g.setFont(new Font("Segoe UI",Font.BOLD,Math.max(18,d/3)));g.setColor(DARK);
                String s=initials.length()==0?"F":initials.toString();FontMetrics fm=g.getFontMetrics();
                g.drawString(s,x+(d-fm.stringWidth(s))/2,y+(d+fm.getAscent()-fm.getDescent())/2);
            }g.dispose();}
        private static void drawProductIcon(Graphics2D g,int x,int y,int d,String name){
            String n=name.toLowerCase(Locale.ROOT);g.translate(x,y);double scale=d/100.0;g.scale(scale,scale);
            if(n.contains("egg")||n.contains("itlog")){
                // All tray edges and eggs stay inside the 100x100 local artboard,
                // which itself fits entirely within the circular image mask.
                // Draw the tray first, then the eggs, then the front lip.
                g.setColor(new Color(119,89,54));
                g.fillRoundRect(13,47,74,35,14,14);
                g.setPaint(new GradientPaint(15,47,new Color(219,183,121),85,81,new Color(154,111,66)));
                g.fillRoundRect(16,45,68,33,13,13);
                g.setColor(new Color(131,104,66));
                for(int xx=27;xx<=73;xx+=23){
                    g.fillOval(xx-11,59,22,11);
                }
                for(int[] pos:new int[][]{{28,37},{50,34},{72,37}}){
                    int xx=pos[0],yy=pos[1];
                    g.setPaint(new GradientPaint(xx-10,yy-19,new Color(255,246,218),xx+11,yy+22,new Color(197,137,81)));
                    g.fillOval(xx-12,yy-17,24,38);
                    g.setColor(new Color(154,105,63));
                    g.setStroke(new BasicStroke(1.2f));
                    g.drawOval(xx-12,yy-17,24,38);
                }
                g.setPaint(new GradientPaint(12,62,new Color(233,195,139),84,83,new Color(146,100,58)));
                g.fillRoundRect(12,63,76,17,8,8);
                g.setColor(new Color(126,87,50));
                g.setStroke(new BasicStroke(1.5f));
                g.drawRoundRect(12,63,76,17,8,8);
                g.setColor(new Color(184,145,94));
                for(int xx=25;xx<=75;xx+=25)g.drawLine(xx,65,xx,78);
            }else if(n.contains("tomato")||n.contains("kamatis")){
                g.setPaint(new GradientPaint(30,25,new Color(255,110,69),65,78,new Color(189,24,30)));
                g.fillOval(20,30,60,52);g.setColor(new Color(46,141,43));g.fillOval(46,24,8,18);
                g.fillPolygon(new int[]{28,50,72,59,48},new int[]{33,43,33,49,27},5);
            }else if(n.contains("corn")||n.contains("mais")){
                g.rotate(-.35,52,51);g.setColor(new Color(251,193,49));g.fillRoundRect(37,16,32,72,23,23);
                g.setColor(new Color(56,158,71));g.fillArc(24,33,55,68,190,120);
                g.setColor(new Color(255,230,120));for(int yy=25;yy<79;yy+=9)for(int xx=43;xx<65;xx+=9)g.fillOval(xx,yy,5,6);
            }else if(n.contains("banana")||n.contains("saging")){
                g.setColor(new Color(249,204,46));g.setStroke(new BasicStroke(15,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g.draw(new QuadCurve2D.Float(22,34,35,87,83,47));g.setColor(new Color(178,136,40));g.setStroke(new BasicStroke(2));g.draw(new QuadCurve2D.Float(18,29,35,87,86,43));
            }else{
                g.setColor(new Color(98,174,76));g.fillOval(24,46,52,34);g.setColor(new Color(39,124,59));
                g.setStroke(new BasicStroke(4));g.drawLine(50,69,50,24);
                g.fillOval(24,23,27,16);g.fillOval(50,29,29,17);
            }
        }
    }
}
