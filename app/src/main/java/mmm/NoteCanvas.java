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
  public int inspected = -1;
  public long playhead = -1;
  public Supplier<List<RestEvent>> restSource = List::of;
  public Supplier<List<ExpressionRange>> expressionSource = List::of;
  public Supplier<List<SoundShape.LiveNote>> liveSource = List::of;
  public BiConsumer<Integer, RestEvent> restEdit = (i, r) -> {};
  public IntConsumer restSelect = i -> {};
  public int selectedRest = -1;
  private int dragX, dragY, dragPitchSpan=36;
  private NoteEvent original;
  private RestEvent originalRest;
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
    setFocusable(editable || select != null);
    if (!editable) {
      if (select != null) {
        addMouseListener(
            new MouseAdapter() {
              public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                requestFocusInWindow();
                selectedRest = hitRest(e.getX(), e.getY());
                select.accept(selectedRest >= 0 ? -1 : hit(e.getX(), e.getY()));
                restSelect.accept(selectedRest);
              }
            });
        for (String direction : new String[] {"LEFT", "RIGHT"}) {
          getInputMap().put(KeyStroke.getKeyStroke(direction), direction);
          getActionMap()
              .put(
                  direction,
                  new AbstractAction() {
                    public void actionPerformed(ActionEvent e) {
                      int size = source.get().size();
                      if (size > 0)
                        select.accept(
                            Math.max(
                                0,
                                Math.min(
                                    size - 1,
                                    inspected < 0
                                        ? 0
                                        : inspected + (direction.equals("LEFT") ? -1 : 1))));
                    }
                  });
        }
        setToolTipText("소리를 클릭해 음 정보를 확인하세요. ← →로도 선택할 수 있어요.");
      }
      return;
    }
    for (String direction : new String[] {"LEFT", "RIGHT", "UP", "DOWN"}) {
      getInputMap().put(KeyStroke.getKeyStroke(direction), direction);
      getActionMap()
          .put(
              direction,
              new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                  if (selectedRest >= 0 && selectedRest < restSource.get().size()) {
                    RestEvent r = restSource.get().get(selectedRest);
                    int start = r.start();
                    if (direction.equals("LEFT")) start = Math.max(0, start - Model.GRID);
                    if (direction.equals("RIGHT")) start = Math.min(Model.BAR - r.duration(), start + Model.GRID);
                    restEdit.accept(selectedRest, new RestEvent(start, r.duration()));
                    gestureFinished.run();
                    repaint();
                    return;
                  }
                  if (selected < 0 || selected >= source.get().size()) return;
                  NoteEvent n = source.get().get(selected);
                  int start = n.start(), pitch = n.pitch();
                  switch (direction) {
                    case "LEFT" -> start = Math.max(0, start - 120);
                    case "RIGHT" -> start = Math.min(Model.BAR - n.duration(), start + 120);
                    case "UP" -> pitch = Math.min(Model.MAX_PITCH, pitch + 1);
                    case "DOWN" -> pitch = Math.max(Model.MIN_PITCH, pitch - 1);
                  }
                  edit.accept(selected, new NoteEvent(start, n.duration(), pitch, n.velocity(), n.articulation()));
                  gestureFinished.run();
                  repaint();
                }
              });
    }
    MouseAdapter listener =
        new MouseAdapter() {
          public void mousePressed(MouseEvent e) {
            requestFocusInWindow();
            int rest = hitRest(e.getX(), e.getY());
            if (rest >= 0) {
              selected = -1;
              selectedRest = rest;
              original = null;
              originalRest = restSource.get().get(rest);
              resize = Math.abs(e.getX() - tickX(originalRest.start() + originalRest.duration())) <= 10;
              dragX = e.getX();
              select.accept(-1);
              restSelect.accept(rest);
              repaint();
              return;
            }
            selectedRest = -1;
            originalRest = null;
            restSelect.accept(-1);
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
              int[] range=SoundShape.pitchRange(source.get());dragPitchSpan=range[1]-range[0];
            }
            repaint();
          }

          public void mouseDragged(MouseEvent e) {
            if (originalRest != null && selectedRest >= 0) {
              int delta = Model.snap(Math.round((e.getX() - dragX) * Model.BAR / (double) plotWidth()));
              int start = resize ? originalRest.start()
                  : Math.max(0, Math.min(Model.BAR - originalRest.duration(), originalRest.start() + delta));
              int duration = resize ? Math.max(Model.GRID,
                  Math.min(Model.BAR - start, originalRest.duration() + delta)) : originalRest.duration();
              restEdit.accept(selectedRest, new RestEvent(start, duration));
              repaint();
              return;
            }
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
                  new NoteEvent(original.start(), duration, original.pitch(), original.velocity(), original.articulation());
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
                      Model.MIN_PITCH,
                      Math.min(
                          Model.MAX_PITCH,
                          original.pitch()
                              - (int) Math.round((e.getY() - dragY) * (double)dragPitchSpan / plotHeight())));
              next = new NoteEvent(start, original.duration(), pitch, original.velocity(), original.articulation());
            }
            edit.accept(selected, next);
            repaint();
          }

          public void mouseReleased(MouseEvent e) {
            original = null;
            originalRest = null;
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
    int[] range=displayRange();
    int low=Math.min(range[0],n.pitch()),high=Math.max(range[1],n.pitch());
    return new Point2D.Double(
        LEFT + n.start() * plotWidth() / (double) Model.BAR,
        TOP + (high - n.pitch()) * plotHeight() / (double)(high-low));
  }

  private int[] displayRange() {
    int[] range=SoundShape.pitchRange(source.get());
    for(SoundShape.LiveNote note:liveSource.get()){range[0]=Math.min(range[0],note.pitch());range[1]=Math.max(range[1],note.pitch());}
    return range;
  }

  public Shape liveShape(SoundShape.LiveNote note) {
    int[] range=displayRange();double y=TOP+(range[1]-note.pitch())*plotHeight()/(double)(range[1]-range[0]);
    return SoundShape.contour(tickX(note.start()),y,Math.max(2,note.duration()*plotWidth()/Model.BAR),SoundShape.thickness(note.velocity()));
  }

  public Shape noteShape(NoteEvent n) {
    Point2D p = noteCenter(n);
    return SoundShape.contour(
        p.getX(),
        p.getY(),
        n.duration() * plotWidth() / (double) Model.BAR,
        SoundShape.thickness(Model.effectiveVelocity(n, expressionSource.get())));
  }

  private double tickX(double tick) {
    return LEFT + tick * plotWidth() / (double) Model.BAR;
  }

  public Rectangle2D restBounds(RestEvent rest) {
    return new Rectangle2D.Double(tickX(rest.start()), getHeight() - BOTTOM - 24,
        rest.duration() * plotWidth() / (double) Model.BAR, 24);
  }

  private int hitRest(int x, int y) {
    List<RestEvent> rests = restSource.get();
    for (int i = rests.size() - 1; i >= 0; i--)
      if (restBounds(rests.get(i)).contains(x, y)) return i;
    return -1;
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
    if (editable && selected >= 0 && selected < ns.size() && noteShape(ns.get(selected)).contains(x, y))
      return selected;
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
    if (!editable) g.drawString("읽기 전용 · 선택하여 듣기", LEFT, 18);
    for (ExpressionRange range : expressionSource.get()) {
      int x1 = (int) tickX(range.start()), x2 = (int) tickX(range.end());
      g.setColor(Theme.ORANGE);
      g.drawLine(x1, TOP - 17, x2, TOP - 24);
      g.drawLine(x1, TOP - 17, x2, TOP - 10);
    }
    for (int i = 0; i < restSource.get().size(); i++) {
      RestEvent rest = restSource.get().get(i);
      Rectangle2D box = restBounds(rest);
      g.setColor(i == selectedRest ? Theme.WASH : new Color(237, 237, 232));
      g.fill(box);
      g.setColor(i == selectedRest ? Theme.ORANGE : Theme.MUTED);
      g.draw(box);
      g.drawString("쉼 " + rest.duration() / 480.0 + "박", (int) box.getX() + 4, (int) box.getY() + 16);
      if (i == selectedRest && editable)
        g.fillRect((int) box.getMaxX() - 5, (int) box.getY() + 8, 5, 8);
    }
    List<NoteEvent> ns = source.get();
    java.util.List<Integer> order = new java.util.ArrayList<>();
    for (int i = 0; i < ns.size(); i++) if (i != selected || !editable) order.add(i);
    if (editable && selected >= 0 && selected < ns.size()) order.add(selected);
    for (int i : order) {
      NoteEvent n = ns.get(i);
      Shape shape = noteShape(n);
      boolean picked = i == selected && editable;
      boolean sounding = playhead >= n.start() && playhead < n.start() + n.gateDuration();
      SoundShape.paint(g, shape, picked || i == inspected, sounding, !editable);
      if (n.articulation() == Articulation.STACCATO) {
        Point2D dot = noteCenter(n);
        g.setColor(Theme.INK);
        g.fillOval((int) dot.getX() + 4, (int) dot.getY() - 6, 4, 4);
      }
      if (picked) {
        Point2D tip = tailPoint(n);
        g.setColor(Theme.SURFACE);
        g.fill(new Ellipse2D.Double(tip.getX() - 4, tip.getY() - 4, 8, 8));
        g.setColor(Theme.ORANGE);
        g.draw(new Ellipse2D.Double(tip.getX() - 4, tip.getY() - 4, 8, 8));
        Point2D start = noteCenter(n);
        g.setColor(Theme.MUTED);
        g.drawString(
            noteName(n.pitch()) + " · " + n.duration() / 480.0 + "박 · 세기 " + Model.effectiveVelocity(n, expressionSource.get()),
            (int) Math.max(LEFT, Math.min(start.getX(), getWidth() - 180)),
            (int) (start.getY() - SoundShape.thickness(n.velocity()) - 10));
      }
    }
    for(SoundShape.LiveNote note:liveSource.get())SoundShape.paintLive(g,liveShape(note));
    if (ns.isEmpty() && restSource.get().isEmpty() && liveSource.get().isEmpty()) {
      g.setColor(Theme.MUTED);
      String message = editable ? "아래 건반에서 오늘의 첫 소리를 만들어 보세요." : "기록된 음과 쉼이 없습니다.";
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
