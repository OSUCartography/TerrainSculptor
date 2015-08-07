package ika.utils;

import java.awt.event.*;
import java.awt.*;
import javax.swing.*;
import javax.swing.event.*;

/**
 * Blocks a JFrame from receiving mouse events. It temporarily replaces the
 * frame's glass pane that catches all mouse events.
 */
public class WindowBlocker extends JComponent implements MouseInputListener {
    
    /**
     * a reference to the JFrame that is blocked.
     */
    final private JFrame frame;
    
    public WindowBlocker(JFrame frame) {
        this.frame = frame;
    }
    
    public void mouseMoved(MouseEvent e) {
    }
    public void mouseDragged(MouseEvent e) {
    }
    public void mouseClicked(MouseEvent e) {
        //System.out.println("WindowBlocker blocked event");
        
        // beep when the user clicks anywhere on the window.
        Toolkit.getDefaultToolkit().beep();
    }
    public void mouseEntered(MouseEvent e) {
    }
    public void mouseExited(MouseEvent e) {
    }
    public void mousePressed(MouseEvent e) {
    }
    public void mouseReleased(MouseEvent e) {
    }
    
    /**
     * Block a frame from receiving mouse events. This call must be balanced with
     * a call to unBlock(), otherwise the frame is blocked forever.
     */
    public void block() {
        //System.out.println ("WindowBlocker: block");
        
        frame.setGlassPane(this);
        
        // Windows OS somehow looses the event listeners when they are attached by
        // the constructor of this class. Attach the listeners here.
        addMouseListener(this);
        addMouseMotionListener(this);
        
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        this.setVisible(true);
    }
    
    /**
     * Enable the frame to receive mouse events again.
     */
    public void unBlock() {
        //System.out.println ("WindowBlocker: unblock");
        
        this.setVisible(false);
    }
}
