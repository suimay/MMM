package mmm;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.function.*;
import javax.swing.*;

/** KeyListener repeat guard + MouseListener; focus loss releases every active note. */
public final class Piano extends JPanel {
  private final Map<Integer, Integer> held = new HashMap<>();
  private final IntConsumer press, release;
  private final BooleanSupplier pentatonic;
  private final String keys = "ASDFGHJK";
  private int mouse = -1;

  public Piano(BooleanSupplier pentatonic, IntConsumer press, IntConsumer release) {
    this.pentatonic = pentatonic;
    this.press = press;
    this.release = release;
    setFocusable(true);
    setPreferredSize(new Dimension(800, 100));
    addKeyListener(
        new KeyAdapter() {
          public void keyPressed(KeyEvent e) {
            if (e.isControlDown() || e.isMetaDown() || e.isAltDown()) return;
            int i = keys.indexOf(Character.toUpperCase(e.getKeyChar()));
            if (i >= 0 && !held.containsKey(e.getKeyCode())) {
              int p = pitch(i);
              held.put(e.getKeyCode(), p);
              press.accept(p);
              repaint();
            }
          }

          public void keyReleased(KeyEvent e) {
            Integer p = held.remove(e.getKeyCode());
            if (p != null) {
              release.accept(p);
              repaint();
            }
          }
        });
    addMouseListener(
        new MouseAdapter() {
          public void mousePressed(MouseEvent e) {
            requestFocusInWindow();
            mouse = pitch(Math.min(7, Math.max(0, e.getX() * 8 / Math.max(1, getWidth()))));
            press.accept(mouse);
            repaint();
          }

          public void mouseReleased(MouseEvent e) {
            if (mouse >= 0) {
              release.accept(mouse);
              mouse = -1;
              repaint();
            }
          }

          public void mouseExited(MouseEvent e) {
            if (mouse >= 0) {
              release.accept(mouse);
              mouse = -1;
              repaint();
            }
          }
        });
    addFocusListener(
        new FocusAdapter() {
          public void focusLost(FocusEvent e) {
            releaseAll();
          }
        });
  }

  private int pitch(int i) {
    return 60
        + (pentatonic.getAsBoolean()
                ? new int[] {0, 2, 4, 7, 9, 12, 14, 16}
                : new int[] {0, 2, 4, 5, 7, 9, 11, 12})
            [i];
  }

  public void releaseAll() {
    for (int p : held.values()) release.accept(p);
    held.clear();
    if (mouse >= 0) release.accept(mouse);
    mouse = -1;
    repaint();
  }

  @Override
  protected void paintComponent(Graphics raw) {
    super.paintComponent(raw);
    Graphics2D g = (Graphics2D) raw.create();
    for (int i = 0; i < 8; i++) {
      int x = i * getWidth() / 8, w = getWidth() / 8, p = pitch(i);
      g.setColor(held.containsValue(p) || mouse == p ? Theme.ORANGE : Theme.SURFACE);
      g.fillRoundRect(x + 1, 1, w - 3, getHeight() - 3, 5, 5);
      g.setColor(Theme.LINE);
      g.drawRoundRect(x + 1, 1, w - 3, getHeight() - 3, 5, 5);
      g.setColor(Theme.INK);
      g.setFont(Theme.font(12, false));
      g.drawString(Character.toString(keys.charAt(i)), x + 12, getHeight() - 23);
      g.setColor(Theme.MUTED);
      g.drawString(NoteCanvas.noteName(p), x + 12, getHeight() - 8);
    }
    if (hasFocus()) {
      g.setColor(Theme.ORANGE);
      g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
    }
    g.dispose();
  }
}
