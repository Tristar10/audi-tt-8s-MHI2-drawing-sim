package local.tt;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/**
 * Chibi stickers: cuts a drawing on a plain white background out (flood fill from the edges stops
 * at the black outline, so white inside the character stays), un-mattes the soft edge, scales it,
 * and bakes a coloured glow and a drop shadow behind it.
 */
final class Cutout {
    private Cutout() {
    }

    /** Null when the file is missing. pad px of room around the character for glow and shadow. */
    static BufferedImage sticker(File f, int height, Color glow, int pad) {
        BufferedImage in;
        try {
            in = ImageIO.read(f);
        } catch (Exception e) {
            return null;
        }
        if (in == null) {
            return null;
        }
        int w = in.getWidth();
        int h = in.getHeight();
        int[] px = in.getRGB(0, 0, w, h, null, 0, w);
        boolean[] bg = new boolean[w * h];
        int[] q = new int[w * h];
        int qn = 0;
        for (int x = 0; x < w; x++) {
            qn = seed(px, bg, q, qn, x, 0, w);
            qn = seed(px, bg, q, qn, x, h - 1, w);
        }
        for (int y = 0; y < h; y++) {
            qn = seed(px, bg, q, qn, 0, y, w);
            qn = seed(px, bg, q, qn, w - 1, y, w);
        }
        for (int qi = 0; qi < qn; qi++) {
            int i = q[qi];
            int x = i % w;
            int y = i / w;
            if (x > 0) {
                qn = seed(px, bg, q, qn, x - 1, y, w);
            }
            if (x < w - 1) {
                qn = seed(px, bg, q, qn, x + 1, y, w);
            }
            if (y > 0) {
                qn = seed(px, bg, q, qn, x, y - 1, w);
            }
            if (y < h - 1) {
                qn = seed(px, bg, q, qn, x, y + 1, w);
            }
        }
        // alpha: background 0; within 3 px of it, from how white the pixel is (soft edge), colour
        // un-matted from white
        int minX = w;
        int minY = h;
        int maxX = -1;
        int maxY = -1;
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (bg[i]) {
                    continue;
                }
                int c = px[i];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                double a = 1;
                if (nearBg(bg, x, y, w, h, 3)) {
                    double white = Math.min(r, Math.min(g, b)) / 255.0;
                    a = Math.max(0, Math.min(1, (1 - white) / 0.55));
                    if (a > 0.01) {
                        r = clamp((r - (1 - a) * 255) / a);
                        g = clamp((g - (1 - a) * 255) / a);
                        b = clamp((b - (1 - a) * 255) / a);
                    }
                }
                int ai = (int) Math.round(a * 255);
                if (ai == 0) {
                    continue;
                }
                out[i] = (ai << 24) | (r << 16) | (g << 8) | b;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        if (maxX < 0) {
            return null;
        }
        int cw = maxX - minX + 1;
        int ch = maxY - minY + 1;
        BufferedImage cut = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
        cut.setRGB(0, 0, cw, ch, out, minY * w + minX, w);
        // scale (premultiplied while scaling, so edges do not go dark)
        int sw = Math.max(1, Math.round(cw * height / (float) ch));
        BufferedImage pre = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB_PRE);
        pre.createGraphics().drawImage(cut, 0, 0, null);
        BufferedImage small = new BufferedImage(sw, height, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D sg = small.createGraphics();
        sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        sg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        // two steps down for a smoother result
        BufferedImage mid = new BufferedImage(Math.max(sw, cw / 2), Math.max(height, ch / 2),
            BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D mg = mid.createGraphics();
        mg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        mg.drawImage(pre, 0, 0, mid.getWidth(), mid.getHeight(), null);
        mg.dispose();
        sg.drawImage(mid, 0, 0, sw, height, null);
        sg.dispose();
        // glow and shadow from the alpha
        int W = sw + 2 * pad;
        int H = height + 2 * pad;
        float[] m = new float[W * H];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < sw; x++) {
                m[(y + pad) * W + x + pad] = ((small.getRGB(x, y) >>> 24) & 0xFF) / 255f;
            }
        }
        float[] blur = m.clone();
        for (int k = 0; k < 3; k++) {
            blur = box(blur, W, H, Math.max(2, pad / 3));
        }
        BufferedImage res = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB_PRE);
        int[] rp = new int[W * H];
        for (int i = 0; i < rp.length; i++) {
            float sa = 0;
            int sx = (i % W) - 3;
            int sy = (i / W) - 4;
            if (sx >= 0 && sy >= 0) {
                sa = blur[sy * W + sx] * 0.55f; // shadow, offset down right
            }
            float ga = Math.min(1f, blur[i] * 1.6f) * 0.85f; // glow
            // shadow under glow: premultiplied over
            float r = glow.getRed() / 255f * ga;
            float g = glow.getGreen() / 255f * ga;
            float b = glow.getBlue() / 255f * ga;
            float a = ga + sa * (1 - ga);
            rp[i] = pack(a, r, g, b);
        }
        res.setRGB(0, 0, W, H, rp, 0, W);
        Graphics2D rg = res.createGraphics();
        rg.setComposite(AlphaComposite.SrcOver);
        rg.drawImage(small, pad, pad, null);
        rg.dispose();
        BufferedImage done = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        done.createGraphics().drawImage(res, 0, 0, null);
        return done;
    }

    private static int pack(float a, float r, float g, float b) {
        // TYPE_INT_ARGB_PRE setRGB takes non-premultiplied values: undo
        if (a <= 0.001f) {
            return 0;
        }
        return (Math.round(a * 255) << 24) | (Math.round(Math.min(1, r / a) * 255) << 16)
            | (Math.round(Math.min(1, g / a) * 255) << 8) | Math.round(Math.min(1, b / a) * 255);
    }

    private static float[] box(float[] s, int w, int h, int r) {
        float[] t = new float[s.length];
        for (int y = 0; y < h; y++) {
            float acc = 0;
            for (int x = -r; x < w; x++) {
                if (x + r < w) {
                    acc += s[y * w + x + r];
                }
                if (x - r - 1 >= 0) {
                    acc -= s[y * w + x - r - 1];
                }
                if (x >= 0) {
                    t[y * w + x] = acc / (2 * r + 1);
                }
            }
        }
        float[] u = new float[s.length];
        for (int x = 0; x < w; x++) {
            float acc = 0;
            for (int y = -r; y < h; y++) {
                if (y + r < h) {
                    acc += t[(y + r) * w + x];
                }
                if (y - r - 1 >= 0) {
                    acc -= t[(y - r - 1) * w + x];
                }
                if (y >= 0) {
                    u[y * w + x] = acc / (2 * r + 1);
                }
            }
        }
        return u;
    }

    private static int seed(int[] px, boolean[] bg, int[] q, int qn, int x, int y, int w) {
        int i = y * w + x;
        if (bg[i]) {
            return qn;
        }
        int c = px[i];
        int r = (c >> 16) & 0xFF;
        int g = (c >> 8) & 0xFF;
        int b = c & 0xFF;
        int mn = Math.min(r, Math.min(g, b));
        int mx = Math.max(r, Math.max(g, b));
        if (mn >= 228 && mx - mn < 24) {
            bg[i] = true;
            q[qn++] = i;
        }
        return qn;
    }

    private static boolean nearBg(boolean[] bg, int x, int y, int w, int h, int r) {
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                int xx = x + dx;
                int yy = y + dy;
                if (xx >= 0 && yy >= 0 && xx < w && yy < h && bg[yy * w + xx]) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    /** Test sheet: all four stickers on the theme blue. java -cp out local.tt.Cutout OUT.png */
    public static void main(String[] a) throws Exception {
        String[] f = {"chibi-pink.jpg", "chibi-white.jpg", "chibi-plush.jpg", "chibi-doll.jpg"};
        Color[] c = {new Color(0xFF5FA8), new Color(0x2FD4FF), new Color(0xFF5FA8), new Color(0xFF5FA8)};
        BufferedImage sheet = new BufferedImage(900, 260, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x0A1436));
        g.fillRect(0, 0, 900, 260);
        for (int i = 0; i < f.length; i++) {
            BufferedImage s = sticker(new File("skins", f[i]), 200, c[i], 18);
            if (s != null) {
                g.drawImage(s, 20 + i * 220, 10, null);
            }
        }
        g.dispose();
        ImageIO.write(sheet, "png", new File(a[0]));
    }
}
