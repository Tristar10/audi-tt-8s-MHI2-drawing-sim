package local.tt;

import java.awt.BasicStroke;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Prints the outline of a view's visible area shrunk by INSET px, as integer points for the head
 * unit's layout (TeleLayout.SP_X/SP_Y). java -cp out local.tt.OutlinePts [view] [inset] [x0 x1] [tol]
 * Sport: view 1, inset 8, x 0..720 (the part left of the big rev counter).
 */
public final class OutlinePts {
    private OutlinePts() {
    }

    public static void main(String[] a) {
        int view = a.length > 0 ? Integer.parseInt(a[0]) : 1;
        double inset = a.length > 1 ? Double.parseDouble(a[1]) : 8;
        double x0 = a.length > 3 ? Double.parseDouble(a[2]) : 0;
        double x1 = a.length > 3 ? Double.parseDouble(a[3]) : 720;
        double tol = a.length > 4 ? Double.parseDouble(a[4]) : 1.5;
        Area v = TeleSim.visible(view);
        v.subtract(new Area(new BasicStroke((float) (2 * inset)).createStrokedShape(v)));
        v.intersect(new Area(new Rectangle2D.Double(x0, 0, x1 - x0, 540)));
        PathIterator it = v.getPathIterator(null, 0.3);
        List<double[]> pts = new ArrayList<>();
        double[] c = new double[6];
        while (!it.isDone()) {
            int t = it.currentSegment(c);
            if (t == PathIterator.SEG_MOVETO && !pts.isEmpty()) {
                break; // the first (largest, outer) piece only
            }
            if (t != PathIterator.SEG_CLOSE) {
                pts.add(new double[] {c[0], c[1]});
            }
            it.next();
        }
        List<double[]> s = new ArrayList<>();
        dp(pts, 0, pts.size() - 1, tol, s);
        s.add(pts.get(pts.size() - 1));
        StringBuilder xs = new StringBuilder();
        StringBuilder ys = new StringBuilder();
        int last = -1;
        for (double[] p : s) {
            int x = (int) Math.round(p[0]);
            int y = (int) Math.round(p[1]);
            if (last >= 0 && xs.length() > 0) {
                xs.append(", ");
                ys.append(", ");
            }
            xs.append(x);
            ys.append(y);
            last = 0;
        }
        System.out.println(s.size() + " points");
        System.out.println("{" + xs + "}");
        System.out.println("{" + ys + "}");
    }

    /** Douglas-Peucker: keeps the points needed to stay within tol px of the outline. */
    private static void dp(List<double[]> p, int i, int j, double tol, List<double[]> out) {
        double best = -1;
        int k = -1;
        double ax = p.get(i)[0], ay = p.get(i)[1], bx = p.get(j)[0], by = p.get(j)[1];
        double len = Math.hypot(bx - ax, by - ay);
        for (int m = i + 1; m < j; m++) {
            double px = p.get(m)[0], py = p.get(m)[1];
            double d = len < 1e-9 ? Math.hypot(px - ax, py - ay)
                : Math.abs((bx - ax) * (ay - py) - (ax - px) * (by - ay)) / len;
            if (d > best) {
                best = d;
                k = m;
            }
        }
        if (k >= 0 && best > tol) {
            dp(p, i, k, tol, out);
            dp(p, k, j, tol, out);
        } else {
            out.add(p.get(i));
        }
    }
}
