package mmm;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.List;
import java.util.function.*;
import javax.swing.*;
import mmm.Model.*;

public final class NoteCanvas extends JPanel {
  private final Supplier<List<NoteEvent>> source;
  private final boolean editable;
  private final BiConsumer<Integer, NoteEvent> edit;
  private final IntConsumer select;
  public Runnable gestureFinished = () -> {};
  public int selected = -1;
  public long playhead = -1;
  private int dragX, dragY;
  private NoteEvent original;
  private boolean resize;
  public static final int LEFT = 42, TOP = 44, BOTTOM = 44, RIGHT = 24;

  public NoteCanvas(
      Supplier<List<NoteEvent>> source,
      boolean editable,
      BiConsumer<Integer, NoteEvent> edit,
      IntConsumer select) {
    this.source = source;
    this.editable = editable;
    this.edit = edit;
    this.select = select;
    setPreferredSize(new Dimension(800, 300));
    setBackground(Theme.SURFACE);
    setFocusable(editable);
    if (!editable) return;
    for (String direction : new String[] {"LEFT", "RIGHT", "UP", "DOWN"}) {
      getInputMap().put(KeyStroke.getKeyStroke(direction), direction);
      getActionMap()
          .put(
              direction,
              new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                  if (selected < 0 || selected >= source.get().size()) return;
                  NoteEvent n = source.get().get(selected);
                  int start = n.start(), pitch = n.pitch();
                  switch (direction) {
                    case "LEFT" -> start = Math.max(0, start - 120);
                    case "RIGHT" -> start = Math.min(Model.BAR - n.duration(), start + 120);
                    case "UP" -> pitch = Math.min(84, pitch + 1);
                    case "DOWN" -> pitch = Math.max(48, pitch - 1);
                  }
                  edit.accept(selected, new NoteEvent(start, n.duration(), pitch, n.velocity()));
                  gestureFinished.run();
                  repaint();
                }
              });
    }
    MouseAdapter listener =
        new MouseAdapter() {
          public void mousePressed(MouseEvent e) {
            requestFocusInWindow();
            resize =
                selected >= 0
                    && selected < source.get().size()
                    && handle(source.get().get(selected)).contains(e.getPoint());
            if (!resize) selected = hit(e.getX(), e.getY());
            select.accept(selected);
            if (selected >= 0) {
              original = source.get().get(selected);
              dragX = e.getX();
              dragY = e.getY();
            }
            repaint();
          }

          public void mouseDragged(MouseEvent e) {
            if (selected < 0 || original == null) return;
            NoteEvent next;
            if (resize) {
              int duration =
                  Math.max(
                      120,
                      Math.min(
                          Model.BAR - original.start(),
                          Model.snap(
                              original.duration()
                                  + Math.round(
                                      (e.getX() - dragX) * Model.BAR / (double) plotWidth()))));
              next =
                  new NoteEvent(original.start(), duration, original.pitch(), original.velocity());
            } else {
              int start =
                  Math.max(
                      0,
                      Math.min(
                          Model.BAR - original.duration(),
                          Model.snap(
                              original.start()
                                  + Math.round(
                                      (e.getX() - dragX) * Model.BAR / (double) plotWidth()))));
              int pitch =
                  Math.max(
                      48,
                      Math.min(
                          84,
                          original.pitch()
                              - (int) Math.round((e.getY() - dragY) * 36.0 / plotHeight())));
              next = new NoteEvent(start, original.duration(), pitch, original.velocity());
            }
            edit.accept(selected, next);
            repaint();
          }

          public void mouseReleased(MouseEvent e) {
            original = null;
            resize = false;
            gestureFinished.run();
          }

          public void mouseMoved(MouseEvent e) {
            boolean tail =
                selected >= 0
                    && selected < source.get().size()
                    && handle(source.get().get(selected)).contains(e.getPoint());
            setCursor(
                Cursor.getPredefinedCursor(
                    tail
                        ? Cursor.E_RESIZE_CURSOR
                        : hit(e.getX(), e.getY()) >= 0
                            ? Cursor.MOVE_CURSOR
                            : Cursor.DEFAULT_CURSOR));
          }
        };
    addMouseListener(listener);
    addMouseMotionListener(listener);
  }

  private int plotWidth() {
    return Math.max(1, getWidth() - LEFT - RIGHT);
  }

  private int plotHeight() {
    return Math.max(1, getHeight() - TOP - BOTTOM);
  }

  public Point2D noteCenter(NoteEvent n) {
    return new Point2D.Double(
        LEFT + n.start() * plotWidth() / (double) Model.BAR,
        TOP + (84 - n.pitch()) * plotHeight() / 36.0);
  }

  public Shape noteShape(NoteEvent n) {
    Point2D p = noteCenter(n);
    return SoundShape.contour(
        p.getX(),
        p.getY(),
        n.duration() * plotWidth() / (double) Model.BAR,
        SoundShape.thickness(n.velocity()));
  }

  public Point2D tailPoint(NoteEvent n) {
    Point2D p = noteCenter(n);
    return new Point2D.Double(p.getX() + n.duration() * plotWidth() / (double) Model.BAR, p.getY());
  }

  private Shape handle(NoteEvent n) {
    Point2D p = tailPoint(n);
    return new Ellipse2D.Double(p.getX() - 8, p.getY() - 8, 16, 16);
  }

  private int hit(int x, int y) {
    List<NoteEvent> ns = source.get();
    for (int i = ns.size() - 1; i >= 0; i--) {
      Shape s = noteShape(ns.get(i));
      if (s.contains(x, y) || new BasicStroke(10).createStrokedShape(s).contains(x, y)) return i;
    }
    return -1;
  }

  @Override
  protected void paintComponent(Graphics raw) {
    super.paintComponent(raw);
    Graphics2D g = (Graphics2D) raw.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setFont(Theme.font(11, false));
    for (int i = 0; i <= 16; i++) {
      int x = LEFT + i * plotWidth() / 16;
      g.setColor(i % 4 == 0 ? Theme.LINE : new Color(244, 240, 232));
      g.drawLine(x, TOP - 12, x, getHeight() - BOTTOM + 5);
      if (i % 4 == 0 && i < 16) {
        g.setColor(Theme.MUTED);
        g.drawString((i / 4 + 1) + "박", x + 4, getHeight() - 16);
      }
    }
    g.setColor(Theme.MUTED);
    g.drawString("높게", 7, TOP);
    g.drawString("낮게", 7, getHeight() - BOTTOM);
    List<NoteEvent> ns = source.get();
    java.util.List<Integer> order = new java.util.ArrayList<>();
    for (int i = 0; i < ns.size(); i++) if (i != selected || !editable) order.add(i);
    if (editable && selected >= 0 && selected < ns.size()) order.add(selected);
    for (int i : order) {
      NoteEvent n = ns.get(i);
      Shape shape = noteShape(n);
      boolean picked = i == selected && editable;
      boolean sounding = playhead >= n.start() && playhead < n.start() + n.duration();
      SoundShape.paint(g, shape, picked, sounding, !editable);
      if (picked) {
        Point2D tip = tailPoint(n);
        g.setColor(Theme.SURFACE);
        g.fill(new Ellipse2D.Double(tip.getX() - 4, tip.getY() - 4, 8, 8));
        g.setColor(Theme.ORANGE);
        g.draw(new Ellipse2D.Double(tip.getX() - 4, tip.getY() - 4, 8, 8));
        Point2D start = noteCenter(n);
        g.setColor(Theme.MUTED);
        g.drawString(
            noteName(n.pitch()) + " · " + n.duration() / 480.0 + "박 · 세기 " + n.velocity(),
            (int) Math.max(LEFT, Math.min(start.getX(), getWidth() - 180)),
            (int) (start.getY() - SoundShape.thickness(n.velocity()) - 10));
      }
    }
    if (ns.isEmpty()) {
      g.setColor(Theme.MUTED);
      String message = editable ? "아래 건반에서 오늘의 첫 소리를 만들어 보세요." : "이 날은 쉼 한 마디입니다.";
      g.drawString(
          message,
          Math.max(LEFT, (getWidth() - g.getFontMetrics().stringWidth(message)) / 2),
          getHeight() / 2);
    }
    if (playhead >= 0) {
      g.setColor(Theme.ORANGE);
      int x = LEFT + (int) ((playhead % Model.BAR) * plotWidth() / Model.BAR);
      g.drawLine(x, TOP - 18, x, getHeight() - BOTTOM + 6);
      g.fillOval(x - 3, TOP - 23, 6, 6);
    }
    g.dispose();
  }

  public static String noteName(int p) {
    return new String[] {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"}[p % 12]
        + (p / 12 - 1);
  }
}
