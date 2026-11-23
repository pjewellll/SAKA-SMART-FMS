package com.sakasmart;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Locale;

/** Presentation-only table styling for Production and Accounting; preserves models and buttons. */
final class RecordsTablePolish {
    private static final Color DARK = new Color(4,76,43);
    private static final Color ROW_A = new Color(253,255,252);
    private static final Color ROW_B = new Color(233,248,235);
    private static final Color GRID = new Color(207,234,210);
    private RecordsTablePolish() {}

    static void apply(Main.TableParts parts, String kind) {
        JTable table = parts.table();
        String[] cols = kind.equals("production")
                ? new String[]{"ID","Item","Kind","Target","Actual","Unit","Date","Notes"}
                : new String[]{"ID","Date","Type","Category","Amount","Notes","Sale ID"};
        int[] weights = kind.equals("production")
                ? new int[]{42,124,91,91,91,66,118,158}
                : new int[]{38,113,94,149,103,155,95};
        // Auto-resize is essential: all columns fit their viewport; never force a horizontal bar.
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setFillsViewportHeight(true);
        table.setRowHeight(40);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setForeground(DARK);
        table.setSelectionForeground(DARK);
        table.setSelectionBackground(new Color(180,231,190));
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(GRID);
        table.setIntercellSpacing(new Dimension(0,1));
        table.setBackground(ROW_A);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setResizingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(0,44));
        table.getTableHeader().setDefaultRenderer(new HeaderRenderer());
        table.getTableHeader().setToolTipText("Production and accounting records — all columns fit the available width");

        for (int c=0;c<table.getColumnCount();c++) {
            TableColumn column=table.getColumnModel().getColumn(c);
            column.setMinWidth(18);
            column.setPreferredWidth(weights[Math.min(c, weights.length-1)]);
            column.setCellRenderer(new CellRenderer(c,cols[Math.min(c,cols.length-1)]));
            // Preserve readable full text on hover when the window is narrow.
        }
        JScrollPane inner = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, table);
        if(inner!=null) {
            inner.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            inner.getViewport().setBackground(ROW_A);
            inner.setBorder(BorderFactory.createLineBorder(new Color(169,210,177),1,true));
            inner.getVerticalScrollBar().setUnitIncrement(20);
        }
        // Main.tableView wraps the searchable table in another scroll pane.
        parts.scroll().setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        parts.scroll().getViewport().setBackground(ROW_A);
        // Allow using the full viewport width at narrow or wide window sizes.
        table.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                table.getTableHeader().repaint();
            }
        });
    }

    private static final class HeaderRenderer extends JPanel implements TableCellRenderer {
        private final JLabel text = new JLabel("",SwingConstants.CENTER);
        HeaderRenderer(){
            super(new BorderLayout());
            setOpaque(false);
            text.setFont(new Font("Segoe UI",Font.BOLD,12));
            text.setForeground(Color.WHITE);
            text.setBorder(new EmptyBorder(2,4,2,4));
            add(text,BorderLayout.CENTER);
        }
        @Override public Component getTableCellRendererComponent(JTable t,Object value,boolean selected,boolean focus,int row,int col){
            text.setText(String.valueOf(value));
            text.setToolTipText(String.valueOf(value));
            return this;
        }
        @Override protected void paintComponent(Graphics g){
            Graphics2D x=(Graphics2D)g.create();
            try {
                x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                x.setPaint(new GradientPaint(0,0,new Color(95,205,126),0,getHeight(),new Color(0,99,52)));
                x.fillRoundRect(0,0,getWidth(),getHeight(),13,13);
                x.setColor(new Color(232,255,237,180));x.drawLine(5,2,Math.max(5,getWidth()-7),2);
                x.setColor(new Color(1,75,41,110));x.drawLine(getWidth()-1,7,getWidth()-1,getHeight()-8);
            } finally {x.dispose();}
            super.paintComponent(g);
        }
    }

    private static final class CellRenderer extends DefaultTableCellRenderer {
        private final int index;
        private final String name;
        CellRenderer(int index,String name){this.index=index;this.name=name;setOpaque(true);}
        @Override public Component getTableCellRendererComponent(JTable table,Object value,boolean selected,boolean focus,int row,int column){
            super.getTableCellRendererComponent(table,value,selected,focus,row,column);
            setFont(new Font("Segoe UI",Font.PLAIN,13));
            setBorder(new EmptyBorder(0,8,0,8));
            String raw=value==null?"":value.toString();
            setToolTipText(raw);
            if(name.equals("Target")||name.equals("Actual")||name.equals("Amount")) {
                setHorizontalAlignment(SwingConstants.RIGHT);
                try {
                    double n=Double.parseDouble(raw.replace(",",""));
                    setText(String.format(Locale.US,"%,.2f",n));
                }catch(NumberFormatException ex){setText(raw);}
            } else if (name.equals("ID")||name.equals("Kind")||name.equals("Type")||name.equals("Unit")||name.equals("Date")||name.equals("Sale ID")){
                setHorizontalAlignment(SwingConstants.CENTER);
            } else setHorizontalAlignment(SwingConstants.LEFT);
            if (selected) {setBackground(new Color(182,233,190));setForeground(DARK);}
            else {setBackground(table.convertRowIndexToModel(row)%2==0?ROW_A:ROW_B);setForeground(DARK);}
            return this;
        }
    }
}
