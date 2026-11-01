package mmm;

import java.awt.*;
import java.time.*;
import javax.swing.*;
import mmm.Model.*;

/** Separate playback and editing targets so listening never opens the editor. */
public final class DayCard extends JPanel {
  public final LocalDate date;
  private final boolean today;
  private boolean active;

  public DayCard(LocalDate date, Phrase phrase, LocalDate today, Runnable open, Runnable play) {
    super(new BorderLayout(0, 6));
    this.date = date;
    this.today = date.equals(today);
    boolean future = date.isAfter(today);
    setOpaque(false);
    setBorder(BorderFactory.createEmptyBorder(9, 8, 10, 8));
    String day =
        new String[] {"월", "화", "수", "목", "금", "토", "일"}[date.getDayOfWeek().getValue() - 1];
    JButton listen = Theme.button(day + " " + date.getDayOfMonth() + "  ▶", play);
    listen.setFont(Theme.font(18, true));
    listen.setEnabled(!future);
    listen.setToolTipText(date + "부터 일요일까지 이어 듣기 · 반복도 이 날부터");
    listen.getAccessibleContext().setAccessibleName(date + "부터 재생");
    add(listen, BorderLayout.NORTH);
    JPanel drawing =
        new JPanel() {
          protected void paintComponent(Graphics raw) {
            Graphics2D g = (Graphics2D) raw.create();
            if (phrase != null && !phrase.notes().isEmpty())
              SoundShape.miniature(
                  g, phrase.notes(), 5, 12, getWidth() - 10, Math.max(35, getHeight() - 24));
            else {
              g.setColor(Theme.LINE);
              g.drawLine(8, getHeight() / 2, getWidth() - 8, getHeight() / 2);
            }
            g.dispose();
          }
        };
    drawing.setOpaque(false);
    drawing.setPreferredSize(new Dimension(100, 90));
    add(drawing);
    JPanel bottom = new JPanel(new BorderLayout(0, 6));
    bottom.setOpaque(false);
    String memo =
        phrase == null
            ? future ? "아직 오지 않은 날" : "쉼 한 마디"
            : phrase.memo().isBlank() ? "소리만 남긴 날" : phrase.memo();
    JLabel caption = Theme.muted(memo);
    caption.setToolTipText(memo);
    bottom.add(caption, BorderLayout.NORTH);
    JButton details = Theme.button(date.equals(today) ? "오늘 편집" : "기록 열기", open);
    details.setEnabled(!future);
    bottom.add(details);
    add(bottom, BorderLayout.SOUTH);
    setPreferredSize(new Dimension(140, 220));
  }

  public void setActive(boolean active) {
    if (this.active != active) {
      this.active = active;
      repaint();
    }
  }

  protected void paintComponent(Graphics raw) {
    Graphics2D g = (Graphics2D) raw.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(today || active ? Theme.WASH : Theme.SURFACE);
    g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
    g.setColor(today || active ? Theme.ORANGE : Theme.LINE);
    g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
    g.dispose();
    super.paintComponent(raw);
  }
}
