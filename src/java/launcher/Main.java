package launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    public static final String MINECRAFT_VERSION = "1.21.11";
    public static final String FABRIC_LOADER = "0.19.3";
    public static final String MOD_VERSION = "1.6.1";

    public static void main(String[] args) throws Exception {
        System.setProperty("org.lwjgl.system.allocator", "system");
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }
}