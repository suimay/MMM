package mmm;

import java.awt.*;
import java.time.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import mmm.Model.*;

/** S05: click a weekday to listen from there, with independent record/edit buttons. */
public final class PersonalWeekPanel extends JPanel {
  private final MMM app;
  private final Song song;
  private final List<Phrase> records;
  private final LocalDate week;
  private LocalDate selected;
  private final List<DayCard> days = new ArrayList<>();
  private final JLabel status = Theme.muted("요일의 ▶를 누르면 그 날부터 이어 듣습니다. 기록 열기는 아래 버튼을 사용하세요.");
  private final javax.swing.Timer timer;

  public PersonalWeekPanel(MMM app, Song song, List<Phrase> records, LocalDate week) {
    super(new BorderLayout(12, 18));
    this.app = app;
    this.song = song;
    this.records = List.copyOf(records);
    this.week = week;
    selected = week;
    setBorder(BorderFactory.createEmptyBorder(24, 30, 20, 30));
    JPanel title = Theme.column();
    title.add(Theme.label(song.name(), 26));
    title.add(Theme.muted(week + " — " + week.plusDays(6) + " · 요일은 이어 듣기 / 기록은 따로 열기"));
    title.add(
        Theme.row(
            Theme.button("← 지난주", () -> app.moveWeek(-1)),
            Theme.button("이번 주", () -> app.moveWeek(0)),
            Theme.button("다음주 →", () -> app.moveWeek(1)),
            Theme.button("새로고침", app::week),
            Theme.button("소리 일기장", () -> app.showJournal(records)),
            Theme.button("곡 관리", app::showManagement),
            Theme.button("악보·출력", app::showScore)));
    for (Component c : title.getComponents())
      if (c instanceof JComponent j) j.setAlignmentX(Component.LEFT_ALIGNMENT);
    add(title, BorderLayout.NORTH);
    JPanel body = new JPanel(new BorderLayout(0, 18));
    JPanel grid = new JPanel(new GridLayout(1, 7, 9, 0));
    for (int i = 0; i < 7; i++) {
      LocalDate day = week.plusDays(i);
      Phrase own = EnsembleData.find(records, app.service.user(), day);
      DayCard card =
          new DayCard(
              day,
              own,
              app.service.today(),
              () -> {
                if (day.equals(app.service.today())) app.edit(own, day);
                else app.inspectRecord(own, day, app.service.user(), () -> app.show("S05", this));
              },
              () -> playFrom(day));
      grid.add(card);
      days.add(card);
    }
    body.add(grid, BorderLayout.NORTH);
    JPanel journal = Theme.card(new BorderLayout(0, 8));
    journal.add(Theme.label("이번 주의 소리와 문장", 18), BorderLayout.NORTH);
    JTextArea words = new JTextArea();
    words.setEditable(false);
    words.setOpaque(false);
    words.setLineWrap(true);
    words.setWrapStyleWord(true);
    words.setForeground(Theme.MUTED);
    words.setFont(Theme.font(14, false));
    StringJoiner lines = new StringJoiner("   /   ");
    records.stream()
        .filter(
            f ->
                f.author().equals(app.service.user())
                    && !f.date().isBefore(week)
                    && f.date().isBefore(week.plusDays(7)))
        .sorted(Comparator.comparing(Phrase::date))
        .forEach(
            f ->
                lines.add(
                    f.date().getDayOfMonth()
                        + "일 · "
                        + (f.memo().isBlank() ? "소리만 남긴 날" : f.memo())));
    words.setText(lines.length() == 0 ? "작은 소리 하나로, 오늘을 기억해 보세요." : lines.toString());
    journal.add(words);
    journal.add(
        Theme.row(Theme.secondary("메모 모아 보기", () -> app.showJournal(records))), BorderLayout.SOUTH);
    journal.setPreferredSize(new Dimension(900, 150));
    JPanel wrap = new JPanel(new BorderLayout());
    wrap.add(journal, BorderLayout.NORTH);
    body.add(wrap);
    add(body);
    JPanel footer = Theme.column();
    footer.add(status);
    footer.add(new Transport(app, "주간 재생", () -> playFrom(week)));
    Phrase today = EnsembleData.find(records, app.service.user(), app.service.today());
    footer.add(
        Theme.row(
            Theme.button(
                "저장본 감상",
                () ->
                    app.inspectRecord(
                        today,
                        app.service.today(),
                        app.service.user(),
                        () -> app.show("S05", this))),
            Theme.button("오늘 편집", () -> app.edit(today, app.service.today()))));
    add(footer, BorderLayout.SOUTH);
    timer =
        new javax.swing.Timer(
            45,
            e -> {
              long pos = app.midi.position();
              for (DayCard d : days)
                d.setActive(pos >= 0 && d.date.equals(week.plusDays(pos / Model.BAR)));
            });
  }

  public void playFrom(LocalDate date) {
    if (date.isBefore(week) || date.isAfter(week.plusDays(6)) || date.isAfter(app.service.today()))
      return;
    selected = date;
    List<MidiEngine.Part> parts =
        EnsembleData.parts(
            records,
            List.of(new EnsembleData.Member(app.service.user(), "나", true)),
            Map.of(app.service.user(), 100),
            week,
            true);
    long tick = java.time.temporal.ChronoUnit.DAYS.between(week, date) * Model.BAR;
    app.playFrom(parts, 7 * Model.BAR, song.bpm(), Map.of(), tick);
    status.setText(date + "부터 일요일까지 이어 듣기 · 반복 시에도 선택한 요일부터");
  }

  public LocalDate selectedDate() {
    return selected;
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (timer != null) timer.start();
  }

  @Override
  public void removeNotify() {
    if (timer != null) timer.stop();
    super.removeNotify();
  }
}
