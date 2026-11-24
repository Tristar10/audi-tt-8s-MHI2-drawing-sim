package local.tt;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Live PC simulator of the OpenHaldex autocross view. It runs the head unit's own layout code
 * (TeleLayout, TeleIcons, TeleStrip) and receiver (TeleLink) and draws the layout's slots with AWT,
 * at the head unit's tick rate, over a photo of the cockpit.
 *
 * What it shows like the car: layout, motion, colours, data rate and smoothing, the UDP link (ack,
 * port sweep, presets). What it does not: the head unit's font (about 1.45x at the same size, drawn
 * at 70 % there), EAL quirks (tilt sign, blending, clipping) and the head unit's speed.
 *
 * Keys: V view (Classic/Sport), P painted strip on/off, D demo/live UDP, G glide on/off,
 * R demo data rate (10/20/50 Hz), F tick rate (15/20/30/60 Hz), B background (the stock cockpit drawn
 * clean, the phone photos, black), H readout, S save PNG.
 */
public final class TeleSim extends JPanel {
    private static final int SW = TeleLayout.SCREEN_W;
    private static final int SH = TeleLayout.SCREEN_H;
    private static final String FONT = Chrome.FAMILY;
    private static final String[] BG_NAMES = {"skin", "drawn cockpit", "phone photo", "black"};
    private static final int[] GLIDE_CH = {TeleLayout.CH_RPM, TeleLayout.CH_SPEED, TeleLayout.CH_PEDAL,
        TeleLayout.CH_BOOST, TeleLayout.CH_BRAKE, TeleLayout.CH_HX_TARGET, TeleLayout.CH_HX_ACTUAL};
    private static final int[] DATA_HZ = {10, 20, 50};
    private static final int[] TICK_HZ = {15, 20, 30, 60};

    private final TeleLayout layout = new TeleLayout();
    private final TeleLayout sample = new TeleLayout();
    private final BufferedImage frame = new BufferedImage(SW, SH, BufferedImage.TYPE_INT_RGB);
    private final BufferedImage[] photo = new BufferedImage[2];
    private final Map<Long, BufferedImage> icons = new HashMap<Long, BufferedImage>();
    private final Map<Integer, Font> fonts = new HashMap<Integer, Font>();
    private BufferedImage stripTrack;
    private BufferedImage stripLit;
    private BufferedImage stripLitRed;

    private volatile int view = TeleLayout.VIEW_LARGE;
    private volatile boolean painted = true;
    private volatile boolean demo;
    private volatile boolean glide;
    /** 0 skin (pieces of a real cockpit picture, skins/), 1 drawn (Chrome), 2 phone photos, 3 black. */
    private volatile int bgMode = 0;
    private final Skin[] skin = new Skin[2];
    private volatile boolean hud = true;
    private volatile int dataHzI = 1;
    private volatile int tickHzI = 2;
    private volatile boolean savePng;
    /** Itasha theme: the livery pattern (Theme) instead of our layer's black background. */
    private volatile boolean theme;
    private final BufferedImage[] themeImg = new BufferedImage[2];
    private long shotAt = -1;
    /** --set overrides: channel -> value (x1000), applied after demo or live values. */
    private final Map<Integer, Integer> fixed = new HashMap<Integer, Integer>();
    private static final String[] SET_NAMES = {"rpm", "speed", "pedal", "coolant", "oil", "iat", "boost", "brake",
        "hxt", "hxa", "gear", "dsg", "clutch", "preset"};
    private static final int[] SET_CH = {TeleLayout.CH_RPM, TeleLayout.CH_SPEED, TeleLayout.CH_PEDAL,
        TeleLayout.CH_COOLANT, TeleLayout.CH_OIL, TeleLayout.CH_IAT, TeleLayout.CH_BOOST, TeleLayout.CH_BRAKE,
        TeleLayout.CH_HX_TARGET, TeleLayout.CH_HX_ACTUAL, TeleLayout.CH_GEAR, TeleLayout.CH_DSG,
        TeleLayout.CH_CLUTCH, TeleLayout.CH_PRESET};
    private String shotFile;

    // demo sampling and glide
    private long sampledAt;
    private long sampleSeq;
    private long seenSeq = -1;
    private final int[] lastRaw = new int[TeleLayout.CHANNELS];
    private final TeleGlide teleGlide = new TeleGlide();
    private final TeleUi ui = new TeleUi();
    private final double[] prev = new double[TeleLayout.CHANNELS];
    private final double[] cur = new double[TeleLayout.CHANNELS];
    private long prevAt;
    private long curAt;

    // readout
    private double fps;
    private double buildMs;
    private double drawMs;
    private int slots;
    private long ticks;

    private TeleSim(boolean demo, int tickHz, File photos) {
        this.demo = demo;
        for (int i = 0; i < TICK_HZ.length; i++) {
            if (TICK_HZ[i] == tickHz) {
                tickHzI = i;
            }
        }
        photo[0] = read(new File(photos, "cockpit-large-view.jpg"));
        photo[1] = read(new File(photos, "cockpit-small-view.jpg"));
        skin[0] = Skin.load(new File("skins", "classic.skin"));
        skin[1] = Skin.load(new File("skins", "sport.skin"));
        setPreferredSize(new Dimension(SW, SH + 40));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                key(e.getKeyCode());
            }
        });
    }

    public static void main(String[] a) throws Exception {
        boolean demo = true;
        int key = 0;
        int hz = 30;
        double scale = 0.9;
        File photos = new File("photos");
        double shotAfter = -1;
        String shotFile = null;
        int view = TeleLayout.VIEW_LARGE;
        boolean pieces = false;
        boolean glide = false;
        boolean bgPhoto = false;
        boolean themeOn = false;
        java.util.List<String> sets = new java.util.ArrayList<String>();
        for (int i = 0; i < a.length; i++) {
            if (a[i].equals("--live")) {
                demo = false;
            } else if (a[i].equals("--key")) {
                key = (int) Long.parseLong(a[++i]);
            } else if (a[i].equals("--hz")) {
                hz = Integer.parseInt(a[++i]);
            } else if (a[i].equals("--scale")) {
                scale = Double.parseDouble(a[++i]);
            } else if (a[i].equals("--photos")) {
                photos = new File(a[++i]);
            } else if (a[i].equals("--sport")) {
                view = TeleLayout.VIEW_SMALL;
            } else if (a[i].equals("--pieces")) {
                pieces = true;
            } else if (a[i].equals("--set")) {
                sets.add(a[++i]);
            } else if (a[i].equals("--ui")) {
                for (String u : a[++i].split(",")) {
                    TeleUi.post(TeleUi.action(u));
                }
            } else if (a[i].equals("--theme")) {
                themeOn = true;
            } else if (a[i].equals("--portraits")) {
                Theme.portraits = a[++i].equals("eyes") ? 3 : a[i].equals("colour") ? 2 : a[i].equals("tint") ? 1 : 0;
            } else if (a[i].equals("--photo")) {
                bgPhoto = true;
            } else if (a[i].equals("--glide")) {
                glide = true;
            } else if (a[i].equals("--shot")) {
                shotAfter = Double.parseDouble(a[++i]);
                shotFile = a[++i];
            } else {
                System.out.println("usage: TeleSim [--live] [--key N] [--hz 15|20|30|60] [--scale 0.9] [--photos dir]"
                    + " [--sport] [--pieces] [--glide] [--photo] [--theme] [--ui open,down,press,...] [--portraits tint|colour|eyes] [--set NAME=VALUE]... [--shot SECONDS FILE.png]");
                return;
            }
        }
        final TeleSim sim = new TeleSim(demo, hz, photos);
        sim.view = view;
        sim.painted = !pieces;
        sim.glide = glide;
        sim.bgMode = bgPhoto ? 2 : 0;
        sim.theme = themeOn;
        for (String kv : sets) {
            String[] p = kv.split("=");
            int ch = -1;
            for (int k = 0; k < SET_NAMES.length; k++) {
                if (SET_NAMES[k].equals(p[0])) {
                    ch = SET_CH[k];
                }
            }
            if (ch < 0 || p.length != 2) {
                System.out.println("--set NAME=VALUE, names: " + String.join(" ", SET_NAMES) + " (boost in kPa)");
                return;
            }
            sim.fixed.put(ch, (int) Math.round(Double.parseDouble(p[1]) * 1000));
        }
        sim.shotAt = shotAfter < 0 ? -1 : System.currentTimeMillis() + (long) (shotAfter * 1000);
        sim.shotFile = shotFile;
        final double sc = scale;
        TeleLink.start(key);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                JFrame f = new JFrame("TT autocross view simulator");
                f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                sim.setPreferredSize(new Dimension((int) (SW * sc), (int) ((SH + 40) * sc)));
                f.add(sim);
                f.pack();
                f.setVisible(true);
                sim.requestFocusInWindow();
            }
        });
        System.out.println("keys: V view, P painted strip, D demo/live, G glide, R demo data rate, F tick rate, "
            + "B background (skin/drawn/photo/black), T theme, H readout, S save PNG");
        sim.loop();
    }

    // ---------------------------------------------------------------- tick (like tt-tele-tick)

    private void loop() {
        long next = System.nanoTime();
        long fpsAt = System.currentTimeMillis();
        int fpsN = 0;
        for (;;) {
            long period = 1000000000L / TICK_HZ[tickHzI];
            next += period;
            long now = System.currentTimeMillis();
            TeleLink.want(!demo);
            long t0 = System.nanoTime();
            inputs(now);
            layout.paintedStrip = painted;
            layout.themed = theme;
            layout.build(view, TeleLayout.PAGE_LIVE);
            long t1 = System.nanoTime();
            synchronized (frame) {
                render();
            }
            long t2 = System.nanoTime();
            buildMs = 0.9 * buildMs + 0.1 * (t1 - t0) / 1e6;
            drawMs = 0.9 * drawMs + 0.1 * (t2 - t1) / 1e6;
            slots = layout.count;
            ticks++;
            fpsN++;
            if (now - fpsAt >= 1000) {
                fps = fpsN * 1000.0 / (now - fpsAt);
                fpsN = 0;
                fpsAt = now;
            }
            if (savePng) {
                savePng = false;
                save(null);
            }
            if (shotAt > 0 && now >= shotAt) {
                save(shotFile);
                System.exit(0);
            }
            repaint();
            long wait = next - System.nanoTime();
            if (wait > 0) {
                try {
                    Thread.sleep(wait / 1000000L, (int) (wait % 1000000L));
                } catch (InterruptedException e) {
                    return;
                }
            } else {
                next = System.nanoTime();
            }
        }
    }

    /** Values for this tick: demo sampled at the data rate, or the receiver's; then glide. */
    private void inputs(long now) {
        for (int i = 0; i < TeleLayout.CHANNELS; i++) {
            layout.has[i] = false;
        }
        layout.now = now;
        long seq;
        if (demo) {
            if (now - sampledAt >= 1000 / DATA_HZ[dataHzI]) {
                sampledAt = now;
                for (int i = 0; i < TeleLayout.CHANNELS; i++) {
                    sample.has[i] = false;
                }
                TeleLink.demo(sample, now);
                sampleSeq++;
            }
            System.arraycopy(sample.val, 0, layout.val, 0, TeleLayout.CHANNELS);
            System.arraycopy(sample.has, 0, layout.has, 0, TeleLayout.CHANNELS);
            layout.link = sample.link;
            layout.rateHz = DATA_HZ[dataHzI];
            layout.catN = sample.catN;
            for (int i = 0; i < sample.catN; i++) {
                layout.catId[i] = sample.catId[i];
                layout.catMode[i] = sample.catMode[i];
                layout.catName[i] = sample.catName[i];
            }
            seq = sampleSeq;
        } else {
            TeleLink.snapshot(layout, now);
            seq = seenSeq;
            for (int k = 0; k < GLIDE_CH.length; k++) {
                if (layout.val[GLIDE_CH[k]] != lastRaw[GLIDE_CH[k]]) {
                    seq = seenSeq + 1; // a new datagram changed something
                }
            }
        }
        boolean fresh = seq != seenSeq;
        seenSeq = seq;
        for (int k = 0; k < GLIDE_CH.length; k++) {
            lastRaw[GLIDE_CH[k]] = layout.val[GLIDE_CH[k]];
        }
        for (Map.Entry<Integer, Integer> e : fixed.entrySet()) {
            layout.val[e.getKey()] = e.getValue();
            layout.has[e.getKey()] = true;
        }
        if (glide) {
            teleGlide.apply(layout, now, demo && fresh); // the head unit's own glide (TeleGlide)
        }
        TeleLink.demoLink = demo;
        ui.tick(layout, now); // the Haldex control page (TeleUi), as on the head unit
    }

    // ---------------------------------------------------------------- drawing (like TelePreview)

    private void render() {
        Graphics2D g = frame.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, SW, SH);
        BufferedImage bg = photo[view];
        int mode = bgMode == 0 && skin[view] == null ? 1 : bgMode; // no skin: draw the cockpit
        if (mode == 0) {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, SW, SH);
            skin[view].paint(g);
            g.setClip(visible(view));
        } else if (mode == 1) {
            int rpm = layout.has[TeleLayout.CH_RPM] ? layout.val[TeleLayout.CH_RPM] / 1000 : 0;
            int mph;
            if (layout.has[TeleLayout.CH_SPEED]) {
                mph = (int) Math.round(layout.val[TeleLayout.CH_SPEED] / 1000.0 * 0.621371);
            } else if (demo && layout.has[TeleLayout.CH_GEAR]) {
                mph = rpm * (layout.val[TeleLayout.CH_GEAR] / 1000) / 260; // demo has no speed
            } else {
                mph = 0;
            }
            if (view == TeleLayout.VIEW_LARGE) {
                Chrome.classic(g, rpm, mph);
            } else {
                Chrome.sport(g, rpm, mph);
            }
            g.setClip(visible(view));
        } else if (mode == 2 && bg != null) {
            g.drawImage(bg, 0, 0, SW, bg.getHeight() * SW / bg.getWidth(), null);
            g.setClip(visible(view));
        }
        for (int i = 0; i < layout.count; i++) {
            if (!layout.vis[i]) {
                continue;
            }
            int c = layout.rgba[i];
            AffineTransform saved = g.getTransform();
            if (layout.rot[i] != 0) {
                g.rotate(Math.toRadians(layout.rot[i]), layout.x[i], layout.y[i]);
            }
            g.setColor(new Color((c >>> 24) & 0xFF, (c >>> 16) & 0xFF, (c >>> 8) & 0xFF, c & 0xFF));
            int k = layout.kind[i];
            if (theme && i == 0 && k == TeleLayout.BOX && layout.w[i] == SW && layout.h[i] == SH) {
                if (themeImg[view] == null) {
                    themeImg[view] = Theme.paint(view);
                }
                g.drawImage(themeImg[view], 0, 0, null);
            } else if (k == TeleLayout.STRIP) {
                strip(g, i);
            } else if (k == TeleLayout.BOX && layout.rot[i] != 0) {
                g.fillRect(layout.x[i], layout.y[i] - layout.h[i] / 2, layout.w[i], layout.h[i]);
            } else if (k == TeleLayout.BOX) {
                g.fillRect(layout.x[i], layout.y[i], layout.w[i], layout.h[i]);
            } else if (k == TeleLayout.QUAD) {
                int[] xs = new int[4];
                int[] ys = new int[4];
                for (int j = 0; j < 4; j++) {
                    xs[j] = layout.qx[4 * i + j];
                    ys[j] = layout.qy[4 * i + j];
                }
                g.fillPolygon(xs, ys, 4);
            } else if (k == TeleLayout.ICON) {
                g.drawImage(icon(layout.size[i], layout.w[i], layout.h[i], c), layout.x[i], layout.y[i], null);
            } else if (layout.text[i] != null) {
                Font f = font(layout.bold[i], layout.size[i]);
                g.setFont(f);
                FontMetrics fm = g.getFontMetrics(f);
                int tw = fm.stringWidth(layout.text[i]);
                int x = layout.x[i];
                if (layout.align[i] == TeleLayout.CENTER) {
                    x -= tw / 2;
                } else if (layout.align[i] == TeleLayout.RIGHT) {
                    x -= tw;
                }
                g.drawString(layout.text[i], x, layout.y[i] + Math.round(0.82f * layout.size[i]));
            }
            g.setTransform(saved);
        }
        g.dispose();
    }

    /** STRIP slot: the track picture, or the lit picture clipped to x .. x + w (the car's clip). */
    private void strip(Graphics2D g, int i) {
        if (stripTrack == null) {
            TeleStrip s = TeleStrip.paint(layout);
            stripTrack = argbImage(s.track);
            stripLit = argbImage(s.lit);
            stripLitRed = argbImage(s.litRed);
        }
        if (layout.size[i] == 0) {
            g.drawImage(stripTrack, layout.x[i], layout.y[i], null);
            return;
        }
        Shape old = g.getClip();
        g.clip(new Rectangle(layout.x[i], layout.y[i], layout.w[i], layout.h[i]));
        g.drawImage(layout.size[i] == 2 ? stripLitRed : stripLit, layout.x[i], layout.y[i], null);
        g.setClip(old);
    }

    private static BufferedImage argbImage(int[] px) {
        BufferedImage b = new BufferedImage(TeleStrip.W, TeleStrip.H, BufferedImage.TYPE_INT_ARGB);
        b.setRGB(0, 0, TeleStrip.W, TeleStrip.H, px, 0, TeleStrip.W);
        return b;
    }

    private BufferedImage icon(int id, int w, int h, int c) {
        Long k = Long.valueOf(((long) id << 48) ^ ((long) w << 36) ^ ((long) h << 24) ^ (c & 0xFFFFFFFFL));
        BufferedImage b = icons.get(k);
        if (b == null) {
            b = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
            int[] px = TeleIcons.argb(id, w, h, c >>> 8);
            int al = c & 0xFF;
            for (int j = 0; j < px.length; j++) {
                px[j] = ((((px[j] >>> 24) * al) / 255) << 24) | (px[j] & 0xFFFFFF);
            }
            b.setRGB(0, 0, w, h, px, 0, w);
            icons.put(k, b);
        }
        return b;
    }

    private Font font(boolean bold, int size) {
        Integer k = Integer.valueOf(size * 2 + (bold ? 1 : 0));
        Font f = fonts.get(k);
        if (f == null) {
            f = new Font(FONT, bold ? Font.BOLD : Font.PLAIN, size);
            fonts.put(k, f);
        }
        return f;
    }

    /** What of our layer the car lets through: the cluster's window minus the stock parts above it. */
    private static Area visible(int view) {
        Area v;
        if (view == TeleLayout.VIEW_LARGE) {
            v = new Area(new Rectangle(0, 0, SW, SH));
            v.subtract(new Area(new Ellipse2D.Double(205 - 128, 386 - 128, 256, 256))); // rev counter (symmetric about x 720)
            v.subtract(new Area(new Ellipse2D.Double(1235 - 128, 386 - 128, 256, 256))); // speedometer
            v.subtract(new Area(new Rectangle(440, 40, 560, 54))); // tab bar, centred on 720
            v.subtract(new Area(new Rectangle(0, 464, SW, 76))); // bottom bar
            v.subtract(new Area(new Polygon(new int[] {0, 290, 0}, new int[] {0, 0, 110}, 3))); // frame
            v.subtract(new Area(new Polygon(new int[] {1100, 1440, 1440}, new int[] {0, 0, 108}, 3)));
        } else {
            v = new Area(new Polygon(new int[] {0, 200, 520, 520, 0}, new int[] {110, 50, 50, 468, 468}, 5));
            v.subtract(new Area(new Rectangle(180, 30, 300, 38))); // status line
            v.subtract(new Area(new Ellipse2D.Double(791 - 290, 288 - 290, 580, 580))); // rev counter
        }
        return v;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        double sc = getWidth() / (double) SW;
        int fh = (int) Math.round(SH * sc);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        synchronized (frame) {
            g.drawImage(frame, 0, 0, getWidth(), fh, null);
        }
        if (!hud) {
            return;
        }
        g.setColor(new Color(0x15181C));
        g.fillRect(0, fh, getWidth(), getHeight() - fh);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Monospaced", Font.PLAIN, 13));
        String data = demo ? "demo " + DATA_HZ[dataHzI] + " Hz" : "live udp " + TeleLink.info();
        g.drawString(String.format("%s | %s strip | glide %s | tick %d Hz, drawn %.1f/s, layout %.2f ms, draw %.1f ms, "
                + "%d slots | %s | %s", view == 0 ? "Classic" : "Sport", painted ? "painted" : "pieces",
            glide ? "on" : "off", TICK_HZ[tickHzI], fps, buildMs, drawMs, slots, BG_NAMES[bgMode], data), 8, fh + 16);
        g.setColor(Color.GRAY);
        g.drawString("V view  P strip  D demo/live  G glide  R data rate  F tick rate  B background  T theme  O Haldex control (arrows, Enter, Backspace)  H readout  S save PNG",
            8, fh + 33);
        g.setStroke(new BasicStroke(1));
    }

    private void key(int k) {
        switch (k) {
            case KeyEvent.VK_V:
                view = view == TeleLayout.VIEW_LARGE ? TeleLayout.VIEW_SMALL : TeleLayout.VIEW_LARGE;
                break;
            case KeyEvent.VK_P:
                painted = !painted;
                break;
            case KeyEvent.VK_D:
                demo = !demo;
                break;
            case KeyEvent.VK_G:
                glide = !glide;
                break;
            case KeyEvent.VK_R:
                dataHzI = (dataHzI + 1) % DATA_HZ.length;
                break;
            case KeyEvent.VK_F:
                tickHzI = (tickHzI + 1) % TICK_HZ.length;
                break;
            case KeyEvent.VK_B:
                bgMode = (bgMode + 1) % BG_NAMES.length;
                break;
            case KeyEvent.VK_O:
                TeleUi.post(TeleUi.OPEN);
                break;
            case KeyEvent.VK_UP:
                TeleUi.post(TeleUi.UP);
                break;
            case KeyEvent.VK_DOWN:
                TeleUi.post(TeleUi.DOWN);
                break;
            case KeyEvent.VK_ENTER:
                TeleUi.post(TeleUi.PRESS);
                break;
            case KeyEvent.VK_BACK_SPACE:
            case KeyEvent.VK_ESCAPE:
                TeleUi.post(TeleUi.BACK);
                break;
            case KeyEvent.VK_T:
                theme = !theme;
                break;
            case KeyEvent.VK_H:
                hud = !hud;
                break;
            case KeyEvent.VK_S:
                savePng = true;
                break;
            default:
                break;
        }
    }

    private void save(String name) {
        File f = new File(name != null ? name
            : "sim-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".png");
        try {
            synchronized (frame) {
                ImageIO.write(frame, "png", f);
            }
            System.out.println("saved " + f.getPath());
        } catch (Exception e) {
            System.out.println("save failed: " + e);
        }
    }

    private static BufferedImage read(File f) {
        try {
            return ImageIO.read(f);
        } catch (Exception e) {
            System.out.println("no photo " + f.getPath() + " (black background)");
            return null;
        }
    }
}
