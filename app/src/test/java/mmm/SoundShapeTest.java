package mmm;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.util.*;
import javax.imageio.ImageIO;
import mmm.Model.*;

public final class SoundShapeTest {
  static int count;

  static void check(boolean ok, String why) {
    if (!ok) throw new AssertionError(why);
    count++;
  }

  static double section(Shape s, double x) {
    Rectangle2D b = s.getBounds2D();
    double total = 0;
    for (double y = b.getMinY(); y < b.getMaxY(); y += .1) if (s.contains(x, y)) total += .1;
    return total;
  }

  public static void main(String[] args) throws Exception {
    NoteEvent a = new NoteEvent(0, 480, 60, 40),
        b = new NoteEvent(0, 960, 60, 40),
        strong = new NoteEvent(0, 480, 60, 110),
        high = new NoteEvent(0, 480, 72, 40);
    NoteCanvas view =
        new NoteCanvas(() -> java.util.List.of(a, b, strong, high), true, (i, n) -> {}, i -> {});
    view.setSize(900, 330);
    Rectangle2D shortBox = view.noteShape(a).getBounds2D(),
        longBox = view.noteShape(b).getBounds2D(),
        strongBox = view.noteShape(strong).getBounds2D();
    check(
        Math.abs(longBox.getWidth() / shortBox.getWidth() - 2) < .001,
        "duration is proportional to full tail length");
    check(
        strongBox.getHeight() > shortBox.getHeight() * 1.5,
        "higher MIDI velocity creates thicker body");
    check(
        view.noteCenter(high).getY() < view.noteCenter(a).getY(),
        "higher MIDI pitch moves body upward");
    Shape contour = view.noteShape(strong);
    Rectangle2D bounds = contour.getBounds2D();
    check(
        section(contour, bounds.getX() + 22) > section(contour, bounds.getMaxX() - 10) * 3,
        "attack body tapers into a thin decay tail");
    check(
        section(contour, bounds.getX() + 22) > section(contour, bounds.getMaxX() - 22),
        "glyph is directionally asymmetric, never a capsule");
    check(
        Math.abs(view.tailPoint(b).getX() - view.noteCenter(b).getX() - longBox.getWidth()) < .01,
        "tail handle matches exact MIDI note-off location");
    NoteCanvas readonly = new NoteCanvas(() -> java.util.List.of(a), false, null, null);
    check(
        readonly.getMouseListeners().length == 0 && readonly.getMouseMotionListeners().length == 0,
        "read-only renderer has no edit handlers");
    BufferedImage image = new BufferedImage(900, 330, BufferedImage.TYPE_INT_RGB);
    view.paint(image.getGraphics());
    check(a.equals(new NoteEvent(0, 480, 60, 40)), "drawing preserves source data");
    BufferedImage guide = new BufferedImage(960, 470, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = guide.createGraphics();
    g.setColor(Theme.PAPER);
    g.fillRect(0, 0, 960, 470);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(Theme.INK);
    g.setFont(Theme.font(23, true));
    g.drawString("소리 덩어리의 의미", 32, 42);
    g.setFont(Theme.font(14, false));
    String[] headings = {"길이 · 꼬리의 길이", "세기 · 덩어리의 두께", "높낮이 · 위아래 위치"};
    for (int r = 0; r < 3; r++) {
      int y = 120 + r * 135;
      g.setColor(Theme.MUTED);
      g.drawString(headings[r], 32, y - 35);
      double w1 = r == 0 ? 95 : 180, w2 = r == 0 ? 300 : 180;
      double h1 = r == 1 ? 10 : 20, h2 = r == 1 ? 31 : 20;
      SoundShape.paint(g, SoundShape.contour(240, y, w1, h1), false, false, false);
      SoundShape.paint(
          g, SoundShape.contour(565, y - (r == 2 ? 35 : 0), w2, h2), true, false, false);
      g.setColor(Theme.MUTED);
      g.drawString(r == 0 ? "짧게" : r == 1 ? "여리게" : "낮게", 240, y + 45);
      g.drawString(r == 0 ? "길게" : r == 1 ? "세게" : "높게", 565, y + 45);
    }
    g.dispose();
    ImageIO.write(guide, "png", new java.io.File("docs/sound-shape-semantics.png"));
    System.out.println("PASS: " + count + " semantic sound geometry and read-only checks");
  }
}
