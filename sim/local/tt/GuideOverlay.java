package local.tt;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/** Draws the visible-area mask over a render: outside dimmed, edge in yellow. java ... IN OUT [view] */
public final class GuideOverlay {
    public static void main(String[] a) throws Exception {
        BufferedImage im = ImageIO.read(new File(a[0]));
        int view = a.length > 2 ? Integer.parseInt(a[2]) : 0;
        Area vis = TeleSim.visible(view);
        Area out = new Area(new java.awt.Rectangle(0, 0, im.getWidth(), im.getHeight()));
        out.subtract(vis);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0, 0, 0, 150));
        g.fill(out);
        g.setColor(new Color(255, 227, 91));
        g.setStroke(new BasicStroke(2f));
        g.draw(vis);
        g.dispose();
        ImageIO.write(im, "png", new File(a[1]));
    }
}
