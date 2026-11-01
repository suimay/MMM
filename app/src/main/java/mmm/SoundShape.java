package mmm;

import java.awt.*;
import java.awt.geom.*;
import mmm.Model.*;

/** Semantic sound glyph: attack body -> decaying tail, not a capsule or measured waveform. */
public final class SoundShape {
  private SoundShape() {}

  public static double thickness(int velocity) {
    return 8 + velocity * 0.19;
  }

  public static Shape contour(double x, double y, double width, double halfHeight) {
    double w = Math.max(2, width),
        head = Math.min(32, w * .25),
        neck = Math.min(w * .76, head * 2.5);
    Path2D p = new Path2D.Double();
    p.moveTo(x, y);
    p.curveTo(x, y - halfHeight * .62, x + head * .32, y - halfHeight, x + head, y - halfHeight);
    p.curveTo(
        x + head * 1.5,
        y - halfHeight,
        x + head * 1.6,
        y - halfHeight * .22,
        x + neck,
        y - halfHeight * .14);
    p.curveTo(x + w * .9, y - halfHeight * .08, x + w, y - halfHeight * .10, x + w, y);
    p.curveTo(
        x + w,
        y + halfHeight * .10,
        x + w * .9,
        y + halfHeight * .08,
        x + neck,
        y + halfHeight * .14);
    p.curveTo(
        x + head * 1.6,
        y + halfHeight * .22,
        x + head * 1.5,
        y + halfHeight,
        x + head,
        y + halfHeight);
    p.curveTo(x + head * .32, y + halfHeight, x, y + halfHeight * .62, x, y);
    p.closePath();
    return p;
  }

  public static void paint(
      Graphics2D g, Shape shape, boolean selected, boolean sounding, boolean readonly) {
    g.setColor(readonly ? new Color(151, 148, 139) : new Color(91, 89, 81));
    g.fill(shape);
    if (selected) {
      g.setColor(Theme.ORANGE);
      g.setStroke(new BasicStroke(2));
      g.draw(shape);
    }
    if (sounding) {
      g.setColor(Theme.ORANGE);
      g.fill(shape);
    }
  }

  public static void miniature(
      Graphics2D g, java.util.List<NoteEvent> notes, double x, double y, double w, double h) {
    for (NoteEvent n : notes) {
      double cx = x + n.start() * w / Model.BAR, cy = y + (84 - n.pitch()) * h / 36;
      Shape s =
          contour(
              cx,
              cy,
              n.duration() * w / Model.BAR,
              Math.min(h * .15, thickness(n.velocity()) * .32));
      paint(g, s, false, false, false);
    }
  }
}
