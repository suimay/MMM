package mmm;

import java.awt.*;
import java.time.*;
import javax.swing.*;
import mmm.Model.*;

/** One keyboard-accessible day target, with a semantic sound miniature. */
public final class DayCard extends JButton {
  private final LocalDate date;
  private final Phrase phrase;
  private final boolean today;

  public DayCard(LocalDate date, Phrase phrase, LocalDate today, Runnable open) {
    super(date.equals(today) ? "오늘 편집" : "감상");
    this.date = date;
    this.phrase = phrase;
    this.today = date.equals(today);
    setEnabled(!date.isAfter(today));
    setContentAreaFilled(false);
    setBorderPainted(false);
    setOpaque(false);
    setPreferredSize(new Dimension(140, 230));
    setToolTipText(date + (phrase == null ? " · 쉼 한 마디" : " · " + phrase.memo()));
    getAccessibleContext().setAccessibleName(date + " " + getText());
    addActionListener(e -> open.run());
  }

  @Override
  protected void paintComponent(Graphics raw) {
    Graphics2D g = (Graphics2D) raw.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    int w = getWidth(), h = getHeight();
    g.setColor(today ? Theme.WASH : Theme.SURFACE);
    g.fillRoundRect(0, 0, w - 1, h - 1, 12, 12);
    g.setColor(today || hasFocus() ? Theme.ORANGE : Theme.LINE);
    g.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);
    if (!isEnabled()) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .45f));
    g.setFont(Theme.font(12, false));
    g.setColor(Theme.MUTED);
    g.drawString(
        new String[] {"월", "화", "수", "목", "금", "토", "일"}[date.getDayOfWeek().getValue() - 1]
            + (today ? " · 오늘" : ""),
        14,
        25);
    g.setFont(Theme.font(24, true));
    g.setColor(Theme.INK);
    g.drawString(Integer.toString(date.getDayOfMonth()), 14, 58);
    if (phrase != null && !phrase.notes().isEmpty())
      SoundShape.miniature(g, phrase.notes(), 14, 78, w - 28, 65);
    else {
      g.setColor(Theme.LINE);
      g.drawLine(15, 111, w - 16, 111);
    }
    g.setColor(Theme.MUTED);
    g.setFont(Theme.font(11, false));
    String memo =
        phrase == null
            ? "아직 남기지 않은 소리"
            : phrase.memo().isBlank() ? phrase.notes().size() + "개의 소리" : phrase.memo();
    while (g.getFontMetrics().stringWidth(memo) > w - 28 && memo.length() > 2)
      memo = memo.substring(0, memo.length() - 2) + "…";
    g.drawString(memo, 14, h - 53);
    g.setFont(Theme.font(12, false));
    g.setColor(today ? Theme.ORANGE : Theme.MUTED);
    g.drawString(
        !isEnabled()
            ? "아직 오지 않은 날"
            : today
                ? (phrase == null ? "오늘의 첫 소리 +" : "이어서 다듬기 →")
                : phrase == null ? "쉼 한 마디" : "기록 감상 →",
        14,
        h - 22);
    g.dispose();
  }
}
