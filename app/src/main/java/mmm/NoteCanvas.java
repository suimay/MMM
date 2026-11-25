package mmm;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
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
  private static final int LEFT = 52, TOP = 30, BOTTOM = 35;

  public NoteCanvas(
      Supplier<List<NoteEvent>> source,
      boolean editable,
      BiConsumer<Integer, NoteEvent> edit,
      IntConsumer select) {
    this.source = source;
    this.editable = editable;
    this.edit = edit;
    this.select = select;
    setPreferredSize(new Dimension(800, 285));
    setBackground(Color.WHITE);
    setBorder(BorderFactory.createLineBorder(Theme.LINE));
    if (editable) {
      setFocusable(true);
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
              selected = hit(e.getX(), e.getY());
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
              int start =
                  Math.max(
                      0,
                      Math.min(
                          Model.BAR - original.duration(),
                          Model.snap(
                              original.start()
                                  + Math.round(
                                      (e.getX() - dragX) * Model.BAR / (double) width()))));
              int pitch =
                  Math.max(
                      48,
                      Math.min(
                          84,
                          original.pitch()
                              - (int) Math.round((e.getY() - dragY) * 36.0 / height())));
              edit.accept(
                  selected, new NoteEvent(start, original.duration(), pitch, original.velocity()));
              repaint();
            }

            public void mouseReleased(MouseEvent e) {
              original = null;
              gestureFinished.run();
            }
          };
      addMouseListener(listener);
      addMouseMotionListener(listener);
    }
  }

  private int width() {
    return Math.max(1, getWidth() - LEFT - 22);
  }

  private int height() {
    return Math.max(1, getHeight() - TOP - BOTTOM);
  }

  private Rectangle bounds(NoteEvent n) {
    int x = LEFT + (int) (n.start() * width() / (double) Model.BAR),
        y = TOP + (84 - n.pitch()) * height() / 36;
    return new Rectangle(x, y - 10, Math.max(15, n.duration() * width() / Model.BAR), 20);
  }

  private int hit(int x, int y) {
    List<NoteEvent> ns = source.get();
    for (int i = ns.size() - 1; i >= 0; i--) if (bounds(ns.get(i)).contains(x, y)) return i;
    return -1;
  }

  @Override
  protected void paintComponent(Graphics raw) {
    super.paintComponent(raw);
    Graphics2D g = (Graphics2D) raw.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    for (int i = 0; i <= 16; i++) {
      int x = LEFT + i * width() / 16;
      g.setColor(i % 4 == 0 ? Theme.LINE : new Color(241, 239, 233));
      g.drawLine(x, TOP, x, getHeight() - BOTTOM);
      if (i % 4 == 0 && i < 16) {
        g.setColor(Theme.MUTED);
        g.drawString((i / 4 + 1) + "박", x + 5, getHeight() - 10);
      }
    }
    g.setColor(Theme.MUTED);
    g.drawString("높게", 7, TOP + 8);
    g.drawString("낮게", 7, getHeight() - BOTTOM);
    List<NoteEvent> ns = source.get();
    for (int i = 0; i < ns.size(); i++) {
      NoteEvent n = ns.get(i);
      Rectangle r = bounds(n);
      g.setColor(new Color(237, 98, 40, 75 + n.velocity()));
      g.fillRoundRect(r.x, r.y, r.width, r.height, 20, 20);
      if (i == selected && editable) {
        g.setColor(Theme.INK);
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(r.x - 2, r.y - 2, r.width + 4, r.height + 4, 22, 22);
      }
      g.setColor(Theme.INK);
      g.setFont(getFont().deriveFont(11f));
      g.drawString(noteName(n.pitch()), r.x + 5, r.y + 14);
    }
    if (ns.isEmpty()) {
      g.setColor(Theme.MUTED);
      g.drawString(
          editable ? "아래 건반으로 오늘의 첫 소리를 넣어 보세요." : "이 날은 쉼 한 마디입니다.", LEFT + 35, getHeight() / 2);
    }
    if (playhead >= 0) {
      g.setColor(Theme.INK);
      int x = LEFT + (int) ((playhead % Model.BAR) * width() / Model.BAR);
      g.drawLine(x, TOP - 10, x, getHeight() - BOTTOM);
    }
    g.dispose();
  }

  public static String noteName(int p) {
    return new String[] {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"}[p % 12]
        + (p / 12 - 1);
  }
}
