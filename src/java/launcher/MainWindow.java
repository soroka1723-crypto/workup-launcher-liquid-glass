package launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * Compact Liquid Glass launcher menu (iOS 26 style).
 * Launch/install logic is unchanged; UI rewritten from scratch with extra quality-of-life features.
 */
public class MainWindow extends JFrame implements Game.Progress {

    private static final int W = 448;
    private static final int H = 508;
    private static final int R = 20;
    private static final long LOOP = 1_000_000L;

    private static final Color ACCENT = new Color(0x78, 0xB4, 0xFF);
    private static final Color ACCENT2 = new Color(0xC8, 0x94, 0xFF);
    private static final Color TEXT = new Color(0xF7, 0xFA, 0xFF);
    private static final Color DIM = new Color(0xB4, 0xC0, 0xE6);
    private static final Color MUTED = new Color(0x7A, 0x86, 0xB0);
    private static final Color OK = new Color(0x63, 0xE6, 0xBE);
    private static final Color ERR = new Color(0xFF, 0x82, 0x96);

    private Font f, fTitle, fBold, fSmall, fMono;
    private Image wallpaper;

    private final JTextField nick = new JTextField();
    private final JButton play = new JButton("ЗАПУСК");
    private final JProgressBar bar = new JProgressBar(0, 100);
    private final JLabel status = new JLabel("ГОТОВ К ЗАПУСКУ");
    private final JTextArea log = new JTextArea();
    private final JScrollPane logScroll;
    private final JComboBox<String> ramBox = new JComboBox<>(new String[]{"2048 MB", "3072 MB", "4096 MB", "6144 MB", "8192 MB", "12288 MB"});
    private final JPanel logWrap;

    private boolean busy;
    private boolean logVisible;

    public MainWindow() {
        setTitle("Workup Launcher");
        setUndecorated(true);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(W, H);
        setLocationRelativeTo(null);
        setBackground(new Color(0, 0, 0, 0));

        loadFonts();
        loadImages();

        log.setEditable(false);
        log.setFont(fMono);
        log.setForeground(new Color(0xCC, 0xD8, 0xF7));
        log.setOpaque(false);
        log.setLineWrap(false);
        log.setBorder(new EmptyBorder(6, 8, 6, 8));
        DefaultCaret caret = new DefaultCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        log.setCaret(caret);
        logScroll = new JScrollPane(log);
        logScroll.setOpaque(false);
        logScroll.getViewport().setOpaque(false);
        logScroll.setBorder(BorderFactory.createEmptyBorder());
        logScroll.getVerticalScrollBar().setUI(new MiniBarUI());
        logScroll.getHorizontalScrollBar().setUI(new MiniBarUI());
        logScroll.setPreferredSize(new Dimension(0, 92));
        logWrap = new GlassInset();
        logWrap.setLayout(new BorderLayout());
        logWrap.add(logScroll, BorderLayout.CENTER);
        logWrap.setVisible(false);
        logVisible = false;

        buildUi();
        setupDrag();
        loadPrefs();

        new javax.swing.Timer(1000 / 45, e -> repaint()).start();
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                try { setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), R + 8, R + 8)); }
                catch (Throwable ignored) {}
            }
        });
    }

    private void loadPrefs() {
        String saved = UserPrefs.getNick();
        if (saved != null) nick.setText(saved);
        int ram = UserPrefs.getRamMb();
        String target = ram + " MB";
        for (int i = 0; i < ramBox.getItemCount(); i++) {
            if (ramBox.getItemAt(i).equals(target)) { ramBox.setSelectedIndex(i); break; }
        }
    }

    private void loadFonts() {
        try (InputStream is = getClass().getResourceAsStream("/launcher/montserrat.ttf")) {
            Font base = Font.createFont(Font.TRUETYPE_FONT, is);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(base);
            f = base.deriveFont(Font.PLAIN, 13f);
            fTitle = base.deriveFont(Font.BOLD, 15f);
            fBold = base.deriveFont(Font.BOLD, 24f);
            fSmall = base.deriveFont(Font.PLAIN, 10.5f);
            fMono = new Font("Consolas", Font.PLAIN, 10);
        } catch (Exception e) {
            f = new Font("Segoe UI", Font.PLAIN, 13);
            fTitle = new Font("Segoe UI", Font.BOLD, 15);
            fBold = new Font("Segoe UI", Font.BOLD, 24);
            fSmall = new Font("Segoe UI", Font.PLAIN, 10);
            fMono = new Font("Consolas", Font.PLAIN, 10);
        }
    }

    private void loadImages() {
        try (InputStream is = getClass().getResourceAsStream("/launcher/wallpaper.png")) {
            wallpaper = is == null ? null : javax.imageio.ImageIO.read(is);
        } catch (IOException e) { wallpaper = null; }
        if (wallpaper == null) {
            BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setPaint(new GradientPaint(0, 0, new Color(0x0A, 0x0E, 0x1C), W, H, new Color(0x16, 0x10, 0x2A)));
            g.fillRect(0, 0, W, H);
            g.dispose();
            wallpaper = img;
        }
    }

    private void buildUi() {
        Bg bg = new Bg();
        bg.setLayout(new GridBagLayout());
        setContentPane(bg);

        GlassCard card = new GlassCard();
        card.setPreferredSize(new Dimension(W - 24, H - 24));
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(10, 14, 12, 14));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel title = new JLabel("Workup Launcher");
        title.setFont(fTitle);
        title.setForeground(TEXT);
        top.add(title, BorderLayout.WEST);
        JPanel dots = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        dots.setOpaque(false);
        dots.add(dot(new Color(0xFF, 0x5F, 0x57), this::dispose));
        dots.add(dot(new Color(0xFE, 0xBC, 0x2E), () -> setState(Frame.ICONIFIED)));
        top.add(dots, BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(8, 4, 0, 4));

        JLabel brand = new JLabel("WORKUP");
        brand.setFont(fBold);
        brand.setForeground(TEXT);
        brand.setAlignmentX(Component.CENTER_ALIGNMENT);
        body.add(brand);
        body.add(Box.createVerticalStrut(2));
        JLabel sub = new JLabel("1.21.11 • Fabric • Liquid Glass");
        sub.setFont(fSmall);
        sub.setForeground(MUTED);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        body.add(sub);
        body.add(Box.createVerticalStrut(14));

        body.add(label("НИКНЕЙМ"));
        styleField(nick);
        nick.setAlignmentX(Component.CENTER_ALIGNMENT);
        nick.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        body.add(nick);
        body.add(Box.createVerticalStrut(10));

        body.add(label("ПАМЯТЬ (RAM)"));
        styleCombo(ramBox);
        ramBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        ramBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        body.add(ramBox);
        body.add(Box.createVerticalStrut(12));

        styleBar(bar);
        bar.setAlignmentX(Component.CENTER_ALIGNMENT);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 7));
        body.add(bar);
        body.add(Box.createVerticalStrut(6));

        status.setFont(fSmall.deriveFont(Font.BOLD));
        status.setForeground(DIM);
        status.setAlignmentX(Component.CENTER_ALIGNMENT);
        body.add(status);
        body.add(Box.createVerticalStrut(12));

        stylePlay(play);
        play.setAlignmentX(Component.CENTER_ALIGNMENT);
        play.setMaximumSize(new Dimension(220, 46));
        play.setPreferredSize(new Dimension(220, 46));
        body.add(play);
        body.add(Box.createVerticalStrut(10));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.CENTER_ALIGNMENT);
        actions.add(miniBtn("Папка", this::openFolder));
        actions.add(miniBtn("Лог", this::toggleLog));
        actions.add(miniBtn("Очистить", () -> log.setText("")));
        actions.add(miniBtn("Копия лога", this::copyLog));
        body.add(actions);
        body.add(Box.createVerticalStrut(8));

        logWrap.setAlignmentX(Component.CENTER_ALIGNMENT);
        logWrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        body.add(logWrap);

        card.add(body, BorderLayout.CENTER);
        bg.add(card);

        ActionListener go = e -> startGame();
        play.addActionListener(go);
        nick.addActionListener(go);
    }

    private JLabel label(String t) {
        JLabel l = new JLabel(t);
        l.setFont(fSmall);
        l.setForeground(MUTED);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 3, 0));
        return l;
    }

    private JComponent dot(Color c, Runnable r) {
        JButton b = new JButton();
        b.setPreferredSize(new Dimension(13, 13));
        b.setFocusPainted(false); b.setBorderPainted(false); b.setContentAreaFilled(false); b.setOpaque(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
            @Override public void paint(Graphics g, JComponent cpt) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                ButtonModel m = ((AbstractButton) cpt).getModel();
                g2.setColor(m.isPressed() ? c.darker() : m.isRollover() ? c.brighter() : c);
                g2.fillOval(1, 1, 11, 11);
                g2.setColor(new Color(255, 255, 255, m.isRollover() ? 85 : 45));
                g2.fillOval(3, 3, 4, 3);
                g2.dispose();
            }
        });
        b.addActionListener(e -> r.run());
        return b;
    }

    private JButton miniBtn(String text, Runnable r) {
        JButton b = new JButton(text);
        b.setFont(fSmall);
        b.setForeground(DIM);
        b.setFocusPainted(false); b.setBorderPainted(false); b.setContentAreaFilled(false); b.setOpaque(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(new EmptyBorder(5, 8, 5, 8));
        b.addActionListener(e -> r.run());
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { b.setForeground(TEXT); }
            @Override public void mouseExited(MouseEvent e) { b.setForeground(DIM); }
        });
        return b;
    }

    private void styleField(JTextField tf) {
        tf.setFont(f.deriveFont(14.5f));
        tf.setForeground(TEXT);
        tf.setCaretColor(ACCENT);
        tf.setHorizontalAlignment(SwingConstants.CENTER);
        tf.setOpaque(false);
        tf.setBorder(BorderFactory.createCompoundBorder(new FieldBorder(), new EmptyBorder(10, 12, 10, 12)));
    }

    private void styleCombo(JComboBox<String> cb) {
        cb.setFont(fSmall.deriveFont(12f));
        cb.setForeground(TEXT);
        cb.setOpaque(false);
        cb.setFocusable(false);
        cb.setBorder(new EmptyBorder(2, 6, 2, 6));
        cb.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                l.setOpaque(true);
                l.setBackground(isSelected ? new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 70) : new Color(16, 22, 38, 230));
                l.setForeground(TEXT);
                l.setBorder(new EmptyBorder(6, 10, 6, 10));
                return l;
            }
        });
    }

    private void styleBar(JProgressBar p) {
        p.setOpaque(false);
        p.setBorderPainted(false);
        p.setStringPainted(false);
        p.setUI(new javax.swing.plaf.basic.BasicProgressBarUI() {
            @Override protected void paintDeterminate(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = c.getWidth(), h = c.getHeight();
                g2.setColor(new Color(255, 255, 255, 16));
                g2.fillRoundRect(0, 0, w, h, h, h);
                int v = p.getValue();
                if (v > 0) {
                    int fw = Math.max(h, w * v / 100);
                    g2.setPaint(new GradientPaint(0, 0, ACCENT, fw, 0, ACCENT2));
                    g2.fillRoundRect(0, 0, fw, h, h, h);
                    g2.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 60), 0, h, new Color(255, 255, 255, 0)));
                    g2.fillRoundRect(0, 0, fw, h, h, h);
                }
                g2.dispose();
            }
            @Override protected void paintIndeterminate(Graphics g, JComponent c) { paintDeterminate(g, c); }
        });
    }

    private void stylePlay(JButton b) {
        b.setFont(f.deriveFont(Font.BOLD, 13.5f));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false); b.setBorderPainted(false); b.setContentAreaFilled(false); b.setOpaque(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
            @Override public void paint(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = c.getWidth(), h = c.getHeight();
                ButtonModel m = b.getModel();
                Color c1 = !b.isEnabled() ? new Color(90, 100, 130, 170) : m.isPressed() ? ACCENT.darker() : m.isRollover() ? blend(ACCENT, Color.WHITE, 0.1f) : ACCENT;
                Color c2 = !b.isEnabled() ? new Color(80, 90, 120, 170) : m.isPressed() ? ACCENT2.darker() : m.isRollover() ? blend(ACCENT2, Color.WHITE, 0.1f) : ACCENT2;
                RoundRectangle2D rr = new RoundRectangle2D.Float(0, 0, w, h, 14, 14);
                g2.setPaint(new GradientPaint(0, 0, c1, w, h, c2));
                g2.fill(rr);
                g2.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, b.isEnabled() ? 65 : 18), 0, h * 0.5f, new Color(255, 255, 255, 0)));
                g2.fill(rr);
                g2.setColor(new Color(255, 255, 255, m.isRollover() ? 70 : 38));
                g2.draw(rr);
                g2.dispose();
                super.paint(g, c);
            }
        });
    }

    private void setupDrag() {
        MouseAdapter ma = new MouseAdapter() {
            Point s;
            @Override public void mousePressed(MouseEvent e) { if (e.getY() < 46) s = e.getPoint(); }
            @Override public void mouseDragged(MouseEvent e) { if (s != null) { Point p = e.getLocationOnScreen(); setLocation(p.x - s.x, p.y - s.y); } }
            @Override public void mouseReleased(MouseEvent e) { s = null; }
        };
        addMouseListener(ma); addMouseMotionListener(ma);
    }

    private void toggleLog() {
        logVisible = !logVisible;
        logWrap.setVisible(logVisible);
        setSize(W, logVisible ? H + 108 : H);
        revalidate(); repaint();
    }

    private void openFolder() {
        try {
            Files.createDirectories(UserPrefs.gameDir());
            Desktop.getDesktop().open(UserPrefs.gameDir().toFile());
            log("Открыта папка: " + UserPrefs.gameDir());
        } catch (Exception ex) {
            log("Не удалось открыть папку: " + ex.getMessage());
        }
    }

    private void copyLog() {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(log.getText()), null);
            setStatus("Лог скопирован", OK);
        } catch (Exception ex) { setStatus("Не удалось скопировать", ERR); }
    }

    // ---- Core launch logic (unchanged behavior) ----
    private void startGame() {
        String name = nick.getText().trim();
        if (name.isEmpty()) {
            setStatus("Введите никнейм", ERR);
            nick.requestFocus();
            shake();
            return;
        }
        if (!name.matches("[A-Za-z0-9_]{3,16}")) {
            setStatus("Ник: 3-16 символов (A-Z, 0-9, _)", ERR);
            nick.requestFocus();
            shake();
            return;
        }
        int ram = parseRam();
        UserPrefs.saveNick(name);
        UserPrefs.saveRamMb(ram);
        setBusy(true);
        setStatus("Подготовка...", DIM);
        bar.setValue(0);
        log.setText("");

        new Thread(() -> {
            try {
                Game game = new Game(UserPrefs.gameDir(), name, this);
                game.setRamMb(ram);
                game.installAndLaunch();
            } catch (Exception ex) {
                log("ERROR: " + ex);
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> { setBusy(false); setStatus("Ошибка — откройте лог", ERR); if (!logVisible) toggleLog(); });
                return;
            }
            SwingUtilities.invokeLater(() -> { setBusy(false); setStatus("Игра закрыта", DIM); });
        }, "launcher-game").start();
    }

    private int parseRam() {
        try {
            String s = String.valueOf(ramBox.getSelectedItem());
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) { return 4096; }
    }

    private void setBusy(boolean b) {
        busy = b;
        play.setEnabled(!b);
        play.setText(b ? "ЗАПУСК..." : "ЗАПУСК");
        nick.setEnabled(!b);
        ramBox.setEnabled(!b);
    }

    private void setStatus(String t, Color c) { status.setText(t.toUpperCase()); status.setForeground(c); }

    private void shake() {
        Point p = getLocation();
        new Thread(() -> {
            try { for (int i = 0; i < 6; i++) { setLocation(p.x + (i % 2 == 0 ? 7 : -7), p.y); Thread.sleep(26); } }
            catch (InterruptedException ignored) {}
            setLocation(p);
        }).start();
    }

    // ---- Game.Progress ----
    @Override public void step(String message, int percent) {
        SwingUtilities.invokeLater(() -> { bar.setValue(Math.max(0, Math.min(100, percent))); setStatus(message, percent >= 100 ? OK : DIM); });
    }
    @Override public void log(String line) { SwingUtilities.invokeLater(() -> log.append(line + "\n")); }

    // ---- Visual helpers ----
    private static Color withA(int rgb, int a) { return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, clamp(a, 0, 255)); }
    private static Color withA(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), clamp(a, 0, 255)); }
    private static Color blend(Color a, Color b, float t) { t = Math.max(0, Math.min(1, t)); return new Color(
            Math.round(a.getRed() + (b.getRed() - a.getRed()) * t), Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t), Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t)); }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private void paintGlass(Graphics2D g2, int x, int y, int w, int h, int r, float op, boolean shadow) {
        g2 = (Graphics2D) g2.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (w <= 0 || h <= 0) { g2.dispose(); return; }
        r = Math.min(r, Math.min(w, h) / 2);
        if (shadow) {
            g2.setColor(withA(0x000000, (int) (0x22 * op))); g2.fillRoundRect(x + 1, y + 4, w, h, (r + 2) * 2, (r + 2) * 2);
            g2.setColor(withA(0x000000, (int) (0x12 * op))); g2.fillRoundRect(x, y + 2, w, h, (r + 1) * 2, (r + 1) * 2);
        }
        RoundRectangle2D shape = new RoundRectangle2D.Float(x, y, w, h, r * 2f, r * 2f);
        g2.setColor(withA(0x0E1A2C, (int) (0x68 * op))); // more transparent
        g2.fill(shape);
        Shape clip = g2.getClip(); g2.setClip(shape);
        g2.setPaint(new GradientPaint(x, y, new Color(255, 255, 255, (int) (40 * op)), x, y + h, new Color(255, 255, 255, 0)));
        g2.fill(shape);
        long now = Math.floorMod(System.currentTimeMillis(), LOOP);
        int band = Math.max(55, w / 2), span = w + band * 2, steps = 12;
        float sw = w / (float) steps;
        for (int b = 0; b < 2; b++) {
            float sp = b == 0 ? 28f : 18f, off = b == 0 ? span * 0.35f : span * 0.72f;
            float cx = x + w + band - ((now / sp + off) % span);
            int ba = b == 0 ? 0x24 : 0x14;
            for (int i = 0; i < steps; i++) {
                float px = x + (i + 0.5f) * sw, d = Math.abs(px - cx);
                if (d >= band) continue;
                float fall = 1f - d / band, bias = 0.35f + 0.65f * ((float) i / steps);
                int a = (int) (ba * fall * fall * op * bias);
                if (a > 2) { g2.setColor(new Color(255, 255, 255, clamp(a, 0, 255))); g2.fillRect(x + Math.round(i * sw), y, Math.max(1, Math.round(sw)), h); }
            }
        }
        g2.setClip(clip);
        g2.setColor(new Color(255, 255, 255, (int) (58 * op)));
        g2.draw(shape);
        g2.setPaint(new GradientPaint(x + r, y + 1, new Color(255, 255, 255, (int) (80 * op)), x + w - r, y + 1, new Color(255, 255, 255, (int) (18 * op))));
        g2.fillRoundRect(x + r, y + 1, Math.max(1, w - r * 2), 1, 1, 1);
        g2.setPaint(new GradientPaint(x + 3, y + r, withA(ACCENT, (int) (190 * op)), x + 3, y + h - r, withA(ACCENT2, (int) (190 * op))));
        g2.fillRoundRect(x + 3, y + r, 2, Math.max(2, h - r * 2), 2, 2);
        g2.dispose();
    }

    private class Bg extends JPanel {
        Bg() { setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(wallpaper, 0, 0, getWidth(), getHeight(), null);
            g2.setColor(new Color(5, 8, 16, 165));
            g2.fillRect(0, 0, getWidth(), getHeight());
            orb(g2, getWidth() * 0.2f, getHeight() * 0.25f, 170, withA(ACCENT, 22));
            orb(g2, getWidth() * 0.82f, getHeight() * 0.78f, 190, withA(ACCENT2, 18));
            g2.dispose();
        }
        private void orb(Graphics2D g2, float x, float y, int r, Color c) {
            g2.setPaint(new RadialGradientPaint(x, y, r, new float[]{0f, 1f}, new Color[]{c, new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
            g2.fillOval(Math.round(x - r), Math.round(y - r), r * 2, r * 2);
        }
    }

    private class GlassCard extends JPanel {
        GlassCard() { setOpaque(false); }
        @Override protected void paintComponent(Graphics g) { paintGlass((Graphics2D) g, 0, 0, getWidth(), getHeight(), R, 1f, true); super.paintComponent(g); }
    }

    private class GlassInset extends JPanel {
        GlassInset() { setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(withA(0x08101C, 130));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g2.setColor(new Color(255, 255, 255, 22));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private class FieldBorder extends javax.swing.border.AbstractBorder {
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean focus = c.isFocusOwner();
            g2.setColor(withA(0x0B1424, 135));
            g2.fillRoundRect(x, y, w, h, 12, 12);
            g2.setColor(focus ? withA(ACCENT, 150) : new Color(255, 255, 255, 24));
            g2.drawRoundRect(x, y, w - 1, h - 1, 12, 12);
            g2.dispose();
        }
        @Override public Insets getBorderInsets(Component c) { return new Insets(2, 2, 2, 2); }
        @Override public Insets getBorderInsets(Component c, Insets i) { i.left = i.right = i.top = i.bottom = 2; return i; }
    }

    private static class MiniBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        @Override protected void configureScrollBarColors() { thumbColor = new Color(255, 255, 255, 30); trackColor = new Color(0, 0, 0, 0); }
        @Override protected JButton createDecreaseButton(int o) { JButton b = new JButton(); b.setPreferredSize(new Dimension(0,0)); return b; }
        @Override protected JButton createIncreaseButton(int o) { JButton b = new JButton(); b.setPreferredSize(new Dimension(0,0)); return b; }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            Graphics2D g2 = (Graphics2D) g.create(); g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(255, 255, 255, isThumbRollover() ? 52 : 30));
            g2.fillRoundRect(r.x + 2, r.y + 2, Math.max(4, r.width - 4), Math.max(4, r.height - 4), 8, 8); g2.dispose();
        }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {}
    }
}
