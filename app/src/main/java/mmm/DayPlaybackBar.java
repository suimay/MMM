package mmm;

import java.awt.*;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;

/** Accessible weekday playback targets, shared by the board and the mixer. */
public final class DayPlaybackBar extends JPanel {
  public final List<JToggleButton> buttons = new ArrayList<>();
  private final LocalDate week;

  public DayPlaybackBar(LocalDate week, LocalDate today, Consumer<LocalDate> play) {
    super(new GridLayout(1, 7, 5, 0));
    this.week = week;
    ButtonGroup group = new ButtonGroup();
    for (int i = 0; i < 7; i++) {
      LocalDate day = week.plusDays(i);
      String label =
          new String[] {"월", "화", "수", "목", "금", "토", "일"}[i] + " " + day.getDayOfMonth() + " ▶";
      JToggleButton b = new JToggleButton(label);
      b.setFont(Theme.font(13, false));
      b.setEnabled(!day.isAfter(today));
      b.setToolTipText(day + " · 이 날의 전체 합주 듣기");
      b.getAccessibleContext().setAccessibleName(day + " 전체 합주 재생");
      b.addActionListener(e -> play.accept(day));
      group.add(b);
      buttons.add(b);
      add(b);
    }
  }

  public void select(LocalDate day) {
    long i = java.time.temporal.ChronoUnit.DAYS.between(week, day);
    if (i >= 0 && i < 7) buttons.get((int) i).setSelected(true);
  }
}
