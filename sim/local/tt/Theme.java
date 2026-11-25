package local.tt;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
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

    /** Portraits from skins/art.jpg (the user's two-character illustration): 0 none, 1 tinted, 2 colour, 3 eye strips. */
    static int portraits = 0;

    static BufferedImage paint() {
        return paint(TeleLayout.VIEW_LARGE);
    }

    /** The theme for Classic (VIEW_LARGE) or Sport (VIEW_SMALL). */
    static BufferedImage paint(int view) {
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
        // the slanted parallelogram stripes of the first sketch along the sill line (user preferred
        // them to chevrons)
        stripes(g, new Rectangle2D.Double(0, 424, W, 40), COBALT, 0.55f);
        checker(g, 336, 448, 220, 12, 6, 0.30f);
        checker(g, 864, 448, 220, 12, 6, 0.30f);
        // fender arches around the stock dials (user 2026-11-25: liked this contour best), at the
        // dial centres from the user's clipping guide
        arch(g, 231, 389, 150, 22, WHITE, 0.16f);
        arch(g, 231, 389, 172, 6, CYAN, 0.35f);
        arch(g, 1209, 389, 150, 22, WHITE, 0.16f);
        arch(g, 1209, 389, 172, 6, CYAN, 0.35f);
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
                double a = ((p[i] >>> 24) / 255.0) * mask(view, x, y) * 0.55; // keep it all dim
                base[i] = over(base[i], p[i], a);
            }
        }
        out.setRGB(0, 0, W, H, base, 0, W);
        if (view == TeleLayout.VIEW_SMALL) {
            sport(out);
            return out;
        }
        if (portraits > 0) {
            portraitsOn(out);
        }
        housings(out);
        chibis(out);
        return out;
    }

    /**
     * Sketch sheet: the background alone, and again with the layout's zones dashed (where it stays
     * faint) and the empty pockets lettered (where characters could go).
     *   java -cp out local.tt.Theme OUT.png
     */
    public static void main(String[] a) throws Exception {
        if (a.length > 0 && a[0].equals("--export")) {
            export(a);
            return;
        }
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
        g.draw(new Ellipse2D.Double(119, 273, 232, 232)); // stock rev counter
        g.draw(new Ellipse2D.Double(1089, 273, 232, 232)); // stock speedometer
        g.draw(new Rectangle2D.Double(440, 40, 560, 54)); // stock tab bar
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

    /**
     * Housings so the gauges sit in the theme instead of on it: a plate under the rev strip and one
     * under the value block (dark glass, hairline edge, glow, drop shadow, corner brackets), and a
     * rail under the shift lights. Drawn at full strength, after the mask.
     */
    private static void housings(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Area strip = stripShape(12);
        Path2D.Double panel = path(388, 216, 1070, 216, 1070, 412, 1052, 430, 370, 430, 370, 234); // centred on 720, a clear gap to the dials
        Area rail = new Area(new Rectangle2D.Double(720 - 338, 102, 676, 34));
        for (Shape sh : new Shape[] {strip, panel, rail}) {
            // drop shadow: the shape a few times, offset down, faint
            for (int k = 1; k <= 6; k++) {
                g.setComposite(AlphaComposite.SrcOver.derive(0.10f));
                g.setColor(Color.BLACK);
                g.fill(java.awt.geom.AffineTransform.getTranslateInstance(0, k * 1.5).createTransformedShape(sh));
            }
            // glow: wide faint strokes in cobalt
            for (float w : new float[] {22f, 14f, 8f}) {
                g.setComposite(AlphaComposite.SrcOver.derive(0.14f));
                g.setColor(COBALT);
                g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(sh);
            }
            g.setComposite(AlphaComposite.SrcOver.derive(0.92f));
            g.setPaint(new java.awt.GradientPaint(0, 100, new Color(0x0A1230), 0, 440, new Color(0x03050C)));
            g.fill(sh);
            // 2026-11-25 (user: frames too thin): a 3 px edge with a bright 1 px core
            g.setComposite(AlphaComposite.SrcOver.derive(0.85f));
            g.setColor(new Color(0x2F6BFF));
            g.setStroke(new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(sh);
            g.setComposite(AlphaComposite.SrcOver.derive(0.8f));
            g.setColor(new Color(0x8FD8FF));
            g.setStroke(new BasicStroke(1.1f));
            g.draw(sh);
        }
        // The panel's decoration (user 2026-11-24, no artwork): embossed crosses, cells with
        // hairlines, corner ticks and small tabs, rings behind the gear (the notch is see-through
        // when themed), a pink line on the left and a cyan one on the right for the two characters,
        // a glass reflection on top and dots fading in from the bottom corners.
        Shape oldClip = g.getClip();
        g.setClip(panel);
        // 1 embossed crosses: a light edge above, a dark edge below, no fill
        for (int row = 0, y = 196; y < 450; y += 44, row++) {
            for (int x = 300 + (row % 2) * 44; x < 1120; x += 88) {
                Path2D.Double pl = plus(x, y, 12);
                g.setStroke(new BasicStroke(1.2f));
                g.setComposite(AlphaComposite.SrcOver.derive(0.10f));
                g.setColor(Color.WHITE);
                g.draw(java.awt.geom.AffineTransform.getTranslateInstance(-1, -1).createTransformedShape(pl));
                g.setComposite(AlphaComposite.SrcOver.derive(0.45f));
                g.setColor(Color.BLACK);
                g.draw(java.awt.geom.AffineTransform.getTranslateInstance(1, 1).createTransformedShape(pl));
            }
        }
        // 3 rings behind the gear, stronger inside the notch
        Path2D.Double notch = path(596, 226, 844, 226, 814, 350, 626, 350);
        for (int r = 30, k = 0; r < 330; r += 18, k++) {
            g.setComposite(AlphaComposite.SrcOver.derive(k % 4 == 0 ? 0.14f : 0.07f));
            g.setColor(CYAN);
            g.setStroke(new BasicStroke(k % 4 == 0 ? 2.2f : 1f));
            g.draw(new Ellipse2D.Double(720 - r, 288 - r, 2 * r, 2 * r));
        }
        // 6 the FL5's centre glow: its own picture (notchGlow), shown by the layout on the live page only
        // 5 dots fading in from the bottom corners
        g.setColor(CYAN);
        for (int y = 222; y < 432; y += 8) {
            for (int x = 326; x < 1096; x += 8) {
                double d = Math.min(Math.hypot(x - 322, y - 430), Math.hypot(x - 1098, y - 430));
                double r = 1.6 * (1 - d / 220);
                if (r > 0.3) {
                    g.setComposite(AlphaComposite.SrcOver.derive(0.16f));
                    g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
                }
            }
        }
        // 5 glass reflection along the top, soft shadow along the bottom
        g.setComposite(AlphaComposite.SrcOver);
        g.setPaint(new java.awt.GradientPaint(0, 216, new Color(255, 255, 255, 26), 0, 246, new Color(255, 255, 255, 0)));
        g.fill(new Rectangle2D.Double(300, 216, 820, 30));
        g.setPaint(new java.awt.GradientPaint(0, 400, new Color(0, 0, 0, 0), 0, 432, new Color(0, 0, 0, 90)));
        g.fill(new Rectangle2D.Double(300, 400, 820, 32));
        // inner cobalt glow along the edges
        for (int i = 0; i < 4; i++) {
            g.setComposite(AlphaComposite.SrcOver.derive(0.06f));
            g.setColor(COBALT);
            g.setStroke(new BasicStroke(10 + i * 10));
            g.draw(panel);
        }
        g.setClip(oldClip);
        // 2 cells: a hairline over the temperature rail, corner ticks, small tabs
        g.setComposite(AlphaComposite.SrcOver.derive(0.55f));
        g.setColor(new Color(0x3E8BFF));
        g.setStroke(new BasicStroke(1f));
        // (the hairline over the temperature row is drawn by the layout, live page only)
        // (the cyan corner ticks around the rpm and boost cells went 2026-11-25: on the Haldex page
        // they framed nothing, like an aim box)
        // (the DRIVER / TURBO tabs went with tele17: the control page uses that space)
        // 4 the two characters' colours along the panel's sides
        g.setComposite(AlphaComposite.SrcOver.derive(0.95f));
        g.setStroke(new BasicStroke(4.5f));
        g.setColor(new Color(0xFF5FA8));
        g.draw(line(370, 262, 370, 428));
        g.setColor(CYAN);
        g.draw(line(1070, 228, 1070, 394));
        g.setComposite(AlphaComposite.SrcOver);
        // corner brackets on the value panel (cyan), and a checker tab at its top left
        g.setComposite(AlphaComposite.SrcOver.derive(0.9f));
        g.setColor(CYAN);
        g.setStroke(new BasicStroke(5.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.draw(line(370, 256, 370, 234, 388, 216, 414, 216));
        g.draw(line(1026, 430, 1052, 430, 1070, 412, 1070, 388));
        g.setComposite(AlphaComposite.SrcOver);
        checker(g, 392, 220, 30, 6, 3, 0.55f);
        // the strip's corner marks: small cyan ticks where its slopes meet the middle
        g.setColor(CYAN);
        g.setStroke(new BasicStroke(2f));
        int[] sx = TeleLayout.TP_X;
        int[] sy = TeleLayout.TP_Y;
        for (int i = 1; i <= 2; i++) {
            g.draw(new java.awt.geom.Line2D.Double(sx[i], sy[i] - TeleLayout.HALF_MAX - 18, sx[i], sy[i] - TeleLayout.HALF_MAX - 12));
        }
        g.dispose();
    }

    /**
     * The two characters, large, at the sides: each a crop of the illustration faded softly into
     * the pattern (no cut-out), partly behind the stock dials and the housings. Tinted: mapped to
     * the livery's blues by brightness. Colour: the art itself, dimmed.
     */
    private static void portraitsOn(BufferedImage out) {
        BufferedImage art;
        try {
            art = javax.imageio.ImageIO.read(new java.io.File("skins", "art.jpg"));
        } catch (Exception e) {
            art = null;
        }
        if (art == null) {
            System.out.println("no skins/art.jpg: portraits off");
            return;
        }
        if (portraits == 3) {
            // Eye strips (user 2026-11-24: the pockets are too short for faces). Each is a band
            // across the eyes, turned so they are level, in a panel slanted along its slope.
            // art: eye A, eye B (A left of B once level), band height in art px
            eyePanel(out, art, 915, 790, 1020, 580, 170, 0, 40, 300, 104, 162); // pink, left slope
            eyePanel(out, art, 1365, 405, 1490, 505, 130, 2, 60, 330, 110, 160); // white-haired, right slope
            return;
        }
        // The first portraits' look (user: "good already"), zoomed out a little, each centred on
        // the point between the eyes; pink turned clockwise to follow the rising slope.
        // art eye centre, scale, turn (deg, clockwise), screen centre, fade oval w h
        // 2026-11-25: pink zoomed out a little; white-haired lower (more forehead, less chin), clear
        // of the Haldex readout
        turnedPortrait(out, art, 967, 685, 0.36, 22, 142, 132, 320, 380);   // pink, left
        turnedPortrait(out, art, 1427, 452, 0.42, 0, 1335, 168, 300, 370);  // white-haired, right
    }

    /**
     * A panel along strip segment seg (0 left slope, 2 right slope) from s0 to s1 (along the
     * segment) and o0 to o1 px out from its centre line, filled with the art band across the eyes
     * (eye A at the panel's left third, eye B at its right third), framed like a manga panel.
     */
    private static void eyePanel(BufferedImage out, BufferedImage art, double ax, double ay, double bx, double by,
                                 double bandH, int seg, double s0, double s1, double o0, double o1) {
        int[] tx = TeleLayout.TP_X;
        int[] ty = TeleLayout.TP_Y;
        double px0 = tx[seg];
        double py0 = ty[seg];
        double L = Math.hypot(tx[seg + 1] - px0, ty[seg + 1] - py0);
        double ux = (tx[seg + 1] - px0) / L;
        double uy = (ty[seg + 1] - py0) / L;
        double nx = uy; // outward normal (up, away from the screen centre), as TeleLayout.tp
        double ny = -ux;
        // art frame: e along the eyes, d "down" the face
        double ex = bx - ax;
        double ey = by - ay;
        double eyeDist = Math.hypot(ex, ey);
        ex /= eyeDist;
        ey /= eyeDist;
        double dx = -ey;
        double dy = ex;
        double cx = (ax + bx) / 2;
        double cy = (ay + by) / 2;
        double pw = s1 - s0;
        double ph = o1 - o0;
        double k = eyeDist * 3.0 / pw; // art px per panel px: the eyes at the thirds... spread
        double kv = bandH / ph;
        double kk = Math.max(k, kv); // keep the art's proportions
        boolean left = seg == 0;
        for (int Y = 0; Y < H; Y++) {
            for (int X = 0; X < W; X++) {
                double rx = X + 0.5 - px0;
                double ry = Y + 0.5 - py0;
                double s = rx * ux + ry * uy;
                double o = rx * nx + ry * ny;
                if (s < s0 || s > s1 || o < o0 || o > o1) {
                    continue;
                }
                // panel coords: u across (left to right on screen), v down
                double u = (left ? s - s0 : s1 - s) - pw / 2;
                if (!left) {
                    u = (s - s0) - pw / 2; // right slope runs left to right too
                }
                double v = (o1 - o) - ph / 2;
                double sx = cx + (u * ex + v * dx) * kk;
                double sy = cy + (u * ey + v * dy) * kk;
                int ix = (int) Math.max(0, Math.min(art.getWidth() - 1, sx));
                int iy = (int) Math.max(0, Math.min(art.getHeight() - 1, sy));
                int c = art.getRGB(ix, iy);
                // soft fade at the panel's long ends
                double fe = Math.min(1, Math.min(s - s0, s1 - s) / 30.0);
                double a = 0.9 * fe;
                int dim = ((int) (((c >> 16) & 0xFF) * 0.8) << 16) | ((int) (((c >> 8) & 0xFF) * 0.8) << 8)
                    | (int) ((c & 0xFF) * 0.8);
                out.setRGB(X, Y, over(out.getRGB(X, Y), dim, a));
            }
        }
        // frame: hairline edges along the slope, cyan, and short ticks at the ends
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x3E8BFF));
        g.setStroke(new BasicStroke(1.6f));
        for (double o : new double[] {o0, o1}) {
            g.draw(new java.awt.geom.Line2D.Double(px0 + ux * (s0 + 20) + nx * o, py0 + uy * (s0 + 20) + ny * o,
                px0 + ux * (s1 - 20) + nx * o, py0 + uy * (s1 - 20) + ny * o));
        }
        g.setColor(CYAN);
        g.setStroke(new BasicStroke(3f));
        g.draw(new java.awt.geom.Line2D.Double(px0 + ux * (s1 - 20) + nx * o1, py0 + uy * (s1 - 20) + ny * o1,
            px0 + ux * (s1 - 60) + nx * o1, py0 + uy * (s1 - 60) + ny * o1));
        g.draw(new java.awt.geom.Line2D.Double(px0 + ux * (s0 + 20) + nx * o0, py0 + uy * (s0 + 20) + ny * o0,
            px0 + ux * (s0 + 60) + nx * o0, py0 + uy * (s0 + 60) + ny * o0));
        g.dispose();
    }

    private static void turnedPortrait(BufferedImage out, BufferedImage art, double ecx, double ecy, double sc,
                                       double turn, double dcx, double dcy, double mw, double mh) {
        double t = Math.toRadians(turn);
        double cs = Math.cos(t);
        double sn = Math.sin(t);
        int x0 = (int) Math.max(0, dcx - mw / 2);
        int x1 = (int) Math.min(W, dcx + mw / 2);
        int y0 = (int) Math.max(0, dcy - mh / 2);
        int y1 = (int) Math.min(H, dcy + mh / 2);
        for (int Y = y0; Y < y1; Y++) {
            for (int X = x0; X < x1; X++) {
                double qx = X + 0.5 - dcx;
                double qy = Y + 0.5 - dcy;
                double ex = qx / (mw / 2);
                double ey = qy / (mh / 2);
                double r = Math.sqrt(ex * ex + ey * ey);
                double a = 1 - smooth((r - 0.55) / 0.45);
                a *= smooth(Math.max(Math.max(330 - X, X - 1090), Math.max(118 - Y, Y - 425)) / 60.0 + 0.0001);
                if (a <= 0) {
                    continue;
                }
                // undo the turn, then the scale: screen offset -> art
                double ux = (qx * cs + qy * sn) / sc;
                double uy = (-qx * sn + qy * cs) / sc;
                int c = sample(art, ecx + ux, ecy + uy);
                int cr = (c >> 16) & 0xFF;
                int cg = (c >> 8) & 0xFF;
                int cb = c & 0xFF;
                int col;
                if (portraits == 1) {
                    col = ramp((0.3 * cr + 0.59 * cg + 0.11 * cb) / 255.0);
                    a *= 0.75;
                } else {
                    col = (int) (cr * 0.5) << 16 | (int) (cg * 0.5) << 8 | (int) (cb * 0.5);
                    a *= 0.85;
                }
                out.setRGB(X, Y, over(out.getRGB(X, Y), col, a));
            }
        }
    }

    private static void eyesPortrait(BufferedImage out, BufferedImage art, double ecx, double ecy, double sc,
                                     double dcx, double dcy, double rx, double ry) {
        int x0 = (int) Math.max(0, dcx - 2.2 * rx);
        int x1 = (int) Math.min(W, dcx + 2.2 * rx);
        int y0 = (int) Math.max(0, dcy - 2.2 * ry);
        int y1 = (int) Math.min(H, dcy + 2.2 * ry);
        for (int Y = y0; Y < y1; Y++) {
            for (int X = x0; X < x1; X++) {
                double ex = (X + 0.5 - dcx) / rx;
                double ey = (Y + 0.5 - dcy) / ry;
                double r = Math.sqrt(ex * ex + ey * ey);
                double a = 1 - smooth((r - 0.45) / 0.55);
                if (a <= 0) {
                    continue;
                }
                // bilinear sample of the art
                double sx = ecx + (X + 0.5 - dcx) / sc;
                double sy = ecy + (Y + 0.5 - dcy) / sc;
                int c = sample(art, sx, sy);
                int cr = (c >> 16) & 0xFF;
                int cg = (c >> 8) & 0xFF;
                int cb = c & 0xFF;
                int col;
                if (portraits == 1) {
                    col = ramp((0.3 * cr + 0.59 * cg + 0.11 * cb) / 255.0);
                    a *= 0.85;
                } else {
                    col = (int) (cr * 0.8) << 16 | (int) (cg * 0.8) << 8 | (int) (cb * 0.8);
                    a *= 0.95;
                }
                out.setRGB(X, Y, over(out.getRGB(X, Y), col, a));
            }
        }
    }

    private static int sample(BufferedImage im, double x, double y) {
        int ix = (int) Math.floor(x - 0.5);
        int iy = (int) Math.floor(y - 0.5);
        double fx = x - 0.5 - ix;
        double fy = y - 0.5 - iy;
        int[] c = new int[4];
        int k = 0;
        for (int j = 0; j < 2; j++) {
            for (int i = 0; i < 2; i++) {
                c[k++] = im.getRGB(Math.max(0, Math.min(im.getWidth() - 1, ix + i)),
                    Math.max(0, Math.min(im.getHeight() - 1, iy + j)));
            }
        }
        int o = 0;
        for (int sh = 0; sh <= 16; sh += 8) {
            double v = ((c[0] >> sh) & 0xFF) * (1 - fx) * (1 - fy) + ((c[1] >> sh) & 0xFF) * fx * (1 - fy)
                + ((c[2] >> sh) & 0xFF) * (1 - fx) * fy + ((c[3] >> sh) & 0xFF) * fx * fy;
            o |= ((int) Math.round(v) & 0xFF) << sh;
        }
        return o;
    }

    private static void portrait(BufferedImage out, BufferedImage art, int sx, int sy, int sw, int sh, int dx, int dy,
                                 double sc) {
        int dw = (int) Math.round(sw * sc);
        int dh = (int) Math.round(sh * sc);
        BufferedImage p = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = p.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(art, 0, 0, dw, dh, sx, sy, sx + sw, sy + sh, null);
        g.dispose();
        for (int y = 0; y < dh; y++) {
            for (int x = 0; x < dw; x++) {
                int X = dx + x;
                int Y = dy + y;
                if (X < 0 || Y < 0 || X >= W || Y >= H) {
                    continue;
                }
                // soft oval fade, and nothing behind the value panel
                double ex = (x + 0.5 - dw / 2.0) / (dw / 2.0);
                double ey = (y + 0.5 - dh / 2.0) / (dh / 2.0);
                double r = Math.sqrt(ex * ex + ey * ey);
                double a = 1 - smooth((r - 0.55) / 0.45);
                a *= smooth(Math.max(Math.max(330 - X, X - 1090), Math.max(118 - Y, Y - 425)) / 60.0 + 0.0001);
                if (a <= 0) {
                    continue;
                }
                int c = p.getRGB(x, y);
                int cr = (c >> 16) & 0xFF;
                int cg = (c >> 8) & 0xFF;
                int cb = c & 0xFF;
                int col;
                if (portraits == 1) {
                    double l = (0.3 * cr + 0.59 * cg + 0.11 * cb) / 255.0;
                    col = ramp(l);
                    a *= 0.75;
                } else {
                    col = (int) (cr * 0.5) << 16 | (int) (cg * 0.5) << 8 | (int) (cb * 0.5);
                    a *= 0.85;
                }
                int d = out.getRGB(X, Y);
                out.setRGB(X, Y, over(d, col, a));
            }
        }
    }

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** Brightness to the livery's blues: navy, cobalt, cyan, pale blue. */
    private static int ramp(double l) {
        int[] st = {0x020616, 0x0B2A9C, 0x1E5BFF, 0x5FD0FF, 0xCFEFFF};
        double f = Math.max(0, Math.min(0.999, l)) * (st.length - 1);
        int i = (int) f;
        double t = f - i;
        int a = st[i];
        int b = st[i + 1];
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bb = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bb;
    }

    /** Two chibi stickers standing on the rev strip's top corners, outside the shift-light rail. */
    private static void chibis(BufferedImage out) {
        Graphics2D g = out.createGraphics();
        // 2026-11-25 (user): between the stock dials and the value panel, in the pocket above each
        // dial's inner shoulder and under the strip
        BufferedImage pink = Cutout.sticker(new java.io.File("skins", "chibi-pink.jpg"), 66, new Color(0xFF5FA8), 12);
        BufferedImage white = Cutout.sticker(new java.io.File("skins", "chibi-white.jpg"), 66, new Color(0x2FD4FF), 12);
        if (pink != null) {
            g.drawImage(pink, 338 - pink.getWidth() / 2, 300 - pink.getHeight() + 12, null);
        }
        if (white != null) {
            g.drawImage(white, 1102 - white.getWidth() / 2, 300 - white.getHeight() + 12, null);
        }
        g.dispose();
    }

    /** A small sticker-style label: caps in a hairline box, right- or left-anchored at (x, baseline). */
    private static void tab(Graphics2D g, String t, double x, double y, boolean leftAnchor) {
        Font f = new Font(Chrome.FAMILY, Font.BOLD, 11);
        g.setFont(f);
        int w = g.getFontMetrics().stringWidth(t) + 10;
        double x0 = leftAnchor ? x : x - w;
        g.setComposite(AlphaComposite.SrcOver.derive(0.55f));
        g.setColor(new Color(0x3E8BFF));
        g.setStroke(new BasicStroke(1f));
        g.draw(new Rectangle2D.Double(x0, y - 11, w, 15));
        g.setComposite(AlphaComposite.SrcOver.derive(0.7f));
        g.setColor(new Color(0x9FC4FF));
        g.drawString(t, (float) x0 + 5, (float) y);
        g.setComposite(AlphaComposite.SrcOver);
    }

    /**
     * Sport (the map area left of the big rev counter, user 2026-11-24): one glass housing around
     * the panel's content with the Classic panel's decoration (embossed crosses, rings behind the
     * gear, pink and cyan side lines, corner brackets, glass light), and the doll chibi in the gap
     * between the panel and the rev counter.
     */
    private static void sport(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // the panel traces the part of the screen the cluster lets through (TeleLayout.SP_X/SP_Y)
        Path2D.Double panel = new Path2D.Double();
        panel.moveTo(TeleLayout.SP_X[0], TeleLayout.SP_Y[0]);
        for (int k = 1; k < TeleLayout.SP_X.length; k++) {
            panel.lineTo(TeleLayout.SP_X[k], TeleLayout.SP_Y[k]);
        }
        panel.closePath();
        for (int k = 1; k <= 6; k++) {
            g.setComposite(AlphaComposite.SrcOver.derive(0.10f));
            g.setColor(Color.BLACK);
            g.fill(java.awt.geom.AffineTransform.getTranslateInstance(0, k * 1.5).createTransformedShape(panel));
        }
        for (float w : new float[] {16f, 10f, 5f}) {
            g.setComposite(AlphaComposite.SrcOver.derive(0.10f));
            g.setColor(COBALT);
            g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(panel);
        }
        g.setComposite(AlphaComposite.SrcOver.derive(0.92f));
        g.setPaint(new java.awt.GradientPaint(0, 100, new Color(0x0A1230), 0, 440, new Color(0x03050C)));
        g.fill(panel);
        Shape oldClip = g.getClip();
        g.setClip(panel);
        for (int row = 0, y = 90; y < 460; y += 44, row++) {
            for (int x = 20 + (row % 2) * 44; x < 540; x += 88) {
                Path2D.Double pl = plus(x, y, 12);
                g.setStroke(new BasicStroke(1.2f));
                g.setComposite(AlphaComposite.SrcOver.derive(0.10f));
                g.setColor(Color.WHITE);
                g.draw(java.awt.geom.AffineTransform.getTranslateInstance(-1, -1).createTransformedShape(pl));
                g.setComposite(AlphaComposite.SrcOver.derive(0.45f));
                g.setColor(Color.BLACK);
                g.draw(java.awt.geom.AffineTransform.getTranslateInstance(1, 1).createTransformedShape(pl));
            }
        }
        for (int r = 24, k = 0; r < 220; r += 16, k++) {
            g.setComposite(AlphaComposite.SrcOver.derive(k % 4 == 0 ? 0.14f : 0.07f));
            g.setColor(CYAN);
            g.setStroke(new BasicStroke(k % 4 == 0 ? 2.2f : 1f));
            g.draw(new Ellipse2D.Double(132 - r, 275 - r, 2 * r, 2 * r));
        }
        g.setComposite(AlphaComposite.SrcOver);
        g.setPaint(new java.awt.GradientPaint(0, 106, new Color(255, 255, 255, 26), 0, 140, new Color(255, 255, 255, 0)));
        g.fill(new Rectangle2D.Double(0, 84, 560, 56));
        for (int i = 0; i < 4; i++) {
            g.setComposite(AlphaComposite.SrcOver.derive(0.06f));
            g.setColor(COBALT);
            g.setStroke(new BasicStroke(10 + i * 10));
            g.draw(panel);
        }
        g.setClip(oldClip);
        g.setComposite(AlphaComposite.SrcOver.derive(0.75f));
        g.setColor(new Color(0x3E8BFF));
        g.setStroke(new BasicStroke(1.4f));
        g.draw(panel);
        g.setComposite(AlphaComposite.SrcOver.derive(0.9f));
        g.setColor(CYAN);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.draw(line(150, 93, 176, 84, 194, 98, 215, 106, 244, 106)); // the notch's corner (the Sport guide)
        g.draw(line(64, 391, 74, 409, 96, 440, 124, 452, 156, 455));
        g.setStroke(new BasicStroke(2.4f));
        g.setColor(new Color(0xFF5FA8));
        g.draw(line(25, 190, 33, 275, 58, 360)); // along the bezel, 7 px in
        g.setColor(CYAN);
        g.draw(new java.awt.geom.Arc2D.Double(720 - 265, 309 - 265, 530, 530, 148, 36, java.awt.geom.Arc2D.OPEN)); // along the dial
        g.setComposite(AlphaComposite.SrcOver);
        // (the two chibis are pictures of their own since 2026-11-25, sportChibi: the layout shows
        // them on the live page only)
        g.dispose();
    }

    /** The rev strip's outline grown by pad: its segments as quads plus round corners. */
    private static Area stripShape(double pad) {
        Area a = new Area();
        double len = TeleLayout.tpLen();
        double[] o = new double[4];
        double[] q = new double[4];
        for (double s = 0; s < len; s += 3) {
            double s1 = Math.min(len, s + 3.5);
            TeleLayout.tp(s, o);
            TeleLayout.tp(s1, q);
            double h0 = TeleLayout.half(s) + pad;
            double h1 = TeleLayout.half(s1) + pad;
            a.add(new Area(path(o[0] + o[2] * h0, o[1] + o[3] * h0, q[0] + q[2] * h1, q[1] + q[3] * h1,
                q[0] - q[2] * h1, q[1] - q[3] * h1, o[0] - o[2] * h0, o[1] - o[3] * h0)));
        }
        for (int i = 1; i <= 2; i++) {
            double r = TeleLayout.HALF_MAX + pad;
            a.add(new Area(new Ellipse2D.Double(TeleLayout.TP_X[i] - r, TeleLayout.TP_Y[i] - r, 2 * r, 2 * r)));
        }
        return a;
    }

    /**
     * The car's theme pictures (tele17): theme-classic.rgba and theme-sport.rgba, 1440 x 540 RGBA
     * (opaque), plus PNG previews, into DIR. Portraits as given (default colour).
     *   java -cp out local.tt.Theme --export DIR [colour|tint|none]
     * Copy both .rgba files to the car's /tmp, then: sh tt.sh tele theme install; sh tt.sh tele theme on
     */
    private static void export(String[] a) throws Exception {
        java.io.File dir = new java.io.File(a.length > 1 ? a[1] : "theme-out");
        dir.mkdirs();
        String pm = a.length > 2 ? a[2] : "colour";
        portraits = pm.equals("none") ? 0 : pm.equals("tint") ? 1 : 2;
        String[] names = {"classic", "sport"};
        for (int v = 0; v < 2; v++) {
            BufferedImage img = paint(v);
            byte[] b = new byte[W * H * 4];
            int[] px = img.getRGB(0, 0, W, H, null, 0, W);
            for (int i = 0; i < px.length; i++) {
                b[4 * i] = (byte) (px[i] >> 16);
                b[4 * i + 1] = (byte) (px[i] >> 8);
                b[4 * i + 2] = (byte) px[i];
                b[4 * i + 3] = (byte) 255;
            }
            java.io.File f = new java.io.File(dir, "theme-" + names[v] + ".rgba");
            java.io.FileOutputStream o = new java.io.FileOutputStream(f);
            o.write(b);
            o.close();
            javax.imageio.ImageIO.write(img, "png", new java.io.File(dir, "theme-" + names[v] + ".png"));
            System.out.println("wrote " + f.getPath() + " (" + b.length + " bytes)");
        }
        String[] cn = {"up", "side"};
        for (int k = 0; k < 2; k++) {
            BufferedImage c = carPicture(k == 1);
            if (c == null) {
                System.out.println("no skins/car-top.png: no car pictures");
                break;
            }
            int[] px = c.getRGB(0, 0, c.getWidth(), c.getHeight(), null, 0, c.getWidth());
            byte[] b = new byte[px.length * 4];
            for (int i = 0; i < px.length; i++) {
                b[4 * i] = (byte) (px[i] >> 16);
                b[4 * i + 1] = (byte) (px[i] >> 8);
                b[4 * i + 2] = (byte) px[i];
                b[4 * i + 3] = (byte) (px[i] >>> 24);
            }
            java.io.File f = new java.io.File(dir, "hx-car-" + cn[k] + ".rgba");
            java.io.FileOutputStream o = new java.io.FileOutputStream(f);
            o.write(b);
            o.close();
            System.out.println("wrote " + f.getPath() + " (" + b.length + " bytes)");
        }
        // the Sport chibis, bloom and emblem (2026-11-25)
        String[] spn = {"sp-chibi-white", "sp-chibi-doll", "sp-bloom", "sp-emblem", "sp-bloom-hot"};
        for (int k = 0; k < 5; k++) {
            BufferedImage c = k < 2 ? sportChibi(k == 0) : k == 2 ? sportBloom() : k == 3 ? sportEmblem() : sportBloomHot();
            if (c == null) {
                continue;
            }
            int[] px = c.getRGB(0, 0, c.getWidth(), c.getHeight(), null, 0, c.getWidth());
            byte[] b = new byte[px.length * 4];
            for (int i = 0; i < px.length; i++) {
                b[4 * i] = (byte) (px[i] >> 16);
                b[4 * i + 1] = (byte) (px[i] >> 8);
                b[4 * i + 2] = (byte) px[i];
                b[4 * i + 3] = (byte) (px[i] >>> 24);
            }
            java.io.File f = new java.io.File(dir, spn[k] + ".rgba");
            java.io.FileOutputStream o = new java.io.FileOutputStream(f);
            o.write(b);
            o.close();
            System.out.println("wrote " + f.getPath() + " (" + b.length + " bytes)");
        }
        // the notch glow (2026-11-25)
        {
            BufferedImage c = notchGlow();
            int[] px = c.getRGB(0, 0, c.getWidth(), c.getHeight(), null, 0, c.getWidth());
            byte[] b = new byte[px.length * 4];
            for (int i = 0; i < px.length; i++) {
                b[4 * i] = (byte) (px[i] >> 16);
                b[4 * i + 1] = (byte) (px[i] >> 8);
                b[4 * i + 2] = (byte) px[i];
                b[4 * i + 3] = (byte) (px[i] >>> 24);
            }
            java.io.File f = new java.io.File(dir, "notch-glow.rgba");
            java.io.FileOutputStream o = new java.io.FileOutputStream(f);
            o.write(b);
            o.close();
            System.out.println("wrote " + f.getPath() + " (" + b.length + " bytes)");
        }
        // the painted strip's pictures (tele21: the head unit took 4.7 s to paint them)
        TeleStrip st = TeleStrip.paint(new TeleLayout());
        int[][] sp = {st.track, st.lit, st.litRed};
        String[] sn = {"strip-track", "strip-lit", "strip-red"};
        for (int k = 0; k < 3; k++) {
            byte[] b = new byte[sp[k].length * 4];
            for (int i = 0; i < sp[k].length; i++) {
                int px = sp[k][i];
                b[4 * i] = (byte) (px >> 16);
                b[4 * i + 1] = (byte) (px >> 8);
                b[4 * i + 2] = (byte) px;
                b[4 * i + 3] = (byte) (px >>> 24);
            }
            java.io.File f = new java.io.File(dir, sn[k] + ".rgba");
            java.io.FileOutputStream o = new java.io.FileOutputStream(f);
            o.write(b);
            o.close();
            System.out.println("wrote " + f.getPath() + " (" + b.length + " bytes)");
        }
    }

    /**
     * Sport's rpm bloom (2026-11-25, after the Viper's tach): a soft deep-red disc, SP_BLOOM_S
     * square, hot in the middle and fading out; the layout scales it with rpm.
     */
    static BufferedImage sportBloom() {
        int n = TeleLayout.SP_BLOOM_S;
        BufferedImage im = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.setPaint(new RadialGradientPaint(n / 2f, n / 2f, n / 2f, new float[] {0f, 0.35f, 0.7f, 1f},
            new Color[] {new Color(128, 10, 26, 235), new Color(104, 6, 24, 200), new Color(70, 2, 20, 110),
                new Color(40, 0, 14, 0)}));
        g.fillRect(0, 0, n, n);
        g.dispose();
        return im;
    }

    /** The bright layer over the dark bloom (TeleLayout.SP_BLOOM_HOT): bright red, hot in the middle. */
    static BufferedImage sportBloomHot() {
        int n = TeleLayout.SP_BLOOM_S;
        BufferedImage im = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.setPaint(new RadialGradientPaint(n / 2f, n / 2f, n / 2f, new float[] {0f, 0.4f, 0.75f, 1f},
            new Color[] {new Color(255, 70, 60, 230), new Color(236, 36, 46, 190), new Color(200, 20, 40, 90),
                new Color(160, 10, 30, 0)}));
        g.fillRect(0, 0, n, n);
        g.dispose();
        return im;
    }

    /**
     * The emblem the bloom reveals: the user's line art (skins/emblem.png) as dark wine lines,
     * SP_EMBLEM_S square, soft at the edges. Nearly invisible on the navy panel, dark strokes on the
     * red bloom, as the Viper's snake on its tach.
     */
    static BufferedImage sportEmblem() {
        BufferedImage src;
        try {
            src = javax.imageio.ImageIO.read(new java.io.File("skins", "emblem.png"));
        } catch (Exception e) {
            return null;
        }
        if (src == null) {
            return null;
        }
        int n = TeleLayout.SP_EMBLEM_S;
        BufferedImage sc = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sc.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, n, n, null);
        g.dispose();
        BufferedImage im = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int c = sc.getRGB(x, y);
                double rg = (((c >> 16) & 0xFF) + ((c >> 8) & 0xFF)) / 510.0;
                double ink = Math.max(0, Math.min(1, (1 - rg) * 1.9));
                double d = Math.hypot(x - n / 2.0, y - n / 2.0) / (n / 2.0);
                double fade = 1 - smooth((d - 0.62) / 0.38);
                int a = (int) Math.round(255 * 0.85 * ink * fade);
                im.setRGB(x, y, (a << 24) | (40 << 16) | (2 << 8) | 14);
            }
        }
        return im;
    }

    /**
     * Sport's chibis as pictures (TeleLayout.SP_WHITE / SP_DOLL, at their fixed size): the
     * white-haired one top right, the doll bottom right, standing on the bottom edge.
     */
    static BufferedImage sportChibi(boolean white) {
        int w = white ? TeleLayout.SP_WHITE_W : TeleLayout.SP_DOLL_W;
        int h = white ? TeleLayout.SP_WHITE_H : TeleLayout.SP_DOLL_H;
        BufferedImage c = white
            ? Cutout.sticker(new java.io.File("skins", "chibi-white.jpg"), 72, new Color(0x2FD4FF), 12)
            : Cutout.sticker(new java.io.File("skins", "chibi-doll.jpg"), 62, new Color(0xFF5FA8), 12);
        if (c == null) {
            return null;
        }
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.drawImage(c, (w - c.getWidth()) / 2, h - c.getHeight(), null);
        g.dispose();
        return im;
    }

    /**
     * The FL5's centre glow for Classic's gear notch (user 2026-11-25), TeleLayout.NOTCH_GLOW_W x _H at
     * the notch's top left (NOTCH_X0, LINE_Y): a warm red core behind the gear fading out to the
     * notch's sides, fine rays fanning up from below, a hot rim under the red line. Not
     * premultiplied (the head unit premultiplies on reading).
     */
    static BufferedImage notchGlow() {
        int w = TeleLayout.NOTCH_GLOW_W;
        int h = TeleLayout.NOTCH_GLOW_H;
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(-596, -226);
        Path2D.Double notch = path(596, 226, 844, 226, 814, 350, 626, 350);
        g.clip(notch);
        g.setPaint(new RadialGradientPaint(new java.awt.geom.Point2D.Float(720f, 292f), 150f,
            new java.awt.geom.Point2D.Float(720f, 292f), new float[] {0f, 0.35f, 0.7f, 1f},
            new Color[] {new Color(255, 120, 70, 245), new Color(240, 40, 40, 185), new Color(160, 12, 36, 80),
                new Color(80, 0, 30, 0)},
            java.awt.MultipleGradientPaint.CycleMethod.NO_CYCLE, java.awt.MultipleGradientPaint.ColorSpaceType.SRGB,
            new java.awt.geom.AffineTransform(1.25, 0, 0, 0.85, -180, 44)));
        g.fill(notch);
        g.setStroke(new BasicStroke(1f));
        for (double t = -64; t <= 64; t += 2.4) {
            double r = Math.toRadians(t);
            g.setComposite(AlphaComposite.SrcOver.derive(0.10f));
            g.setColor(new Color(0xFFC0A0));
            g.draw(new java.awt.geom.Line2D.Double(720 + 40 * Math.sin(r), 420 - 40 * Math.cos(r),
                720 + 260 * Math.sin(r), 420 - 260 * Math.cos(r)));
        }
        g.setComposite(AlphaComposite.SrcOver);
        g.setPaint(new java.awt.GradientPaint(0, 226, new Color(255, 120, 70, 120), 0, 246, new Color(255, 60, 40, 0)));
        g.fill(new Rectangle2D.Double(596, 226, 248, 20));
        g.dispose();
        return im;
    }

    /**
     * The Haldex page's car picture (skins/car-top.png, nose up) at a size: upright, or turned so
     * the nose points right. Null when the file is missing.
     */
    static BufferedImage carPicture(boolean side) {
        BufferedImage src;
        try {
            src = javax.imageio.ImageIO.read(new java.io.File("skins", "car-top.png"));
        } catch (Exception e) {
            return null;
        }
        if (src == null) {
            return null;
        }
        int w = side ? TeleLayout.CAR_SIDE_W : TeleLayout.CAR_UP_W;
        int h = side ? TeleLayout.CAR_SIDE_H : TeleLayout.CAR_UP_H;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        if (side) {
            // nose right: turn the upright picture 90 degrees clockwise
            g.translate(w, 0);
            g.rotate(Math.PI / 2);
            g.drawImage(src, 0, 0, h, w, null);
        } else {
            g.drawImage(src, 0, 0, w, h, null);
        }
        g.dispose();
        // 2026-11-25: the livery at 70 % so the drivetrain reads over it (an x-ray look)
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = out.getRGB(x, y);
                int r = (int) (((c >> 16) & 0xFF) * 0.7);
                int gg = (int) (((c >> 8) & 0xFF) * 0.7);
                int bb = (int) ((c & 0xFF) * 0.7);
                out.setRGB(x, y, (c & 0xFF000000) | r << 16 | gg << 8 | bb);
            }
        }
        return out;
    }

    /** 0.12 behind the gauges (strip, notch, value rows), up to 1 in the pockets and edges. */
    private static double mask(int view, int x, int y) {
        boolean sp = view == TeleLayout.VIEW_SMALL;
        double dx = Math.max(0, Math.max((sp ? 40 : 330) - x, x - (sp ? 450 : 1090))) / 90.0;
        double dy = Math.max(0, Math.max((sp ? 100 : 118) - y, y - (sp ? 432 : 425))) / 40.0;
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

    /** An arc of radius r from angle start over extent degrees (counter-clockwise from the right). */
    private static void arcSpan(Graphics2D g, double cx, double cy, double r, float w, Color c, float a, double start,
                                double extent) {
        g.setComposite(AlphaComposite.SrcOver.derive(a));
        g.setColor(c);
        g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, start, extent, java.awt.geom.Arc2D.OPEN));
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

    private static Path2D.Double line(double... xy) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) {
            p.lineTo(xy[i], xy[i + 1]);
        }
        return p;
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
