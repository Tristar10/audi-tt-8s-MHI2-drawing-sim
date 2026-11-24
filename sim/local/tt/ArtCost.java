package local.tt;

/**
 * How much picture work each view asks of the head unit when it is built from scratch: slots by
 * kind, and the pixels TeleView rasterizes for quads (4 x 4 samples each) and icons, the
 * work done on the tick thread before anything shows.
 *   java -cp out local.tt.ArtCost
 */
public final class ArtCost {
    public static void main(String[] a) {
        for (int view = 0; view < 2; view++) {
            TeleLayout t = new TeleLayout();
            t.link = true;
            t.val[TeleLayout.CH_RPM] = 5600000;
            t.has[TeleLayout.CH_RPM] = true;
            t.now = 0;
            t.build(view, TeleLayout.PAGE_LIVE);
            int[] n = new int[5];
            long quadPx = 0;
            long iconPx = 0;
            int distinctQuads = 0;
            for (int i = 0; i < t.count; i++) {
                n[t.kind[i]]++;
                if (t.kind[i] == TeleLayout.QUAD) {
                    int sc = t.w[i] * t.h[i] > 40000 ? 2 : 1;
                    quadPx += (long) ((t.w[i] + sc - 1) / sc) * ((t.h[i] + sc - 1) / sc);
                    distinctQuads++;
                } else if (t.kind[i] == TeleLayout.ICON) {
                    iconPx += (long) t.w[i] * t.h[i];
                }
            }
            System.out.println((view == 0 ? "Classic" : "Sport") + ": " + t.count + " slots (box " + n[0] + ", text "
                + n[1] + ", quad " + n[2] + ", icon " + n[3] + ", strip " + n[4] + "), quad pixels " + quadPx
                + " (x16 samples = " + quadPx * 16 + "), icon pixels " + iconPx);
        }
    }
}
