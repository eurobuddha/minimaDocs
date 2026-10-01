package org.mininotes.android;

import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;
import javax.swing.*;

/**
 * Right-click, the same way everywhere: whatever is under the pointer offers what can be done to it. A row with
 * buttons offers those buttons, so its menu can never say or do anything else.
 */
final class DesktopMenus {
    private DesktopMenus(){}

    /**
     * The menu comes up on a right-click on this or anything on it: Windows says so as the button comes up,
     * other systems as it goes down, so both are heard. Said once the row is built, since what is on it then
     * is what is listened to - a child that listens takes the clicks its parent would have had.
     */
    static void onRightClick(Component on,Consumer<MouseEvent> show) {
        MouseAdapter asked=new MouseAdapter(){
            @Override public void mousePressed(MouseEvent e){if(e.isPopupTrigger())show.accept(e);}
            @Override public void mouseReleased(MouseEvent e){if(e.isPopupTrigger())show.accept(e);}
        };
        listen(on,asked);
    }
    private static void listen(Component c,MouseListener l) {
        c.addMouseListener(l);
        if(c instanceof Container k)for(Component one:k.getComponents())listen(one,l);
    }

    /** A row's own buttons, as a menu: each line presses its button. A null is a divider. */
    static JPopupMenu echo(AbstractButton... buttons) {
        JPopupMenu menu=new JPopupMenu();boolean divide=false;
        for(AbstractButton b:buttons) {
            if(b==null){divide=menu.getComponentCount()>0;continue;}
            if(divide){menu.addSeparator();divide=false;}
            String said=b.getText()==null||b.getText().isBlank()?b.getAccessibleContext().getAccessibleName():b.getText();
            JMenuItem line=b instanceof JCheckBox box?new JCheckBoxMenuItem(said,box.isSelected()):new JMenuItem(said);
            line.setEnabled(b.isEnabled());line.addActionListener(e->b.doClick(0));menu.add(line);
        }
        return menu;
    }

    /** A right-click on this row offers its buttons. */
    static void echoOnRightClick(JComponent row,AbstractButton... buttons) {
        onRightClick(row,e->echo(buttons).show(e.getComponent(),e.getX(),e.getY()));
    }
}
