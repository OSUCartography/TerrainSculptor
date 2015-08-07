package ch.ethz.karto.swa.atlas;

import ika.utils.PropertiesLoader;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.Properties;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;


public class SystemInfo {
    private static final String PROPERTIES_FILE = "ch.ethz.karto.swa.atlas.systeminfo";
    private static String newline = System.getProperty("line.separator");
    
    protected JTextPane systemInfo;
    protected StyledDocument doc;
    protected Style docStyle, titleStyle, title1Style, title2Style, textStyle;
    private Properties properties;
    protected final static int MB = 1024 * 1024;

    public static void main (String[] args) {
        new SystemInfo(null);
    }

    private class CopyAction extends AbstractAction {

        CopyAction(String name) {
            super(name);
        }

        public void actionPerformed(ActionEvent e) {
            systemInfo.selectAll();
            systemInfo.copy();
            systemInfo.setCaretPosition(0);
        }
    }

    public SystemInfo(Frame parent) {
        properties = PropertiesLoader.loadProperties(PROPERTIES_FILE, true);

        JDialog dialog = new JDialog(parent);
        dialog.setTitle(res("windowTitle"));
        dialog.setModal(true);
        dialog.setSize(600, 600);
        dialog.setLocationRelativeTo(null);

        Container container = new Container();
        container.setLayout(new BorderLayout());

        systemInfo = new JTextPane();
        systemInfo.setEditable(false);
        systemInfo.setDragEnabled(true);
        systemInfo.setMargin(new Insets(20, 20, 20, 20));
        JScrollPane scrollPane = new JScrollPane(systemInfo, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        container.add(scrollPane, BorderLayout.CENTER);

        Container panel = new Container();
        panel.setLayout(new FlowLayout(FlowLayout.RIGHT, 16, 5));
        JButton copyButton = new JButton(new CopyAction(res("copy")));
        panel.add(copyButton);
        container.add(panel, BorderLayout.SOUTH);

        dialog.getContentPane().add(container);

        createStyles();
        addInfo();

        dialog.setVisible(true);
    }

    private void createStyles() {
        doc = systemInfo.getStyledDocument();

        docStyle = doc.addStyle("docStyle", null);
        StyleConstants.setFontFamily(docStyle, "Dialog");
        StyleConstants.setLineSpacing(docStyle, 0.2f);

        titleStyle = doc.addStyle("titleStyle", docStyle);
        StyleConstants.setBold(titleStyle, true);

        title1Style = doc.addStyle("title1Style", titleStyle);
        StyleConstants.setFontSize(title1Style, 15);

        title2Style = doc.addStyle("title2Style", titleStyle);
        StyleConstants.setFontSize(title2Style, 13);

        textStyle = doc.addStyle("textStyle", docStyle);
        StyleConstants.setFontSize(textStyle, 12);
    }

    protected void addText(String text, Style style) {
        try {
            doc.insertString(doc.getLength(), text, style);
        } catch (Exception e) {
        }
    }

    protected void addTitle1(String title) {
        addText(title + newline, title1Style);
    }

    protected void addTitle2(String title) {
        addText(newline + title + newline, title2Style);
    }

    protected void addParagraph(String paragraph) {
        addText(paragraph + newline, textStyle);
    }

    protected void addInfo() {
        System.gc();

        addTitle1(res("systemTitle"));
        addTitle2(res("osTitle"));
        addParagraph(String.format(res("osInfo"), info("os.name"), info("os.version")));

        addTitle2(res("cpuTitle"));
        addParagraph(String.format(res("cpuInfo"), info("os.arch"), info("sun.arch.data.model")));

        addTitle2(res("javaTitle"));
        addParagraph(String.format(res("javaInfo"), info("java.version")));
        addParagraph(String.format(res("javaRT"), info("java.runtime.name"), info("java.runtime.version")));
        addParagraph(String.format(res("javaVM"), info("java.vm.name"), info("java.vm.version")));
        addParagraph(String.format(res("javaFreeMem"), Runtime.getRuntime().freeMemory() / MB));
        addParagraph(String.format(res("javaTotalMem"), Runtime.getRuntime().totalMemory() / MB));
        addParagraph(String.format(res("javaMaxMem"), Runtime.getRuntime().maxMemory() / MB));

        doc.setParagraphAttributes(0, doc.getLength(), docStyle, true);
        systemInfo.setCaretPosition(0);
    }

    private String res(String id) {
        return properties.getProperty(id);
    }

    private static String info(String propName) {
        return System.getProperty(propName);
    }
} 
