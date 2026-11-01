package mmm;

import java.awt.*;
import java.awt.event.*;
import java.time.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.*;
import mmm.Model.*;

/** S08: participant × day scorecard, with an actual record inspector. */
public final class SharedWeekPanel extends JPanel {
  private final MMM app;
  private final Song song;
  private final List<Phrase> records;
  private final List<EnsembleData.Member> members;
  private final LocalDate week;
  public final JTable table;
  public final RecordInspector inspector;
  private final JLabel status = Theme.muted("날짜 칸을 눌러 작성자의 소리와 메모를 확인하세요.");
  private LocalDate selectedDate;
  private EnsembleData.Member selectedMember;
  private Phrase selectedPhrase;
  private String playingAuthor;
  private LocalDate playingDate;
  private boolean playingWeek;
  private javax.swing.Timer timer;

  public SharedWeekPanel(
      MMM app, Song song, List<Phrase> records, List<EnsembleData.Member> members, LocalDate week) {
    super(new BorderLayout(12, 12));
    this.app = app;
    this.song = song;
    this.records = List.copyOf(records);
    this.members = List.copyOf(members);
    this.week = week;
    setBorder(BorderFactory.createEmptyBorder(24, 30, 20, 30));
    JPanel heading = new JPanel(new BorderLayout());
    JPanel title = Theme.column();
    title.add(Theme.label(song.name(), 26));
    title.add(
        Theme.muted(
            "함께하는 곡 · "
                + week
                + " — "
                + week.plusDays(6)
                + " · 공통 "
                + song.bpm()
                + " BPM · 로컬 모의"));
    heading.add(title);
    heading.add(
        Theme.row(
            Theme.button("초대", () -> app.showInvite(song)),
            Theme.button("곡 관리", app::showManagement)),
        BorderLayout.EAST);
    add(heading, BorderLayout.NORTH);
    JPanel body = new JPanel(new BorderLayout(8, 12));
    body.add(
        Theme.row(
            Theme.button("← 지난주", () -> app.moveWeek(-1)),
            Theme.button("이번 주", () -> app.moveWeek(0)),
            Theme.button("다음주 →", () -> app.moveWeek(1)),
            Theme.button("새로고침", app::week),
            status),
        BorderLayout.NORTH);
    table = new JTable(new BoardModel());
    table.setName("participant-week-table");
    table.setRowHeight(104);
    table.setCellSelectionEnabled(true);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setShowGrid(false);
    table.setIntercellSpacing(new Dimension(6, 6));
    table.setDefaultRenderer(Phrase.class, new PhraseCell());
    table.getColumnModel().getColumn(0).setCellRenderer(new MemberCell());
    table.getColumnModel().getColumn(0).setCellEditor(new MemberEditor());
    table.getColumnModel().getColumn(0).setMinWidth(145);
    table.getColumnModel().getColumn(0).setPreferredWidth(170);
    for (int i = 1; i < 8; i++) {
      table.getColumnModel().getColumn(i).setMinWidth(62);
      table.getColumnModel().getColumn(i).setPreferredWidth(83);
    }
    table.getTableHeader().setReorderingAllowed(false);
    table
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (!e.getValueIsAdjusting()) selection();
            });
    table
        .getColumnModel()
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (!e.getValueIsAdjusting()) selection();
            });
    table.addMouseListener(
        new MouseAdapter() {
          public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2 && table.columnAtPoint(e.getPoint()) > 0) openDetail();
          }
        });
    table.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "record-detail");
    table
        .getActionMap()
        .put(
            "record-detail",
            new AbstractAction() {
              public void actionPerformed(ActionEvent e) {
                openDetail();
              }
            });
    inspector = new RecordInspector(app);
    inspector.onAudition = () -> playingAuthor = null;
    inspector.setPreferredSize(new Dimension(286, 390));
    JPanel preview = new JPanel(new BorderLayout());
    preview.add(inspector);
    preview.add(
        Theme.row(
            Theme.secondary("기록 자세히", this::openDetail),
            Theme.button("이 기록 듣기", this::playSelected)),
        BorderLayout.SOUTH);
    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(table), preview);
    split.setResizeWeight(.72);
    split.setDividerLocation(795);
    split.setBorder(null);
    split.setContinuousLayout(true);
    body.add(split);
    add(body);
    JPanel footer = Theme.column();
    footer.add(new Transport(app, "참여자 주간 재생", this::playMemberWeek));
    footer.add(
        Theme.row(
            Theme.primary(
                "내 오늘 기록",
                () -> {
                  Phrase own = EnsembleData.find(records, app.service.user(), app.service.today());
                  app.edit(own, app.service.today());
                }),
            Theme.secondary("함께 듣기", () -> app.showEnsemble(records, selectedDate)),
            Theme.muted("가로: 한 사람의 일주일 · 세로: 같은 날의 여러 사람")));
    add(footer, BorderLayout.SOUTH);
    int own = 0;
    for (int i = 0; i < members.size(); i++)
      if (members.get(i).id().equals(app.service.user())) own = i;
    int day =
        app.service.today().isBefore(week)
            ? 1
            : app.service.today().isAfter(week.plusDays(6))
                ? 7
                : app.service.today().getDayOfWeek().getValue();
    table.changeSelection(own, day, false, false);
    selection();
    timer =
        new javax.swing.Timer(
            40,
            e -> {
              long pos = app.midi.position();
              LocalDate active =
                  playingWeek && pos >= 0 ? week.plusDays(pos / Model.BAR) : playingDate;
              inspector.updatePlayback(
                  pos >= 0
                          && selectedMember != null
                          && selectedMember.id().equals(playingAuthor)
                          && selectedDate.equals(active)
                      ? pos % Model.BAR
                      : -1);
            });
  }

  private void selection() {
    int row = table.getSelectedRow(), col = table.getSelectedColumn();
    if (row < 0 || row >= members.size() || inspector == null) return;
    selectedMember = members.get(row);
    selectedDate = week.plusDays(Math.max(0, col - 1));
    selectedPhrase = EnsembleData.find(records, selectedMember.id(), selectedDate);
    boolean future = selectedDate.isAfter(app.service.today());
    status.setText(
        future
            ? "미래 기록은 열 수 없습니다."
            : selectedMember.nickname()
                + " · "
                + selectedDate
                + (selectedPhrase == null
                    ? " · 쉼"
                    : " · " + ArtAssets.instrumentName(selectedPhrase.instrument())));
    inspector.setRecord(
        future ? null : selectedPhrase,
        selectedDate,
        selectedMember,
        song.bpm(),
        song.instrument());
  }

  public void selectRecord(String user, LocalDate date) {
    for (int row = 0; row < members.size(); row++)
      if (members.get(row).id().equals(user)) {
        int col = (int) java.time.temporal.ChronoUnit.DAYS.between(week, date) + 1;
        if (col >= 1 && col <= 7) table.changeSelection(row, col, false, false);
      }
  }

  public void openDetail() {
    if (selectedMember == null || selectedDate.isAfter(app.service.today())) return;
    app.inspectRecord(
        selectedPhrase, selectedDate, selectedMember.id(), () -> app.show("S08", this));
  }

  private void playSelected() {
    playingAuthor = selectedMember == null ? null : selectedMember.id();
    playingDate = selectedDate;
    playingWeek = false;
    if (selectedPhrase != null && !selectedDate.isAfter(app.service.today()))
      app.play(
          List.of(new MidiEngine.Part(selectedPhrase, 0, 0, 100)), Model.BAR, selectedPhrase.bpm());
  }

  public void playMemberWeek() {
    if (selectedMember == null) return;
    playingAuthor = selectedMember.id();
    playingWeek = true;
    List<MidiEngine.Part> parts =
        EnsembleData.parts(
            records,
            List.of(selectedMember),
            java.util.Map.of(selectedMember.id(), 100),
            week,
            true);
    app.play(parts, 7 * Model.BAR, song.bpm());
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

  private final class BoardModel extends AbstractTableModel {
    public int getRowCount() {
      return members.size();
    }

    public int getColumnCount() {
      return 8;
    }

    public String getColumnName(int c) {
      return c == 0
          ? "참여자 · 파트 듣기"
          : new String[] {"월", "화", "수", "목", "금", "토", "일"}[c - 1]
              + " "
              + week.plusDays(c - 1).getDayOfMonth();
    }

    public Class<?> getColumnClass(int c) {
      return c == 0 ? EnsembleData.Member.class : Phrase.class;
    }

    public boolean isCellEditable(int r, int c) {
      return c == 0;
    }

    public Object getValueAt(int r, int c) {
      return c == 0
          ? members.get(r)
          : EnsembleData.find(records, members.get(r).id(), week.plusDays(c - 1));
    }
  }

  private final class PhraseCell extends JPanel implements TableCellRenderer {
    private Phrase value;
    private boolean selected, future, today;

    public Component getTableCellRendererComponent(
        JTable t, Object value, boolean selected, boolean focus, int r, int c) {
      this.value = (Phrase) value;
      this.selected = selected;
      LocalDate day = week.plusDays(c - 1);
      future = day.isAfter(app.service.today());
      today = day.equals(app.service.today());
      setToolTipText(
          this.value == null
              ? day + " · 쉼"
              : members.get(r).nickname()
                  + " · "
                  + ArtAssets.instrumentName(this.value.instrument())
                  + " · "
                  + this.value.memo());
      return this;
    }

    protected void paintComponent(Graphics raw) {
      Graphics2D g = (Graphics2D) raw.create();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(selected ? Theme.WASH : Theme.SURFACE);
      g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
      g.setColor(selected || today ? Theme.ORANGE : Theme.LINE);
      g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
      if (future) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .35f));
      if (value != null) {
        SoundShape.miniature(g, value.notes(), 7, 18, getWidth() - 14, 40);
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(11, false));
        g.drawString(ArtAssets.instrumentName(value.instrument()), 7, getHeight() - 20);
        g.drawString(value.notes().size() + "개 음", 7, getHeight() - 5);
      } else {
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(12, false));
        g.drawString(future ? "미래" : "쉼", Math.max(5, getWidth() / 2 - 10), getHeight() / 2);
      }
      g.dispose();
    }
  }

  private final class MemberCell extends JPanel implements TableCellRenderer {
    private EnsembleData.Member member;

    public Component getTableCellRendererComponent(
        JTable t, Object value, boolean selected, boolean focus, int r, int c) {
      member = (EnsembleData.Member) value;
      return this;
    }

    protected void paintComponent(Graphics raw) {
      super.paintComponent(raw);
      Graphics2D g = (Graphics2D) raw.create();
      ArtAssets.avatar(member.id(), 32).paintIcon(this, g, 8, 12);
      g.setFont(Theme.font(13, true));
      g.setColor(Theme.INK);
      g.drawString(member.nickname(), 47, 29);
      Phrase latest =
          records.stream()
              .filter(f -> f.author().equals(member.id()))
              .max(java.util.Comparator.comparing(Phrase::date))
              .orElse(null);
      g.setFont(Theme.font(11, false));
      g.setColor(Theme.MUTED);
      g.drawString(
          latest == null ? "아직 기록 없음" : ArtAssets.instrumentName(latest.instrument()), 47, 47);
      g.setColor(Theme.ORANGE);
      g.drawString("▶ 한 주 듣기", 12, 80);
      g.dispose();
    }
  }

  private final class MemberEditor extends AbstractCellEditor implements TableCellEditor {
    private final JButton button = new JButton();
    private EnsembleData.Member member;

    MemberEditor() {
      button.addActionListener(
          e -> {
            selectedMember = member;
            fireEditingStopped();
            playMemberWeek();
          });
    }

    public Object getCellEditorValue() {
      return member;
    }

    public Component getTableCellEditorComponent(
        JTable t, Object value, boolean selected, int row, int col) {
      member = (EnsembleData.Member) value;
      button.setText(member.nickname() + " · 주간 듣기");
      button.setIcon(ArtAssets.avatar(member.id(), 30));
      return button;
    }
  }
}
