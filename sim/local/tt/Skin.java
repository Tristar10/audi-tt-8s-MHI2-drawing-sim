package local.tt;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * A background built from pieces of a real cockpit picture: each layer cuts a rectangle out of the
 * picture, scales it to where that part of the cockpit sits on the car's screen, sharpens it a
 * little (the pictures are small) and fades its edges into black (a circle for dials, a rectangle
 * for bars). Pictures are not part of this repository: put them in skins/ next to their .skin file.
 *
 * skins/classic.skin, skins/sport.skin, one instruction per line:
 *   image FILE                                    picture, relative to the .skin file
 *   black N                                       levels below N become black (default 18)
 *   patch X Y W H DX DY                           paint over a box with the picture DX, DY away
 *                                                 (hides a manual's callout marker)
 *   layer SX SY SW SH  DX DY DW DH  circle|rect FEATHER
 */
final class Skin {
    private final List<BufferedImage> pics = new ArrayList<BufferedImage>();
    private final List<int[]> at = new ArrayList<int[]>();

    private Skin() {
    }

    /** Null when the file or its picture is missing. */
    static Skin load(File spec) {
        if (!spec.isFile()) {
            return null;
        }
        try {
            Skin s = new Skin();
            BufferedImage src = null;
            int blackAt = 18;
            BufferedReader r = new BufferedReader(new FileReader(spec));
            try {
                for (String l = r.readLine(); l != null; l = r.readLine()) {
                    String[] w = l.trim().split("\\s+");
                    if (w.length == 0 || w[0].length() == 0 || w[0].startsWith("#")) {
                        continue;
                    }
                    if (w[0].equals("image")) {
                        BufferedImage in = ImageIO.read(new File(spec.getParentFile(), w[1]));
                        src = new BufferedImage(in.getWidth(), in.getHeight(), BufferedImage.TYPE_INT_RGB);
                        src.getGraphics().drawImage(in, 0, 0, null);
                    } else if (w[0].equals("black")) {
                        blackAt = Integer.parseInt(w[1]);
                    } else if (w[0].equals("patch") && src != null) {
                        int x0 = Integer.parseInt(w[1]);
                        int y0 = Integer.parseInt(w[2]);
                        int pw = Integer.parseInt(w[3]);
                        int ph = Integer.parseInt(w[4]);
                        int dx = Integer.parseInt(w[5]);
                        int dy = Integer.parseInt(w[6]);
                        int[] from = src.getRGB(x0 + dx, y0 + dy, pw, ph, null, 0, pw);
                        src.setRGB(x0, y0, pw, ph, from, 0, pw);
                    } else if (w[0].equals("layer") && src != null) {
                        int[] v = new int[8];
                        for (int i = 0; i < 8; i++) {
                            v[i] = Integer.parseInt(w[1 + i]);
                        }
                        s.pics.add(piece(src, v, w[9].equals("circle"), Float.parseFloat(w[10]), blackAt));
                        s.at.add(new int[] {v[4], v[5]});
                    }
                }
            } finally {
                r.close();
            }
            System.out.println("skin " + spec.getPath() + ": " + s.pics.size() + " layers");
            return s.pics.isEmpty() ? null : s;
        } catch (Exception e) {
            System.out.println("skin " + spec.getPath() + " failed: " + e);
            return null;
        }
    }

    void paint(Graphics2D g) {
        for (int i = 0; i < pics.size(); i++) {
            g.drawImage(pics.get(i), at.get(i)[0], at.get(i)[1], null);
        }
    }

    /** Pulls the picture's near-black (its dark grey background) down to the black around it. */
    private static int black(int rgb, int at) {
        int o = 0;
        for (int sh = 0; sh <= 16; sh += 8) {
            int c = (rgb >> sh) & 0xFF;
            c = Math.max(0, (c - at) * 255 / (255 - at));
            o |= c << sh;
        }
        return o;
    }

    private static BufferedImage piece(BufferedImage src, int[] v, boolean circle, float feather, int blackAt) {
        int dw = v[6];
        int dh = v[7];
        BufferedImage big = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = big.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, dw, dh, v[0], v[1], v[0] + v[2], v[1] + v[3], null);
        g.dispose();
        // mild unsharp: the pictures are scaled up about 2x
        float k = 0.35f;
        float[] m = {0, -k, 0, -k, 1 + 4 * k, -k, 0, -k, 0};
        BufferedImage sharp = new ConvolveOp(new Kernel(3, 3, m), ConvolveOp.EDGE_NO_OP, null).filter(big, null);
        BufferedImage out = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_ARGB);
        double cx = dw / 2.0;
        double cy = dh / 2.0;
        double rad = Math.min(dw, dh) / 2.0;
        for (int y = 0; y < dh; y++) {
            for (int x = 0; x < dw; x++) {
                double in;
                if (circle) {
                    in = rad - Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
                } else {
                    in = Math.min(Math.min(x + 0.5, dw - x - 0.5), Math.min(y + 0.5, dh - y - 0.5));
                }
                double a = Math.max(0, Math.min(1, in / feather));
                out.setRGB(x, y, ((int) Math.round(a * 255) << 24) | black(sharp.getRGB(x, y), blackAt));
            }
        }
        return out;
    }
}
