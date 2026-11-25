package local.tt;

import java.awt.geom.Path2D;

/** A minimal SVG path reader (M, L, H, V, C, Q, Z, absolute) for the clipping guides in ref/. */
final class SvgPath {
    private SvgPath() {
    }

    static Path2D.Double parse(String d) {
        Path2D.Double p = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        String[] t = d.replace(",", " ").replaceAll("([MLHVCQZmlhvcqz])", " $1 ").trim().split("\\s+");
        int i = 0;
        char cmd = 'M';
        double x = 0;
        double y = 0;
        while (i < t.length) {
            if (Character.isLetter(t[i].charAt(0))) {
                cmd = t[i++].charAt(0);
                if (cmd == 'Z' || cmd == 'z') {
                    p.closePath();
                    continue;
                }
            }
            switch (cmd) {
                case 'M':
                    x = n(t[i++]);
                    y = n(t[i++]);
                    p.moveTo(x, y);
                    cmd = 'L';
                    break;
                case 'L':
                    x = n(t[i++]);
                    y = n(t[i++]);
                    p.lineTo(x, y);
                    break;
                case 'H':
                    x = n(t[i++]);
                    p.lineTo(x, y);
                    break;
                case 'V':
                    y = n(t[i++]);
                    p.lineTo(x, y);
                    break;
                case 'C':
                    double c1x = n(t[i++]), c1y = n(t[i++]), c2x = n(t[i++]), c2y = n(t[i++]);
                    x = n(t[i++]);
                    y = n(t[i++]);
                    p.curveTo(c1x, c1y, c2x, c2y, x, y);
                    break;
                case 'Q':
                    double qx = n(t[i++]), qy = n(t[i++]);
                    x = n(t[i++]);
                    y = n(t[i++]);
                    p.quadTo(qx, qy, x, y);
                    break;
                default:
                    throw new IllegalArgumentException("unsupported SVG path command " + cmd);
            }
        }
        return p;
    }

    private static double n(String s) {
        return Double.parseDouble(s);
    }
}
