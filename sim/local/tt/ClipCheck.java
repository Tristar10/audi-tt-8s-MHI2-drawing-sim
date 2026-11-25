package local.tt;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Clip checker: builds every view and page in several states and tests each element against the
 * part of the screen the cluster lets through (TeleSim.visible). Lists every element that is cut,
 * with how much. Exit code 1 when anything is cut.
 *
 *   java -cp out local.tt.ClipCheck [margin px, default 2]
 *
 * Skipped on purpose: full-screen backgrounds and tints (bigger than 100 000 px), the Sport redline tint and
 * warning frame (drawn along the edge), and the rev strip's ends that run under the frame.
 * Text widths come from the PC font (the car's font is close at the size drawn, not identical).
 */
public final class ClipCheck {
    public static void main(String[] a) {
        double margin = a.length > 0 ? Double.parseDouble(a[0]) : 2;
        String[] viewName = {"Classic", "Sport"};
        String[] pageName = {"live", "", "haldex"};
        int[] rpms = {900, 4500, 6600};
        int cut = 0;
        List<String> seen = new ArrayList<String>();
        for (int view = 0; view < 2; view++) {
            Area vis = TeleSim.visible(view);
            Area shrunk = shrink(vis, margin);
            for (int page : new int[] {TeleLayout.PAGE_LIVE, TeleLayout.PAGE_HALDEX}) {
                for (int pop = 0; pop < 2; pop++) {
                    for (int rpm : rpms) {
                        for (boolean painted : new boolean[] {false, true}) {
                            TeleLayout t = state(rpm, pop == 1, painted);
                            t.build(view, page);
                            for (int i = 1; i < t.count; i++) {
                                if (!t.vis[i])
                                    continue;
                                Area s = shape(t, i);
                                if (s == null)
                                    continue;
                                Rectangle2D b = s.getBounds2D();
                                if (b.getWidth() * b.getHeight() > 100000)
                                    continue; // backgrounds, tints
                                if (t.kind[i] == TeleLayout.IMAGE && (t.size[i] == TeleLayout.SP_BLOOM || t.size[i] == TeleLayout.SP_BLOOM_HOT
                                    || t.size[i] == TeleLayout.SP_EMBLEM))
                                    continue; // Sport bloom and emblem: soft-edged, fade out before the edge
                                if (t.rgba[i] == 0xFF3B3050 || t.rgba[i] == 0xFF3B3030
                                    || (view == 1 && t.kind[i] == TeleLayout.QUAD && t.rgba[i] == TeleLayout.RED && onOutline(t, i)))
                                    continue; // Sport redline tint and warning frame: drawn along the edge on purpose
                                Area out = new Area(s);
                                out.subtract(shrunk);
                                if (out.isEmpty())
                                    continue;
                                Rectangle2D o = out.getBounds2D();
                                if (o.getWidth() < 1.5 && o.getHeight() < 1.5)
                                    continue;
                                if (isStripEnd(t, i, view))
                                    continue;
                                String what = describe(t, i);
                                String line = String.format("%-7s %-6s%s %-34s at x %4.0f..%4.0f y %3.0f..%3.0f  cut %3.0f x %3.0f px",
                                    viewName[view], pageName[page], pop == 1 ? "+pop" : "    ", what, b.getMinX(),
                                    b.getMaxX(), b.getMinY(), b.getMaxY(), o.getWidth(), o.getHeight());
                                String key = viewName[view] + pageName[page] + pop + what;
                                if (!seen.contains(key)) {
                                    seen.add(key);
                                    System.out.println(line);
                                    cut++;
                                }
                            }
                        }
                    }
                }
            }
        }
        System.out.println(cut == 0 ? "No element is cut (margin " + margin + " px)."
            : cut + " elements cut (margin " + margin + " px).");
        System.exit(cut == 0 ? 0 : 1);
    }

    private static TeleLayout state(int rpm, boolean pop, boolean painted) {
        TeleLayout t = new TeleLayout();
        t.link = true;
        t.now = 0; // blink on
        t.rateHz = 20;
        set(t, TeleLayout.CH_RPM, rpm);
        set(t, TeleLayout.CH_GEAR, 3);
        set(t, TeleLayout.CH_PEDAL, 88);
        set(t, TeleLayout.CH_BRAKE, 40);
        set(t, TeleLayout.CH_BOOST, 120);
        set(t, TeleLayout.CH_HX_TARGET, 100);
        set(t, TeleLayout.CH_HX_ACTUAL, 100);
        set(t, TeleLayout.CH_COOLANT, 105);
        set(t, TeleLayout.CH_OIL, 135);
        set(t, TeleLayout.CH_IAT, 60);
        set(t, TeleLayout.CH_DSG, 115);
        set(t, TeleLayout.CH_CLUTCH, 118);
        set(t, TeleLayout.CH_STEER, -540);
        set(t, TeleLayout.CH_MODE, 9);
        set(t, TeleLayout.CH_LOCK_SET, 255);
        set(t, TeleLayout.CH_PRESET, 2);
        String[] names = {"Street", "Autocross", "Launch", "Wet", "FWD"};
        int[] modes = {0, 9, 2, 9, 1};
        for (int i = 0; i < names.length; i++) {
            t.catId[i] = i + 1;
            t.catName[i] = names[i];
            t.catMode[i] = modes[i];
        }
        t.catN = names.length;
        t.paintedStrip = painted;
        t.carArt = true;
        t.uiOpen = pop;
        t.uiMsg = pop ? "PRESET AUTOCROSS ON" : null;
        t.uiMsgRgba = TeleLayout.ACTIVE;
        return t;
    }

    private static void set(TeleLayout t, int ch, double v) {
        t.val[ch] = (int) Math.round(v * 1000);
        t.has[ch] = true;
    }

    /** The element's outline on the screen (turned when it is turned), or null to skip. */
    private static Area shape(TeleLayout t, int i) {
        int k = t.kind[i];
        Path2D.Double p;
        if (k == TeleLayout.QUAD) {
            p = new Path2D.Double();
            p.moveTo(t.qx[4 * i], t.qy[4 * i]);
            for (int j = 1; j < 4; j++)
                p.lineTo(t.qx[4 * i + j], t.qy[4 * i + j]);
            p.closePath();
            return new Area(p);
        }
        Rectangle2D r;
        if (k == TeleLayout.TEXT) {
            if (t.text[i] == null || t.text[i].length() == 0)
                return null;
            Font f = new Font(Chrome.FAMILY, t.bold[i] ? Font.BOLD : Font.PLAIN, t.size[i]);
            double w = f.getStringBounds(t.text[i], new FontRenderContext(null, true, true)).getWidth();
            double x = t.x[i] - (t.align[i] == TeleLayout.CENTER ? w / 2 : t.align[i] == TeleLayout.RIGHT ? w : 0);
            // cap height to baseline: the visible ink of upper-case text and digits
            r = new Rectangle2D.Double(x, t.y[i] + 0.12 * t.size[i], w, 0.72 * t.size[i]);
        } else if (k == TeleLayout.BOX && t.rot[i] != 0) {
            r = new Rectangle2D.Double(t.x[i], t.y[i] - t.h[i] / 2.0, t.w[i], t.h[i]);
        } else {
            if (t.w[i] <= 0 || t.h[i] <= 0)
                return null;
            r = new Rectangle2D.Double(t.x[i], t.y[i], t.w[i], t.h[i]);
        }
        Area s = new Area(r);
        if (t.rot[i] != 0)
            s.transform(AffineTransform.getRotateInstance(Math.toRadians(t.rot[i]), t.x[i], t.y[i]));
        return s;
    }

    /** The rev strip's pieces and pictures run under the frame at both ends by design. */
    private static boolean isStripEnd(TeleLayout t, int i, int view) {
        if (view != TeleLayout.VIEW_LARGE)
            return false;
        if (t.kind[i] == TeleLayout.STRIP)
            return true;
        Area s = shape(t, i);
        Rectangle2D b = s.getBounds2D();
        return (b.getMinX() < 60 || b.getMaxX() > 1380) && b.getMinY() > 120 && b.getMaxY() < 300
            && t.kind[i] != TeleLayout.TEXT && t.kind[i] != TeleLayout.ICON;
    }

    /** A quad whose corners all lie within 6 px of the Sport outline (the warning frame). */
    private static boolean onOutline(TeleLayout t, int i) {
        int[] xs = TeleLayout.SP_X;
        int[] ys = TeleLayout.SP_Y;
        for (int j = 0; j < 4; j++) {
            double px = t.qx[4 * i + j];
            double py = t.qy[4 * i + j];
            double best = 1e9;
            for (int k = 0; k < xs.length; k++) {
                int m = (k + 1) % xs.length;
                best = Math.min(best, java.awt.geom.Line2D.ptSegDist(xs[k], ys[k], xs[m], ys[m], px, py));
            }
            if (best > 12)
                return false;
        }
        return true;
    }

        private static String describe(TeleLayout t, int i) {
        String[] kinds = {"box", "text", "quad", "icon", "strip", "image"};
        String s = kinds[t.kind[i]];
        if (t.kind[i] == TeleLayout.TEXT)
            s += " \"" + t.text[i] + "\"";
        if (t.kind[i] == TeleLayout.ICON)
            s += " #" + t.size[i];
        return s;
    }

    /** The visible area shrunk by m px: an element must keep that far inside. */
    private static Area shrink(Area a, double m) {
        if (m <= 0)
            return a;
        java.awt.BasicStroke st = new java.awt.BasicStroke((float) (2 * m));
        Area edge = new Area(st.createStrokedShape(a));
        Area r = new Area(a);
        r.subtract(edge);
        return r;
    }
}
