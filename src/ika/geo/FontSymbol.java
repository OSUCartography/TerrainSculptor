/*
 * FontSymbol.java
 *
 * Created on August 10, 2005, 11:48 AM
 *
 */

package ika.geo;

import java.awt.*;
import java.awt.font.*;
import java.awt.geom.*;

/**
 * Font symbol. Default is sans serif with 12 points, scale invariant, 
 * horizontally and vertically centered, black;
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich
 */
public class FontSymbol extends Symbol implements Cloneable{
    
    private enum HAlign { CENTER, LEFT, RIGHT };
    
    private HAlign hAlign = HAlign.CENTER;
    
    private Font font = new Font("SansSerif", Font.PLAIN, 12);
    
    private boolean scaleInvariant = true;
    
    private double fontScale = 1;
    
    private boolean centerVer = true;
    
    private Color color = Color.BLACK;
    
    /** Creates a new instance of FontSymbol */
    public FontSymbol() {
    }
    
    @Override
    public Object clone() {
        try {
            FontSymbol fontSymbol = (FontSymbol)super.clone();
            
            // make deep copy of FontSymbol
            fontSymbol.font = this.font.deriveFont(this.font.getSize());
            
            return fontSymbol;
        } catch (CloneNotSupportedException exc) {
            return null;
        }
    }
    
    // FIXME
    public void drawFontSymbol(Graphics2D g2d,
            double scale,
            boolean drawSelected,
            double x, double y,
            double dx, double dy,
            String text,
            double rotation) {
        
        if (text == null) {
            return;
        }
        
        g2d = (Graphics2D)g2d.create(); //copy g2d
        
        if (rotation != 0) {
            g2d.translate(x, y);
            g2d.rotate(Math.toRadians(rotation));
            g2d.translate(-x, -y);
        }

        g2d.setColor(this.color);
        /*g2d.setColor(drawSelected ?
            ika.utils.ColorUtils.getSelectionColor() :
            java.awt.Color.black);
        */
        
        // tx / ty: the position of the first character
        double tx = x;
        double ty = y;
        
        // horizontal alignment
        Rectangle2D bounds = null;
        switch (this.hAlign) {
            case LEFT:
                break;
            case CENTER:
                bounds = this.getBounds2D(text, x, y, dx, dy, scale);
                tx -= bounds.getWidth() / 2;
                break;
            case RIGHT:
                bounds = this.getBounds2D(text, x, y, dx, dy, scale);
                tx -= bounds.getWidth();
                break;
        }
        
        // vertical alignment
        if (this.centerVer) {
            if (bounds == null) {
                bounds = this.getBounds2D(text, x, y, dx, dy, scale);
            }
            ty -= bounds.getHeight() / 2;
        }
        
        // scale-independent offset
        tx += dx / scale;
        ty += dy / scale;
        
        g2d.translate(tx, ty);
        
        final double s =  this.scaleInvariant ? this.fontScale/scale : this.fontScale;
        g2d.scale(s, -s);
        g2d.setFont(this.font);
        g2d.drawString(text, 0, 0);
        
        g2d.dispose(); //release the copy's resources. Recomended by Sun tutorial.
    }
    
    public Rectangle2D getBounds2D(String str, double x, double y, 
            double dx, double dy, double scale) {
        
        if (str == null) {
            return null;
        }
        
        if (isScaleInvariant()) {
            if (scale <= 0.d) {
                return new Rectangle2D.Double(x, y, 0, 0);
            }
            else {
                dx /= scale;
                dy /= scale;
            }
        } else {
            scale = 1;
        }
        
        // see http://forum.java.sun.com/thread.jspa?forumID=5&threadID=619854
        // for an example of how to measure text size
        final FontRenderContext frc = new FontRenderContext(null, true, false);
        final LineMetrics lineMetrics = this.font.getLineMetrics(str, frc);
        final GlyphVector gv = font.createGlyphVector(frc, str);
        final Rectangle2D visualBounds  = gv.getVisualBounds();
        
        final double voffset = visualBounds.getHeight() + visualBounds.getMinY();
        final Rectangle2D bounds = new Rectangle2D.Double(x + dx,
                y - voffset / scale + dy,
                visualBounds.getWidth() * this.fontScale / scale,
                visualBounds.getHeight() * this.fontScale / scale);
        return bounds;
    }
    
    public Font getFont() {
        return font;
    }
    
    public void setFont(Font font) {
        this.font = font;
    }
    
    public boolean isScaleInvariant() {
        return scaleInvariant;
    }
    
    public void setScaleInvariant(boolean scaleInvariant) {
        this.scaleInvariant = scaleInvariant;
    }
    
    public double getFontScale() {
        return fontScale;
    }
    
    public void setFontScale(double fontScale) {
        this.fontScale = fontScale;
    }
    
    public boolean isCenterHor() {
        return this.hAlign == HAlign.CENTER;
    }
    
    public void setCenterHor(boolean centerHor) {
        this.hAlign = centerHor ? HAlign.CENTER : HAlign.LEFT;
    }
    
    public void setAlignLeft() {
        this.hAlign = HAlign.LEFT;
    }
    
    public boolean isAlignLeft() {
        return this.hAlign == HAlign.LEFT;
    }
    
    public void setAlignRight() {
        this.hAlign = HAlign.RIGHT;
    }
    
    public boolean isAlignRight() {
        return this.hAlign == HAlign.RIGHT;
    }
    
    public boolean isCenterVer() {
        return centerVer;
    }
    
    public void setCenterVer(boolean centerVer) {
        this.centerVer = centerVer;
    }
    
    public int getSize() {
        return this.font.getSize();
    }

    public void setSize (int size) {
       this.font = this.font.deriveFont((float)size);
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }
}
