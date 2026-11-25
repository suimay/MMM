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
        new Dimension((int) (960 * zoom), (int) (Math.max(260,phrases.stream().mapToInt(this::rowHeight).sum()) * zoom + 65)));
  }

  private int staffTop(Phrase phrase) {
    int highest=phrase.notes().stream().mapToInt(n->diatonic(n.pitch())).max().orElse(0);
    return Math.max(65,40+highest*6);
  }
  private int footerTop(Phrase phrase) {
    int lowest=phrase.notes().stream().mapToInt(n->diatonic(n.pitch())).min().orElse(0),top=staffTop(phrase);
    return Math.max(top+125,top+60-lowest*6+32);
  }
  private int rowHeight(Phrase phrase){return Math.max(260,footerTop(phrase)+75);}

  public static int diatonic(int midi) {
    int[] steps = {0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6};
    return (midi / 12 - 5) * 7 + steps[midi % 12];
  }

  public static double beatX(int tick) {
    return 105 + tick * 800.0 / Model.BAR;
  }

  public enum Symbol { WHOLE, HALF, QUARTER, EIGHTH, SIXTEENTH, COMPOSITE }

  public static Symbol symbol(int duration) {
    return switch (duration) {
      case 1920 -> Symbol.WHOLE;
      case 960 -> Symbol.HALF;
      case 480 -> Symbol.QUARTER;
      case 240 -> Symbol.EIGHTH;
      case 120 -> Symbol.SIXTEENTH;
      default -> Symbol.COMPOSITE;
    };
  }

  private void rest(Graphics2D g, RestEvent rest, int y) {
    int x = (int) beatX(rest.start());
    g.setColor(Theme.INK);
    Symbol s = symbol(rest.duration());
    if (s == Symbol.WHOLE || s == Symbol.HALF) {
      g.drawLine(x - 9, y, x + 10, y);
      g.fillRect(x - 5, s == Symbol.WHOLE ? y : y - 5, 11, 5);
    } else if (s == Symbol.QUARTER) {
      g.setStroke(new BasicStroke(3));
      g.drawPolyline(new int[] {x, x + 6, x - 2, x + 5, x - 1},
          new int[] {y - 15, y - 6, y + 1, y + 8, y + 15}, 5);
      g.setStroke(new BasicStroke(1));
    } else if (s == Symbol.EIGHTH || s == Symbol.SIXTEENTH) {
      g.drawLine(x + 5, y - 12, x - 2, y + 16);
      g.fillOval(x - 4, y - 13, 7, 7);
      g.drawArc(x - 1, y - 14, 8, 10, 210, 130);
      if (s == Symbol.SIXTEENTH) {
        g.fillOval(x - 7, y - 3, 7, 7);
        g.drawArc(x - 4, y - 4, 8, 10, 210, 130);
      }
    } else g.drawRect(x, y - 3, (int) (beatX(rest.start() + rest.duration()) - x) - 3, 6);
    g.setFont(Theme.font(10, false));
    g.drawString("쉼 " + rest.duration() / 480.0 + "박", x - 5, y + 34);
  }

  private void bar(Graphics2D g, Phrase phrase, int index) {
    int top = staffTop(phrase),footer=footerTop(phrase);
    g.setColor(Theme.INK);
    g.setFont(Theme.font(16, true));
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
    g.setFont(Theme.font(13, false));
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
      g.drawLine(x, top - 12, x, footer+15);
      g.setColor(Theme.MUTED);
      g.setFont(new Font("SansSerif", Font.PLAIN, 11));
      if (beat < 4) g.drawString((beat + 1) + "박", x, footer+37);
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
      Symbol symbol = symbol(note.duration());
      if (symbol == Symbol.COMPOSITE) {
        g.drawRect(x - 4, y - 3, 9, 6);
      } else if (symbol == Symbol.WHOLE || symbol == Symbol.HALF) {
        g.setColor(Color.WHITE);
        g.fillOval(x - 6, y - 4, 13, 9);
        g.setColor(Theme.INK);
        g.drawOval(x - 6, y - 4, 13, 9);
      } else g.fillOval(x - 6, y - 4, 13, 9);
      if (symbol != Symbol.WHOLE && symbol != Symbol.COMPOSITE) g.drawLine(x + 6, y, x + 6, y - 30);
      if (symbol == Symbol.EIGHTH || symbol == Symbol.SIXTEENTH) g.drawArc(x + 6, y - 30, 10, 18, 270, 180);
      if (symbol == Symbol.SIXTEENTH) g.drawArc(x + 6, y - 23, 10, 18, 270, 180);
      if (note.articulation() == Articulation.STACCATO) g.fillOval(x + 1, y + 15, 4, 4);
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
    for (RestEvent rest : phrase.rests()) {
      for (int j = rest.start() / Model.GRID; j < (rest.start() + rest.duration()) / Model.GRID; j++)
        occupied[j] = true;
      rest(g, rest, top + 24);
    }
    for (ExpressionRange range : phrase.expressions()) {
      int x1 = (int) beatX(range.start()), x2 = (int) beatX(range.end());
      g.setColor(Theme.INK);
      g.drawLine(x1, footer, x2, footer-8);
      g.drawLine(x1, footer, x2, footer+8);
      g.setFont(Theme.font(10, false));
      g.drawString(range.startVelocity() + " → " + range.endVelocity(), x1, footer+24);
    }
    g.setColor(Theme.MUTED);
    for (int i = 0; i < 16; ) {
      if (occupied[i]) {
        i++;
        continue;
      }
      int start = i;
      while (i < 16 && !occupied[i]) i++;
      g.drawString("빈칸 " + (i - start) / 4.0 + "박", (int) beatX(start * 120), footer+59);
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
    int offset=0;
    for (int i = 0; i < phrases.size(); i++) {
      Graphics2D row = (Graphics2D) g.create();
      row.translate(0, offset);
      bar(row, phrases.get(i), i);
      row.dispose();
      offset+=rowHeight(phrases.get(i));
    }
    g.dispose();
  }

  @Override
  public int print(Graphics raw, PageFormat format, int page) {
    if(page<0 || phrases.isEmpty())return NO_SUCH_PAGE;
    int tallest=phrases.stream().mapToInt(this::rowHeight).max().orElse(260);
    double scale=Math.min(format.getImageableWidth()/960,format.getImageableHeight()/(tallest+45));
    if(scale<=0)return NO_SUCH_PAGE;
    double capacity=format.getImageableHeight()/scale-45;
    int start=0;
    for(int p=0;p<page && start<phrases.size();p++)start=pageEnd(start,capacity);
    if (start >= phrases.size()) return NO_SUCH_PAGE;
    Graphics2D g = (Graphics2D) raw.create();
    g.translate(format.getImageableX(), format.getImageableY());
    g.scale(scale, scale);
    g.setColor(Color.BLACK);
    g.setFont(new Font("SansSerif", Font.BOLD, 18));
    g.drawString(title + " · " + (page + 1), 25, 25);
    g.translate(0, 45);
    int offset=0,end=pageEnd(start,capacity);
    for (int i = start; i < end; i++) {
      Graphics2D row = (Graphics2D) g.create();
      row.translate(0, offset);
      bar(row, phrases.get(i), i);
      row.dispose();
      offset+=rowHeight(phrases.get(i));
    }
    g.dispose();
    return PAGE_EXISTS;
  }
  private int pageEnd(int start,double capacity) {
    int end=start,height=0;
    while(end<phrases.size() && (end==start || height+rowHeight(phrases.get(end))<=capacity+.01))
      height+=rowHeight(phrases.get(end++));
    return end;
  }
}
