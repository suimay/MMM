package mmm;

import java.awt.*;
import java.awt.print.*;
import java.util.List;
import javax.swing.*;
import mmm.Model.*;

/** Time-proportional staff notation. Duration labels preserve arbitrary 1/4-beat lengths. */
public final class ScorePanel extends JPanel implements Printable {
  private final List<Phrase> phrases;
  private final String title;
  private double zoom = 1;

  public ScorePanel(String title, List<Phrase> phrases) {
    this.title = title;
    this.phrases = List.copyOf(phrases);
    setBackground(Color.WHITE);
    resize();
  }

  public void zoom(double value) {
    zoom = value;
    resize();
    revalidate();
    repaint();
  }

  private void resize() {
    setPreferredSize(
        new Dimension((int) (960 * zoom), (int) (Math.max(1, phrases.size()) * 260 * zoom + 65)));
  }

  public static int diatonic(int midi) {
    int[] steps = {0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6};
    return (midi / 12 - 5) * 7 + steps[midi % 12];
  }

  public static double beatX(int tick) {
    return 105 + tick * 800.0 / Model.BAR;
  }

  private void bar(Graphics2D g, Phrase phrase, int index) {
    int top = 65;
    g.setColor(Theme.INK);
    g.setFont(new Font("SansSerif", Font.BOLD, 16));
    g.drawString(
        (index + 1)
            + ". "
            + phrase.date()
            + "  ·  "
            + phrase.author()
            + "  ·  "
            + phrase.bpm()
            + " BPM",
        25,
        23);
    g.setFont(new Font("SansSerif", Font.PLAIN, 13));
    g.drawString(phrase.memo(), 25, 45);
    // Treble staff: E4 is bottom line; C4 is its first ledger line.
    for (int i = 0; i < 5; i++) g.drawLine(80, top + i * 12, 915, top + i * 12);
    Font clef = new Font("Apple Symbols", Font.PLAIN, 52);
    g.setFont(clef);
    g.drawString(clef.canDisplayUpTo("𝄞") == -1 ? "𝄞" : "G", 18, top + 45);
    g.setFont(new Font("Serif", Font.BOLD, 24));
    g.drawString("4", 55, top + 22);
    g.drawString("4", 55, top + 45);
    for (int beat = 0; beat <= 4; beat++) {
      int x = (int) beatX(beat * 480);
      g.setColor(new Color(235, 232, 224));
      g.drawLine(x, top - 12, x, 205);
      g.setColor(Theme.MUTED);
      g.setFont(new Font("SansSerif", Font.PLAIN, 11));
      if (beat < 4) g.drawString((beat + 1) + "박", x, 227);
    }
    boolean[] occupied = new boolean[16];
    for (NoteEvent note : phrase.notes()) {
      int x = (int) beatX(note.start()), y = top + 60 - diatonic(note.pitch()) * 6;
      for (int j = note.start() / 120; j < (note.start() + note.duration()) / 120; j++)
        occupied[j] = true;
      g.setColor(Theme.INK);
      for (int ledger = top + 60; ledger <= y; ledger += 12)
        g.drawLine(x - 10, ledger, x + 12, ledger);
      for (int ledger = top - 12; ledger >= y; ledger -= 12)
        g.drawLine(x - 10, ledger, x + 12, ledger);
      if (note.duration() >= 960) {
        g.setColor(Color.WHITE);
        g.fillOval(x - 6, y - 4, 13, 9);
        g.setColor(Theme.INK);
        g.drawOval(x - 6, y - 4, 13, 9);
      } else g.fillOval(x - 6, y - 4, 13, 9);
      if (note.duration() < 1920) g.drawLine(x + 6, y, x + 6, y - 30);
      if (note.duration() < 480) g.drawArc(x + 6, y - 30, 10, 18, 270, 180);
      if (note.duration() == 120) g.drawArc(x + 6, y - 23, 10, 18, 270, 180);
      if (note.duration() == 360 || note.duration() == 720 || note.duration() == 1440)
        g.fillOval(x + 12, y - 3, 3, 3);
      int pc = note.pitch() % 12;
      if (pc == 1 || pc == 3 || pc == 6 || pc == 8 || pc == 10) {
        g.setFont(new Font("Serif", Font.PLAIN, 17));
        g.drawString("#", x - 20, y + 5);
      }
      g.setColor(Theme.ORANGE);
      int end = (int) beatX(note.start() + note.duration());
      g.drawLine(x, y + 8, end - 3, y + 8);
      g.setFont(new Font("SansSerif", Font.PLAIN, 10));
      g.drawString(
          NoteCanvas.noteName(note.pitch()) + " · " + note.duration() / 480.0 + "박", x - 5, y - 35);
    }
    g.setColor(Theme.MUTED);
    for (int i = 0; i < 16; ) {
      if (occupied[i]) {
        i++;
        continue;
      }
      int start = i;
      while (i < 16 && !occupied[i]) i++;
      g.drawString("쉼 " + (i - start) / 4.0 + "박", (int) beatX(start * 120), 249);
    }
  }

  @Override
  protected void paintComponent(Graphics raw) {
    super.paintComponent(raw);
    Graphics2D g = (Graphics2D) raw.create();
    g.scale(zoom, zoom);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(Theme.INK);
    g.setFont(new Font("SansSerif", Font.BOLD, 20));
    g.drawString(title + " · 시간 비례 악보 (4/4)", 25, 28);
    g.translate(0, 55);
    for (int i = 0; i < phrases.size(); i++) {
      Graphics2D row = (Graphics2D) g.create();
      row.translate(0, i * 260);
      bar(row, phrases.get(i), i);
      row.dispose();
    }
    g.dispose();
  }

  @Override
  public int print(Graphics raw, PageFormat format, int page) {
    int perPage =
        Math.max(
            1,
            (int) ((format.getImageableHeight() - 35) / (260 * format.getImageableWidth() / 960)));
    int start = page * perPage;
    if (start >= phrases.size()) return NO_SUCH_PAGE;
    Graphics2D g = (Graphics2D) raw.create();
    g.translate(format.getImageableX(), format.getImageableY());
    double scale = format.getImageableWidth() / 960;
    g.scale(scale, scale);
    g.setColor(Color.BLACK);
    g.setFont(new Font("SansSerif", Font.BOLD, 18));
    g.drawString(title + " · " + (page + 1), 25, 25);
    g.translate(0, 45);
    for (int i = start; i < Math.min(start + perPage, phrases.size()); i++) {
      Graphics2D row = (Graphics2D) g.create();
      row.translate(0, (i - start) * 260);
      bar(row, phrases.get(i), i);
      row.dispose();
    }
    g.dispose();
    return PAGE_EXISTS;
  }
}
