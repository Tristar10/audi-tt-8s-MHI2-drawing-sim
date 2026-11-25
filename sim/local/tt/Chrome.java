package local.tt;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RadialGradientPaint;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Arrays;
import java.util.List;

/**
 * The stock cockpit around our layer, drawn clean (as a screenshot of the cluster would show it)
 * instead of phone photos: in Classic the small rev counter and speedometer, the tab bar and the
 * bottom bar; in Sport the big rev counter, the status lines and the on-board computer card.
 * Positions follow the visible-area masks (TeleSim.visible), so our layer meets them as in the car.
 * The needles move with the simulated rpm and speed. Our own drawing, approximate, no stock artwork.
 */
final class Chrome {
    static final String FAMILY = family();

    private static final Color WHITE = new Color(0xF2F5F7);
    private static final Color GREY = new Color(0x8A949E);
    private static final Color DIM = new Color(0x3A424B);
    private static final Color RED = new Color(0xE8261C);

    private Chrome() {
    }

    /** Bahnschrift (Windows) or DIN Alternate (macOS): close to the cockpit's Audi Type. */
    private static String family() {
        List<String> have = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getAvailableFontFamilyNames());
        String[] want = {"Bahnschrift", "DIN Alternate", "DIN Next LT Pro", "Roboto Condensed", "Arial Narrow"};
        for (String w : want) {
            if (have.contains(w)) {
                return w;
            }
        }
        return Font.SANS_SERIF;
    }

    static Font font(boolean bold, float size) {
        return new Font(FAMILY, bold ? Font.BOLD : Font.PLAIN, 10).deriveFont(size);
    }

    // ---------------------------------------------------------------- Classic (map tab)

    static void classic(Graphics2D g, int rpm, int mph) {
        backdrop(g);
        smallDial(g, 235, 389, 116, rpm / 8000.0, true, "D", "READY");
        smallDial(g, 1205, 389, 116, mph / 160.0, false, Integer.toString(mph), "MPH");
        tabBar(g);
        bottomBar(g, 720);
    }

    /** Black, as our layer's own background: no seam where the two meet (as in the car). */
    private static void backdrop(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 1440, 540);
    }

    /** Small fuel pump (the font has no such symbol): body, window, hose. */
    private static float fuel(Graphics2D g, float x, int y) {
        g.setColor(WHITE);
        g.fillRect((int) x, y - 18, 12, 18);
        g.setColor(Color.BLACK);
        g.fillRect((int) x + 3, y - 15, 6, 5);
        g.setColor(WHITE);
        g.setStroke(new BasicStroke(2f));
        g.draw(new Line2D.Float(x + 12, y - 13, x + 16, y - 9));
        g.draw(new Line2D.Float(x + 16, y - 9, x + 16, y - 2));
        return x + 24;
    }

    /** Warning triangle with an exclamation mark. */
    private static float warn(Graphics2D g, float x, int y) {
        g.setColor(WHITE);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Path2D.Float() {
            {
                moveTo(x + 11, y - 20);
                lineTo(x + 22, y);
                lineTo(x, y);
                closePath();
            }
        });
        g.fillRect((int) x + 10, y - 13, 2, 7);
        g.fillRect((int) x + 10, y - 4, 2, 2);
        return x + 36;
    }

    private static void smallDial(Graphics2D g, int cx, int cy, int r, double frac, boolean revs, String big,
                                  String small) {
        // rim: a thin bright ring over a darker bevel
        g.setPaint(new GradientPaint(cx, cy - r, new Color(0xC9CED3), cx, cy + r, new Color(0x3C4146)));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        g.setPaint(new RadialGradientPaint(cx, cy - r * 0.3f, r, new float[] {0f, 1f},
            new Color[] {new Color(0x22262B), new Color(0x08090B)}));
        g.fill(new Ellipse2D.Double(cx - r + 5, cy - r + 5, 2 * r - 10, 2 * r - 10));
        double a0 = 225;
        double sweep = 270;
        if (revs) {
            arcBand(g, cx, cy, r - 16, r - 8, a0 - sweep * 7 / 8.0, a0 - sweep, RED);
        }
        int majors = revs ? 8 : 8;
        int minors = revs ? 2 : 4;
        for (int i = 0; i <= majors * minors; i++) {
            double a = Math.toRadians(a0 - sweep * i / (majors * minors));
            boolean major = i % minors == 0;
            double ro = r - 8;
            double ri = ro - (major ? 14 : 7);
            g.setColor(revs && i >= 7 * minors ? WHITE : WHITE);
            g.setStroke(new BasicStroke(major ? 2.6f : 1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            g.draw(new Line2D.Double(cx + Math.cos(a) * ri, cy - Math.sin(a) * ri, cx + Math.cos(a) * ro,
                cy - Math.sin(a) * ro));
            if (major && revs && i > 0) {
                double rt = r - 38;
                centred(g, Integer.toString(i / minors), font(true, 24), WHITE, cx + Math.cos(a) * rt,
                    cy - Math.sin(a) * rt + 9);
            }
        }
        // needle: red, from the hub to the scale
        double an = Math.toRadians(a0 - sweep * Math.max(0, Math.min(1, frac)));
        g.setColor(new Color(0x55, 0x10, 0x0C, 120));
        g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        needle(g, cx, cy, an, r * 0.52, r - 14);
        g.setColor(RED);
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        needle(g, cx, cy, an, r * 0.52, r - 14);
        // hub
        g.setPaint(new RadialGradientPaint(cx, cy, r * 0.5f, new float[] {0f, 1f},
            new Color[] {new Color(0x15181C), new Color(0x050607)}));
        g.fill(new Ellipse2D.Double(cx - r * 0.5, cy - r * 0.5, r, r));
        g.setColor(DIM);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Ellipse2D.Double(cx - r * 0.5, cy - r * 0.5, r, r));
        centred(g, big, font(true, 50), WHITE, cx, cy + 16);
        centred(g, small, font(true, 13), GREY, cx, cy + 40);
        if (revs) {
            centred(g, "1/min x 1000", font(false, 12), GREY, cx, cy + r - 26);
        }
    }

    private static void needle(Graphics2D g, double cx, double cy, double a, double r0, double r1) {
        g.draw(new Line2D.Double(cx + Math.cos(a) * r0, cy - Math.sin(a) * r0, cx + Math.cos(a) * r1,
            cy - Math.sin(a) * r1));
    }

    /** Ring segment between radii, from angle a0 to a1 (degrees, counter-clockwise from the right). */
    private static void arcBand(Graphics2D g, double cx, double cy, double rIn, double rOut, double a0, double a1,
                                Color c) {
        double rm = (rIn + rOut) / 2;
        g.setColor(c);
        g.setStroke(new BasicStroke((float) (rOut - rIn), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.draw(new Arc2D.Double(cx - rm, cy - rm, 2 * rm, 2 * rm, a1, a0 - a1, Arc2D.OPEN));
    }

    /** The tab bar: status items between hairlines, the map tab outlined. */
    private static void tabBar(Graphics2D g) {
        int y = 76;
        Font f = font(false, 24);
        float x = 446;
        x = fuel(g, x, y);
        x = item(g, "345 mi", f, x, y);
        x = sep(g, x, y);
        x = warn(g, x, y);
        x = sep(g, x, y);
        x = item(g, "88.7", f, x, y);
        x = sep(g, x, y);
        x = item(g, "NE", f, x, y);
        // active tab: a rounded outline with a soft top
        float tx = x + 6;
        float tw = 140;
        g.setPaint(new GradientPaint(tx, 46, new Color(0x2A2F35), tx, 90, new Color(0x0C0E10)));
        g.fill(new RoundRectangle2D.Float(tx, 46, tw, 44, 12, 12));
        g.setColor(new Color(0xB8C0C8));
        g.setStroke(new BasicStroke(1.4f));
        g.draw(new RoundRectangle2D.Float(tx, 46, tw, 44, 12, 12));
        centred(g, "Autocross", font(true, 20), WHITE, tx + tw / 2, y - 1);
    }

    private static float item(Graphics2D g, String s, Font f, float x, int y) {
        g.setFont(f);
        g.setColor(WHITE);
        g.drawString(s, x, y);
        return x + g.getFontMetrics(f).stringWidth(s) + 14;
    }

    private static float sep(Graphics2D g, float x, int y) {
        g.setColor(DIM);
        g.fillRect((int) x, y - 22, 1, 26);
        return x + 15;
    }

    private static void bottomBar(Graphics2D g, int cx) {
        centred(g, "5:04 PM", font(false, 26), WHITE, cx, 512);
        centred(g, "+22.5°C", font(false, 22), WHITE, cx + 250, 512);
        centred(g, "D", font(true, 22), WHITE, cx - 250, 512);
    }

    // ---------------------------------------------------------------- Sport (small view)

    static void sport(Graphics2D g, int rpm, int mph) {
        backdrop(g);
        bigDial(g, 791, 288, 290, rpm / 8000.0, mph);
        // status lines and the on-board computer card on the right
        Font f = font(false, 24);
        float x = 300;
        x = item(g, "88.7", f, x, 58);
        x = sep(g, x, 58);
        item(g, "NE", f, x, 58);
        float r = 1110;
        r = fuel(g, r, 40);
        item(g, "390 mi", f, r, 40);
        g.setPaint(new GradientPaint(0, 128, new Color(0x1B1F24), 0, 340, new Color(0x0A0C0E)));
        g.fill(new RoundRectangle2D.Float(1104, 128, 330, 210, 14, 14));
        g.setColor(DIM);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new RoundRectangle2D.Float(1104, 128, 330, 210, 14, 14));
        g.setColor(RED);
        g.fillRect(1104, 128, 150, 3);
        g.setFont(font(false, 22));
        g.setColor(GREY);
        g.drawString("Short-term memory", 1122, 166);
        g.setColor(WHITE);
        g.setFont(font(false, 30));
        g.drawString("13 mi", 1122, 222);
        g.drawString("0:31 h", 1122, 270);
        g.drawString("28.4 mpg", 1122, 318);
        centred(g, "92°C", font(false, 24), WHITE, 320, 512);
        centred(g, "9:09 PM", font(false, 26), WHITE, 720, 512);
        centred(g, "+18.5°C", font(false, 24), WHITE, 950, 512);
    }

    private static void bigDial(Graphics2D g, int cx, int cy, int r, double frac, int mph) {
        g.setPaint(new GradientPaint(cx, cy - r, new Color(0xD5DADF), cx, cy + r, new Color(0x2E3237)));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        g.setPaint(new RadialGradientPaint(cx, cy - r * 0.25f, r, new float[] {0f, 1f},
            new Color[] {new Color(0x1E2226), new Color(0x060708)}));
        g.fill(new Ellipse2D.Double(cx - r + 8, cy - r + 8, 2 * r - 16, 2 * r - 16));
        double a0 = 220;
        double sweep = 260;
        arcBand(g, cx, cy, r - 70, r - 14, a0 - sweep * 7 / 8.0, a0 - sweep, RED);
        int minors = 10;
        for (int i = 0; i <= 8 * minors; i++) {
            double a = Math.toRadians(a0 - sweep * i / (8 * minors));
            boolean major = i % minors == 0;
            boolean half = i % (minors / 2) == 0;
            double ro = r - 14;
            double ri = ro - (major ? 34 : half ? 20 : 11);
            g.setColor(WHITE);
            g.setStroke(new BasicStroke(major ? 5f : half ? 3f : 1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            g.draw(new Line2D.Double(cx + Math.cos(a) * ri, cy - Math.sin(a) * ri, cx + Math.cos(a) * ro,
                cy - Math.sin(a) * ro));
            if (major && i > 0) {
                double rt = r - 92;
                centred(g, Integer.toString(i / minors), font(true, 72), WHITE, cx + Math.cos(a) * rt,
                    cy - Math.sin(a) * rt + 26);
            }
        }
        double an = Math.toRadians(a0 - sweep * Math.max(0, Math.min(1, frac)));
        g.setColor(new Color(0x55, 0x10, 0x0C, 120));
        g.setStroke(new BasicStroke(16f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        needle(g, cx, cy, an, r * 0.56, r - 20);
        g.setColor(RED);
        g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        needle(g, cx, cy, an, r * 0.56, r - 20);
        g.setPaint(new RadialGradientPaint(cx, cy, r * 0.54f, new float[] {0f, 1f},
            new Color[] {new Color(0x15181C), new Color(0x040506)}));
        g.fill(new Ellipse2D.Double(cx - r * 0.54, cy - r * 0.54, r * 1.08, r * 1.08));
        g.setColor(DIM);
        g.setStroke(new BasicStroke(1.6f));
        g.draw(new Ellipse2D.Double(cx - r * 0.54, cy - r * 0.54, r * 1.08, r * 1.08));
        centred(g, "1/min x 1000", font(false, 18), GREY, cx, cy - r * 0.38);
        centred(g, "MPH", font(true, 20), WHITE, cx, cy - 56);
        centred(g, Integer.toString(mph), font(true, 110), WHITE, cx, cy + 40);
        centred(g, "D", font(true, 52), WHITE, cx, cy + 104);
        centred(g, "COMFORT", font(true, 16), WHITE, cx, cy + 128);
    }

    private static void centred(Graphics2D g, String s, Font f, Color c, double cx, double baseline) {
        g.setFont(f);
        g.setColor(c);
        FontMetrics fm = g.getFontMetrics(f);
        g.drawString(s, (float) (cx - fm.stringWidth(s) / 2.0), (float) baseline);
    }
}
