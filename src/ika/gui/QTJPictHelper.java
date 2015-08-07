package ika.gui;

import java.awt.*;
import java.io.*;

import quicktime.*;
import quicktime.qd.*;
import quicktime.std.*;
import quicktime.std.image.*;
import quicktime.std.movies.media.*;
import quicktime.app.view.*;

/**
 * This class is not needed on operating systems other than Macintosh, and it
 * will not compile on these operating systems. This file can be savely deleted
 * on these systems. 
 * QTJPictHelper is helper class that converts raster images in Macintosh PICT 
 * format to standard Java images. This is useful for drag-and-drop support on 
 * Macintosh. It uses QuickTime for the conversion.
 * On Mac, this class must be compiled with:
 * javac -classpath /System/Library/Java/Extensions/QTJava.zip QTJPictHelper.java
 */
public class QTJPictHelper extends Object {

    static Image pictStreamToJavaImage (InputStream in)
        throws IOException {
        Image image = null;
        // create a buffer for bytes read from stream
        byte[] buffy = new byte [2048];
        // must have empty 512-byte header so GraphicsImporter
        // will think it's a file
        int off = 512;
        int totalRead = 0;
        // loop, attempting to read as many bytes as will fit
        // in the array, growing array as necessary
        int bytesRead = 0;
        while ((bytesRead = in.read (buffy, off, buffy.length-off)) > -1) {
            totalRead += bytesRead;
            off += bytesRead;
            if (off == buffy.length) {
                // reallocate new array
                byte[] buffy2 = new byte [buffy.length * 2];
                System.arraycopy (buffy, 0, buffy2, 0, buffy.length);
                buffy = buffy2;
            }
        }
        try {
            // hand it to QTJ GraphicsImporter
            QTSession.open();
            Pict pict = new Pict (buffy);
            DataRef ref = new DataRef (pict,
                                       StdQTConstants.kDataRefQTFileTypeTag,
                                       "PICT");
            GraphicsImporter gi =
                new GraphicsImporter (StdQTConstants.kQTFileTypePicture);
            gi.setDataReference (ref);
            QDRect rect = gi.getSourceRect ();
            Dimension dim = new Dimension (rect.getWidth(),
                                           rect.getHeight());
            GraphicsImporterDrawer gid =
                new GraphicsImporterDrawer (gi);
            QTImageProducer ip = new QTImageProducer (gid, dim);

            // create AWT image
            image = Toolkit.getDefaultToolkit().createImage (ip);

        } catch (QTException qte) {
            qte.printStackTrace();
        } finally {
            QTSession.close();
        }
        return image;
    }
}
