package local.tt;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * The itasha theme's background (user's livery: cobalt, white and cyan, hazard stripes, target
 * rings, x marks, circuit traces, race number 87). Drawn in code at the screen's 1440 x 540, then
 * faded by a mask that keeps it faint behind the gauges (the strip, the notch, the value rows) and
 * lets it show in the empty pockets. One picture: on the car it would be loaded once as a PNG.
 */
final class Theme {
    static final int W = 1440;
    static final int H = 540;

    private static final Color COBALT = new Color(0x1E46E0);
    private static final Color CYAN = new Color(0x2FD4FF);
    private static final Color WHITE = new Color(0xE8F0FF);

    private Theme() {
    }

    static BufferedImage paint() {
        BufferedImage pat = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = pat.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // wrap panels (user's print layout): faceted blue crosses on the lower corners and the
        // top-right pocket, chevrons along the sill line, fender arches around the stock dials
        // (they sit where the wheels would), a checker strip
        BufferedImage fac = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D f = fac.createGraphics();
        f.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        facets(f, new Rectangle2D.Double(0, 0, W, H), 1f);
        f.dispose();
        fadeFromCorners(fac);
        g.drawImage(fac, 0, 0, null);
        chevrons(g, 0, 434, W, 26, COBALT, 0.75f);
        arch(g, 190, 386, 150, 22, WHITE, 0.16f);
        arch(g, 190, 386, 172, 6, CYAN, 0.35f);
        arch(g, 1220, 383, 150, 22, WHITE, 0.16f);
        arch(g, 1220, 383, 172, 6, CYAN, 0.35f);
        checker(g, 336, 448, 220, 12, 6, 0.30f);
        checker(g, 864, 448, 220, 12, 6, 0.30f);
        // target rings (the livery's circle motif), behind the left pocket and lower right
        rings(g, 140, 150, 40, 210, 16, CYAN, 0.30f);
        rings(g, 1330, 430, 30, 170, 14, CYAN, 0.22f);
        rings(g, 560, 470, 20, 120, 12, WHITE, 0.10f);
        // circuit traces with pads
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        trace(g, CYAN, 0.35f, 0, 300, 40, 300, 70, 330, 70, 400, 100, 430);
        trace(g, CYAN, 0.30f, 300, 60, 260, 60, 230, 90, 120, 90);
        trace(g, CYAN, 0.30f, 1440, 250, 1390, 250, 1360, 280, 1360, 330);
        trace(g, WHITE, 0.18f, 1110, 470, 1180, 470, 1210, 440, 1300, 440);
        // scattered x marks
        Random rnd = new Random(87);
        for (int i = 0; i < 90; i++) {
            double x = rnd.nextDouble() * W;
            double y = rnd.nextDouble() * H;
            double s = 6 + rnd.nextDouble() * 10;
            float a = 0.25f + rnd.nextFloat() * 0.35f;
            cross(g, x, y, s, rnd.nextInt(3) == 0 ? WHITE : CYAN, a);
        }
        g.dispose();

        // base and mask
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D b = out.createGraphics();
        b.setPaint(new RadialGradientPaint(720f, 290f, 820f, new float[] {0f, 0.55f, 1f},
            new Color[] {new Color(0x020309), new Color(0x040817), new Color(0x0A1436)}));
        b.fillRect(0, 0, W, H);
        b.dispose();
        int[] base = out.getRGB(0, 0, W, H, null, 0, W);
        int[] p = pat.getRGB(0, 0, W, H, null, 0, W);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                double a = ((p[i] >>> 24) / 255.0) * mask(x, y) * 0.55; // keep it all dim
                base[i] = over(base[i], p[i], a);
            }
        }
        out.setRGB(0, 0, W, H, base, 0, W);
        return out;
    }

    /**
     * Sketch sheet: the background alone, and again with the layout's zones dashed (where it stays
     * faint) and the empty pockets lettered (where characters could go).
     *   java -cp out local.tt.Theme OUT.png
     */
    public static void main(String[] a) throws Exception {
        BufferedImage bg = paint();
        BufferedImage sheet = new BufferedImage(W, 2 * H + 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x15181C));
        g.fillRect(0, 0, W, sheet.getHeight());
        g.setColor(Color.WHITE);
        g.setFont(new Font(Chrome.FAMILY, Font.BOLD, 22));
        g.drawString("1  Background alone", 14, 22);
        g.drawImage(bg, 0, 30, null);
        g.drawString("2  Layout zones (dashed, background kept faint) and empty pockets A-E (characters)", 14, H + 52);
        g.drawImage(bg, 0, H + 60, null);
        g.translate(0, H + 60);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {8f, 6f}, 0f));
        g.setColor(new Color(255, 255, 255, 170));
        int[] sx = TeleLayout.TP_X;
        int[] sy = TeleLayout.TP_Y;
        Path2D.Double strip = new Path2D.Double();
        strip.moveTo(sx[0], sy[0] - TeleLayout.HALF_MIN);
        strip.lineTo(sx[1], sy[1] - TeleLayout.HALF_MAX);
        strip.lineTo(sx[2], sy[2] - TeleLayout.HALF_MAX);
        strip.lineTo(sx[3], sy[3] - TeleLayout.HALF_MIN);
        strip.lineTo(sx[3], sy[3] + TeleLayout.HALF_MIN);
        strip.lineTo(sx[2], sy[2] + TeleLayout.HALF_MAX);
        strip.lineTo(sx[1], sy[1] + TeleLayout.HALF_MAX);
        strip.lineTo(sx[0], sy[0] + TeleLayout.HALF_MIN);
        strip.closePath();
        g.draw(strip);
        g.draw(new Rectangle2D.Double(325, 100, 732, 30)); // shift lights
        g.draw(new Rectangle2D.Double(330, 226, 760, 196)); // values, notch, boost, temperatures
        g.draw(new Ellipse2D.Double(62, 258, 256, 256)); // stock rev counter
        g.draw(new Ellipse2D.Double(1092, 255, 256, 256)); // stock speedometer
        g.draw(new Rectangle2D.Double(428, 40, 560, 54)); // stock tab bar
        g.draw(new Rectangle2D.Double(0, 464, W, 76)); // stock bottom bar
        g.setFont(new Font(Chrome.FAMILY, Font.PLAIN, 15));
        g.drawString("rev strip", 600, 176);
        g.drawString("shift lights", 340, 96);
        g.drawString("values / gear / boost / temps", 340, 418);
        g.drawString("stock dial", 150, 254);
        g.drawString("stock dial", 1180, 251);
        g.drawString("stock tab bar", 436, 36);
        g.drawString("stock bottom bar", 600, 480);
        String[] lab = {"A", "B", "C", "D", "E"};
        double[][] at = {{40, 120, 200, 110}, {1120, 120, 200, 90}, {320, 425, 260, 40}, {860, 425, 230, 40},
            {0, 290, 60, 150}};
        String[] what = {"preset pocket", "Haldex pocket", "low band left", "low band right", "edge"};
        for (int i = 0; i < lab.length; i++) {
            g.setColor(new Color(0x2FD4FF));
            g.draw(new Rectangle2D.Double(at[i][0], at[i][1], at[i][2], at[i][3]));
            g.setFont(new Font(Chrome.FAMILY, Font.BOLD, 20));
            g.drawString(lab[i], (float) at[i][0] + 6, (float) at[i][1] + 22);
            g.setFont(new Font(Chrome.FAMILY, Font.PLAIN, 14));
            g.drawString(what[i], (float) at[i][0] + 26, (float) at[i][1] + 20);
        }
        g.dispose();
        javax.imageio.ImageIO.write(sheet, "png", new java.io.File(a[0]));
        javax.imageio.ImageIO.write(bg, "png", new java.io.File(a[0].replace(".png", "-bg.png")));
        System.out.println("wrote " + a[0]);
    }

    /** 0.12 behind the gauges (strip, notch, value rows), up to 1 in the pockets and edges. */
    private static double mask(int x, int y) {
        double dx = Math.max(0, Math.max(330 - x, x - 1090)) / 90.0;
        double dy = Math.max(0, Math.max(118 - y, y - 425)) / 40.0;
        double d = Math.min(1, Math.hypot(dx, dy));
        return 0.12 + 0.88 * d * d * (3 - 2 * d);
    }

    private static int over(int dst, int src, double a) {
        int r = (int) Math.round(((dst >> 16) & 0xFF) * (1 - a) + ((src >> 16) & 0xFF) * a);
        int gg = (int) Math.round(((dst >> 8) & 0xFF) * (1 - a) + ((src >> 8) & 0xFF) * a);
        int bb = (int) Math.round((dst & 0xFF) * (1 - a) + (src & 0xFF) * a);
        return (r << 16) | (gg << 8) | bb;
    }

    /** Extruded "+" blocks on a staggered grid: face, darker sides, a light top edge. */
    private static void facets(Graphics2D g, Shape clip, float a) {
        Shape old = g.getClip();
        g.setClip(clip);
        Rectangle2D r = clip.getBounds2D();
        double u = 20; // arm width
        double d = 12; // extrusion
        Random shade = new Random(141);
        g.setComposite(AlphaComposite.SrcOver.derive(a));
        int row = 0;
        for (double y = r.getMinY() - 3 * u; y < r.getMaxY() + 3 * u; y += 3.2 * u, row++) {
            for (double x = r.getMinX() - 3 * u + (row % 2) * 3.2 * u; x < r.getMaxX() + 3 * u; x += 6.4 * u) {
                if (shade.nextInt(5) == 0) {
                    continue; // gaps, like the wrap's loose blocks
                }
                g.setComposite(AlphaComposite.SrcOver.derive(a * (0.45f + 0.55f * shade.nextFloat())));
                Path2D.Double plus = plus(x, y, u);
                g.setColor(new Color(0x06134F));
                g.fill(java.awt.geom.AffineTransform.getTranslateInstance(d, d).createTransformedShape(plus));
                g.setColor(new Color(0x0B2BA8));
                g.fill(java.awt.geom.AffineTransform.getTranslateInstance(d / 2, d / 2).createTransformedShape(plus));
                g.setColor(new Color(0x1E46E0));
                g.fill(plus);
                g.setColor(new Color(0x5C86FF));
                g.setStroke(new BasicStroke(1.4f));
                g.draw(plus);
            }
        }
        g.setClip(old);
        g.setComposite(AlphaComposite.SrcOver);
    }

    /** Keeps the blocks only towards three corners (bottom left, bottom right, top right), soft. */
    private static void fadeFromCorners(BufferedImage img) {
        double[][] c = {{0, H, 380}, {W, H, 400}, {W, 108, 300}};
        int[] px = img.getRGB(0, 0, W, H, null, 0, W);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double k = 0;
                for (double[] cc : c) {
                    double t = Math.min(1, Math.hypot(x - cc[0], y - cc[1]) / cc[2]);
                    k = Math.max(k, 1 - t * t * (3 - 2 * t));
                }
                int i = y * W + x;
                int a = (int) Math.round((px[i] >>> 24) * k);
                px[i] = (a << 24) | (px[i] & 0xFFFFFF);
            }
        }
        img.setRGB(0, 0, W, H, px, 0, W);
    }

    private static Path2D.Double plus(double x, double y, double u) {
        return path(x + u, y, x + 2 * u, y, x + 2 * u, y + u, x + 3 * u, y + u, x + 3 * u, y + 2 * u, x + 2 * u,
            y + 2 * u, x + 2 * u, y + 3 * u, x + u, y + 3 * u, x + u, y + 2 * u, x, y + 2 * u, x, y + u, x + u, y + u);
    }

    private static void chevrons(Graphics2D g, double x0, double y, double w, double h, Color c, float a) {
        g.setComposite(AlphaComposite.SrcOver.derive(a));
        g.setColor(c);
        for (double x = x0; x < x0 + w; x += 30) {
            g.fill(path(x, y, x + 10, y, x + 10 + h / 2, y + h / 2, x + 10, y + h, x, y + h, x + h / 2, y + h / 2));
        }
        g.setComposite(AlphaComposite.SrcOver);
    }

    /** Upper half ring around a dial, like a fender arch. */
    private static void arch(Graphics2D g, double cx, double cy, double r, float w, Color c, float a) {
        g.setComposite(AlphaComposite.SrcOver.derive(a));
        g.setColor(c);
        g.setStroke(new BasicStroke(w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, 10, 160, java.awt.geom.Arc2D.OPEN));
        g.setComposite(AlphaComposite.SrcOver);
    }

    private static void checker(Graphics2D g, double x0, double y0, double w, double h, double s, float a) {
        g.setComposite(AlphaComposite.SrcOver.derive(a));
        g.setColor(WHITE);
        for (int i = 0; i * s < w; i++) {
            for (int j = 0; j * s < h; j++) {
                if ((i + j) % 2 == 0) {
                    g.fill(new Rectangle2D.Double(x0 + i * s, y0 + j * s, s, s));
                }
            }
        }
        g.setComposite(AlphaComposite.SrcOver);
    }

    private static void stripes(Graphics2D g, Shape clip, Color c, float a) {
        Shape old = g.getClip();
        g.setClip(clip);
        g.setComposite(AlphaComposite.SrcOver.derive(a));
        g.setColor(c);
        Rectangle2D r = clip.getBounds2D();
        for (double x = r.getMinX() - r.getHeight(); x < r.getMaxX(); x += 28) {
            g.fill(path(x, r.getMaxY(), x + 14, r.getMaxY(), x + 14 + r.getHeight(), r.getMinY(),
                x + r.getHeight(), r.getMinY()));
        }
        g.setClip(old);
        g.setComposite(AlphaComposite.SrcOver);
    }

    private static void rings(Graphics2D g, double cx, double cy, double r0, double r1, double step, Color c,
                              float a) {
        g.setComposite(AlphaComposite.SrcOver.derive(a));
        g.setColor(c);
        int k = 0;
        for (double r = r0; r <= r1; r += step, k++) {
            g.setStroke(new BasicStroke(k % 3 == 0 ? 3f : 1.2f));
            g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        }
        g.fill(new Ellipse2D.Double(cx - r0 * 0.4, cy - r0 * 0.4, r0 * 0.8, r0 * 0.8));
        g.setComposite(AlphaComposite.SrcOver);
    }

    private static void trace(Graphics2D g, Color c, float a, double... xy) {
        g.setComposite(AlphaComposite.SrcOver.derive(a));
        g.setColor(c);
        Path2D.Double p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) {
            p.lineTo(xy[i], xy[i + 1]);
        }
        g.draw(p);
        int n = xy.length;
        g.fill(new Ellipse2D.Double(xy[n - 2] - 4, xy[n - 1] - 4, 8, 8));
        g.setComposite(AlphaComposite.SrcOver);
    }

    private static void cross(Graphics2D g, double x, double y, double s, Color c, float a) {
        g.setComposite(AlphaComposite.SrcOver.derive(a));
        g.setColor(c);
        g.setStroke(new BasicStroke((float) Math.max(1.5, s / 6), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Line2D.Double(x - s / 2, y - s / 2, x + s / 2, y + s / 2));
        g.draw(new java.awt.geom.Line2D.Double(x - s / 2, y + s / 2, x + s / 2, y - s / 2));
        g.setComposite(AlphaComposite.SrcOver);
    }

    private static Path2D.Double path(double... xy) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) {
            p.lineTo(xy[i], xy[i + 1]);
        }
        p.closePath();
        return p;
    }
}
