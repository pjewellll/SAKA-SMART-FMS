package com.sakasmart;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;

import com.sakasmart.Main.RoundedPanel;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.List;
import java.util.function.*;
import java.util.zip.*;

/** SAKA-SMART -- Java 21, no third-party UI libraries. */
public final class Main {
    static final Color DARK=new Color(9,65,32), DEEP=new Color(5,45,23), GREEN=new Color(20,137,54);
    static final Color LIGHT=new Color(225,247,196), TEXT=new Color(17,62,32), MUTED=new Color(74,107,67);
    static final Color BG=new Color(244,250,227), ORANGE=new Color(224,146,33), RED=new Color(205,67,60), BLUE=new Color(51,127,183);
    static final Color LIME=new Color(201,241,26), LEAF=new Color(57,181,74), EMERALD=new Color(21,128,61), FOREST=new Color(11,93,42);
    private static final Image GREEN_THEME_TEXTURE=loadGreenThemeTexture();
    private static Image loadGreenThemeTexture(){
        Path p=Paths.get("assets","green-theme-texture.png");
        if(!Files.isRegularFile(p))return null;
        return new ImageIcon(p.toString()).getImage();
    }
    static final Font FONT=new Font("Segoe UI",Font.PLAIN,14), BOLD=new Font("Segoe UI",Font.BOLD,14);
    Storage store;
    private Storage backend;
    JFrame frame;
    Map<String,String> user;
    private JPanel sidebar, pageHolder;
    private String route="Dashboard";
    // Selected tab inside administrator User Management. No separate password route.
    String usersTab="accounts";
    boolean inventoryHarvest=false, calendarLivestock=false;
    YearMonth calendarMonth=YearMonth.now();
    private String lastSearch="";

    // Login-only visual motion. No authentication, networking or database state is stored here.
    private final LoginMotion loginMotion = new LoginMotion();
    private javax.swing.Timer loginAnimationTimer;

    private void stopLoginAnimation(){
        if(loginAnimationTimer!=null){loginAnimationTimer.stop();loginAnimationTimer=null;}
    }

    private void startLoginAnimation(JPanel loginRoot){
        stopLoginAnimation();
        if(!loginMotion.enabled)return;
        final long startNanos=System.nanoTime()-(long)(loginMotion.seconds*1_000_000_000L);
        loginAnimationTimer=new javax.swing.Timer(40,e->{ // ~25 FPS; coalesced Swing EDT ticks
            if(frame==null || frame.getContentPane()!=loginRoot){stopLoginAnimation();return;}
            if(!frame.isShowing() || (frame.getExtendedState()&Frame.ICONIFIED)!=0)return;
            loginMotion.seconds=(System.nanoTime()-startNanos)/1_000_000_000.0;
            if(loginMotion.stage!=null && loginMotion.seconds<2.2)loginMotion.stage.doLayout();
            loginRoot.repaint();
        });
        loginAnimationTimer.setCoalesce(true);
        loginAnimationTimer.start();
    }

    private static final class LoginMotion {
        double seconds=0;
        boolean enabled=!Boolean.getBoolean("saka.reduceMotion");
        AnimatedLoginStage stage;
        double time(){return enabled?seconds:0;}
        double entrance(double delay,double duration){
            if(!enabled)return 1.0;
            double p=Math.max(0,Math.min(1,(seconds-delay)/duration));
            return 1-Math.pow(1-p,3); // soft upward ease, no overshoot or flashing
        }
    }

    /** Lightweight reveal: paints contents rising with opacity, but never changes input listeners. */
    private static final class RiseReveal extends JPanel {
        private final JComponent content;
        private final LoginMotion motion;
        private final double delay;
        RiseReveal(JComponent content,LoginMotion motion,double delay){
            super(new BorderLayout());this.content=content;this.motion=motion;this.delay=delay;
            setOpaque(false);setAlignmentX(Component.LEFT_ALIGNMENT);
            add(content,BorderLayout.CENTER);
        }
        @Override public Dimension getPreferredSize(){return content.getPreferredSize();}
        @Override public Dimension getMinimumSize(){return content.getMinimumSize();}
        @Override public Dimension getMaximumSize(){return content.getMaximumSize();}
        @Override protected void paintChildren(Graphics g){
            double p=motion.entrance(delay,.68);
            if(p>=.999){super.paintChildren(g);return;}
            if(p<=0)return;
            Graphics2D a=(Graphics2D)g.create();
            a.setComposite(AlphaComposite.SrcOver.derive((float)p));
            a.translate(0,(1-p)*24);
            super.paintChildren(a);
            a.dispose();
        }
    }

    /** Positions the entire working login card for its initial cinematic upward entrance. */
    private static final class AnimatedLoginStage extends LoginBackground {
        private final LoginMotion motion;
        AnimatedLoginStage(LoginMotion motion){super(true,new GridBagLayout(),motion);this.motion=motion;}
        @Override public void doLayout(){
            super.doLayout();
            double p=motion.entrance(.04,.98);
            int offset=(int)Math.round((1-p)*110);
            if(offset==0)return;
            for(Component c:getComponents())if(c instanceof GlassLoginCard){
                c.setLocation(c.getX(),c.getY()+offset);
            }
        }
    }


    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale.enabled","true");
        SwingUtilities.invokeLater(()->new Main().start());
    }
    private void start() {
        try {
            String mode=System.getenv().getOrDefault("SAKA_MODE","demo");
            store="postgres".equalsIgnoreCase(mode)?new PostgresStorage():new DemoStorage();
            backend=store;
        } catch(Exception e) {
            StyledDialogs.showMessageDialog(null,"Unable to initialize SAKA-SMART database.\n\n"+message(e)+"\n\nFor PostgreSQL mode, first create the database and place the PostgreSQL JDBC JAR in lib/.\nYou can still run run-demo.bat.","Database Connection Error",JOptionPane.ERROR_MESSAGE);
            return;
        }
        UIManager.put("OptionPane.messageFont",FONT);
        UIManager.put("OptionPane.buttonFont",FONT);
        frame=new JFrame("SAKA-SMART | Farm Management System");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1050,760));
        frame.setSize(1450,870);frame.setLocationRelativeTo(null);
        showLogin();frame.setVisible(true);
    }
    private static String message(Exception e) {
        Throwable t=e;while(t.getCause()!=null)t=t.getCause();
        return Objects.toString(t.getMessage(),e.getClass().getSimpleName());
    }
    static JLabel label(String s,int size,boolean bold,Color color) {
        JLabel v=new JLabel(s);v.setFont(new Font("Segoe UI",bold?Font.BOLD:Font.PLAIN,size));v.setForeground(color);return v;
    }
    static JPanel pane(LayoutManager layout){JPanel p=new JPanel(layout);p.setOpaque(false);return p;}
    static void pad(JComponent c,int t,int l,int b,int r){c.setBorder(new EmptyBorder(t,l,b,r));}
    static JButton btn(String s,Color color) {
        JButton b=new JButton(s){
            @Override protected void paintComponent(Graphics g){
                Graphics2D x=(Graphics2D)g.create();x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                x.setColor(getModel().isPressed()?color.darker():getModel().isRollover()?color.brighter():color);
                x.fillRoundRect(0,0,getWidth(),getHeight(),14,14);x.dispose();super.paintComponent(g);
            }
        };
        b.setForeground(Color.WHITE);b.setFont(BOLD);b.setContentAreaFilled(false);b.setBorderPainted(false);b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(9,16,9,16));b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));b.setOpaque(false);return b;
    }
    static JButton secondary(String text){
        JButton b=btn(text,new Color(107,127,115));return b;
    }
    static class RoundedPanel extends JPanel {
        final Color color;final int arc;
        RoundedPanel(Color color,int arc,LayoutManager layout){super(layout);this.color=color;this.arc=arc;setOpaque(false);}
        @Override protected void paintComponent(Graphics g) {
            Graphics2D x=(Graphics2D)g.create();x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            x.setColor(color);x.fillRoundRect(0,0,getWidth(),getHeight(),arc,arc);x.dispose();super.paintComponent(g);
        }
    }
    static JPanel card(LayoutManager l){return new RoundedPanel(new Color(253,255,244),22,l);}
    static JComponent spacer(int h){JPanel p=pane(new BorderLayout());p.setPreferredSize(new Dimension(1,h));p.setMaximumSize(new Dimension(Integer.MAX_VALUE,h));return p;}
    static void stretch(JComponent c){c.setAlignmentX(Component.LEFT_ALIGNMENT);c.setMaximumSize(new Dimension(Integer.MAX_VALUE,Short.MAX_VALUE));}
    static String today(){return LocalDate.now().toString();}
    List<Map<String,String>> rows(String table){try{return store.all(table);}catch(Exception e){error(e);return List.of();}}
    void error(Exception e){StyledDialogs.showMessageDialog(frame,message(e),"SAKA-SMART Error",JOptionPane.ERROR_MESSAGE);}
    void info(String msg){StyledDialogs.showMessageDialog(frame,msg,"SAKA-SMART",JOptionPane.INFORMATION_MESSAGE);}
    String username(){return user==null?"Guest":user.getOrDefault("username","Guest");}
    String role(){return user==null?"":user.getOrDefault("role","");}
    boolean admin(){return "SYSTEM_ADMIN".equals(role());}
    boolean editable(String name) {
        if(admin()||"FARM_OWNER".equals(role()))return true;
        return switch(name){
            case "inventory" -> "FARM_MANAGER".equals(role())||"INVENTORY_STAFF".equals(role());
            case "events","production" -> "FARM_MANAGER".equals(role());
            case "transactions","sales" -> "FARM_MANAGER".equals(role())||"BOOKKEEPER".equals(role());
            default -> false;
        };
    }

    // -------------------------------- LOGIN V3: LIVING FARM CINEMATIC ENTRANCE --------------------------------
    // Presentation-only redesign. All authentication/database operations remain unchanged.
    private void showLogin() {
        stopLoginAnimation();
        loginMotion.seconds=0;
        frame.setTitle("SAKA-SMART | Farm Management System");
        final Color ink = new Color(9, 62, 44);
        // Original two-column design: full branding on left, half-logo glass login on right.
        JPanel root = new JPanel(new GridLayout(1, 2, 0, 0));
        root.setOpaque(true);

        JPanel left = new LoginBackground(false, new BorderLayout(0, 10), loginMotion);
        left.setBorder(new EmptyBorder(34, 30, 30, 30));
        JComponent logo = new JComponent() {
            private final Image image = loadLoginLogo();
            @Override protected void paintComponent(Graphics g) {
                Graphics2D x = (Graphics2D) g.create();
                x.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                x.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                if (image != null && image.getWidth(this)>0 && image.getHeight(this)>0) {
                    int iw=image.getWidth(this), ih=image.getHeight(this);
                    double scale=Math.min((getWidth()-22.0)/iw,(getHeight()-22.0)/ih);
                    int w=(int)(iw*scale), h=(int)(ih*scale);
                    x.drawImage(image,(getWidth()-w)/2,(getHeight()-h)/2,w,h,this);
                } else {
                    x.setColor(ink);x.setFont(new Font("Segoe UI",Font.BOLD,35));
                    x.drawString("SAKA-SMART",Math.max(8,getWidth()/2-135),getHeight()/2);
                }
                x.dispose();
            }
        };
        logo.setPreferredSize(new Dimension(520,450));
        left.add(new RiseReveal(logo,loginMotion,.08), BorderLayout.CENTER);
        JPanel bottomLeft=pane(new BorderLayout(0,19));
        JLabel motto=label("Manage   |   Produce   |   Grow   |   Sustain",17,true,ink);
        motto.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel mottoRow=pane(new BorderLayout(8,0));
        mottoRow.add(motto,BorderLayout.CENTER);
        JButton animationToggle=new JButton(loginMotion.enabled?"Pause motion":"Play motion");
        animationToggle.setFont(new Font("Segoe UI",Font.BOLD,10));
        animationToggle.setForeground(new Color(10,90,49));
        animationToggle.setContentAreaFilled(false);
        animationToggle.setBorderPainted(false);
        animationToggle.setFocusPainted(false);
        animationToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        animationToggle.setToolTipText("Toggle decorative login animations (Alt+M)");
        Runnable switchMotion=()->{
            loginMotion.enabled=!loginMotion.enabled;
            animationToggle.setText(loginMotion.enabled?"Pause motion":"Play motion");
            if(loginMotion.enabled){
                loginMotion.seconds=Math.max(loginMotion.seconds,2.4); // no replay on unpausing
                startLoginAnimation(root);
            }else stopLoginAnimation();
            if(loginMotion.stage!=null)loginMotion.stage.doLayout();
            root.repaint();
        };
        animationToggle.addActionListener(e->switchMotion.run());
        animationToggle.setMnemonic(KeyEvent.VK_M);
        mottoRow.add(animationToggle,BorderLayout.EAST);
        bottomLeft.add(mottoRow,BorderLayout.NORTH);
        JPanel benefits=pane(new GridLayout(1,4,10,0));
        String[] benefitNames={"Healthy<br>Farms","Productive<br>Communities","Sustainable<br>Future","Greener<br>Tomorrow"};
        String[] benefitIcons={"leaf","chart","group","globe"};
        for(int i=0;i<4;i++) {
            JPanel b=pane(new BorderLayout(0,3));
            b.add(new LoginIcon(benefitIcons[i],29,new Color(10,112,61)),BorderLayout.CENTER);
            JLabel t=label("<html><center>"+benefitNames[i]+"</center></html>",11,true,ink);
            t.setHorizontalAlignment(SwingConstants.CENTER);
            b.add(t,BorderLayout.SOUTH);
            benefits.add(b);
        }
        benefits.setPreferredSize(new Dimension(500,70));
        bottomLeft.add(benefits,BorderLayout.CENTER);
        bottomLeft.add(new GlossBand(loginMotion),BorderLayout.SOUTH);
        left.add(bottomLeft,BorderLayout.SOUTH);
        root.add(left);

        AnimatedLoginStage rightStage=new AnimatedLoginStage(loginMotion);
        loginMotion.stage=rightStage;
        rightStage.setBorder(new EmptyBorder(8,12,8,12));
        JPanel card=new GlassLoginCard(new BorderLayout(0,7),loginMotion);
        card.setPreferredSize(new Dimension(525,535));
        card.setBorder(new EmptyBorder(10,35,10,35));

        // The top HALF of the user's actual logo replaces the old "Welcome to" heading.
        // Asset: assets/saka-smart-half-logo.png (transparent PNG).
        JComponent halfLogo = new JComponent() {
            private final Image image = loadHalfLoginLogo();
            @Override protected void paintComponent(Graphics g) {
                Graphics2D x=(Graphics2D)g.create();
                x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                x.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                if(image!=null && image.getWidth(this)>0 && image.getHeight(this)>0){
                    int iw=image.getWidth(this),ih=image.getHeight(this);
                    double scale=Math.min((getWidth()-12.0)/iw,(getHeight()-10.0)/ih);
                    int dw=(int)Math.round(iw*scale),dh=(int)Math.round(ih*scale);
                    x.drawImage(image,(getWidth()-dw)/2,(getHeight()-dh)/2,dw,dh,this);
                }else{
                    x.setFont(new Font("Segoe UI",Font.BOLD,31));
                    x.setColor(new Color(8,83,38));
                    String title="SAKA-SMART";
                    FontMetrics fm=x.getFontMetrics();
                    x.drawString(title,(getWidth()-fm.stringWidth(title))/2,getHeight()/2);
                }
                x.dispose();
            }
        };
        halfLogo.setPreferredSize(new Dimension(440,72));
        card.add(new RiseReveal(halfLogo,loginMotion,.20),BorderLayout.NORTH);

        JPanel center=new JPanel();center.setOpaque(false);
        center.setLayout(new BoxLayout(center,BoxLayout.Y_AXIS));
        JPanel toggles=pane(new GridLayout(1,2,5,0));
        toggles.setBorder(new EmptyBorder(3,5,3,5));
        JToggleButton farmerChoice=new GlassLoginChoice("User Login","For Farmers / Staff",true,loginMotion);
        JToggleButton adminChoice=new GlassLoginChoice("Admin Login","For System Administrators",false,loginMotion);
        ButtonGroup choices=new ButtonGroup();choices.add(farmerChoice);choices.add(adminChoice);
        farmerChoice.addActionListener(e->{farmerChoice.repaint();adminChoice.repaint();});
        adminChoice.addActionListener(e->{farmerChoice.repaint();adminChoice.repaint();});
        toggles.add(farmerChoice);toggles.add(adminChoice);
        toggles.setMaximumSize(new Dimension(Integer.MAX_VALUE,48));
        toggles.setPreferredSize(new Dimension(380,48));
        toggles.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(new RiseReveal(toggles,loginMotion,.35));
        center.add(spacer(4));

        JLabel loginError=label(" ",12,true,new Color(181,34,39));
        loginError.setMaximumSize(new Dimension(Integer.MAX_VALUE,17));
        loginError.setPreferredSize(new Dimension(400,17));
        loginError.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(loginError);
        JPanel fields=new GlassInner(new GridBagLayout());
        fields.setBorder(new EmptyBorder(8,13,8,13));
        GridBagConstraints c=new GridBagConstraints();
        c.gridx=0;c.gridy=0;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;
        c.insets=new Insets(0,0,4,0);
        fields.add(label("Username",12,true,ink),c);
        JTextField usernameField=new JTextField();
        usernameField.setFont(new Font("Segoe UI",Font.PLAIN,13));
        usernameField.setForeground(ink);usernameField.setOpaque(false);
        usernameField.setBorder(new EmptyBorder(0,6,0,4));
        JPanel userInput=new LoginInput("user",usernameField,"Enter your username",loginMotion);
        userInput.setPreferredSize(new Dimension(340,36));
        c.gridy++;c.insets=new Insets(0,0,8,0);fields.add(userInput,c);
        c.gridy++;c.insets=new Insets(0,0,4,0);fields.add(label("Password",12,true,ink),c);
        JPasswordField passwordField=new JPasswordField();
        passwordField.setFont(new Font("Segoe UI",Font.PLAIN,13));
        passwordField.setForeground(ink);passwordField.setOpaque(false);
        passwordField.setBorder(new EmptyBorder(0,6,0,4));
        JPanel passwordInput=new LoginInput("lock",passwordField,"Enter your password",loginMotion);
        passwordInput.setPreferredSize(new Dimension(340,36));
        JButton eye=new JButton("Show");
        eye.setFont(new Font("Segoe UI",Font.BOLD,11));eye.setForeground(new Color(20,89,60));
        eye.setContentAreaFilled(false);eye.setBorderPainted(false);eye.setFocusPainted(false);
        eye.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        char mask=passwordField.getEchoChar();
        eye.addActionListener(e->{boolean hidden=passwordField.getEchoChar()!=(char)0;
            passwordField.setEchoChar(hidden?(char)0:mask);eye.setText(hidden?"Hide":"Show");});
        passwordInput.add(eye,BorderLayout.EAST);
        c.gridy++;c.insets=new Insets(0,0,8,0);fields.add(passwordInput,c);
        JPanel options=pane(new BorderLayout(2,0));
        JCheckBox remember=new JCheckBox("Remember me");
        remember.setOpaque(false);remember.setFont(new Font("Segoe UI",Font.PLAIN,11));
        remember.setForeground(ink);
        remember.setIcon(new LoginCheckIcon(false));remember.setSelectedIcon(new LoginCheckIcon(true));
        JButton forgot=new JButton("Forgot Password?");
        forgot.setFont(new Font("Segoe UI",Font.BOLD,11));forgot.setForeground(ink);
        forgot.setContentAreaFilled(false);forgot.setBorderPainted(false);forgot.setFocusPainted(false);
        forgot.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        forgot.addActionListener(e->showRecoveryOptions());
        options.add(remember,BorderLayout.WEST);options.add(forgot,BorderLayout.EAST);
        c.gridy++;c.insets=new Insets(0,0,0,0);fields.add(options,c);
        java.util.prefs.Preferences prefs=java.util.prefs.Preferences.userNodeForPackage(Main.class);
        String saved=prefs.get("remembered_username","");
        if(!saved.isBlank()){usernameField.setText(saved);remember.setSelected(true);}
        watch(usernameField,()->loginError.setText(" "));
        watch(passwordField,()->loginError.setText(" "));
        fields.setMaximumSize(new Dimension(Integer.MAX_VALUE,180));
        fields.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(new RiseReveal(fields,loginMotion,.52));
        center.add(spacer(4));

        JButton enter=new GlossLoginButton("Log In   →",true,loginMotion);
        enter.setPreferredSize(new Dimension(375,43));enter.setMaximumSize(new Dimension(Integer.MAX_VALUE,43));
        enter.setMinimumSize(new Dimension(270,43));
        enter.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(new RiseReveal(enter,loginMotion,.75));
        center.add(spacer(4));
        JPanel or=new JPanel(new BorderLayout(12,0));or.setOpaque(false);
        JLabel orLabel=label("OR",10,false,new Color(56,108,78));orLabel.setHorizontalAlignment(SwingConstants.CENTER);
        or.add(new LoginSeparator(),BorderLayout.WEST);or.add(orLabel,BorderLayout.CENTER);
        or.add(new LoginSeparator(),BorderLayout.EAST);
        or.setMaximumSize(new Dimension(Integer.MAX_VALUE,14));
        or.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(new RiseReveal(or,loginMotion,.83));
        center.add(spacer(4));
        JButton register=new GlossLoginButton("+   Create a New Account",false,loginMotion);
        register.setPreferredSize(new Dimension(375,38));register.setMaximumSize(new Dimension(Integer.MAX_VALUE,38));
        register.setMinimumSize(new Dimension(270,38));
        register.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(new RiseReveal(register,loginMotion,.94));

        card.add(center,BorderLayout.CENTER);

        JPanel footer=pane(new BorderLayout(0,4));
        footer.setBorder(new EmptyBorder(0,0,9,0)); // Keep social buttons within the visible glass frame.
        JLabel continueWith=label("CONTINUE WITH",11,true,ink);continueWith.setHorizontalAlignment(SwingConstants.CENTER);
        footer.add(continueWith,BorderLayout.NORTH);
        JPanel social=pane(new GridLayout(1,3,7,0));
        for(String service:new String[]{"Google","Facebook","Phone Number"}){
            JButton b=new SocialPreviewButton(service,loginMotion);
            b.setEnabled(true);
            b.setForeground(new Color(9,68,43));
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.setToolTipText("Requires verified account and provider configuration.");
            b.addActionListener(e->{
                if("Phone Number".equals(service))phoneSignIn();
                else socialSignIn(service.toUpperCase(Locale.ROOT));
            });
            social.add(b);
        }
        social.setPreferredSize(new Dimension(360,29));footer.add(social,BorderLayout.CENTER);
        card.add(new RiseReveal(footer,loginMotion,1.06),BorderLayout.SOUTH);

        enter.addActionListener(e->{
            String login=usernameField.getText().trim();char[] pw=passwordField.getPassword();
            try{
                Map<String,String> matched=null;
                for(Map<String,String> u:rows("users"))if(login.equalsIgnoreCase(u.get("username"))){matched=u;break;}
                if(matched==null||!Security.verify(pw,matched.getOrDefault("password_hash",""))){
                    loginError.setText("Incorrect username or password.");return;}
                if(!Boolean.parseBoolean(matched.getOrDefault("active","false"))){
                    loginError.setText("Your farm membership is awaiting owner approval.");return;}
                boolean isAdmin="SYSTEM_ADMIN".equals(matched.get("role"));
                if(isAdmin!=adminChoice.isSelected()){loginError.setText("Select the correct User or Admin tab.");return;}
                if(remember.isSelected())prefs.put("remembered_username",login);else prefs.remove("remembered_username");
                loginUser(matched);
            }finally{Arrays.fill(pw,'\0');}
        });
        passwordField.addActionListener(e->enter.doClick());
        register.addActionListener(e->register());
        GridBagConstraints gc=new GridBagConstraints();gc.gridx=0;gc.gridy=0;gc.anchor=GridBagConstraints.CENTER;
        rightStage.add(card,gc);
        root.add(rightStage);
        frame.setContentPane(root);
        frame.revalidate();frame.repaint();
        startLoginAnimation(root);
    }

    /** All artwork is procedural and uses the existing green-theme-texture PNG as its base. */
    private static class LoginBackground extends JPanel {
        private final boolean right;
        private final LoginMotion motion;
        private java.awt.image.BufferedImage scaledTexture;
        private int cacheW=-1,cacheH=-1;

        LoginBackground(boolean right,LayoutManager layout,LoginMotion motion){
            super(layout);this.right=right;this.motion=motion;setOpaque(false);
        }

        private void paintBase(Graphics2D x,int w,int h){
            if(GREEN_THEME_TEXTURE!=null && GREEN_THEME_TEXTURE.getWidth(this)>0){
                if(scaledTexture==null || cacheW!=w || cacheH!=h){
                    cacheW=w;cacheH=h;
                    scaledTexture=new java.awt.image.BufferedImage(Math.max(1,w),Math.max(1,h),java.awt.image.BufferedImage.TYPE_INT_RGB);
                    Graphics2D cache=scaledTexture.createGraphics();
                    cache.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    cache.drawImage(GREEN_THEME_TEXTURE,0,0,w,h,this);
                    cache.dispose();
                }
                x.drawImage(scaledTexture,0,0,null);
                x.setColor(right?new Color(222,255,185,55):new Color(242,255,210,150));
                x.fillRect(0,0,w,h);
            }else{
                x.setPaint(new GradientPaint(0,0,LIME,0,h,FOREST));x.fillRect(0,0,w,h);
                x.setColor(right?new Color(235,255,209,45):new Color(245,255,219,130));
                x.fillRect(0,0,w,h);
            }
        }

        @Override protected void paintComponent(Graphics g){
            Graphics2D x=(Graphics2D)g.create();
            x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();
            if(w<1||h<1){x.dispose();return;}
            paintBase(x,w,h);
            double time=motion.time();
            float pulse=(float)(0.5+0.5*Math.sin(time*.85));
            float glowX=(float)(right?w*.66:w*.78),glowY=(float)(right?h*.16:h*.17);
            float glowRadius=Math.max(w,h)*.49f;
            x.setPaint(new RadialGradientPaint(new Point2D.Float(glowX,glowY),glowRadius,
                new float[]{0f,.45f,1f},new Color[]{new Color(255,254,185,38+(int)(25*pulse)),
                    new Color(241,255,187,15),new Color(255,255,190,0)}));
            x.fillRect(0,0,w,h);
            // Soft drifting clouds; deliberately translucent so the logos remain readable.
            for(int i=0;i<(right?2:3);i++){
                double drift=motion.enabled?time*(right?2.2:3.0):0;
                double cx=(((i*245.0+90+drift)%(w+290))+w+290)%(w+290)-140;
                double cy=65+i*57+Math.sin(time*.18+i)*3;
                paintCloud(x,cx,cy,.68+(i%2)*.18,right?21:36);
            }
            int pageW=getParent()!=null?getParent().getWidth():w;
            int pageX=getX();
            // A rising sun and a small flock of birds give the farm real motion and depth.
            if(!right) paintMovingSun(x,w,h,time,motion.enabled);
            paintBirds(x,pageX,w,pageW,h,time,motion.enabled);
            // Slow golden motes in the corners of the image.
            for(int i=0;i<(right?11:17);i++){
                double px=w*(.045+((i*41)%100)/110.0);
                double py=h*(.09+((i*27)%100)/115.0);
                double offset=motion.enabled?Math.sin(time*.42+i*2.9)*4:0;
                float size=(float)(1.6+(i%3)*.6);
                x.setColor(new Color(255,248,178,right?26:46));
                x.fill(new Ellipse2D.Double(px,py+offset,size,size));
            }
            if(!right){
                // Keep a single main seedling and remove the extra tiny growing leaves for a cleaner look.
                paintGrowingSeedling(x,w*.91,h*.69,time,2.0,motion.enabled,1.0);
                for(int i=0;i<13;i++){
                    double base=(i<7?i*16.0:w-(i-7)*16.0);
                    double sway=motion.enabled?Math.sin(time*.72+i*.53)*2.2:0;
                    double top=h-42-(i%5)*13;
                    x.setStroke(new BasicStroke(1.25f));
                    x.setColor(new Color(15,98,42,26));
                    x.draw(new QuadCurve2D.Double(base,h,base+sway-4,top+19,base+sway,top));
                }
            }else{
                for(int i=0;i<4;i++){
                    double lx=i%2==0?22+i*6:w-28-i*8;
                    double ly=(i*148+time*4.5)%(h+90)-45;
                    paintLeaf(x,lx+Math.sin(time*.35+i)*5,ly,.46,Math.sin(time*.45+i)*.42,18);
                }
            }
            x.dispose();super.paintComponent(g);
        }

        /** Slowly orbiting sun with visible golden rays (procedural vector graphics). */
        private static void paintMovingSun(Graphics2D g,int w,int h,double t,boolean animate){
            double x=w*.86+(animate?Math.sin(t*.08)*18:0);
            double y=h*.125+(animate?Math.sin(t*.07+.6)*6:0);
            double breathe=animate?(.5+.5*Math.sin(t*.55)):.55;
            Graphics2D a=(Graphics2D)g.create();
            float r=105;
            a.setPaint(new RadialGradientPaint(new Point2D.Double(x,y),r,
                new float[]{0f,.3f,1f},new Color[]{new Color(255,239,120,110+(int)(20*breathe)),
                new Color(255,239,150,45),new Color(255,239,150,0)}));
            a.fill(new Ellipse2D.Double(x-r,y-r,r*2,r*2));
            a.setStroke(new BasicStroke(2.4f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            a.setColor(new Color(248,192,53,140));
            for(int i=0;i<12;i++){
                double ang=i*Math.PI/6+(animate?Math.sin(t*.11)*.06:0);
                double ix=x+30*Math.cos(ang),iy=y+30*Math.sin(ang);
                double ox=x+38*Math.cos(ang),oy=y+38*Math.sin(ang);
                a.draw(new Line2D.Double(ix,iy,ox,oy));
            }
            a.setPaint(new GradientPaint((float)x-20,(float)y-18,new Color(255,255,189,230),
                (float)x+20,(float)y+24,new Color(255,181,57,220)));
            a.fill(new Ellipse2D.Double(x-24,y-24,48,48));
            a.dispose();
        }

        /** Curved flight paths with animated V-shaped wings; travels across the whole page more softly. */
        private static void paintBirds(Graphics2D g,int panelX,int panelW,int pageW,int h,double t,boolean animate){
            Graphics2D a=(Graphics2D)g.create();
            a.setStroke(new BasicStroke(2.1f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            for(int i=0;i<3;i++){
                double worldX=animate?((t*(9.0+i*1.5)+i*240)%(pageW+240))-110:(pageW*.12+i*145);
                double x=worldX-panelX;
                double y=h*(.082+.028*(i%3))+Math.sin(t*.24+i*.85)*6;
                double wings=animate?Math.sin(t*3.1+i*1.45):.18;
                double spread=11+(i%2)*2;
                double up=6+2.6*wings;
                a.setColor(new Color(13,79,51,96+i*12));
                a.draw(new QuadCurve2D.Double(x-spread,y-up,x-spread*.40,y-up*.58,x,y));
                a.draw(new QuadCurve2D.Double(x,y,x+spread*.40,y-up*.58,x+spread,y-up));
            }
            a.dispose();
        }

        /** Each cycle has a germination phase, stem growth, leaf opening, then a short rest. */
        private static void paintGrowingSeedling(Graphics2D g,double x,double base,double t,
                                                 double offset,boolean animate,double scale){
            double cycle=animate?(t*.7+offset)%10:5.5;
            double grow=Math.max(0,Math.min(1,(cycle-.30)/3.6));
            grow=1-Math.pow(1-grow,2);
            double hide=cycle>9.15?Math.max(0,(10-cycle)/.85):1;
            Graphics2D a=(Graphics2D)g.create();
            a.translate(x,base);a.scale(scale,scale);
            a.setComposite(AlphaComposite.SrcOver.derive((float)hide));
            a.setColor(new Color(72,63,28,125));
            a.fill(new Ellipse2D.Double(-22,-4,44,10));
            a.setColor(new Color(108,69,30,218));
            a.fill(new Ellipse2D.Double(-4,-5,8,5));
            if(grow>.025){
                double tall=83*grow;
                double sway=animate?Math.sin(t*1.25+offset)*5*grow:0;
                a.setStroke(new BasicStroke(3.3f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                a.setColor(new Color(26,117,46,230));
                a.draw(new QuadCurve2D.Double(0,-3,-7,-tall*.60,sway,-tall));
                for(int i=0;i<4;i++){
                    double at=(.3+.18*i);
                    if(grow<at*.85)continue;
                    double leafOpen=Math.min(1,(grow-at*.85)/.34);
                    double side=i%2==0?-1:1;
                    double yy=-tall*at;
                    double xx=sway*at-3;
                    a.setColor(i%2==0?new Color(25,139,55,225):new Color(47,164,61,235));
                    a.translate(xx,yy);a.rotate(side*.35);
                    a.fill(new Ellipse2D.Double(side<0?-22*leafOpen:0,-11*leafOpen,22*leafOpen,11*leafOpen));
                    a.rotate(-side*.35);a.translate(-xx,-yy);
                }
            }
            a.dispose();
        }

        private static void paintCloud(Graphics2D g,double cx,double cy,double scale,int alpha){
            Graphics2D a=(Graphics2D)g.create();
            a.translate(cx,cy);a.scale(scale,scale);
            a.setColor(new Color(255,255,238,alpha));
            a.fill(new Ellipse2D.Double(-60,0,120,27));
            a.fill(new Ellipse2D.Double(-36,-15,53,43));
            a.fill(new Ellipse2D.Double(0,-11,46,36));
            a.dispose();
        }

        private static void paintLeaf(Graphics2D g,double x,double y,double size,double rotation,int opacity){
            Graphics2D a=(Graphics2D)g.create();
            a.translate(x,y);a.rotate(rotation);a.scale(size,size);
            a.setColor(new Color(34,126,48,Math.min(255,Math.max(0,opacity))));
            a.fill(new Ellipse2D.Double(-8,-3,16,8));
            a.setColor(new Color(247,255,196,Math.min(255,Math.max(0,opacity/2))));
            a.draw(new Line2D.Double(-7,1,7,1));
            a.dispose();
        }
    }

    /** Premium glass effect with a low-opacity light sweep and breathing edge highlight. */
    private static class GlassLoginCard extends JPanel {
        private final LoginMotion motion;
        GlassLoginCard(LayoutManager layout,LoginMotion motion){super(layout);this.motion=motion;setOpaque(false);}
        @Override public void paint(Graphics g){
            double p=motion.entrance(.06,.78);
            if(p<=0)return;
            if(p>=.999){super.paint(g);return;}
            Graphics2D a=(Graphics2D)g.create();
            a.setComposite(AlphaComposite.SrcOver.derive((float)p));
            super.paint(a);a.dispose();
        }
        @Override protected void paintComponent(Graphics g){
            Graphics2D x=(Graphics2D)g.create();
            x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();
            if(w<42||h<42){x.dispose();return;}
            for(int i=11;i>=1;i--){
                x.setColor(new Color(5,51,22,Math.max(3,14-i)));
                x.fillRoundRect(9-i/2,12-i/2,w-19+i,h-24+i,41+i,41+i);
            }
            // Stacked translucent shadows and a dark-green outer bevel create actual layer depth.
            x.setColor(new Color(3,49,24,110));x.fillRoundRect(7,13,w-14,h-20,43,43);
            x.setColor(new Color(5,57,24,85));x.fillRoundRect(8,12,w-16,h-20,43,43);
            x.setPaint(new GradientPaint(0,5,new Color(235,251,221,248),w,h,new Color(103,176,106,246)));
            x.fillRoundRect(5,5,w-14,h-20,42,42);
            x.setPaint(new GradientPaint(0,0,new Color(255,255,255,115),0,h*.57f,new Color(255,255,255,6)));
            x.fillRoundRect(9,9,w-22,h/2,39,39);
            // Shimmer is clipped to the card; it never covers mouse or keyboard events.
            Shape oldClip=x.getClip();
            x.clip(new RoundRectangle2D.Double(9,9,w-23,h-29,39,39));
            float shift=(float)((motion.time()*43)%(w+240)-120);
            x.setPaint(new LinearGradientPaint(shift,8,shift+125,h/2.0f,
                new float[]{0f,.48f,1f},new Color[]{new Color(255,255,255,0),
                new Color(255,255,245,18),new Color(255,255,255,0)}));
            x.fillRect(9,9,w-23,h-29);x.setClip(oldClip);
            float glow=(float)(.5+.5*Math.sin(motion.time()*.82));
            x.setColor(new Color(249,255,243,180+(int)(55*glow)));
            x.setStroke(new BasicStroke(2.4f));x.drawRoundRect(5,5,w-15,h-21,42,42);
            x.setColor(new Color(23,93,45,65));x.setStroke(new BasicStroke(1.1f));
            x.drawRoundRect(9,9,w-23,h-29,39,39);
            x.setColor(new Color(255,255,245,155));x.fillOval(29,21,8,17);
            // Interior left edge + lower bevel: restrained 3D gloss, readable controls.
            x.setColor(new Color(255,255,255,80));x.setStroke(new BasicStroke(1.7f));
            x.draw(new Line2D.Double(17,55,17,h-55));
            x.setColor(new Color(6,73,39,48));
            x.setStroke(new BasicStroke(1.25f));
            x.draw(new RoundRectangle2D.Double(16,16,w-34,h-46,31,31));
            x.dispose();super.paintComponent(g);
        }
    }
    private static class GlassInner extends JPanel {
        GlassInner(LayoutManager layout){super(layout);setOpaque(false);}
        @Override protected void paintComponent(Graphics g){
            Graphics2D x=(Graphics2D)g.create();x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();
            x.setColor(new Color(255,255,246,60));x.fillRoundRect(0,0,w-1,h-1,22,22);
            x.setColor(new Color(246,255,240,110));x.setStroke(new BasicStroke(1.2f));x.drawRoundRect(1,1,w-3,h-3,22,22);
            x.dispose();super.paintComponent(g);
        }
    }
    private static class LoginInput extends JPanel {
        private final JTextField text;
        private final String prompt;
        private final LoginMotion motion;
        LoginInput(String glyph,JTextField input,String placeholder,LoginMotion motion){
            super(new BorderLayout(4,0));setOpaque(false);text=input;prompt=placeholder;this.motion=motion;
            setBorder(new EmptyBorder(4,11,4,5));
            LoginIcon icon=new LoginIcon(glyph,18,new Color(15,86,66));
            icon.setPreferredSize(new Dimension(21,24));add(icon,BorderLayout.WEST);
            add(input,BorderLayout.CENTER);
            input.getDocument().addDocumentListener(new DocumentListener(){
                public void insertUpdate(DocumentEvent e){repaint();}public void removeUpdate(DocumentEvent e){repaint();}
                public void changedUpdate(DocumentEvent e){repaint();}
            });
            input.addFocusListener(new FocusAdapter(){@Override public void focusGained(FocusEvent e){repaint();} @Override public void focusLost(FocusEvent e){repaint();}});
        }
        @Override protected void paintComponent(Graphics g){
            Graphics2D x=(Graphics2D)g.create();x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();
            x.setPaint(new GradientPaint(0,0,new Color(250,255,235,250),0,h,new Color(223,246,203,245)));
            x.fillRoundRect(0,0,w-1,h-1,15,15);
            int focusGlow=text.isFocusOwner()?(int)(130+60*(.5+.5*Math.sin(motion.time()*3.5))):42;
            x.setColor(new Color(18,102,64,focusGlow));x.setStroke(new BasicStroke(text.isFocusOwner()?1.7f:0.7f));
            x.drawRoundRect(1,1,w-3,h-3,15,15);
            if(text.getDocument().getLength()==0&&!text.isFocusOwner()){
                x.setFont(new Font("Segoe UI",Font.PLAIN,11));x.setColor(new Color(97,132,110));
                x.drawString(prompt,48,h/2+4);
            }
            x.dispose();super.paintComponent(g);
        }
    }
    private static class GlossLoginButton extends JButton {
        private final boolean primary;
        private final LoginMotion motion;
        private float hoverAmount;
        GlossLoginButton(String title,boolean primary,LoginMotion motion){
            super(title);this.primary=primary;this.motion=motion;
            setFont(new Font("Segoe UI",Font.BOLD,primary?16:12));
            setForeground(primary?Color.WHITE:new Color(12,79,54));
            setContentAreaFilled(false);setFocusPainted(false);setBorderPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));setRolloverEnabled(true);
        }
        @Override protected void paintComponent(Graphics g){
            Graphics2D x=(Graphics2D)g.create();x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();
            float target=getModel().isRollover()?1f:0f;
            hoverAmount=motion.enabled?hoverAmount+(target-hoverAmount)*.24f:target;
            float hover=hoverAmount;
            x.translate(0,-1.5*hover);
            x.setColor(new Color(20,76,45,40+(int)(28*hover)));x.fillRoundRect(2,5,w-4,h-6,18,18);
            if(primary){
                x.setPaint(new LinearGradientPaint(1,1,w-2,h-5,new float[]{0f,.35f,.7f,1f},
                    new Color[]{new Color(63,191,74),new Color(28,157,62),new Color(12,111,48),new Color(7,76,35)}));
                x.fillRoundRect(1,1,w-3,h-7,18,18);
                x.setPaint(new GradientPaint(0,1,new Color(255,255,255,135),0,h*.53f,new Color(255,255,255,0)));
                x.fillRoundRect(3,2,w-7,h/2,16,16);
            }else{
                x.setPaint(new GradientPaint(0,0,new Color(206,236,196,220),0,h,new Color(167,209,164,230)));
                x.fillRoundRect(1,1,w-3,h-7,18,18);
            }
            if(hover>0){
                x.setColor(new Color(255,255,230,(int)(33*hover)));
                x.fillRoundRect(2,2,w-5,h-9,17,17);
            }
            x.setColor(primary?new Color(243,255,244,200):new Color(39,122,78,90));
            x.setStroke(new BasicStroke(1.2f));x.drawRoundRect(2,2,w-5,h-9,17,17);
            if(getModel().isPressed()){
                x.setColor(new Color(1,35,24,50));x.fillRoundRect(2,2,w-5,h-9,17,17);
            }
            super.paintComponent(x);x.dispose();
        }
    }

    private static class GlassLoginChoice extends JToggleButton {
        private final LoginMotion motion;
        GlassLoginChoice(String heading,String description,boolean selected,LoginMotion motion){
            super("<html><center><b>"+heading+"</b><br><font size='2'>"+description+"</font></center></html>");
            this.motion=motion;setSelected(selected);setFont(new Font("Segoe UI",Font.PLAIN,12));
            setForeground(new Color(9,65,45));setContentAreaFilled(false);
            setBorderPainted(false);setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));setRolloverEnabled(true);
        }
        @Override protected void paintComponent(Graphics g){
            Graphics2D x=(Graphics2D)g.create();x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();
            if(isSelected()){
                x.setColor(new Color(8,81,50,50));x.fillRoundRect(1,5,w-2,h-6,17,17);
                x.setPaint(new GradientPaint(0,0,new Color(56,181,74),w,h,new Color(8,93,43)));
                x.fillRoundRect(1,1,w-2,h-6,17,17);
                x.setPaint(new GradientPaint(0,0,new Color(255,255,255,130),0,h*.54f,new Color(255,255,255,0)));
                x.fillRoundRect(3,2,w-6,h/2,16,16);
                x.setColor(new Color(255,255,255,225));x.drawRoundRect(1,1,w-3,h-7,17,17);
            }else{
                x.setColor(new Color(249,255,246,77));x.fillRoundRect(1,1,w-2,h-6,17,17);
                x.setColor(new Color(245,255,243,125));x.drawRoundRect(1,1,w-3,h-7,17,17);
            }
            if(getModel().isRollover()){
                int alpha=motion.enabled?(int)(25+12*Math.sin(motion.time()*3)):28;
                x.setColor(new Color(255,255,255,Math.max(0,alpha)));
                x.fillRoundRect(2,2,w-5,h-8,17,17);
            }
            setForeground(isSelected()?Color.WHITE:new Color(10,68,46));
            super.paintComponent(x);x.dispose();
        }
    }

    private static class SocialPreviewButton extends JButton {
        private final LoginMotion motion;
        private float hoverAmount;
        SocialPreviewButton(String name,LoginMotion motion){
            super(name);this.motion=motion;setFont(new Font("Segoe UI",Font.BOLD,10));
            setForeground(new Color(82,118,92));setContentAreaFilled(false);setBorderPainted(false);
            setFocusPainted(false);setRolloverEnabled(true);
        }
        @Override protected void paintComponent(Graphics g){
            Graphics2D x=(Graphics2D)g.create();x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();
            float target=getModel().isRollover()?1f:0f;
            hoverAmount=motion.enabled?hoverAmount+(target-hoverAmount)*.24f:target;
            double glow=hoverAmount;
            x.setColor(new Color(241,250,235,160+(int)(50*glow)));
            x.fillRoundRect(0,0,w-1,h-1,15,15);
            x.setColor(new Color(250,255,250,200));x.drawRoundRect(1,1,w-3,h-3,15,15);
            if(glow>0){x.setColor(new Color(32,142,77,(int)(65*glow)));
                x.setStroke(new BasicStroke(1.2f));x.drawRoundRect(1,1,w-3,h-3,15,15);}
            super.paintComponent(x);x.dispose();
        }
    }

    private static class GlossBand extends JPanel {
        private final LoginMotion motion;
        GlossBand(LoginMotion motion){this.motion=motion;setOpaque(false);setPreferredSize(new Dimension(10,43));}
        @Override protected void paintComponent(Graphics g){
            Graphics2D x=(Graphics2D)g.create();x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();if(w<2||h<2){x.dispose();return;}
            x.setPaint(new LinearGradientPaint(0,0,w,0,new float[]{0f,.42f,.6f,1f},
                new Color[]{new Color(201,241,26),new Color(69,190,46),new Color(28,152,50),new Color(9,93,42)}));
            x.fillRoundRect(0,0,w-1,h-1,22,22);
            Shape clip=x.getClip();x.clip(new RoundRectangle2D.Double(1,1,w-2,h-3,22,22));
            float shift=(float)((motion.time()*65)%(w+200)-100);
            x.setPaint(new LinearGradientPaint(shift,0,shift+115,h,
                new float[]{0f,.5f,1f},new Color[]{new Color(255,255,255,0),
                    new Color(255,255,220,52),new Color(255,255,255,0)}));
            x.fillRect(0,0,w,h);x.setClip(clip);
            x.setColor(new Color(255,255,255,100));x.drawRoundRect(1,1,w-3,h-4,21,21);
            x.dispose();
        }
    }
    private static class LoginSeparator extends JPanel {
        LoginSeparator(){setOpaque(false);setPreferredSize(new Dimension(85,15));}
        @Override protected void paintComponent(Graphics g){
            Graphics2D x=(Graphics2D)g.create();x.setColor(new Color(97,145,108,70));
            x.drawLine(0,getHeight()/2,getWidth(),getHeight()/2);x.dispose();
        }
    }
    private static class LoginCheckIcon implements javax.swing.Icon {
        private final boolean checked;
        LoginCheckIcon(boolean checked){this.checked=checked;}
        public int getIconWidth(){return 18;}public int getIconHeight(){return 18;}
        public void paintIcon(Component c,Graphics g,int x,int y){
            Graphics2D a=(Graphics2D)g.create();a.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            a.setColor(checked?new Color(11,106,62):new Color(234,246,232));a.fillRoundRect(x+1,y+2,14,14,4,4);
            a.setColor(new Color(45,125,74));a.drawRoundRect(x+1,y+2,14,14,4,4);
            if(checked){a.setColor(Color.WHITE);a.setStroke(new BasicStroke(1.7f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                a.drawLine(x+4,y+9,x+7,y+12);a.drawLine(x+7,y+12,x+13,y+6);}
            a.dispose();
        }
    }
    private static class LoginIcon extends JComponent {
        private final String kind;private final int size;private final Color color;
        LoginIcon(String kind,int size,Color color){this.kind=kind;this.size=size;this.color=color;setOpaque(false);setPreferredSize(new Dimension(size+8,size+8));}
        @Override protected void paintComponent(Graphics g){
            Graphics2D x=(Graphics2D)g.create();x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            double s=Math.min(size,Math.min(getWidth(),getHeight())-2)/24.0;
            double tx=(getWidth()-24*s)/2,ty=(getHeight()-24*s)/2;
            x.translate(tx,ty);x.scale(s,s);x.setColor(color);
            x.setStroke(new BasicStroke(1.8f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            switch(kind){
                case "user" -> {x.fillOval(8,2,8,8);x.fillRoundRect(4,12,16,9,9,9);}
                case "lock" -> {x.drawRoundRect(6,10,12,11,3,3);x.drawArc(8,3,8,12,0,180);x.drawLine(12,14,12,17);}
                case "leaf" -> {x.fillOval(12,3,10,12);x.fillOval(3,8,12,9);x.drawLine(5,21,18,6);}
                case "chart" -> {x.fillRoundRect(3,14,4,7,2,2);x.fillRoundRect(10,9,4,12,2,2);x.fillRoundRect(17,4,4,17,2,2);}
                case "group" -> {x.fillOval(8,2,8,8);x.fillOval(1,7,6,6);x.fillOval(17,7,6,6);x.fillRoundRect(5,12,14,10,4,4);x.fillRoundRect(0,15,5,8,2,2);x.fillRoundRect(19,15,5,8,2,2);}
                case "globe" -> {x.drawOval(2,2,20,20);x.drawOval(8,2,8,20);x.drawLine(2,12,22,12);x.drawLine(5,6,19,6);x.drawLine(5,18,19,18);}
                default -> x.fillOval(7,7,10,10);
            }
            x.dispose();
        }
    }
    private static Image loadLoginLogo(){
        try{
            Path file=Paths.get("assets","saka-smart-logo.png");
            if(Files.isRegularFile(file))return javax.imageio.ImageIO.read(file.toFile());
            java.net.URL resource=Main.class.getResource("/assets/saka-smart-logo.png");
            if(resource!=null)return javax.imageio.ImageIO.read(resource);
        }catch(IOException ignored){}
        return null;
    }
    private static Image loadHalfLoginLogo(){
        try{
            Path file=Paths.get("assets","saka-smart-half-logo.png");
            if(Files.isRegularFile(file))return javax.imageio.ImageIO.read(file.toFile());
            java.net.URL resource=Main.class.getResource("/assets/saka-smart-half-logo.png");
            if(resource!=null)return javax.imageio.ImageIO.read(resource);
        }catch(IOException ignored){}
        return null;
    }
    // -------------------- PASSWORD RECOVERY (PHASE 2: ADMIN / EMAIL / SMS) --------------------
    private void showRecoveryOptions(){
        Object[] choices={"Ask Administrator", "Enter Recovery Code", "Email Recovery", "SMS Recovery", "Cancel"};
        int selection=StyledDialogs.showOptionDialog(frame,
            "Choose a way to recover your SAKA-SMART account.\n"+
            "An administrator can help even when you do not have email or SMS.",
            "Forgot Password",JOptionPane.DEFAULT_OPTION,JOptionPane.QUESTION_MESSAGE,
            null,choices,choices[0]);
        switch(selection){
            case 0 -> submitRecoveryRequest();
            case 1 -> enterRecoveryCode();
            case 2 -> requestRemoteRecovery("EMAIL");
            case 3 -> requestRemoteRecovery("SMS");
            default -> { }
        }
    }
    /** Remote recovery never shows whether the supplied username is registered. */
    private void requestRemoteRecovery(String channel) {
        if(!DeliveryService.configured(channel)) {
            info(channel+" recovery needs provider configuration on this computer.\n"+
                "Ask your administrator to configure the delivery provider, or use Ask Administrator.");
            return;
        }
        JTextField name=new JTextField();styleInput(name);
        JPanel form=pane(new GridLayout(0,1,0,10));
        form.add(label("Enter your SAKA-SMART username",14,true,DARK));form.add(name);
        form.add(label("A code is sent only when a verified "+channel.toLowerCase(Locale.ROOT)+" contact exists.",12,false,MUTED));
        if(StyledDialogs.showConfirmDialog(frame,form,"Send "+channel+" Recovery Code",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        String login=name.getText().trim();
        if(login.isBlank()){info("Please enter your username.");return;}
        frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<Boolean,Void>() {
            @Override protected Boolean doInBackground() throws Exception {
                RecoveryDelivery delivery=store.issueRecoveryToVerifiedContact(login,channel);
                if(delivery==null)return true; // Do not reveal missing/unverified accounts.
                try {
                    DeliveryService.send(channel,delivery.destination(),delivery.code());
                    return true;
                } catch(Exception ex) {
                    try{store.cancelRecoveryDelivery(delivery.requestId());}catch(Exception ignored){}
                    return false;
                }
            }
            @Override protected void done() {
                frame.setCursor(Cursor.getDefaultCursor());
                try {
                    if(Boolean.TRUE.equals(get()))info("If this account has a verified "+channel.toLowerCase(Locale.ROOT)+
                        " contact and is eligible, a recovery code has been requested.\n"+
                        "Check that contact, then choose Forgot Password > Enter Recovery Code.\n"+
                        "Codes expire after "+RecoveryCodes.LIFETIME_MINUTES+" minutes.");
                    else info("The "+channel+" provider could not accept the request.\n"+
                        "Please try later or ask your administrator for assistance.");
                }catch(Exception ex){error(new Exception("Unable to process the recovery request. Please try again later."));}
            }
        }.execute();
    }

    private void submitRecoveryRequest(){
        JTextField usernameField=new JTextField();styleInput(usernameField);
        JPanel box=pane(new GridLayout(0,1,0,8));
        box.add(label("Enter your SAKA-SMART username",13,true,DARK));box.add(usernameField);
        box.add(label("Your administrator will verify your identity before approving.",11,false,MUTED));
        if(StyledDialogs.showConfirmDialog(frame,box,"Request Password Assistance",
            JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
        String requestedUsername=usernameField.getText().trim();
        if(requestedUsername.isBlank()){info("Please enter a username.");return;}
        try{
            store.requestPasswordReset(requestedUsername);
            // Do not reveal whether a username exists.
            info("If the account exists and is active, your request has been recorded.\n"+
                "Please contact your farm administrator for identity verification.\n"+
                "Once approved, use Forgot Password > Enter Recovery Code.");
        }catch(Exception e){error(e);}
    }
    private void enterRecoveryCode(){
        JTextField usernameField=new JTextField(),codeField=new JTextField();
        JPasswordField password=new JPasswordField(),confirm=new JPasswordField();
        for(JTextField f:List.of(usernameField,codeField,password,confirm))styleInput(f);
        JPanel box=pane(new GridLayout(0,1,0,5));
        box.add(label("Username",12,true,DARK));box.add(usernameField);
        box.add(label("One-time recovery code",12,true,DARK));box.add(codeField);
        box.add(label("New password (8+ characters)",12,true,DARK));box.add(password);
        box.add(label("Confirm new password",12,true,DARK));box.add(confirm);
        if(StyledDialogs.showConfirmDialog(frame,box,"Set a New Password",
            JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
        char[] first=password.getPassword(),second=confirm.getPassword();
        try{
            if(usernameField.getText().isBlank() || codeField.getText().isBlank() || first.length<8 ||
                    !Arrays.equals(first,second)){
                info("Enter your username, code, and matching passwords of at least 8 characters.");return;
            }
            if(store.redeemPasswordReset(usernameField.getText().trim(),codeField.getText().trim(),first)){
                store.log("Account Recovery","Recovery completed for account (sensitive values omitted)");
                info("Your password was changed successfully. You can now log in.");
            }else info("The recovery code is invalid, expired, already used, or locked.\n"+
                "Ask your administrator for a new code if needed.");
        }catch(Exception e){error(e);}finally{Arrays.fill(first,'\0');Arrays.fill(second,'\0');}
    }

    static void styleInput(JTextField tf){tf.setFont(FONT);tf.setPreferredSize(new Dimension(200,38));tf.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(200,217,205)),new EmptyBorder(8,12,8,12)));}
    static void styleToggle(AbstractButton b){b.setFont(BOLD);b.setFocusPainted(false);b.setBackground(new Color(221,242,228));b.setForeground(DARK);b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));}
    private void loginUser(Map<String,String> account){
        if(!Boolean.parseBoolean(account.getOrDefault("active","false"))){
            info("Your membership request is still pending approval by the farm owner.");return;
        }
        user=new HashMap<>(account);
        store=new ScopedStorage(backend,user);
        store.log(username(),"Signed in to SAKA-SMART");
        route="Dashboard";showShell();
    }

    /** Separate onboarding for independent farm owners and applicants joining a farm. */
    private void register(){register(null);}
    private void register(SocialLoginService.Profile social){
        Object[] options={"Create My Own Farm","Join Existing Farm","Cancel"};
        int mode=StyledDialogs.showOptionDialog(frame,
            "Choose how you want to use SAKA-SMART.","Create an Account",
            JOptionPane.DEFAULT_OPTION,JOptionPane.QUESTION_MESSAGE,null,options,options[0]);
        if(mode!=0 && mode!=1)return;
        JTextField name=new JTextField(social==null?"":social.name());
        JTextField login=new JTextField();
        JPasswordField password=new JPasswordField();
        JTextField email=new JTextField(social==null?"":social.email());
        for(JTextField f:List.of(name,login,password,email))styleInput(f);
        JPanel fields=pane(new GridLayout(0,1,0,6));
        fields.add(label("Full name",13,true,DARK));fields.add(name);
        fields.add(label("Choose username",13,true,DARK));fields.add(login);
        if(social==null){fields.add(label("Password (8+ characters)",13,true,DARK));fields.add(password);}
        fields.add(label("Recovery email (optional, not verified yet)",12,true,DARK));fields.add(email);
        JTextField farmName=new JTextField(),location=new JTextField();JComboBox<FarmOption> farmChoice=null;
        if(mode==0){
            styleInput(farmName);styleInput(location);
            fields.add(label("Your farm's name",13,true,DARK));fields.add(farmName);
            fields.add(label("Farm location",13,true,DARK));fields.add(location);
        }else{
            try{
                java.util.List<Map<String,String>> farms=backend.publicFarms();
                if(farms.isEmpty()){info("There are no existing farms. Choose Create My Own Farm instead.");return;}
                farmChoice=new JComboBox<>();
                for(Map<String,String> farm:farms)farmChoice.addItem(new FarmOption(v(farm,"id"),v(farm,"name")));
                fields.add(label("Choose a farm to join",13,true,DARK));fields.add(farmChoice);
                fields.add(label("The owner must approve your membership before you can access farm records.",11,false,MUTED));
            }catch(Exception e){error(e);return;}
        }
        if(StyledDialogs.showConfirmDialog(frame,fields,mode==0?"Create Your Farm":"Join a Farm",
            JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        char[] typed=password.getPassword();
        try{
            if(name.getText().isBlank()||!login.getText().trim().matches("[A-Za-z0-9_.-]{3,40}"))
                throw new IllegalArgumentException("Enter your full name and a username of 3-40 valid characters.");
            if(social==null && typed.length<8)throw new IllegalArgumentException("Password must contain at least 8 characters.");
            if(!email.getText().isBlank() && !ContactValidation.validEmail(email.getText().trim()))
                throw new IllegalArgumentException("Please enter a valid recovery email.");
            // Social accounts have a strong random local password; no implicit account linking by email.
            char[] secret=typed;
            if(social!=null){secret=UUID.randomUUID().toString().concat(UUID.randomUUID().toString()).toCharArray();}
            String hash;
            try{hash=Security.hash(secret);}finally{if(social!=null)Arrays.fill(secret,'\0');}
            Map<String,String> account=new HashMap<>();
            account.put("username",login.getText().trim());account.put("full_name",name.getText().trim());
            account.put("password_hash",hash);account.put("email",email.getText().trim());
            account.put("email_verified","false");account.put("phone_verified","false");
            String provider=social==null?"":social.provider(),subject=social==null?"":social.subject();
            Map<String,String> created;
            if(mode==0){
                if(farmName.getText().isBlank()||location.getText().isBlank())throw new IllegalArgumentException("Enter farm name and location.");
                Map<String,String> farm=new HashMap<>();farm.put("name",farmName.getText().trim());farm.put("location",location.getText().trim());
                farm.put("owner",name.getText().trim());farm.put("area_ha","0");farm.put("crops","");farm.put("livestock","");
                created=backend.registerOwnFarm(account,farm,provider,subject);
                info("Your farm is ready. Welcome to SAKA-SMART!");
                loginUser(backend.byId("users",created.get("id")));
            }else{
                if(farmChoice==null || farmChoice.getSelectedItem()==null)throw new IllegalArgumentException("Select a farm.");
                created=backend.registerJoinFarm(account,((FarmOption)farmChoice.getSelectedItem()).id,provider,subject);
                info("Membership request sent. The farm owner must approve your access.");
            }
        }catch(Exception ex){
            System.err.println("[SAKA-SMART] Farm registration failed:");
            ex.printStackTrace();

            javax.swing.JOptionPane.showMessageDialog(
                frame,
                ex.toString(),
                "Farm Registration Error",
                javax.swing.JOptionPane.ERROR_MESSAGE
            );
        }finally{
            Arrays.fill(typed, '\0');
        }
    }

    private record FarmOption(String id,String name){
        @Override
        public String toString(){
            return name+"  (#"+id+")";
        }
    }

    /** Bring the desktop app forward after browser OAuth completes. Runs on Swing's EDT. */
    private void returnToDesktopAfterOAuth(){
        if(frame==null || !frame.isDisplayable())return;
        if((frame.getExtendedState() & Frame.ICONIFIED)!=0)
            frame.setExtendedState(frame.getExtendedState() & ~Frame.ICONIFIED);
        boolean alreadyOnTop=frame.isAlwaysOnTop();
        try{
            // A brief foreground request improves the handoff on Windows.
            if(!alreadyOnTop)frame.setAlwaysOnTop(true);
            frame.toFront();
            frame.requestFocus();
        }catch(SecurityException ignored){
            frame.toFront();
            frame.requestFocus();
        }finally{
            if(!alreadyOnTop){
                try{frame.setAlwaysOnTop(false);}catch(SecurityException ignored){}
            }
        }
    }

    private void socialSignIn(String provider){
        if(!SocialLoginService.configured(provider)){
            info(provider+" login requires an OAuth provider account. See SOCIAL_LOGIN_SETUP_V12.txt.");return;
        }
        JDialog progress=new JDialog(frame,"Browser Sign-In",false);
        progress.add(new JLabel("  Finish "+provider+" sign-in in your browser.  ",SwingConstants.CENTER));
        progress.setSize(390,125);progress.setLocationRelativeTo(frame);
        SwingWorker<SocialLoginService.Profile,Void> worker=new SwingWorker<>(){
            @Override protected SocialLoginService.Profile doInBackground() throws Exception{return SocialLoginService.authenticate(provider);}
            @Override protected void done(){
                progress.dispose();
                try{
                    SocialLoginService.Profile profile=get();
                    returnToDesktopAfterOAuth();
                    Map<String,String> existing=backend.bySocialIdentity(profile.provider(),profile.subject());
                    if(existing==null)register(profile); // New user still chooses a farm and registers securely.
                    else if(!Boolean.parseBoolean(existing.getOrDefault("active","false")))
                        info("Farm membership is awaiting owner approval.");
                    else{
                        loginUser(existing); // Existing linked account opens its dashboard automatically.
                        returnToDesktopAfterOAuth();
                    }
                }catch(Exception e){
                    returnToDesktopAfterOAuth();
                    error(new Exception(e.getCause()==null?e.getMessage():e.getCause().getMessage()));
                }
            }
        };
        worker.execute();progress.setVisible(true);
    }
    private void phoneSignIn(){
        if(!DeliveryService.configured("SMS")){
            info("Phone sign-in needs a configured SMS provider and verified phone number. See EMAIL_SMS_SETUP_V12.txt.");return;
        }
        String phone=StyledDialogs.showInputDialog(frame,"Verified phone number (+639XXXXXXXXX)","");
        if(phone==null)return;phone=phone.trim();
        if(!ContactValidation.validPhone(phone)){info("Use international phone format, such as +639123456789.");return;}
        RecoveryDelivery issued=null;
        try{
            issued=backend.issuePhoneLoginCode(phone);
            if(issued!=null){
                try{DeliveryService.send("SMS",issued.destination(),issued.code());}
                catch(Exception e){backend.cancelPhoneLoginCode(issued.requestId());throw e;}
            }
            info("If that number belongs to a verified active account, a sign-in code has been requested.");
            String code=StyledDialogs.showInputDialog(frame,"Enter the SMS sign-in code","");
            if(code==null || code.isBlank())return;
            Map<String,String> account=backend.redeemPhoneLoginCode(phone,code.trim());
            if(account==null)info("Incorrect, expired or previously used sign-in code.");
            else loginUser(account);
        }catch(Exception e){error(e);}
    }

    // -------------------------------- MAIN SHELL --------------------------------
    private void showShell(){
        stopLoginAnimation(); // no background animation after entering the dashboard
        frame.setTitle("SAKA-SMART | Farm Management System");
        JPanel root=new JPanel(new BorderLayout());root.setBackground(BG);
        sidebar=new JPanel();sidebar.setLayout(new BoxLayout(sidebar,BoxLayout.Y_AXIS));sidebar.setBackground(FOREST);
        sidebar.setPreferredSize(new Dimension(242,650));pad(sidebar,24,16,18,16);
        // Actual SAKA-SMART logo to the LEFT of the sidebar brand text.
        JPanel brandRow=pane(new BorderLayout(7,0));
        Image brandImg=loadLoginLogo();
        if(brandImg!=null){
            JLabel logoMark=new JLabel(new ImageIcon(brandImg.getScaledInstance(54,45,Image.SCALE_SMOOTH)));
            brandRow.add(logoMark,BorderLayout.WEST);
        }
        JPanel brandText=pane(new GridLayout(2,1,0,0));
        brandText.add(label("SAKA-SMART",18,true,Color.WHITE));
        brandText.add(label("Farm Management System",10,false,new Color(205,229,211)));
        brandRow.add(brandText,BorderLayout.CENTER);
        brandRow.setMaximumSize(new Dimension(Integer.MAX_VALUE,58));
        brandRow.setPreferredSize(new Dimension(205,58));
        brandRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(brandRow);
        sidebar.add(spacer(18));
        String[] routes=admin()?new String[]{"Dashboard","Users","Farm Profile","Learning Center","System Records","Backup & Restore","Settings"}:
            "FARM_OWNER".equals(role())?new String[]{"Dashboard","Inventory","Calendar","Production Tracking","Accounting","Marketplace / Sales","Farm Members","Learning Center","Settings"}:
            new String[]{"Dashboard","Inventory","Calendar","Production Tracking","Accounting","Marketplace / Sales","Learning Center","Settings"};
        for(String r:routes) {
            JButton b="Dashboard".equals(route)?new DashboardNavButton(r,r.equals(route)):
                btn(symbol(r)+"  "+r,r.equals(route)?GREEN:DARK);
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE,47));b.setPreferredSize(new Dimension(210,45));b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.addActionListener(e->{route=r;showShell();});sidebar.add(b);sidebar.add(spacer(7));
        }
        sidebar.add(Box.createVerticalGlue());
        JPanel footer=new FarmBanner(false);footer.setPreferredSize(new Dimension(200,140));footer.setMaximumSize(new Dimension(Integer.MAX_VALUE,140));
        sidebar.add(footer);sidebar.add(spacer(10));
        JLabel slogan=label("Manage  •  Produce  •  Grow",11,false,new Color(217,238,218));slogan.setAlignmentX(Component.LEFT_ALIGNMENT);sidebar.add(slogan);
        root.add(sidebar,BorderLayout.WEST);
        JPanel main=pane(new BorderLayout());main.setBackground(BG);
        main.add(header(),BorderLayout.NORTH);
        pageHolder=pane(new BorderLayout());pageHolder.setBackground(BG);
        JScrollPane scroll=new JScrollPane(pageHolder);scroll.setBorder(null);scroll.getViewport().setBackground(BG);scroll.getVerticalScrollBar().setUnitIncrement(22);
        main.add(scroll,BorderLayout.CENTER);root.add(main,BorderLayout.CENTER);
        frame.setContentPane(root);renderPage();frame.revalidate();frame.repaint();
    }
    private static String symbol(String name){return switch(name){
        case "Dashboard"->"⌂";case "Inventory"->"▣";case "Calendar"->"▦";case "Production Tracking"->"▥";
        case "Accounting"->"₱";case "Marketplace / Sales"->"▤";case "Learning Center"->"▧";
        case "Users"->"♟";case "Farm Members"->"♟";case "Settings"->"⚙";case "Backup & Restore"->"↥";
        case "Farm Profile"->"♣";case "System Records"->"≡";default->"•";};}
    private JComponent header(){
        JPanel top=new FarmBanner(false,true);top.setPreferredSize(new Dimension(800,139));top.setLayout(new BorderLayout());pad(top,21,25,20,25);
        JPanel title=pane(new GridLayout(2,1,0,0));title.add(label(admin()?"ADMIN CONTROL CENTER":"SAKA-SMART  •  "+route.toUpperCase(),25,true,DARK));
        title.add(label(admin()?"Manage users, records, and system settings.":"Smart farms. Brighter tomorrows.",15,false,DARK));top.add(title,BorderLayout.WEST);
        JPanel right=pane(new FlowLayout(FlowLayout.RIGHT,7,0));
        JLabel mode=label(store.mode().startsWith("POSTGRESQL")?"● PostgreSQL connected":"● Local demo",12,true,GREEN);right.add(mode);
        JButton alerts="Dashboard".equals(route)?dashboardButton("Alerts"):secondary("🔔 Alerts");
        alerts.addActionListener(e->notifications());right.add(alerts);
        JButton refresh="Dashboard".equals(route)?dashboardButton("Refresh"):secondary("↻ Refresh");
        refresh.addActionListener(e->renderPage());right.add(refresh);
        JButton logout="Dashboard".equals(route)?dashboardButton("Log Out"):secondary("Log Out");
        logout.addActionListener(e->{store.log(username(),"Signed out");user=null;store=backend;showLogin();});right.add(logout);
        JPanel topRight=pane(new BorderLayout());topRight.add(right,BorderLayout.NORTH);topRight.add(label(user.getOrDefault("full_name",username())+"  •  "+role().replace('_',' '),12,true,DARK),BorderLayout.SOUTH);
        top.add(topRight,BorderLayout.EAST);return top;
    }
    void renderPage(){
        pageHolder.removeAll();
        JPanel body=pane(new BorderLayout());body.setBackground(BG);pad(body,20,23,28,23);
        JComponent content = switch(route){
            case "Dashboard" -> new DashboardPage(this).build();
            case "Inventory" -> new InventoryPage(this).build();
            case "Calendar" -> new CalendarPage(this).build();
            case "Production Tracking" -> new ProductionPage(this).build();
            case "Accounting" -> new AccountingPage(this).build();
            case "Marketplace / Sales" -> new MarketplacePage(this).build();
            case "Learning Center" -> new LearningPage(this).build();
            case "Settings" -> new SettingsPage(this).build();
            case "Users" -> new UsersPage(this).build();
            case "Farm Members" -> new FarmMembersPage(this).build();
             case "Farm Profile" -> new FarmProfilePage(this).build();
            case "System Records" -> new SystemRecordsPage(this).build();
            case "Backup & Restore" -> new BackupPage(this).build();
            default -> label("Page coming soon",22,true,DARK);
        };
        body.add(content,BorderLayout.CENTER);pageHolder.add(body,BorderLayout.CENTER);
        pageHolder.revalidate();pageHolder.repaint();
    }
    JPanel vertical(){JPanel p=pane(new GridBagLayout());p.setAlignmentX(Component.LEFT_ALIGNMENT);return p;}
    JPanel stack(){JPanel p=pane(null);p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));return p;}
    static JPanel horizontal(JComponent... cs){JPanel p=pane(new GridLayout(1,cs.length,12,0));for(JComponent c:cs)p.add(c);return p;}
    static void addBlock(JPanel target,JComponent c,int height){
        c.setAlignmentX(Component.LEFT_ALIGNMENT);c.setMaximumSize(new Dimension(Integer.MAX_VALUE,height));c.setPreferredSize(new Dimension(200,height));
        target.add(c);target.add(spacer(13));
    }
    static JComponent stat(String title,String value,String detail,Color tint,String glyph){
        JPanel p=new RoundedPanel(tint,20,new BorderLayout(8,4));pad(p,15,17,15,14);
        JPanel group=pane(new GridLayout(3,1,0,1));group.add(label(title,13,true,TEXT));group.add(label(value,26,true,DARK));group.add(label(detail,11,false,MUTED));
        p.add(group,BorderLayout.CENTER);p.add(label(glyph,35,true,GREEN),BorderLayout.EAST);return p;
    }
    static JComponent section(String title,String subtitle,JComponent contents){
        JPanel p=card(new BorderLayout(0,12));pad(p,16,17,16,17);
        JPanel head=pane(new GridLayout(subtitle.isBlank()?1:2,1,0,2));head.add(label(title,18,true,DARK));
        if(!subtitle.isBlank())head.add(label(subtitle,12,false,MUTED));p.add(head,BorderLayout.NORTH);
        p.add(contents,BorderLayout.CENTER);return p;
    }
    static JPanel actions(JButton... buttons){JPanel p=pane(new FlowLayout(FlowLayout.LEFT,8,0));for(JButton b:buttons)p.add(b);return p;}
    static JComponent blank(String title,String desc){JPanel p=card(new BorderLayout());pad(p,22,25,22,25);p.add(label(title,20,true,DARK),BorderLayout.NORTH);p.add(label(desc,14,false,MUTED),BorderLayout.CENTER);return p;}
    static String fmt(double val){return String.format(Locale.US,"%,.0f",val);}
    static double total(List<Map<String,String>> rs,String key){return rs.stream().mapToDouble(r->Storage.num(r,key)).sum();}
    static List<Map<String,String>> filter(List<Map<String,String>> rs,Predicate<Map<String,String>> p){return rs.stream().filter(p).toList();}
    static String v(Map<String,String> row,String key){return row.getOrDefault(key,"");}
    void go(String r){route=r;showShell();}
    static String month(){return YearMonth.now().toString();}

    // -------------------------------- DASHBOARD --------------------------------
    static boolean isExpiring(Map<String,String> row){
        try{LocalDate ex=LocalDate.parse(v(row,"expiry_date"));return !ex.isAfter(LocalDate.now().plusDays(30));}
        catch(Exception e){return false;}
    }

    // -------------------------------- INVENTORY --------------------------------
    // -------------------------------- CALENDAR --------------------------------
    // -------------------------------- PRODUCTION --------------------------------
    // -------------------------------- ACCOUNTING --------------------------------
    // -------------------------------- MARKETPLACE --------------------------------
    // -------------------------------- LEARNING --------------------------------
    // -------------------------------- SETTINGS & ADMIN --------------------------------
    /** Two tabs under the existing Users navigation (admins only). */

    // -------------------------------- TABLES, FORMS, CSV --------------------------------
    record TableParts(JTable table,JScrollPane scroll,List<Map<String,String>> source) {
        Map<String,String> selected(){int i=table.getSelectedRow();if(i<0)return null;int model=table.convertRowIndexToModel(i);return model>=0&&model<source.size()?source.get(model):null;}
    }
    private static String headerLabel(String field){return switch(field){
        case "id"->"ID";case "item_id"->"Item ID";case "farm_id"->"Farm ID";case "product_name"->"Product";case "min_stock"->"Low Stock Limit";case "unit_price"->"Unit Price";case "expiry_date"->"Expiry Date";case "full_name"->"Full Name";case "sale_id"->"Sale ID";
        default -> {String s=field.replace('_',' ');yield s.isBlank()?s:Character.toUpperCase(s.charAt(0))+s.substring(1);}
    };}
    static TableParts tableView(String tableName,List<Map<String,String>> rs,List<String> cols,String placeholder){
        String[] labels=cols.stream().map(Main::headerLabel).toArray(String[]::new);
        Object[][] values=new Object[rs.size()][cols.size()];
        for(int i=0;i<rs.size();i++)for(int j=0;j<cols.size();j++)values[i][j]=v(rs.get(i),cols.get(j));
        DefaultTableModel model=new DefaultTableModel(values,labels){@Override public boolean isCellEditable(int r,int c){return false;}};
        JTable table=new JTable(model);table.setFont(new Font("Segoe UI",Font.PLAIN,13));table.setRowHeight(31);
        table.setSelectionBackground(new Color(215,242,220));table.setSelectionForeground(DARK);table.setGridColor(new Color(234,241,236));
        table.getTableHeader().setFont(BOLD);table.getTableHeader().setBackground(new Color(234,243,238));
        table.setAutoCreateRowSorter(true);table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for(int i=0;i<cols.size();i++)table.getColumnModel().getColumn(i).setPreferredWidth(Math.max(85,Math.min(240,cols.get(i).length()*9+105)));
        JScrollPane sp=new JScrollPane(table);sp.setBorder(BorderFactory.createLineBorder(new Color(227,235,230)));
        sp.getViewport().setBackground(Color.WHITE);
        if(placeholder!=null){
            JPanel wrap=pane(new BorderLayout(0,7));JTextField search=new JTextField();styleInput(search);search.putClientProperty("JTextField.placeholderText",placeholder);
            search.setToolTipText(placeholder);wrap.add(search,BorderLayout.NORTH);wrap.add(sp,BorderLayout.CENTER);
            TableRowSorter<DefaultTableModel> sorter=new TableRowSorter<>(model);table.setRowSorter(sorter);
            watch(search,()->sorter.setRowFilter(RowFilter.regexFilter("(?i)"+java.util.regex.Pattern.quote(search.getText()))));
            JScrollPane result=new JScrollPane(wrap);result.setBorder(null);result.getViewport().setBackground(Color.WHITE);
            return new TableParts(table,result,rs);
        }
        return new TableParts(table,sp,rs);
    }
    static void watch(JTextField input,Runnable action){input.getDocument().addDocumentListener(new DocumentListener(){
        public void insertUpdate(DocumentEvent e){action.run();}public void removeUpdate(DocumentEvent e){action.run();}public void changedUpdate(DocumentEvent e){action.run();}
    });}
    JComponent tableAndActions(String tableName,TableParts table,Map<String,String> defaults){
        JPanel p=pane(new BorderLayout(0,10));JPanel controls=pane(new FlowLayout(FlowLayout.RIGHT,8,0));
        if(editable(tableName)) {
            JButton add=btn("+ Add Record",GREEN),edit=secondary("Edit"),delete=btn("Delete",RED);
            add.addActionListener(e->form(tableName,null,defaults,Set.of()));edit.addActionListener(e->selectedEdit(tableName,table));delete.addActionListener(e->selectedDelete(tableName,table));
            controls.add(add);controls.add(edit);controls.add(delete);
        }
        JButton export=secondary("Export CSV");export.addActionListener(e->exportTable(tableName,table.source()));controls.add(export);
        p.add(controls,BorderLayout.NORTH);p.add(table.scroll,BorderLayout.CENTER);return p;
    }
    void selectedEdit(String table,TableParts p){Map<String,String> x=p.selected();if(x==null){info("Please select a row first.");return;}
        form(table,x,Map.of(),Set.of());}
    void selectedDelete(String table,TableParts p){Map<String,String> x=p.selected();if(x==null){info("Please select a row first.");return;}
        deleteRow(table,x);}
    void deleteRow(String table,Map<String,String> x){
        if(StyledDialogs.showConfirmDialog(frame,"Delete selected "+table+" record #"+v(x,"id")+"?","Confirm Delete",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        try{
            if("transactions".equals(table)&&!v(x,"sale_id").isBlank())throw new IllegalArgumentException("Sale-generated income is protected. Use an audited reversal workflow in a future version.");
            store.remove(table,v(x,"id"));store.log(username(),"Deleted "+table+" #"+v(x,"id"));renderPage();
        }catch(Exception ex){error(ex);}
    }
    void form(String table,Map<String,String> original,Map<String,String> defaults,Set<String> locked){
        Schema.Definition def=Schema.TABLES.get(table);
        JPanel form=pane(new GridBagLayout());GridBagConstraints c=new GridBagConstraints();c.gridx=0;c.fill=GridBagConstraints.HORIZONTAL;c.weightx=1;c.insets=new Insets(4,4,4,4);
        Map<String,JComponent> input=new LinkedHashMap<>();int row=0;
        for(Schema.Field field:def.fields()){
            if(field.key().equals("farm_id")||field.key().equals("sale_id")||field.type()==Schema.Type.SECRET)continue;
            c.gridy=row++;form.add(label(field.label()+(field.required()?" *":""),13,true,DARK),c);
            String value=original!=null?v(original,field.key()):defaults.getOrDefault(field.key(),"");
            JComponent control;
            if(field.type()==Schema.Type.CHOICE){JComboBox<String> choice=new JComboBox<>(field.choices().toArray(String[]::new));
                if(!value.isBlank())choice.setSelectedItem(value);control=choice;}
            else if(field.type()==Schema.Type.BOOL)control=new JCheckBox("Enabled",Boolean.parseBoolean(value));
            else if(field.type()==Schema.Type.LONG_TEXT){JTextArea area=new JTextArea(value,4,26);area.setLineWrap(true);area.setWrapStyleWord(true);control=new JScrollPane(area);}
            else{JTextField text=new JTextField(value,25);styleInput(text);control=text;}
            control.setEnabled(!locked.contains(field.key()));c.gridy=row++;form.add(control,c);input.put(field.key(),control);
        }
        JScrollPane scroll=new JScrollPane(form);scroll.setBorder(null);scroll.setPreferredSize(new Dimension(510,Math.min(550,rowsCountHeight(row))));
        int choice=StyledDialogs.showConfirmDialog(frame,scroll,(original==null?"Add ":"Edit ")+def.title(),JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);
        if(choice!=JOptionPane.OK_OPTION)return;
        Map<String,String> changes=new LinkedHashMap<>(defaults);
        for(Schema.Field field:def.fields()){
            JComponent control=input.get(field.key());if(control==null)continue;
            String val;
            if(control instanceof JComboBox<?> combo)val=Objects.toString(combo.getSelectedItem(),"");
            else if(control instanceof JCheckBox box)val=Boolean.toString(box.isSelected());
            else if(control instanceof JScrollPane sp)val=((JTextArea)sp.getViewport().getView()).getText();
            else val=((JTextField)control).getText().trim();
            changes.put(field.key(),val);
        }
        if(!"farms".equals(table)&&!"articles".equals(table))changes.put("farm_id",user==null?"1":v(user,"farm_id"));
        try{
            for(Schema.Field f:def.fields()){
                String value=changes.getOrDefault(f.key(),"");
                if(f.required()&&value.isBlank())throw new IllegalArgumentException(f.label()+" is required.");
                if(f.type()==Schema.Type.NUMBER&&!value.isBlank()&&!"farm_id".equals(f.key())){
                    double n=Double.parseDouble(value);if(n<0||!Double.isFinite(n))throw new IllegalArgumentException(f.label()+" must be a nonnegative number.");
                }
                if(f.type()==Schema.Type.DATE&&!value.isBlank())LocalDate.parse(value);
            }
            if(original==null){Map<String,String> saved=store.add(table,changes);store.log(username(),"Added "+table+" #"+v(saved,"id"));}
            else{store.edit(table,v(original,"id"),changes);store.log(username(),"Updated "+table+" #"+v(original,"id"));}
            renderPage();
        }catch(Exception ex){error(ex);}
    }
    private static int rowsCountHeight(int n){return Math.max(140,n*23+25);}
    void exportTable(String table,List<Map<String,String>> data){
        JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new File("saka-smart-"+table+".csv"));
        if(chooser.showSaveDialog(frame)!=JFileChooser.APPROVE_OPTION)return;
        try{Files.writeString(chooser.getSelectedFile().toPath(),csv(table,data),StandardCharsets.UTF_8);info("CSV exported successfully.");}
        catch(Exception e){error(e);}
    }
    private static String csv(String table,List<Map<String,String>> data){
        List<String> cols=new ArrayList<>();cols.add("id");for(Schema.Field f:Schema.TABLES.get(table).fields())if(f.type()!=Schema.Type.SECRET)cols.add(f.key());
        StringBuilder sb=new StringBuilder();sb.append(String.join(",",cols)).append("\r\n");
        for(Map<String,String> r:data){for(int i=0;i<cols.size();i++){
            if(i>0)sb.append(',');String s=v(r,cols.get(i));
            // Prefix spreadsheet formula markers so exported data isn't interpreted as a formula.
            if(!s.isEmpty()&&"=+-@\t\r".indexOf(s.charAt(0))>=0)s="'"+s;
            sb.append('"').append(s.replace("\"","\"\"")).append('"');
        }sb.append("\r\n");}return sb.toString();
    }
    void backupArchive(){
        JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new File("saka-smart-csv-archive-"+today()+".zip"));
        if(chooser.showSaveDialog(frame)!=JFileChooser.APPROVE_OPTION)return;
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(chooser.getSelectedFile().toPath()))) {
            for(String table:Schema.TABLES.keySet()){
                // Never export OTP hashes or provider-subject mappings as ordinary farm data.
                if(Set.of("password_reset_requests","phone_login_codes","social_identities").contains(table))continue;
                if(!admin() && Set.of("users","logs").contains(table))continue;
                zip.putNextEntry(new ZipEntry(table+".csv"));zip.write(csv(table,rows(table)).getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("README.txt"));zip.write("CSV export of SAKA-SMART records. This archive is not a restorable database backup. Use pgAdmin backup for PostgreSQL.".getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            info("CSV data archive exported. This is not a full PostgreSQL backup/restore.");
        }catch(Exception ex){error(ex);}
    }
    private void notifications(){
        List<String> notes=new ArrayList<>();
        for(Map<String,String> r:rows("inventory")){
            if(Storage.num(r,"quantity")<=Storage.num(r,"min_stock"))notes.add("Low stock: "+v(r,"name")+" ("+v(r,"quantity")+" "+v(r,"unit")+")");
            if(isExpiring(r))notes.add("Expiring soon: "+v(r,"name")+" ("+v(r,"expiry_date")+")");
        }
        for(Map<String,String> r:rows("events")){
            if("Upcoming".equals(v(r,"status"))&&v(r,"date").compareTo(today())<=0)notes.add("Task due/overdue: "+v(r,"title")+" ("+v(r,"date")+")");
        }
        if(admin()){
            for(Map<String,String> r:rows("users"))if(!"true".equalsIgnoreCase(v(r,"active")))notes.add("Approval needed: "+v(r,"username"));
            for(Map<String,String> r:rows("password_reset_requests"))if("PENDING".equals(v(r,"status")))
                notes.add("Password assistance requested: "+v(r,"username"));
        }
        if(notes.isEmpty()){info("No current alerts.");return;}
        JTextArea t=new JTextArea(String.join("\n\n",notes));t.setFont(FONT);t.setEditable(false);t.setWrapStyleWord(true);t.setLineWrap(true);
        JScrollPane sp=new JScrollPane(t);sp.setPreferredSize(new Dimension(520,310));StyledDialogs.showMessageDialog(frame,sp,notes.size()+" Alerts",JOptionPane.INFORMATION_MESSAGE);
    }

    // -------------------------------- CUSTOM GRAPHICS --------------------------------
    /** Custom landscape illustration; no internet and no extra image libraries required. */
    private static class FarmBanner extends JPanel {
        private final boolean tall, lightHeader;
        FarmBanner(boolean tall){this(tall,false);}
        FarmBanner(boolean tall,boolean lightHeader){this.tall=tall;this.lightHeader=lightHeader;setOpaque(false);}
        @Override protected void paintComponent(Graphics g){
            Graphics2D a=(Graphics2D)g.create();a.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();
            a.setPaint(new GradientPaint(0,0,new Color(210,238,223),0,h,new Color(244,249,217)));a.fillRect(0,0,w,h);
            a.setColor(new Color(254,210,109));a.fillOval((int)(w*.72),Math.max(8,(int)(h*.15)),56,56);
            Path2D hill=new Path2D.Double();hill.moveTo(0,h*.65);hill.curveTo(w*.25,h*.18,w*.48,h*.72,w*.7,h*.43);hill.curveTo(w*.86,h*.18,w,h*.25,w,h*.45);hill.lineTo(w,h);hill.lineTo(0,h);hill.closePath();
            a.setColor(new Color(130,181,137,190));a.fill(hill);
            Path2D front=new Path2D.Double();front.moveTo(0,h*.76);front.curveTo(w*.24,h*.59,w*.39,h*.9,w*.64,h*.68);front.curveTo(w*.86,h*.47,w,h*.7,w,h*.64);front.lineTo(w,h);front.lineTo(0,h);front.closePath();
            a.setColor(new Color(52,127,76));a.fill(front);
            a.setStroke(new BasicStroke(tall?4:2));
            a.setColor(new Color(175,211,103));for(int i=0;i<14;i++){
                int x=(int)(i*w/13.0);a.drawLine(x,h,(int)(w*.55+(x-w*.55)*.38),h-(tall?42:18));
            }
            a.setColor(new Color(20,86,48));
            for(int i=0;i<8;i++){
                int x=(int)((i+.3)*w/9.0),y=(int)(h*.61+10*Math.sin(i));
                a.fillRect(x-3,y-11,6,28);a.fillOval(x-17,y-25,34,27);
            }
            if(lightHeader){a.setPaint(new GradientPaint(0,0,new Color(246,252,248,240),0,110,new Color(250,254,251,190)));a.fillRoundRect(0,0,w,115,0,0);}
            a.dispose();super.paintComponent(g);
        }
    }
    // DASHBOARD V2 - Visual polish only. Existing records, routes, and login logic remain intact.
    // These styles are intentionally used only by DashboardPage and the Dashboard navigation.
    private static final class DashboardGlass extends JPanel {
        private final Color tint;
        private final boolean compact;
        private boolean hovered;
        DashboardGlass(LayoutManager layout,Color tint,boolean compact){
            super(layout);this.tint=tint;this.compact=compact;setOpaque(false);
            addMouseListener(new MouseAdapter(){
                @Override public void mouseEntered(MouseEvent e){hovered=true;repaint();}
                @Override public void mouseExited(MouseEvent e){hovered=false;repaint();}
            });
        }
        @Override protected void paintComponent(Graphics original){
            super.paintComponent(original);
            Graphics2D g=(Graphics2D)original.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();if(w<32||h<32){g.dispose();return;}
            int x=3,y=3,bw=w-9,bh=h-13,arc=compact?23:25;
            // Cast shadow, a dark lower bevel, then the raised tinted surface.
            g.setColor(new Color(12,69,40,14));g.fillRoundRect(x+5,y+7,bw,bh,arc,arc);
            g.setColor(new Color(8,58,39,25));g.fillRoundRect(x+2,y+5,bw,bh,arc,arc);
            Color base=tint;
            Color top=mix(base,Color.WHITE,compact?.48f:.70f);
            Color bottom=mix(base,new Color(73,146,103),compact?.14f:.065f);
            if(hovered){top=mix(top,Color.WHITE,.24f);bottom=mix(bottom,Color.WHITE,.10f);}
            g.setPaint(new GradientPaint(x,y,top,x+bw*.24f,y+bh,bottom));
            g.fillRoundRect(x,y,bw,bh,arc,arc);
            Shape clip=g.getClip();g.clip(new RoundRectangle2D.Double(x,y,bw,bh,arc,arc));
            g.setPaint(new GradientPaint(x,y,new Color(255,255,255,115),x,y+Math.max(24,bh*.62f),new Color(255,255,255,0)));
            g.fillRoundRect(x+3,y+2,bw-6,Math.max(18,bh/2),arc-4,arc-4);
            g.setPaint(new RadialGradientPaint(new Point2D.Float(x+bw*.10f,y+7),Math.max(45,bw*.53f),
                new float[]{0f,1f},new Color[]{new Color(255,255,240,52),new Color(255,255,255,0)}));
            g.fillRect(x,y,bw,bh);
            g.setClip(clip);
            g.setStroke(new BasicStroke(1.5f));g.setColor(new Color(255,255,255,205));
            g.drawRoundRect(x+1,y+1,bw-2,bh-2,arc,arc);
            g.setStroke(new BasicStroke(1.0f));g.setColor(new Color(29,101,62,65));
            g.drawRoundRect(x+3,y+3,bw-6,bh-6,arc-5,arc-5);
            g.dispose();
        }
    }
    private static Color mix(Color a,Color b,float f){
        f=Math.max(0f,Math.min(1f,f));
        return new Color((int)(a.getRed()*(1-f)+b.getRed()*f),
            (int)(a.getGreen()*(1-f)+b.getGreen()*f),
            (int)(a.getBlue()*(1-f)+b.getBlue()*f));
    }
    static JComponent dashboardStat(String name,String value,String detail,Color tint,String glyph){
        JPanel p=new DashboardGlass(new BorderLayout(7,3),tint,true);
        p.setBorder(new EmptyBorder(15,18,19,19));
        JPanel words=pane(new GridLayout(3,1,0,2));
        words.add(label(name,13,true,TEXT));
        words.add(label(value,25,true,DARK));
        words.add(label(detail,11,false,MUTED));
        p.add(words,BorderLayout.CENTER);
        JComponent symbol=new DashboardStatGlyph(name,glyph);
        symbol.setPreferredSize(new Dimension(43,52));
        p.add(symbol,BorderLayout.EAST);
        return p;
    }
    static JComponent dashboardSection(String name,String subtitle,JComponent contents){
        JPanel p=new DashboardGlass(new BorderLayout(0,12),new Color(248,252,246),false);
        p.setBorder(new EmptyBorder(18,21,21,21));
        JPanel head=pane(new GridLayout(subtitle.isBlank()?1:2,1,0,2));
        head.add(label(name,18,true,DARK));
        if(!subtitle.isBlank())head.add(label(subtitle,12,false,MUTED));
        p.add(head,BorderLayout.NORTH);p.add(contents,BorderLayout.CENTER);
        return p;
    }
    static JComponent dashboardTip(String name,String desc){
        JPanel p=new DashboardGlass(new BorderLayout(),new Color(238,250,218),false);
        p.setBorder(new EmptyBorder(19,22,21,22));
        p.add(label(name,18,true,DARK),BorderLayout.NORTH);
        p.add(label(desc,13,false,MUTED),BorderLayout.CENTER);
        return p;
    }
    static JScrollPane dashboardTable(String table,List<Map<String,String>> rows,List<String> cols){
        TableParts parts=tableView(table,rows,cols,null);
        JTable t=parts.table();
        t.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        t.setRowHeight(32);
        t.setShowHorizontalLines(true);t.setShowVerticalLines(false);
        t.setBackground(new Color(251,254,250));
        t.setSelectionBackground(new Color(219,246,220));
        t.setGridColor(new Color(222,238,225));
        t.getTableHeader().setFont(new Font("Segoe UI",Font.BOLD,12));
        t.getTableHeader().setBackground(new Color(228,243,230));
        t.getTableHeader().setForeground(DARK);
        for(int i=0;i<t.getColumnCount();i++)t.getColumnModel().getColumn(i).setPreferredWidth(95);
        JScrollPane scroll=parts.scroll();
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(new Color(251,254,250));
        return scroll;
    }
    static JButton dashboardButton(String text){
        JButton b=new JButton(text){
            @Override protected void paintComponent(Graphics source){
                Graphics2D g=(Graphics2D)source.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                int w=getWidth(),h=getHeight();
                Color upper=getModel().isPressed()?new Color(24,113,58):getModel().isRollover()?new Color(62,182,91):new Color(42,151,75);
                g.setColor(new Color(5,52,31,90));g.fillRoundRect(1,5,w-2,h-6,15,15);
                g.setPaint(new GradientPaint(1,1,upper,1,h-5,new Color(8,79,42)));
                g.fillRoundRect(1,1,w-2,h-6,15,15);
                g.setPaint(new GradientPaint(0,0,new Color(255,255,255,125),0,h*.58f,new Color(255,255,255,0)));
                g.fillRoundRect(3,2,w-6,(int)(h*.51),13,13);
                g.setColor(new Color(246,255,243,175));g.drawRoundRect(2,2,w-5,h-8,14,14);
                g.dispose();super.paintComponent(source);
            }
        };
        b.setForeground(Color.WHITE);b.setFont(new Font("Segoe UI",Font.BOLD,12));
        b.setContentAreaFilled(false);b.setFocusPainted(false);b.setBorderPainted(false);
        b.setOpaque(false);b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(new EmptyBorder(10,17,13,17));
        return b;
    }
    private static final class DashboardStatGlyph extends JComponent {
        private final String title,glyph;
        DashboardStatGlyph(String title,String glyph){this.title=title;this.glyph=glyph;setOpaque(false);}
        @Override protected void paintComponent(Graphics original){
            Graphics2D g=(Graphics2D)original.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight(),x=w/2,y=h/2;
            g.setColor(new Color(18,116,66,24));g.fillOval(x-20,y-20,40,40);
            g.setColor(new Color(7,114,55));g.setStroke(new BasicStroke(2.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            if(title.contains("Inventory")||title.contains("Stock" )&& !title.contains("Low")){
                g.drawRect(x-11,y-7,22,18);g.drawLine(x-11,y-7,x,y-14);g.drawLine(x,y-14,x+11,y-7);g.drawLine(x,y-14,x,y+11);
            }else if(title.contains("Tasks")||title.contains("Upcoming")){
                g.drawRoundRect(x-12,y-11,24,23,5,5);g.drawLine(x-12,y-3,x+12,y-3);
                g.drawLine(x-6,y-14,x-6,y-8);g.drawLine(x+6,y-14,x+6,y-8);
                g.fillOval(x-5,y+2,4,4);g.fillOval(x+3,y+2,4,4);
            }else if(title.contains("Income")){
                g.setFont(new Font("Segoe UI",Font.BOLD,29));g.drawString("₱",x-10,y+10);
            }else if(title.contains("Low")||title.contains("Pending")){
                g.drawOval(x-13,y-13,26,26);g.drawLine(x,y-7,x,y+3);g.fillOval(x-2,y+7,4,4);
            }else{
                g.setFont(new Font("Segoe UI",Font.BOLD,28));
                String mark=glyph.matches("[\u0021-\u007e]")?glyph:"✓";
                FontMetrics fm=g.getFontMetrics();g.drawString(mark,x-fm.stringWidth(mark)/2,y+10);
            }
            g.dispose();
        }
    }
    private static final class DashboardNavButton extends JButton {
        private final boolean selected;
        DashboardNavButton(String route,boolean selected){
            super(route);this.selected=selected;
            setForeground(Color.WHITE);setFont(new Font("Segoe UI",Font.BOLD,13));
            setIcon(new DashboardNavIcon(route));setIconTextGap(10);
            setBorder(new EmptyBorder(8,15,10,7));setOpaque(false);
            setContentAreaFilled(false);setBorderPainted(false);setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setRolloverEnabled(true);
        }
        @Override protected void paintComponent(Graphics source){
            Graphics2D g=(Graphics2D)source.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();
            boolean bright=selected||getModel().isRollover();
            g.setColor(new Color(0,28,18,100));g.fillRoundRect(1,5,w-2,h-6,15,15);
            g.setPaint(new GradientPaint(0,0,bright?new Color(36,172,83):new Color(10,81,45),
               w,h,bright?new Color(9,113,54):new Color(5,62,35)));
            g.fillRoundRect(1,1,w-2,h-7,15,15);
            if(bright){g.setPaint(new GradientPaint(0,0,new Color(255,255,255,96),0,h*.7f,new Color(255,255,255,0)));
                g.fillRoundRect(3,2,w-6,h/2,13,13);}
            g.setColor(new Color(188,255,211,selected?150:55));g.drawRoundRect(1,1,w-3,h-8,15,15);
            g.dispose();super.paintComponent(source);
        }
    }
    private static final class DashboardNavIcon implements javax.swing.Icon {
        private final String route;
        DashboardNavIcon(String route){this.route=route;}
        public int getIconWidth(){return 19;}public int getIconHeight(){return 19;}
        public void paintIcon(Component c,Graphics gr,int x,int y){
            Graphics2D g=(Graphics2D)gr.create();g.translate(x,y);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(237,255,235));g.setStroke(new BasicStroke(1.7f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            switch(route){
                case "Dashboard" -> {g.drawRoundRect(2,2,6,6,2,2);g.drawRoundRect(11,2,6,6,2,2);g.drawRoundRect(2,11,6,6,2,2);g.drawRoundRect(11,11,6,6,2,2);}
                case "Calendar" -> {g.drawRoundRect(2,4,15,13,2,2);g.drawLine(2,8,17,8);g.drawLine(6,2,6,6);g.drawLine(13,2,13,6);}
                case "Inventory","Marketplace / Sales" -> {g.drawRect(3,6,13,10);g.drawLine(3,6,9,2);g.drawLine(9,2,16,6);g.drawLine(9,2,9,15);}
                case "Accounting" -> {g.setFont(new Font("Segoe UI",Font.BOLD,17));g.drawString("₱",5,16);}
                case "Production Tracking" -> {g.drawLine(3,16,3,9);g.drawLine(8,16,8,5);g.drawLine(13,16,13,2);g.drawLine(17,16,2,16);}
                case "Learning Center" -> {g.drawRect(2,4,7,12);g.drawRect(10,4,7,12);g.drawLine(9,6,9,18);}
                case "Farm Members","Users" -> {g.drawOval(7,2,5,5);g.drawOval(2,7,5,5);g.drawOval(12,7,5,5);g.drawRoundRect(4,10,11,7,4,4);}
                case "Settings" -> {g.drawOval(5,5,9,9);g.drawOval(8,8,3,3);g.drawLine(9,2,9,5);g.drawLine(9,14,9,17);g.drawLine(2,9,5,9);g.drawLine(14,9,17,9);}
                default -> {g.drawRoundRect(2,3,15,13,3,3);g.drawLine(5,8,14,8);g.drawLine(5,12,11,12);}
            }
            g.dispose();
        }
    }
    static class ChartPanel extends JPanel {
        private final Map<String,Double> values;private final String unit;
        ChartPanel(Map<String,Double> values,String unit){this.values=new LinkedHashMap<>(values);this.unit=unit;setOpaque(false);setPreferredSize(new Dimension(440,207));}
        @Override protected void paintComponent(Graphics gr){
            super.paintComponent(gr);Graphics2D g=(Graphics2D)gr.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight(),bottom=h-33,left=18,top=15,drawHeight=bottom-top;
            g.setColor(new Color(217,233,222));for(int i=0;i<4;i++)g.drawLine(left,top+(drawHeight*i/3),w-15,top+(drawHeight*i/3));
            if(values.isEmpty()){g.setColor(MUTED);g.drawString("Add production or financial records to display a chart.",25,h/2);g.dispose();return;}
            double max=Math.max(1,values.values().stream().mapToDouble(Double::doubleValue).max().orElse(1));
            int n=values.size(),space=Math.max(1,(w-38)/n),bar=Math.min(46,Math.max(13,space-18));int ix=0;
            for(Map.Entry<String,Double> e:values.entrySet()){
                int bx=left+space*ix+Math.max(0,(space-bar)/2);int bh=(int)(Math.max(0,e.getValue())/max*(drawHeight-22));
                g.setPaint(new GradientPaint(bx,top,GREEN,bx,bottom,new Color(160,217,143)));
                g.fillRoundRect(bx,bottom-bh,bar,bh,6,6);
                g.setColor(DARK);g.setFont(new Font("Segoe UI",Font.BOLD,11));
                String val=(unit.equals("₱")?"₱": "")+fmt(e.getValue())+(unit.equals("%")?"%":"");
                if(space>65)g.drawString(val,bx,Math.max(12,bottom-bh-5));
                g.setColor(MUTED);g.setFont(new Font("Segoe UI",Font.PLAIN,11));
                String name=e.getKey();if(name.length()>13)name=name.substring(0,12)+"…";
                g.drawString(name,bx,bottom+16);ix++;
            }
            g.dispose();
        }
    }
}
