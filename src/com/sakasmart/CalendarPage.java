package com.sakasmart;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.List;
import static com.sakasmart.Main.*;

/** Calendar V5.3: visible footer, AM/PM editing, and action-specific selection warnings. Storage, permissions, task cards, and CRUD remain unchanged. */
final class CalendarPage extends PageBase {
    private static final Color INK=new Color(11,67,43), GREEN1=new Color(44,174,87);
    private static final Color GREEN2=new Color(8,104,55), MINT=new Color(228,250,218);
    private static final Color MUTED_TEXT=new Color(71,117,89);
    private static final DateTimeFormatter DAY_LABEL=DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy",Locale.ENGLISH);
    private LocalDate selectedDate=LocalDate.now();

    CalendarPage(Main host){super(host);}

    JComponent build(){
        LocalDate now=LocalDate.now();
        if(!YearMonth.from(selectedDate).equals(host.calendarMonth))selectedDate=host.calendarMonth.atDay(1);
        List<Map<String,String>> all=rows("events");
        boolean livestock=host.calendarLivestock;
        List<Map<String,String>> chosen=new ArrayList<>();
        for(Map<String,String> e:all)if((livestock?"LIVESTOCK":"CROP").equalsIgnoreCase(v(e,"kind")))chosen.add(e);
        chosen.sort(Comparator.comparing((Map<String,String> e)->v(e,"date")).thenComparing(e->v(e,"time")));

        JPanel page=stack();
        long completed=all.stream().filter(e->"Completed".equalsIgnoreCase(v(e,"status"))).count();
        JPanel metrics=pane(new GridLayout(1,4,13,0));
        metrics.add(metric("THIS WEEK'S TASKS",String.valueOf(weekCount(all,now)),"Scheduled this week",new Color(218,245,194),"▦"));
        metrics.add(metric("OVERDUE TASKS",String.valueOf(DashboardPriorityLists.overdueCount(all,now)),"Needs your attention",new Color(255,222,211),"!"));
        metrics.add(metric("COMPLETED TASKS",String.valueOf(completed),"Recorded as complete",new Color(210,232,252),"✓"));
        metrics.add(metric("UPCOMING EVENTS",String.valueOf(DashboardPriorityLists.upcomingCount(all,now)),"Planned activities",new Color(255,239,184),"◷"));
        addBlock(page,metrics,113);

        JPanel toolbar=glass(new BorderLayout(15,5),new Color(235,254,226),new Color(196,237,180));
        toolbar.setBorder(new EmptyBorder(13,16,13,16));
        JPanel tabs=pane(new FlowLayout(FlowLayout.LEFT,7,0));
        JButton crops=glossButton("Crop Schedule",!livestock),animals=glossButton("Livestock Schedule",livestock);
        crops.addActionListener(e->{host.calendarLivestock=false;renderPage();});
        animals.addActionListener(e->{host.calendarLivestock=true;renderPage();});
        tabs.add(crops);tabs.add(animals);
        JPanel left=pane(new BorderLayout(0,5));
        left.add(label("FIELD & LIVESTOCK CALENDAR",17,true,INK),BorderLayout.NORTH);
        left.add(tabs,BorderLayout.CENTER);
        toolbar.add(left,BorderLayout.WEST);
        if(editable("events")){
            JButton add=glossButton("+  Add Task",true);
            add.addActionListener(e->eventForm(null));
            JPanel right=pane(new FlowLayout(FlowLayout.RIGHT,0,13));right.add(add);toolbar.add(right,BorderLayout.EAST);
        }
        addBlock(page,GlossyTheme.outlined(toolbar,new Color(230,249,224),new Color(128,190,137),1,30),115);

        JPanel calendar=glass(new BorderLayout(0,5),new Color(237,255,225),new Color(185,226,169));
        calendar.setBorder(new EmptyBorder(10,12,8,12));
        JPanel monthHeader=pane(new BorderLayout(10,0));
        JPanel monthActions=pane(new FlowLayout(FlowLayout.LEFT,8,0));
        JButton prev=glossButton("‹",false),next=glossButton("›",false),today=glossButton("Today",false);
        prev.addActionListener(e->{host.calendarMonth=host.calendarMonth.minusMonths(1);selectedDate=host.calendarMonth.atDay(1);renderPage();});
        next.addActionListener(e->{host.calendarMonth=host.calendarMonth.plusMonths(1);selectedDate=host.calendarMonth.atDay(1);renderPage();});
        today.addActionListener(e->{host.calendarMonth=YearMonth.now();selectedDate=LocalDate.now();renderPage();});
        monthActions.add(prev);
        monthActions.add(titleHolder(host.calendarMonth.getMonth().getDisplayName(java.time.format.TextStyle.FULL,Locale.ENGLISH).toUpperCase(Locale.ENGLISH)
                +" "+host.calendarMonth.getYear(),true,19));
        monthActions.add(next);monthActions.add(today);
        monthHeader.add(monthActions,BorderLayout.WEST);
        calendar.add(monthHeader,BorderLayout.NORTH);

        JPanel grid=pane(new GridLayout(0,7,4,4));
        for(String day:List.of("MON","TUE","WED","THU","FRI","SAT","SUN")){
            JLabel title=label(day,11,true,GREEN2);title.setHorizontalAlignment(SwingConstants.CENTER);grid.add(title);
        }
        int offset=host.calendarMonth.atDay(1).getDayOfWeek().getValue()-1;
        int monthDays=host.calendarMonth.lengthOfMonth();
        int slots=((offset+monthDays+6)/7)*7;
        List<DayTile> tiles=new ArrayList<>();
        for(int i=0;i<slots;i++){
            int day=i-offset+1;
            if(day<1||day>monthDays){
                // Keep the 7-column calendar visually filled rather than leaving transparent holes.
                LocalDate adjacent=host.calendarMonth.atDay(1).plusDays(day-1L);
                grid.add(new OutsideMonthTile(adjacent));
                continue;
            }
            LocalDate date=host.calendarMonth.atDay(day);
            List<Map<String,String>> dayTasks=forDate(chosen,date);
            DayTile tile=new DayTile(date,dayTasks,date.equals(now));
            tile.setSelectedDate(date.equals(selectedDate));
            tiles.add(tile);
            grid.add(tile);
        }
        calendar.add(grid,BorderLayout.CENTER);
        JPanel legend=pane(new FlowLayout(FlowLayout.LEFT,9,0));
        for(String txt:List.of(livestock?"LIVESTOCK":"CROP","● Planting","● Water / Care","● Animal health","● Completed")){
            JLabel entry=label(txt,11,false,txt.contains("health")?new Color(71,105,173):MUTED_TEXT);legend.add(entry);
        }
        calendar.add(legend,BorderLayout.SOUTH);

        JPanel side=glass(new BorderLayout(0,8),new Color(244,255,236),new Color(188,228,179));
        side.setBorder(new EmptyBorder(12,12,11,12));
        JLabel chosenLabel=label("",13,true,Color.WHITE);
        chosenLabel.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel selectedHeader=titleHolder("",false,13);
        selectedHeader.setLayout(new BorderLayout());
        selectedHeader.add(chosenLabel,BorderLayout.CENTER);
        selectedHeader.setPreferredSize(new Dimension(100,57));
        JPanel selectedTasks=pane(new BorderLayout());
        JPanel dayView=pane(new BorderLayout(0,8));
        dayView.add(selectedHeader,BorderLayout.NORTH);
        dayView.add(selectedTasks,BorderLayout.CENTER);
        side.add(dayView,BorderLayout.NORTH);

        JPanel records=pane(new BorderLayout(0,7));
        JLabel recTitle=label("TASK RECORDS  •  "+(livestock?"LIVESTOCK":"CROP"),15,true,INK);
        JPanel recordHeading=pane(new BorderLayout(0,3));
        recordHeading.add(recTitle,BorderLayout.NORTH);
        recordHeading.add(label("Select a task card to edit or complete",11,false,MUTED_TEXT),BorderLayout.SOUTH);
        records.add(recordHeading,BorderLayout.NORTH);
        // Vertically scrolling task cards: no JTable, no horizontal scrollbar.
        // The same stored rows and IDs are used for edit, completion, and deletion.
        JPanel list=pane(new BorderLayout());
        JPanel cardList=pane(new GridLayout(0,1,0,7));
        List<TaskCard> recordCards=new ArrayList<>();
        final java.util.concurrent.atomic.AtomicReference<Map<String,String>> current=new java.util.concurrent.atomic.AtomicReference<>();
        for(Map<String,String> record:chosen){
            TaskCard c=new TaskCard(record);
            // Selection listeners are set after the card exists.
            recordCards.add(c);
        }
        for(TaskCard c:recordCards)c.onChoose=()->{
            current.set(c.data);
            for(TaskCard other:recordCards)other.setChosen(other==c);
        };
        if(recordCards.isEmpty()){
            JLabel empty=label("No tasks in this schedule yet.",13,false,MUTED_TEXT);
            empty.setBorder(new EmptyBorder(18,9,18,9));cardList.add(empty);
        }else for(TaskCard c:recordCards)cardList.add(c);
        JPanel topAligned=pane(new BorderLayout());topAligned.add(cardList,BorderLayout.NORTH);
        JScrollPane scroll=new JScrollPane(topAligned,ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);scroll.getViewport().setOpaque(false);scroll.setOpaque(false);
        scroll.setVerticalScrollBar(new GreenScrollBar());
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        records.add(scroll,BorderLayout.CENTER);
        if(editable("events")){
            JPanel commands=pane(new FlowLayout(FlowLayout.LEFT,7,0));
            JButton edit=glossButton("Edit Task",false),done=glossButton("Mark Completed",true),delete=deleteButton();
            edit.addActionListener(e->{if(current.get()==null){noTaskSelected("Please select a task to edit.");return;}eventForm(current.get());});
            delete.addActionListener(e->{
                Map<String,String> record=current.get();
                if(record==null){noTaskSelected("Please select a task to delete.");return;}
                int answer=StyledDialogs.showConfirmDialog(frame,
                    "Are you sure you want to delete?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
                if(answer!=JOptionPane.YES_OPTION)return;
                try{
                    store.remove("events",v(record,"id"));
                    store.log(username(),"Deleted events #"+v(record,"id"));
                    renderPage();
                }catch(Exception ex){error(ex);}
            });
            done.addActionListener(e->{
                Map<String,String> record=current.get();
                if(record==null){noTaskSelected("Please select a task to mark as completed.");return;}
                try{store.edit("events",v(record,"id"),Map.of("status","Completed"));store.log(username(),"Completed task: "+v(record,"title"));renderPage();}
                catch(Exception ex){error(ex);}
            });
            commands.add(edit);commands.add(done);commands.add(delete);
            records.add(commands,BorderLayout.SOUTH);
        }
        // Visual separation: a bright top hairline and a darker green lower line.
        JPanel divider=new JPanel(){
            @Override protected void paintComponent(Graphics g){
                Graphics2D a=(Graphics2D)g.create();
                a.setPaint(new GradientPaint(0,0,new Color(72,153,92),getWidth(),0,new Color(196,238,190)));
                a.fillRoundRect(0,3,getWidth(),2,2,2);
                a.setColor(new Color(255,255,255,220));a.fillRect(0,6,getWidth(),1);
                a.dispose();
            }
        };
        divider.setOpaque(false);divider.setPreferredSize(new Dimension(1,9));
        JPanel recordBlock=pane(new BorderLayout(0,9));
        recordBlock.add(divider,BorderLayout.NORTH);
        recordBlock.add(records,BorderLayout.CENTER);
        side.add(recordBlock,BorderLayout.CENTER);
        Runnable updateDetails=()->{
            // Two lines stay readable in the narrow records panel without horizontal scrolling.
            chosenLabel.setText("<html><center><b>TASKS</b><br>"+selectedDate.format(DAY_LABEL).toUpperCase(Locale.ENGLISH)+"</center></html>");
            selectedTasks.removeAll();
            List<Map<String,String>> tasks=forDate(chosen,selectedDate);
            if(tasks.isEmpty()){
                JLabel empty=label("No tasks scheduled for this day.",12,false,MUTED_TEXT);
                empty.setBorder(new EmptyBorder(12,4,15,4));selectedTasks.add(empty,BorderLayout.CENTER);
            }else{
                JPanel cards=pane(new GridLayout(0,1,0,5));
                for(int i=0;i<Math.min(3,tasks.size());i++){
                    Map<String,String> task=tasks.get(i);
                    JLabel item=label(shorten(v(task,"title"),36)+"  •  "+v(task,"time"),12,true,INK);item.setIcon(new TaskGlyph(task,14));item.setIconTextGap(7);
                    item.setBorder(new CompoundBorder(BorderFactory.createLineBorder(new Color(183,222,175)),new EmptyBorder(6,9,6,7)));
                    item.setOpaque(true);item.setBackground(new Color(226,247,221));item.setToolTipText(v(task,"title"));cards.add(item);
                }
                if(tasks.size()>3)cards.add(label("+ "+(tasks.size()-3)+" more tasks",12,false,GREEN2));
                selectedTasks.add(cards,BorderLayout.CENTER);
            }
            selectedTasks.revalidate();selectedTasks.repaint();
            for(DayTile tile:tiles)tile.setSelectedDate(tile.date.equals(selectedDate));
        };
        for(DayTile tile:tiles)tile.onChoose=()->{selectedDate=tile.date;updateDetails.run();};
        updateDetails.run();

        // 60/40 distribution gives calendar task titles more room than a 50/50 split.
        // Panels use all available width, while the week count controls the preferred height.
        JPanel layout=pane(new GridBagLayout());
        GridBagConstraints lc=new GridBagConstraints();
        lc.gridx=0;lc.gridy=0;lc.fill=GridBagConstraints.BOTH;lc.weightx=.58;lc.weighty=1;
        lc.insets=new Insets(0,0,0,7);
        layout.add(GlossyTheme.outlined(calendar,new Color(235,249,229),new Color(121,180,133),2,29),lc);
        GridBagConstraints rc=new GridBagConstraints();
        rc.gridx=1;rc.gridy=0;rc.fill=GridBagConstraints.BOTH;rc.weightx=.42;rc.weighty=1;
        rc.insets=new Insets(0,6,0,0);
        layout.add(GlossyTheme.outlined(side,new Color(234,248,233),new Color(121,180,133),2,29),rc);
        // A five-week month fits comfortably without occupying the whole viewport.
        // Six-week months receive a little more space to keep task text legible.
        addBlock(page,layout,(slots/7==6)?456:(slots/7==5?416:382));
        return page;
    }

    private static long weekCount(List<Map<String,String>> tasks,LocalDate today){
        LocalDate start=today.minusDays(today.getDayOfWeek().getValue()-1);
        LocalDate end=start.plusDays(6);
        return tasks.stream().filter(e->{LocalDate d=parseDate(v(e,"date"));return d!=null&&!d.isBefore(start)&&!d.isAfter(end);}).count();
    }
    private static LocalDate parseDate(String value){try{return LocalDate.parse(value);}catch(Exception ex){return null;}}
    private static List<Map<String,String>> forDate(List<Map<String,String>> tasks,LocalDate day){
        List<Map<String,String>> result=new ArrayList<>();
        for(Map<String,String> e:tasks)if(day.toString().equals(v(e,"date")))result.add(e);
        return result;
    }
    private static String shorten(String value,int max){return value.length()<=max?value:value.substring(0,max-1)+"…";}
    private static String icon(Map<String,String> task){
        String name=v(task,"title").toLowerCase(Locale.ROOT);
        if(name.matches(".*(vaccin|veterinar|deworm|health|medicine).*"))return "✚";
        if(name.matches(".*(water|irrigat|spray).*"))return "●";
        if(name.matches(".*(fertiliz|pesticid|compost).*"))return "◆";
        if(name.matches(".*(harvest|collect|egg).*"))return "✦";
        if(name.matches(".*(feed|nutrition).*"))return "♥";
        if(name.matches(".*(breed|calv|birth).*"))return "♡";
        if(name.matches(".*(plant|seed|sow|soil).*"))return "✿";
        if("LIVESTOCK".equalsIgnoreCase(v(task,"kind")))return "♞";
        return "◈";
    }
    private static Color taskTint(Map<String,String> task){
        String s=v(task,"title").toLowerCase(Locale.ROOT);
        if("Completed".equalsIgnoreCase(v(task,"status")))return new Color(188,222,200);
        if(s.matches(".*(vaccin|deworm|health|veterinar).*"))return new Color(221,211,255);
        if(s.matches(".*(water|irrigat).*"))return new Color(194,229,253);
        if(s.matches(".*(feed|breed|calv|birth).*"))return new Color(255,220,200);
        if("LIVESTOCK".equalsIgnoreCase(v(task,"kind")))return new Color(244,214,239);
        return new Color(206,241,191);
    }
    private static JPanel glass(LayoutManager layout,Color top,Color bottom){return new JPanel(layout){
        {setOpaque(false);}
        @Override protected void paintComponent(Graphics g){
            Graphics2D x=(Graphics2D)g.create();x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();
            x.setColor(new Color(12,90,45,40));x.fillRoundRect(3,6,w-6,h-8,22,22);
            x.setPaint(new GradientPaint(0,0,top,0,h,bottom));x.fillRoundRect(1,1,w-3,h-7,22,22);
            x.setPaint(new GradientPaint(0,0,new Color(255,255,255,190),0,h/2f,new Color(255,255,255,0)));
            x.fillRoundRect(3,3,w-7,Math.max(3,h/2),20,20);
            x.setColor(new Color(123,184,128));x.drawRoundRect(1,1,w-4,h-8,22,22);
            x.dispose();super.paintComponent(g);
        }
    };}
    private static JButton glossButton(String text,boolean active){
        JButton button=new JButton(text){@Override protected void paintComponent(Graphics g){
            Graphics2D x=(Graphics2D)g.create();x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();
            Color top=active?new Color(82,205,109):new Color(248,255,244);
            Color bottom=active?new Color(4,108,55):new Color(180,216,181);
            if(getModel().isRollover())top=active?new Color(111,222,130):Color.WHITE;
            x.setColor(new Color(9,72,44,60));x.fillRoundRect(2,5,w-4,h-6,13,13);
            x.setPaint(new GradientPaint(0,0,top,0,h-4,bottom));x.fillRoundRect(1,1,w-3,h-6,13,13);
            x.setPaint(new GradientPaint(0,1,new Color(255,255,255,175),0,h/2f,new Color(255,255,255,0)));
            x.fillRoundRect(3,3,w-7,Math.max(2,h/2-1),11,11);
            x.setColor(active?new Color(7,105,52):new Color(103,152,113));x.drawRoundRect(1,1,w-4,h-7,13,13);
            x.dispose();super.paintComponent(g);
        }};
        button.setForeground(active?Color.WHITE:INK);button.setFont(new Font("Segoe UI",Font.BOLD,12));
        button.setContentAreaFilled(false);button.setBorder(new EmptyBorder(9,14,12,14));
        button.setFocusPainted(false);button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }
    /** Glossy red destructive-action button. Uses the same dimensions as the other Calendar actions. */
    private static JButton deleteButton(){
        JButton button=new JButton("Delete"){
            @Override protected void paintComponent(Graphics g){
                Graphics2D x=(Graphics2D)g.create();
                x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                int w=getWidth(),h=getHeight();
                Color top=getModel().isRollover()?new Color(255,150,158):new Color(250,107,118);
                Color bottom=new Color(167,27,48);
                x.setColor(new Color(100,17,34,65));x.fillRoundRect(2,5,w-4,h-6,13,13);
                x.setPaint(new GradientPaint(0,0,top,0,h-4,bottom));
                x.fillRoundRect(1,1,w-3,h-6,13,13);
                x.setPaint(new GradientPaint(0,1,new Color(255,255,255,170),0,h/2f,new Color(255,255,255,0)));
                x.fillRoundRect(3,3,w-7,Math.max(2,h/2-1),11,11);
                x.setColor(new Color(152,32,53));x.drawRoundRect(1,1,w-4,h-7,13,13);
                x.dispose();super.paintComponent(g);
            }
        };
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Segoe UI",Font.BOLD,12));
        button.setContentAreaFilled(false);
        button.setBorder(new EmptyBorder(9,14,12,14));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }
    /** Raised two-tone green holder with shine and a double border, used for section titles. */
    private static JPanel titleHolder(String text,boolean month,int fontSize){
        JPanel holder=new JPanel(new BorderLayout()){
            @Override protected void paintComponent(Graphics g){
                Graphics2D x=(Graphics2D)g.create();
                x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                int w=getWidth(),h=getHeight();
                if(w<5||h<7){x.dispose();return;}
                x.setColor(new Color(6,65,39,65));x.fillRoundRect(2,5,w-4,h-6,17,17);
                x.setPaint(new GradientPaint(0,1,new Color(92,209,108),0,h-5,new Color(7,96,52)));
                x.fillRoundRect(1,1,w-3,h-6,17,17);
                x.setPaint(new GradientPaint(0,1,new Color(255,255,255,175),0,h*.61f,new Color(255,255,255,0)));
                x.fillRoundRect(4,3,w-9,Math.max(3,h/2),15,15);
                x.setStroke(new BasicStroke(1.6f));x.setColor(new Color(230,255,226,220));
                x.drawRoundRect(2,2,w-5,h-8,16,16);
                x.setStroke(new BasicStroke(1f));x.setColor(new Color(10,101,50,175));
                x.drawRoundRect(4,4,w-9,h-12,13,13);
                x.dispose();super.paintComponent(g);
            }
        };
        holder.setOpaque(false);holder.setBorder(new EmptyBorder(month?7:6,month?17:10,month?10:9,month?17:10));
        if(!text.isEmpty()){
            JLabel title=label(text,fontSize,true,Color.WHITE);
            title.setHorizontalAlignment(SwingConstants.CENTER);
            holder.add(title,BorderLayout.CENTER);
        }
        if(month)holder.setPreferredSize(new Dimension(210,44));
        return holder;
    }
    /** Pale inset day for dates falling in the previous or next month. */
    private static final class OutsideMonthTile extends JPanel{
        OutsideMonthTile(LocalDate date){super(new BorderLayout());setOpaque(false);
            JLabel number=label(String.valueOf(date.getDayOfMonth()),15,true,new Color(143,174,146));
            number.setBorder(new EmptyBorder(6,8,0,0));add(number,BorderLayout.NORTH);
            setToolTipText(date.toString());
        }
        @Override protected void paintComponent(Graphics g){
            Graphics2D x=(Graphics2D)g.create();x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();
            x.setPaint(new GradientPaint(0,0,new Color(236,245,232),0,h,new Color(219,236,216)));
            x.fillRoundRect(1,1,w-2,h-4,11,11);
            x.setColor(new Color(200,219,197));x.drawRoundRect(1,1,w-3,h-5,11,11);x.dispose();
            super.paintComponent(g);
        }
    }
    /** Compact painted chip. No nested text component means no cropped label baseline. */
    private static final class TaskChip extends JComponent {
        final Map<String,String> task;
        TaskChip(Map<String,String> task){
            this.task=task;setOpaque(false);
            setPreferredSize(new Dimension(30,31));
            setToolTipText(v(task,"title")+"  •  "+v(task,"time"));
        }
        @Override protected void paintComponent(Graphics g){
            Graphics2D a=(Graphics2D)g.create();
            a.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            a.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();
            if(w<18||h<17){a.dispose();return;}
            a.setPaint(new GradientPaint(0,0,new Color(253,255,250),0,h,taskTint(task)));
            a.fillRoundRect(1,0,w-2,h-2,8,8);
            a.setColor(new Color(97,168,107));a.drawRoundRect(1,0,w-3,h-3,8,8);
            // Icon is given its own small area. The text uses the entire remaining width.
            int iconSize=11;
            new TaskGlyph(task,iconSize).paintIcon(this,a,4,3);
            int tx=17, room=Math.max(0,w-tx-3);
            a.setFont(new Font("Segoe UI",Font.BOLD,10));a.setColor(INK);
            FontMetrics fm=a.getFontMetrics();
            if(room>12){
                List<String> lines=wrapAtMostTwo(v(task,"title"),fm,room);
                int lineHeight=Math.min(12,fm.getHeight());
                int total=lines.size()*lineHeight;
                int baseline=Math.max(fm.getAscent()+1,(h-total)/2+fm.getAscent());
                for(String line:lines){
                    if(baseline<=h-3)a.drawString(line,tx,baseline);
                    baseline+=lineHeight;
                }
            }
            a.dispose();
        }
    }
    /** Wrap by actual font width, ellipsize the last line when space runs out. */
    private static List<String> wrapAtMostTwo(String value,FontMetrics fm,int width){
        List<String> lines=new ArrayList<>();
        if(value==null||value.isBlank())return List.of("Task");
        String[] words=value.trim().split("\\s+");
        String line="";
        int index=0;
        while(index<words.length){
            String candidate=line.isEmpty()?words[index]:line+" "+words[index];
            if(fm.stringWidth(candidate)<=width){line=candidate;index++;continue;}
            if(line.isEmpty()){
                lines.add(ellipsis(words[index],fm,width));index++;
            }else{
                lines.add(line);line="";
            }
            if(lines.size()==2)break;
        }
        if(lines.size()<2&&!line.isEmpty())lines.add(line);
        if(lines.size()>2)return lines.subList(0,2);
        if(index<words.length && !lines.isEmpty()){
            String last=lines.get(lines.size()-1);
            lines.set(lines.size()-1,ellipsis(last+"…",fm,width));
        }
        return lines;
    }
    private static String ellipsis(String text,FontMetrics fm,int maxWidth){
        if(fm.stringWidth(text)<=maxWidth)return text;
        String suffix="…";
        if(fm.stringWidth(suffix)>maxWidth)return "";
        int end=text.length();
        while(end>0&&fm.stringWidth(text.substring(0,end)+suffix)>maxWidth)end--;
        return text.substring(0,end)+suffix;
    }
    private static JComponent metric(String title,String value,String note,Color tint,String glyph){
        // Reuse the application's original double-layer glossy metric treatment.
        String actualTitle=title.contains("WEEK")?"Scheduled Tasks":title.contains("OVERDUE")?"Overdue Tasks":title.contains("COMPLETED")?"Completed Tasks":"Upcoming Events";
        return stat(actualTitle,value,note,tint,glyph);
    }
    private static final class DayTile extends JPanel {
        final LocalDate date;
        private boolean selectedDate;
        Runnable onChoose;
        DayTile(LocalDate date,List<Map<String,String>> tasks,boolean today){
            super(new BorderLayout(0,2));this.date=date;setOpaque(false);
            setBorder(new EmptyBorder(3,4,4,4));
            JLabel number=label(Integer.toString(date.getDayOfMonth()),17,true,today?new Color(0,106,52):INK);
            number.setBorder(new EmptyBorder(0,2,0,0));
            add(number,BorderLayout.NORTH);
            if(!tasks.isEmpty()){
                // Reserve the bottom area for a single readable two-line chip.
                // More tasks remain accessible via the selected-day detail pane.
                TaskChip chip=new TaskChip(tasks.get(0));
                JPanel holder=pane(new BorderLayout());holder.add(chip,BorderLayout.SOUTH);
                add(holder,BorderLayout.CENTER);
                if(tasks.size()>1){
                    setToolTipText(date+" • "+tasks.size()+" tasks. First: "+v(tasks.get(0),"title"));
                }
                forwardClick(chip);
                forwardClick(holder);
            }else setToolTipText(date.toString());
            forwardClick(this);forwardClick(number);
        }
        private void forwardClick(Component component){
            component.addMouseListener(new MouseAdapter(){
                @Override public void mouseClicked(MouseEvent e){if(onChoose!=null)onChoose.run();}
                @Override public void mouseEntered(MouseEvent e){component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));}
            });
        }
        void setSelectedDate(boolean selected){selectedDate=selected;repaint();}
        @Override protected void paintComponent(Graphics g){
            Graphics2D x=(Graphics2D)g.create();x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();
            if(w<7||h<8){x.dispose();return;}
            x.setColor(new Color(10,71,39,45));x.fillRoundRect(1,4,w-2,h-5,12,12);
            x.setPaint(new GradientPaint(0,0,selectedDate?new Color(185,250,188):new Color(255,255,253),0,h,
                    selectedDate?new Color(112,211,135):new Color(219,244,213)));
            x.fillRoundRect(1,1,w-2,h-5,12,12);
            x.setPaint(new GradientPaint(0,1,new Color(255,255,255,205),0,h*.55f,new Color(255,255,255,0)));
            x.fillRoundRect(3,3,w-6,Math.max(4,h/2),10,10);
            x.setColor(selectedDate?new Color(0,122,57):new Color(166,211,169));
            x.setStroke(new BasicStroke(selectedDate?2.5f:1.1f));x.drawRoundRect(1,1,w-3,h-6,12,12);
            x.dispose();super.paintComponent(g);
        }
    }
    /** V5.1 visual-only Add/Edit form. All database calls retain their original flow. */
    private void eventForm(Map<String,String> original){
        if(!editable("events")){info("You do not have permission to modify farm events.");return;}
        boolean editing=original!=null;
        JDialog dialog=new JDialog(frame,editing?"Edit Calendar Task":"Add Calendar Task",Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        // Keep the modal inside the usable screen: the OS taskbar and high-DPI
        // display scaling must never push Save/Cancel offscreen.
        Rectangle usable=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int width=Math.min(800,Math.max(570,usable.width-64));
        int height=Math.min(690,Math.max(450,usable.height-72));
        dialog.setMinimumSize(new Dimension(Math.min(600,width),Math.min(450,height)));
        dialog.setSize(width,height);
        dialog.setLocationRelativeTo(frame);

        JPanel shell=new JPanel(new BorderLayout());
        shell.setBackground(new Color(13,80,44));
        shell.setBorder(new EmptyBorder(8,9,9,9));
        JPanel heading=new FarmBanner(editing);
        heading.setPreferredSize(new Dimension(100,119));
        shell.add(heading,BorderLayout.NORTH);

        JTextField name=farmInput(editing?v(original,"title"):"");
        JComboBox<String> kind=farmChoice(new String[]{"CROP","LIVESTOCK"},editing?v(original,"kind"):(host.calendarLivestock?"LIVESTOCK":"CROP"));
        // All task choices are the original Schema-approved values.
        String[] categories={"Planting","Watering","Fertilizing","Harvest","Vaccination","Breeding","Feeding","Collection","Maintenance","Other"};
        JComboBox<String> category=farmChoice(categories,editing?v(original,"category"):"Planting");
        JTextField target=farmInput(editing?v(original,"target"):"");
        JTextField date=farmInput(editing?v(original,"date"):selectedDate.toString());
        // Display a 12-hour clock while preserving the database's HH:mm (24-hour) format.
        // Existing PostgreSQL rows can continue storing e.g. 08:00 and 20:00 unchanged.
        LocalTime savedTime;
        try { savedTime=LocalTime.parse(editing?v(original,"time"):"08:00",DateTimeFormatter.ofPattern("HH:mm")); }
        catch(DateTimeException ex) { savedTime=LocalTime.of(8,0); }
        JTextField time=farmInput(savedTime.format(DateTimeFormatter.ofPattern("hh:mm",Locale.ENGLISH)));
        JComboBox<String> period=farmChoice(new String[]{"AM","PM"},savedTime.getHour()<12?"AM":"PM");
        JComponent timeWithPeriod=timePeriodField(time,period);
        JComboBox<String> status=farmChoice(new String[]{"Upcoming","Completed","Cancelled"},editing?v(original,"status"):"Upcoming");
        JTextArea notes=new JTextArea(editing?v(original,"notes"):"",3,18);
        notes.setLineWrap(true);notes.setWrapStyleWord(true);
        notes.setFont(new Font("Segoe UI",Font.PLAIN,14));notes.setForeground(INK);
        notes.setBackground(new Color(250,255,248));notes.setBorder(new EmptyBorder(8,10,8,10));
        JScrollPane noteScroll=new JScrollPane(notes,ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        noteScroll.setBorder(BorderFactory.createLineBorder(new Color(113,182,124),1,true));
        noteScroll.setVerticalScrollBar(new GreenScrollBar());
        noteScroll.setPreferredSize(new Dimension(120,66));

        JComponent datePicker=datePickerField(date,dialog);
        JLabel targetLabel=label("Field / Plot *",14,true,INK);
        Runnable updateKind=()->{
            boolean animal="LIVESTOCK".equals(kind.getSelectedItem());
            targetLabel.setText(animal?"Animal Group / Pen *":"Field / Plot *");
            if(!editing || category.getSelectedItem()==null)category.setSelectedItem(animal?"Feeding":"Planting");
        };
        kind.addActionListener(e->updateKind.run());updateKind.run();

        JPanel content=new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content,BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(10,15,12,15));

        JPanel basic=formSection("BASIC INFORMATION","Task identity and schedule",false);
        basic.add(formRow("Task Name *",name));
        basic.add(formRow("Schedule *",kind));
        basic.add(formRow("Task Type *",category));
        content.add(basic);
        content.add(Box.createVerticalStrut(9));

        JPanel location=formSection("LOCATION & TIMING","Where and when the activity happens",true);
        location.add(formRow(targetLabel,target));
        location.add(formRow("Date (YYYY-MM-DD) *",datePicker));
        location.add(formRow("Time (HH:MM) *",timeWithPeriod));
        location.add(formRow("Status *",status));
        content.add(location);
        content.add(Box.createVerticalStrut(9));

        JPanel extra=formSection("ADDITIONAL DETAILS","Optional instructions for farm members",false);
        extra.add(formRow("Notes",noteScroll));
        content.add(extra);

        JPanel scrollerContent=new GreenFormCanvas(new BorderLayout());
        scrollerContent.setBorder(new EmptyBorder(3,4,4,4));
        scrollerContent.add(content,BorderLayout.NORTH);
        JScrollPane scroll=new JScrollPane(scrollerContent,ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(new EmptyBorder(0,0,0,0));
        scroll.setOpaque(false);scroll.getViewport().setOpaque(false);
        scroll.setVerticalScrollBar(new GreenScrollBar());
        scroll.getVerticalScrollBar().setUnitIncrement(19);
        shell.add(scroll,BorderLayout.CENTER);

        JPanel footer=new FarmFooter(new FlowLayout(FlowLayout.RIGHT,13,15));
        JButton cancel=glossButton("Cancel",false),save=glossButton(editing?"Save Changes":"Save Task",true);
        cancel.setPreferredSize(new Dimension(112,44));
        save.setPreferredSize(new Dimension(148,44));
        cancel.addActionListener(e->dialog.dispose());
        save.addActionListener(e->{
            try{
                String titleValue=name.getText().trim(),targetValue=target.getText().trim();
                String dateValue=date.getText().trim(),timeValue=time.getText().trim();
                if(titleValue.isBlank()||targetValue.isBlank())throw new IllegalArgumentException("Task Name and Field / Animal Group are required.");
                LocalDate due=LocalDate.parse(dateValue);
                LocalTime at=parse12HourTime(timeValue,String.valueOf(period.getSelectedItem()));
                Map<String,String> values=new LinkedHashMap<>();
                values.put("title",titleValue);
                values.put("kind",String.valueOf(kind.getSelectedItem()));
                values.put("category",String.valueOf(category.getSelectedItem()));
                values.put("target",targetValue);
                values.put("date",due.toString());values.put("time",at.format(DateTimeFormatter.ofPattern("HH:mm")));
                values.put("status",String.valueOf(status.getSelectedItem()));values.put("notes",notes.getText().trim());
                // ScopedStorage validates this farm ID against the authenticated session.
                values.put("farm_id",user==null?"":v(user,"farm_id"));
                if(editing){store.edit("events",v(original,"id"),values);store.log(username(),"Updated events #"+v(original,"id"));}
                else{Map<String,String> row=store.add("events",values);store.log(username(),"Added events #"+v(row,"id"));}
                dialog.dispose();host.calendarMonth=YearMonth.from(due);selectedDate=due;renderPage();
            }catch(DateTimeException ex){JOptionPane.showMessageDialog(dialog,"Enter a valid date (YYYY-MM-DD) and time (HH:MM AM/PM).","Invalid Date or Time",JOptionPane.WARNING_MESSAGE);}
            catch(IllegalArgumentException ex){JOptionPane.showMessageDialog(dialog,ex.getMessage(),"Check Task Details",JOptionPane.WARNING_MESSAGE);}
            catch(Exception ex){error(ex);}
        });
        footer.add(cancel);footer.add(save);
        footer.setPreferredSize(new Dimension(100,72));
        // Footer is a sibling of the scroll pane, never part of its content.
        // This guarantees buttons stay visible while the middle form scrolls.
        shell.add(footer,BorderLayout.SOUTH);
        dialog.setContentPane(shell);
        dialog.setVisible(true);
    }

    /** Self-contained readable warning: never renders a blank legacy information dialog. */
    private void noTaskSelected(String message){
        JDialog notice=new JDialog(frame,"No Task Selected",Dialog.ModalityType.APPLICATION_MODAL);
        notice.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel body=new JPanel(new BorderLayout(0,12));
        body.setBackground(new Color(13,79,47));
        body.setBorder(new EmptyBorder(20,24,17,24));
        JLabel title=label("NO TASK SELECTED",19,true,Color.WHITE);
        JLabel description=label(message,14,false,new Color(218,249,223));
        JPanel text=pane(new GridLayout(2,1,0,8));
        text.add(title);text.add(description);
        body.add(text,BorderLayout.CENTER);
        JButton okay=glossButton("Okay",true);
        okay.addActionListener(e->notice.dispose());
        JPanel bottom=pane(new FlowLayout(FlowLayout.RIGHT,0,0));bottom.add(okay);
        body.add(bottom,BorderLayout.SOUTH);
        notice.setContentPane(body);
        notice.setSize(465,177);
        notice.setResizable(false);
        notice.setLocationRelativeTo(frame);
        notice.setVisible(true);
    }

    /** Accept 1-12 HH:MM with an explicit AM/PM; persist in legacy 24-hour HH:mm. */
    private static LocalTime parse12HourTime(String value,String period){
        if(!value.matches("(?:0?[1-9]|1[0-2]):[0-5][0-9]"))
            throw new IllegalArgumentException("Enter a time from 01:00 to 12:59, then choose AM or PM.");
        String[] parts=value.split(":");
        int hour=Integer.parseInt(parts[0]);
        int minute=Integer.parseInt(parts[1]);
        if(!"AM".equals(period) && !"PM".equals(period))
            throw new IllegalArgumentException("Please choose AM or PM.");
        hour=hour%12+("PM".equals(period)?12:0);
        return LocalTime.of(hour,minute);
    }
    /** Rounded combined time input and separate, visible AM/PM selector. */
    private static JComponent timePeriodField(JTextField field,JComboBox<String> period){
        JPanel combined=pane(new BorderLayout(8,0));
        combined.add(roundInput(field),BorderLayout.CENTER);
        JComponent periodShell=roundInput(period);
        periodShell.setPreferredSize(new Dimension(105,48));
        combined.add(periodShell,BorderLayout.EAST);
        return combined;
    }

    /** Inputs are transparent inside a painted rounded shell, not merely outlined rectangles. */
    private static JTextField farmInput(String value){
        JTextField f=new JTextField(value);
        f.setFont(new Font("Segoe UI",Font.PLAIN,15));
        f.setForeground(INK);f.setCaretColor(GREEN2);
        f.setOpaque(false);
        f.setBorder(new EmptyBorder(8,14,8,10));
        f.setPreferredSize(new Dimension(150,43));
        return f;
    }
    private static JComboBox<String> farmChoice(String[] choices,String selected){
        JComboBox<String> c=new JComboBox<>(choices);
        c.setSelectedItem(selected);
        c.setFont(new Font("Segoe UI",Font.BOLD,14));
        c.setForeground(INK);c.setBackground(new Color(244,255,242));
        c.setOpaque(false);c.setFocusable(true);
        c.setBorder(new EmptyBorder(0,8,0,0));
        c.setPreferredSize(new Dimension(155,43));
        c.setUI(new javax.swing.plaf.basic.BasicComboBoxUI(){
            @Override protected JButton createArrowButton(){
                JButton b=new JButton(new ArrowGlyph());
                b.setPreferredSize(new Dimension(39,38));
                b.setBorder(BorderFactory.createEmptyBorder());
                b.setContentAreaFilled(false);b.setFocusPainted(false);
                b.setOpaque(false);b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                b.setToolTipText("Choose an option");
                return b;
            }
        });
        return c;
    }
    private static final class ArrowGlyph implements Icon {
        public int getIconWidth(){return 36;}
        public int getIconHeight(){return 36;}
        public void paintIcon(Component c,Graphics g,int x,int y){
            Graphics2D a=(Graphics2D)g.create();a.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            a.setPaint(new GradientPaint(0,y,new Color(249,255,246),0,y+36,new Color(184,229,190)));
            a.fillRoundRect(x+1,y+1,34,34,10,10);
            a.setColor(new Color(85,164,105));a.drawRoundRect(x+1,y+1,34,34,10,10);
            a.setColor(new Color(13,94,54));Path2D p=new Path2D.Float();p.moveTo(x+11,y+14);p.lineTo(x+25,y+14);p.lineTo(x+18,y+23);p.closePath();a.fill(p);a.dispose();
        }
    }
    private static JComponent roundInput(JComponent inner){
        RoundedInputShell shell=new RoundedInputShell(new BorderLayout());
        shell.setBorder(new EmptyBorder(2,4,5,4));
        inner.setOpaque(false);
        inner.setBorder(inner instanceof JScrollPane?new EmptyBorder(2,6,2,6):inner.getBorder());
        shell.add(inner,BorderLayout.CENTER);
        inner.addFocusListener(new FocusAdapter(){
            public void focusGained(FocusEvent e){shell.repaint();}
            public void focusLost(FocusEvent e){shell.repaint();}
        });
        shell.setPreferredSize(new Dimension(155,48));
        return shell;
    }
    private static final class RoundedInputShell extends JPanel{
        RoundedInputShell(LayoutManager layout){super(layout);setOpaque(false);}
        @Override protected void paintComponent(Graphics g){
            Graphics2D a=(Graphics2D)g.create();a.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();if(w<5||h<5){a.dispose();return;}
            boolean focused=false;
            for(Component c:getComponents())if(c.isFocusOwner() || c instanceof JComboBox<?> box && box.isPopupVisible())focused=true;
            a.setColor(new Color(14,97,48,48));a.fillRoundRect(2,5,w-4,h-6,17,17);
            a.setPaint(new GradientPaint(0,1,new Color(255,255,255),0,h-4,new Color(235,252,231)));
            a.fillRoundRect(1,1,w-3,h-6,16,16);
            a.setColor(focused?new Color(10,148,70,85):new Color(71,172,97,39));
            a.setStroke(new BasicStroke(focused?3f:2f));a.drawRoundRect(3,3,w-7,h-10,14,14);
            a.setColor(focused?new Color(8,139,59):new Color(62,152,81));
            a.setStroke(new BasicStroke(focused?1.7f:1.25f));a.drawRoundRect(1,1,w-4,h-7,16,16);
            a.setColor(new Color(255,255,255,205));a.drawRoundRect(4,3,w-10,h-12,13,13);
            a.dispose();super.paintComponent(g);
        }
    }
    /** A real date-picker calendar: month navigation and day selection update the original date field. */
    private static JComponent datePickerField(JTextField dateField,JDialog owner){
        JPanel combined=pane(new BorderLayout(6,0));
        combined.add(roundInput(dateField),BorderLayout.CENTER);
        JButton pick=new JButton(new CalendarGlyph());
        pick.setOpaque(false);pick.setContentAreaFilled(false);pick.setBorderPainted(false);
        pick.setFocusPainted(false);pick.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        pick.setPreferredSize(new Dimension(46,47));pick.setToolTipText("Choose a date from calendar");
        pick.addActionListener(e->showDatePicker(pick,dateField));
        combined.add(pick,BorderLayout.EAST);
        return combined;
    }
    private static final class CalendarGlyph implements Icon {
        public int getIconWidth(){return 43;}
        public int getIconHeight(){return 44;}
        public void paintIcon(Component c,Graphics g,int x,int y){
            Graphics2D a=(Graphics2D)g.create();a.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            a.setColor(new Color(14,89,47,55));a.fillRoundRect(x+2,y+5,40,39,12,12);
            a.setPaint(new GradientPaint(0,y,new Color(237,255,236),0,y+41,new Color(167,226,174)));
            a.fillRoundRect(x+1,y+1,40,39,12,12);
            a.setColor(new Color(51,144,79));a.setStroke(new BasicStroke(1.3f));a.drawRoundRect(x+1,y+1,40,39,12,12);
            a.setColor(new Color(10,100,50));a.setStroke(new BasicStroke(2.2f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            a.drawRoundRect(x+11,y+12,20,19,3,3);a.drawLine(x+11,y+18,x+31,y+18);
            a.drawLine(x+16,y+9,x+16,y+15);a.drawLine(x+26,y+9,x+26,y+15);
            a.dispose();
        }
    }
    private static void showDatePicker(Component anchor,JTextField field){
        LocalDate initial;
        try{initial=LocalDate.parse(field.getText().trim());}
        catch(DateTimeException ignored){initial=LocalDate.now();}
        final LocalDate[] picked={initial};
        final YearMonth[] month={YearMonth.from(initial)};
        JPopupMenu popup=new JPopupMenu();
        popup.setBorder(BorderFactory.createLineBorder(new Color(42,150,79),2,true));
        JPanel holder=new JPanel(new BorderLayout(0,9));holder.setBackground(new Color(240,254,236));
        holder.setBorder(new EmptyBorder(12,12,12,12));
        JLabel title=label("",17,true,INK);title.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel calendarGrid=new JPanel(new GridLayout(0,7,4,4));
        calendarGrid.setOpaque(false);
        Runnable[] rebuild={null};
        rebuild[0]=()->{
            YearMonth display=month[0];
            title.setText(display.getMonth().getDisplayName(java.time.format.TextStyle.FULL,Locale.ENGLISH)+" "+display.getYear());
            calendarGrid.removeAll();
            for(String dow:new String[]{"M","T","W","T","F","S","S"}){
                JLabel heading=label(dow,12,true,GREEN2);heading.setHorizontalAlignment(SwingConstants.CENTER);calendarGrid.add(heading);
            }
            int offset=display.atDay(1).getDayOfWeek().getValue()-1;
            for(int i=0;i<offset;i++)calendarGrid.add(new JLabel());
            for(int d=1;d<=display.lengthOfMonth();d++){
                LocalDate day=display.atDay(d);
                JButton cell=new JButton(String.valueOf(d));
                cell.setFont(new Font("Segoe UI",Font.BOLD,13));
                boolean active=day.equals(picked[0]);
                cell.setForeground(active?Color.WHITE:INK);
                cell.setBackground(active?new Color(20,132,63):day.equals(LocalDate.now())?new Color(193,239,191):new Color(253,255,249));
                cell.setOpaque(true);cell.setFocusPainted(false);
                cell.setBorder(BorderFactory.createLineBorder(active?new Color(9,100,50):new Color(177,216,172),1,true));
                cell.setPreferredSize(new Dimension(37,35));
                cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                cell.addActionListener(e->{picked[0]=day;field.setText(day.toString());popup.setVisible(false);});
                calendarGrid.add(cell);
            }
            calendarGrid.revalidate();calendarGrid.repaint();holder.revalidate();holder.repaint();
        };
        JButton prev=new JButton("‹"),next=new JButton("›");
        for(JButton nav:new JButton[]{prev,next}){
            nav.setFont(new Font("Segoe UI",Font.BOLD,20));nav.setForeground(GREEN2);
            nav.setBackground(new Color(218,246,210));nav.setFocusPainted(false);
            nav.setBorder(BorderFactory.createLineBorder(new Color(107,184,120),1,true));
            nav.setPreferredSize(new Dimension(35,33));
        }
        prev.addActionListener(e->{month[0]=month[0].minusMonths(1);rebuild[0].run();});
        next.addActionListener(e->{month[0]=month[0].plusMonths(1);rebuild[0].run();});
        JPanel nav=pane(new BorderLayout(7,0));nav.add(prev,BorderLayout.WEST);nav.add(title,BorderLayout.CENTER);nav.add(next,BorderLayout.EAST);
        holder.add(nav,BorderLayout.NORTH);holder.add(calendarGrid,BorderLayout.CENTER);
        JButton today=new JButton("Today");today.setFont(new Font("Segoe UI",Font.BOLD,12));
        today.setBackground(new Color(213,247,210));today.setForeground(GREEN2);
        today.addActionListener(e->{field.setText(LocalDate.now().toString());popup.setVisible(false);});
        holder.add(today,BorderLayout.SOUTH);
        popup.add(holder);rebuild[0].run();
        // Anchored under the calendar button, and works for both Add and Edit.
        popup.show(anchor,Math.max(-273,anchor.getWidth()-295),anchor.getHeight());
    }
    private static JPanel formSection(String title,String subtitle,boolean second){
        JPanel p=new RoundedFormSection();
        p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(14,18,13,18));
        JPanel heading=pane(new BorderLayout(10,0));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel badge=label(second?"LOCATION & TIMING":"BASIC INFORMATION".equals(title)?"BASIC INFORMATION":title,15,true,Color.WHITE);
        JPanel strip=new GreenRibbon(new BorderLayout());
        strip.add(badge,BorderLayout.CENTER);
        strip.setBorder(new EmptyBorder(8,17,8,13));
        strip.setPreferredSize(new Dimension(290,39));
        JPanel stripWrap=pane(new FlowLayout(FlowLayout.LEFT,0,0));stripWrap.add(strip);
        heading.add(stripWrap,BorderLayout.NORTH);
        JLabel help=label(subtitle,12,false,MUTED_TEXT);help.setBorder(new EmptyBorder(2,18,0,0));
        heading.add(help,BorderLayout.SOUTH);
        p.add(heading);p.add(Box.createVerticalStrut(12));
        return p;
    }
    private static JComponent formRow(String caption,JComponent input){return formRow(label(caption,14,true,INK),input);}
    private static JComponent formRow(JLabel caption,JComponent input){
        JPanel row=pane(new BorderLayout(16,0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(0,10,9,10));
        JPanel labelWrap=pane(new GridBagLayout());
        labelWrap.setPreferredSize(new Dimension(190,44));
        GridBagConstraints c=new GridBagConstraints();c.gridx=0;c.gridy=0;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;
        labelWrap.add(caption,c);
        row.add(labelWrap,BorderLayout.WEST);
        JPanel inputWrap=pane(new BorderLayout());
        inputWrap.add(input instanceof JTextField || input instanceof JComboBox<?> || input instanceof JScrollPane ? roundInput(input) : input,BorderLayout.CENTER);
        row.add(inputWrap,BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE,input instanceof JScrollPane?75:53));
        row.setPreferredSize(new Dimension(400,input instanceof JScrollPane?75:53));
        return row;
    }
    private static final class RoundedFormSection extends JPanel{
        RoundedFormSection(){super();setOpaque(false);}
        @Override protected void paintComponent(Graphics g){
            Graphics2D a=(Graphics2D)g.create();a.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();
            a.setColor(new Color(35,102,54,35));a.fillRoundRect(3,6,w-6,h-7,25,25);
            a.setPaint(new GradientPaint(0,0,new Color(254,255,251),0,h,new Color(215,245,215)));
            a.fillRoundRect(1,1,w-4,h-8,25,25);
            a.setPaint(new GradientPaint(0,1,new Color(255,255,255,190),0,Math.max(h/2,1),new Color(255,255,255,0)));
            a.fillRoundRect(4,4,w-10,Math.max(6,h/2),21,21);
            a.setColor(new Color(47,161,78));a.setStroke(new BasicStroke(1.35f));a.drawRoundRect(1,1,w-5,h-9,25,25);
            a.setColor(new Color(255,255,255,210));a.drawRoundRect(5,5,w-13,h-17,20,20);
            a.dispose();super.paintComponent(g);
        }
    }
    private static final class GreenRibbon extends JPanel{
        GreenRibbon(LayoutManager layout){super(layout);setOpaque(false);}
        @Override protected void paintComponent(Graphics g){
            Graphics2D a=(Graphics2D)g.create();a.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();
            a.setColor(new Color(8,82,40,66));a.fillRoundRect(2,4,w-4,h-4,22,22);
            a.setPaint(new GradientPaint(0,0,new Color(123,226,144),0,h,new Color(5,101,51)));
            a.fillRoundRect(1,1,w-3,h-6,22,22);
            a.setPaint(new GradientPaint(0,1,new Color(255,255,255,150),0,h*.5f,new Color(255,255,255,0)));
            a.fillRoundRect(3,3,w-7,h/2,19,19);
            a.setColor(new Color(18,114,60));a.drawRoundRect(1,1,w-4,h-7,22,22);
            a.dispose();super.paintComponent(g);
        }
    }
    private static final class GreenFormCanvas extends JPanel{
        GreenFormCanvas(LayoutManager layout){super(layout);setOpaque(false);}
        @Override protected void paintComponent(Graphics g){
            Graphics2D a=(Graphics2D)g.create();a.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();a.setPaint(new GradientPaint(0,0,new Color(248,255,245),0,Math.max(1,h),new Color(223,249,215)));
            a.fillRoundRect(1,1,w-2,h-2,25,25);a.setColor(new Color(143,216,150));a.drawRoundRect(1,1,w-3,h-3,25,25);a.dispose();super.paintComponent(g);
        }
    }
    private static final class FarmFooter extends JPanel{
        FarmFooter(LayoutManager l){super(l);setOpaque(false);}
        @Override protected void paintComponent(Graphics g){
            Graphics2D a=(Graphics2D)g.create();a.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();a.setPaint(new GradientPaint(0,0,new Color(221,252,211),0,h,new Color(163,220,164)));
            a.fillRoundRect(1,1,w-2,h-2,21,21);
            a.setColor(new Color(103,186,108));a.drawRoundRect(1,1,w-3,h-3,21,21);a.dispose();super.paintComponent(g);
        }
    }
    private static final class FarmBanner extends JPanel{
        private final String text;
        FarmBanner(boolean edit){super(null);setOpaque(false);text=edit?"Edit Calendar Task":"Add Calendar Task";}
        @Override protected void paintComponent(Graphics g){
            Graphics2D a=(Graphics2D)g.create();a.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            a.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();
            a.setPaint(new GradientPaint(0,0,new Color(20,128,71),0,h,new Color(3,69,39)));
            a.fillRoundRect(1,1,w-2,h-3,28,28);
            a.setPaint(new GradientPaint(0,0,new Color(255,255,255,112),0,h*.65f,new Color(255,255,255,0)));
            a.fillRoundRect(3,3,w-6,(int)(h*.55),25,25);
            // Farmland scenery is vector-drawn and bundled with the source; no external assets.
            a.setClip(new RoundRectangle2D.Float(3,3,w-6,h-6,25,25));
            a.setColor(new Color(210,241,120,100));a.fillOval(w-103,16,52,52);
            a.setColor(new Color(79,158,76,130));a.fillOval(w-250,h-35,440,110);
            a.setColor(new Color(38,124,65,180));a.fillOval(w-205,h-17,440,92);
            a.setColor(new Color(151,210,85,170));
            for(int i=0;i<12;i++)a.drawLine(w-6-i*31,h-3,w-150+(i-6)*8,h-47);
            a.setClip(null);
            a.setColor(new Color(189,245,203));a.setFont(new Font("Segoe UI",Font.BOLD,12));
            a.drawString("SAKA-SMART  /  FARM TASK MANAGEMENT",24,28);
            a.setColor(Color.WHITE);a.setFont(new Font("Segoe UI",Font.BOLD,27));
            a.drawString(text,25,78);
            a.setFont(new Font("Segoe UI",Font.PLAIN,13));a.setColor(new Color(222,254,226));
            a.drawString("Plan crop and livestock activities for your farm",26,105);
            a.setColor(new Color(166,246,182));a.setStroke(new BasicStroke(1.7f));a.drawRoundRect(1,1,w-3,h-4,27,27);
            a.dispose();super.paintComponent(g);
        }
    }
    private static JTextField input(String initial){
        JTextField tf=new JTextField(initial);tf.setFont(new Font("Segoe UI",Font.PLAIN,14));
        tf.setBackground(new Color(254,255,251));tf.setForeground(INK);
        tf.setCaretColor(GREEN2);tf.setBorder(new CompoundBorder(BorderFactory.createLineBorder(new Color(142,194,149),1,true),new EmptyBorder(9,12,9,12)));
        tf.setPreferredSize(new Dimension(200,42));return tf;
    }
    private static JComboBox<String> choice(String[] options,String selected){
        JComboBox<String> box=new JComboBox<>(options);box.setSelectedItem(selected);
        box.setFont(new Font("Segoe UI",Font.BOLD,13));box.setBackground(new Color(251,255,247));box.setForeground(INK);
        box.setPreferredSize(new Dimension(200,42));return box;
    }
    private static JComponent sectionHeading(String heading,String help){
        JPanel p=pane(new GridLayout(2,1));p.add(label(heading,14,true,GREEN2));p.add(label(help,12,false,MUTED_TEXT));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE,49));p.setBorder(new EmptyBorder(0,0,7,0));return p;
    }
    private static JComponent field(String name,JComponent component){return field(label(name,13,true,INK),component);}
    private static JComponent field(JLabel name,JComponent component){
        JPanel p=pane(new BorderLayout(0,6));p.add(name,BorderLayout.NORTH);p.add(component,BorderLayout.CENTER);
        p.setBorder(new EmptyBorder(0,0,13,0));p.setMaximumSize(new Dimension(Integer.MAX_VALUE,94));
        return p;
    }
    private static final class GreenScrollBar extends JScrollBar{
        GreenScrollBar(){super(JScrollBar.VERTICAL);setPreferredSize(new Dimension(12,50));setUI(new javax.swing.plaf.basic.BasicScrollBarUI(){
            @Override protected void configureScrollBarColors(){thumbColor=new Color(55,150,89);trackColor=new Color(222,244,221);}
            @Override protected JButton createDecreaseButton(int orientation){return zero();}
            @Override protected JButton createIncreaseButton(int orientation){return zero();}
            private JButton zero(){JButton b=new JButton();b.setPreferredSize(new Dimension(0,0));b.setMinimumSize(new Dimension(0,0));b.setMaximumSize(new Dimension(0,0));return b;}
            @Override protected void paintThumb(Graphics g,JComponent c,Rectangle bounds){
                if(bounds.isEmpty())return;Graphics2D x=(Graphics2D)g.create();x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                x.setPaint(new GradientPaint(bounds.x,0,new Color(118,205,138),bounds.x+bounds.width,0,new Color(21,116,68)));
                x.fillRoundRect(bounds.x+2,bounds.y+2,Math.max(3,bounds.width-4),Math.max(3,bounds.height-4),10,10);x.dispose();
            }
        });}
    }
    /** Three aligned regions: centered date, vertically centered title/details, centered pill. */
    private static final class TaskCard extends JPanel {
        final Map<String,String> data;Runnable onChoose;boolean chosen;
        TaskCard(Map<String,String> data){
            super(new BorderLayout(10,0));this.data=data;setOpaque(false);
            setBorder(new EmptyBorder(9,10,9,10));
            setPreferredSize(new Dimension(200,82));
            LocalDate date=parseDate(v(data,"date"));
            JPanel stamp=new JPanel(new GridLayout(2,1,0,0)){
                @Override protected void paintComponent(Graphics g){
                    Graphics2D a=(Graphics2D)g.create();
                    a.setColor(new Color(217,239,214));a.fillRoundRect(0,0,getWidth()-1,getHeight()-1,10,10);
                    a.setColor(new Color(141,190,150));a.drawRoundRect(0,0,getWidth()-1,getHeight()-1,10,10);
                    a.dispose();super.paintComponent(g);
                }
            };
            stamp.setOpaque(false);stamp.setBorder(new EmptyBorder(5,0,4,0));
            stamp.setPreferredSize(new Dimension(52,54));
            JLabel month=label(date==null?"DATE":date.getMonth().getDisplayName(java.time.format.TextStyle.SHORT,Locale.ENGLISH).toUpperCase(Locale.ROOT),10,true,INK);
            JLabel number=label(date==null?"?":String.valueOf(date.getDayOfMonth()),21,true,GREEN2);
            month.setHorizontalAlignment(SwingConstants.CENTER);number.setHorizontalAlignment(SwingConstants.CENTER);
            stamp.add(month);stamp.add(number);
            JPanel stampWrap=pane(new GridBagLayout());stampWrap.add(stamp);
            add(stampWrap,BorderLayout.WEST);

            // Both one-line and two-line titles are vertically centered with the subtitle.
            JComponent details=new JComponent(){
                @Override protected void paintComponent(Graphics g){
                    Graphics2D a=(Graphics2D)g.create();
                    a.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                    a.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    int w=getWidth(),h=getHeight();
                    if(w<25||h<20){a.dispose();return;}
                    int left=23;
                    a.setFont(new Font("Segoe UI",Font.BOLD,13));
                    FontMetrics headingMetrics=a.getFontMetrics();
                    List<String> lines=wrapAtMostTwo(v(data,"title"),headingMetrics,Math.max(10,w-left-2));
                    int lineHeight=17, subHeight=14, gap=4;
                    int blockHeight=lines.size()*lineHeight+gap+subHeight;
                    int top=Math.max(1,(h-blockHeight)/2);
                    new TaskGlyph(data,17).paintIcon(this,a,1,top+1);
                    a.setColor(INK);
                    int baseline=top+headingMetrics.getAscent();
                    for(String line:lines){a.drawString(line,left,baseline);baseline+=lineHeight;}
                    a.setFont(new Font("Segoe UI",Font.PLAIN,11));
                    a.setColor(MUTED_TEXT);
                    FontMetrics subMetrics=a.getFontMetrics();
                    String subtitle=v(data,"target")+"  •  "+v(data,"time");
                    a.drawString(ellipsis(subtitle,subMetrics,Math.max(1,w-2)),1,top+lines.size()*lineHeight+gap+subMetrics.getAscent());
                    a.dispose();
                }
            };
            details.setToolTipText(v(data,"title")+" | "+v(data,"target")+" | "+v(data,"time"));
            add(details,BorderLayout.CENTER);

            String status=DashboardPriorityLists.displayEventStatus(data,LocalDate.now());
            JLabel pill=new StatusPill(status);
            JPanel pillWrap=pane(new GridBagLayout());pillWrap.setPreferredSize(new Dimension(91,58));pillWrap.add(pill);
            add(pillWrap,BorderLayout.EAST);
            MouseAdapter mouse=new MouseAdapter(){
                @Override public void mouseClicked(MouseEvent e){if(onChoose!=null)onChoose.run();}
                @Override public void mouseEntered(MouseEvent e){setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));}
            };
            for(Component clickable:new Component[]{this,stamp,stampWrap,month,number,details,pill,pillWrap})clickable.addMouseListener(mouse);
        }
        void setChosen(boolean selected){chosen=selected;repaint();}
        @Override protected void paintComponent(Graphics g){
            Graphics2D x=(Graphics2D)g.create();x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();
            String status=DashboardPriorityLists.displayEventStatus(data,LocalDate.now());
            boolean overdue="OVERDUE".equals(status);
            boolean warning="DUE TODAY".equals(status)||"DUE SOON".equals(status);
            boolean finished="COMPLETED".equals(status);
            Color top=chosen?new Color(221,255,217):overdue?new Color(255,242,243):warning?new Color(255,251,232):finished?new Color(233,247,237):new Color(251,255,249);
            Color bottom=chosen?new Color(192,239,190):overdue?new Color(255,222,226):warning?new Color(255,235,184):finished?new Color(202,231,209):new Color(222,246,219);
            x.setColor(new Color(36,83,49,30));x.fillRoundRect(2,5,w-4,h-6,15,15);
            x.setPaint(new GradientPaint(0,0,top,0,h,bottom));x.fillRoundRect(1,1,w-3,h-5,15,15);
            x.setColor(chosen?new Color(18,150,80):overdue?new Color(226,112,125):warning?new Color(205,153,64):new Color(159,205,161));
            x.setStroke(new BasicStroke(chosen?2f:1f));x.drawRoundRect(1,1,w-4,h-6,15,15);
            x.dispose();super.paintComponent(g);
        }
    }
    /** Status colors follow the derived display status; stored DB status stays unchanged. */
    private static final class StatusPill extends JLabel{
        private final Color top,bottom,line;
        StatusPill(String status){
            super(status,SwingConstants.CENTER);
            boolean overdue="OVERDUE".equals(status);
            boolean warning="DUE TODAY".equals(status)||"DUE SOON".equals(status);
            boolean cancelled="CANCELLED".equals(status);
            top=overdue?new Color(255,227,230):warning?new Color(255,248,210):cancelled?new Color(242,244,241):new Color(231,250,230);
            bottom=overdue?new Color(249,181,195):warning?new Color(249,218,147):cancelled?new Color(217,225,218):new Color(174,225,184);
            line=overdue?new Color(217,83,105):warning?new Color(184,135,44):cancelled?new Color(145,161,150):new Color(79,157,98);
            setOpaque(false);setFont(new Font("Segoe UI",Font.BOLD,10));
            setForeground(overdue?new Color(170,28,48):warning?new Color(118,82,18):cancelled?new Color(82,103,89):GREEN2);
            setBorder(new EmptyBorder(7,8,7,8));setPreferredSize(new Dimension(86,35));
        }
        @Override protected void paintComponent(Graphics g){
            Graphics2D x=(Graphics2D)g.create();x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();
            x.setPaint(new GradientPaint(0,0,top,0,h,bottom));x.fillRoundRect(1,1,w-3,h-3,25,25);
            x.setColor(line);x.setStroke(new BasicStroke(1.1f));x.drawRoundRect(1,1,w-3,h-3,25,25);
            x.dispose();super.paintComponent(g);
        }
    }
    /** Vector icon avoids unsupported Unicode boxes on some Windows fonts. */
    private static final class TaskGlyph implements Icon{
        final Map<String,String> row;final int size;
        TaskGlyph(Map<String,String> row,int size){this.row=row;this.size=size;}
        public int getIconWidth(){return size;}public int getIconHeight(){return size;}
        public void paintIcon(Component c,Graphics g,int x,int y){
            Graphics2D a=(Graphics2D)g.create();a.translate(x,y);a.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            a.scale(size/20.0,size/20.0);
            String s=(v(row,"category")+" "+v(row,"title")).toLowerCase(Locale.ROOT);
            Color tint=taskTint(row);a.setColor(tint);a.fillRoundRect(0,0,20,20,7,7);
            a.setColor(new Color(19,105,58));a.setStroke(new BasicStroke(2.1f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            if(s.matches(".*(vaccin|deworm|health|veterinar).*")){a.drawLine(10,4,10,16);a.drawLine(4,10,16,10);}
            else if(s.matches(".*(water|irrigat).*")){a.fillOval(6,8,8,8);a.drawLine(10,3,7,9);a.drawLine(10,3,13,9);}
            else if(s.matches(".*(feed|breed|calv|animal).*")){a.drawOval(4,5,12,10);a.fillOval(6,7,3,3);a.fillOval(12,7,3,3);}
            else{a.drawLine(10,16,10,7);a.drawArc(3,5,8,8,20,150);a.drawArc(9,4,8,8,0,150);}
            a.dispose();
        }
    }

}
