package mmm;

import java.awt.*;
import java.awt.event.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;
import mmm.Model.*;

/** S14: a sound picture diary, scoped to the accessible song and its attributed records. */
public final class JournalPanel extends JPanel {
  private final MMM app;
  private final Song song;
  private final List<Phrase> records;
  private final List<EnsembleData.Member> members;
  private final LocalDate week;
  private final Runnable back;
  public final JTextField search = new JTextField(17);
  public final JComboBox<String> period = new JComboBox<>(new String[] {"전체 기록", "보고 있던 한 주"});
  public final JComboBox<String> author = new JComboBox<>();
  public final JComboBox<String> order = new JComboBox<>(new String[] {"최근 날짜부터", "오래된 날짜부터"});
  public final JCheckBox withMemo = new JCheckBox("메모 있는 날만");
  public final DefaultListModel<Phrase> model = new DefaultListModel<>();
  public final JList<Phrase> list = new JList<>(model);
  public final RecordInspector inspector;
  private final JLabel result = Theme.muted("");
  private boolean recordPlaying;
  private final javax.swing.Timer timer;

  public JournalPanel(
      MMM app,
      Song song,
      List<Phrase> records,
      List<EnsembleData.Member> members,
      LocalDate week,
      Runnable back) {
    super(new BorderLayout(12, 12));
    this.app = app;
    this.song = song;
    this.records = List.copyOf(records);
    this.members = List.copyOf(members);
    this.week = week;
    this.back = back;
    setBorder(BorderFactory.createEmptyBorder(22, 30, 18, 30));
    JPanel heading = Theme.column();
    heading.add(Theme.label("소리 일기장", 26));
    heading.add(
        Theme.muted(
            song.name() + " · 하루의 소리와 메모를 한 장씩 · " + (song.shared() ? "참여자의 기록도 함께" : "나의 기록")));
    author.addItem("모든 작성자");
    members.forEach(m -> author.addItem(m.nickname() + " · " + m.id()));
    search.setToolTipText("메모·작성자·날짜·악기로 찾기");
    heading.add(Theme.row(new JLabel("찾기"), search, period, author, order));
    heading.add(
        Theme.row(
            withMemo,
            result,
            Theme.button(
                "필터 초기화",
                () -> {
                  search.setText("");
                  period.setSelectedIndex(0);
                  author.setSelectedIndex(0);
                  order.setSelectedIndex(0);
                  withMemo.setSelected(false);
                  filter();
                })));
    for (Component c : heading.getComponents())
      if (c instanceof JComponent j) j.setAlignmentX(Component.LEFT_ALIGNMENT);
    add(heading, BorderLayout.NORTH);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setFixedCellHeight(130);
    list.setCellRenderer(new DiaryCell());
    inspector = new RecordInspector(app);
    inspector.onAudition = () -> recordPlaying = false;
    JPanel view = new JPanel(new BorderLayout());
    view.add(new JScrollPane(inspector));
    view.add(Theme.row(Theme.secondary("기록 열기", this::openRecord)), BorderLayout.SOUTH);
    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(list), view);
    split.setDividerLocation(350);
    split.setResizeWeight(.32);
    split.setContinuousLayout(true);
    split.setBorder(null);
    add(split);
    list.addListSelectionListener(
        e -> {
          if (!e.getValueIsAdjusting()) updateRecord();
        });
    list.addMouseListener(
        new MouseAdapter() {
          public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) openRecord();
          }
        });
    list.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "open-record");
    list.getActionMap()
        .put(
            "open-record",
            new AbstractAction() {
              public void actionPerformed(ActionEvent e) {
                openRecord();
              }
            });
    search
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              public void insertUpdate(DocumentEvent e) {
                filter();
              }

              public void removeUpdate(DocumentEvent e) {
                filter();
              }

              public void changedUpdate(DocumentEvent e) {
                filter();
              }
            });
    for (JComboBox<String> c : List.of(period, author, order))
      c.addItemListener(
          e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) filter();
          });
    withMemo.addItemListener(e -> filter());
    JPanel footer = Theme.column();
    footer.add(new Transport(app, "선택한 날 듣기", this::playSelected));
    footer.add(
        Theme.row(
            Theme.button("주간으로", back),
            Theme.button("메모 내보내기 · TXT", this::exportMemos),
            Theme.muted("필터에 보이는 기록만 내보냅니다. 소리 원본은 그대로 보관됩니다.")));
    add(footer, BorderLayout.SOUTH);
    filter();
    timer =
        new javax.swing.Timer(
            45, e -> inspector.updatePlayback(recordPlaying ? app.midi.position() : -1));
  }

  private EnsembleData.Member member(String id) {
    return members.stream()
        .filter(m -> m.id().equals(id))
        .findFirst()
        .orElse(new EnsembleData.Member(id, id, false));
  }

  public void filter() {
    Phrase selected = list.getSelectedValue();
    String q = search.getText().strip().toLowerCase(Locale.ROOT);
    int who = author.getSelectedIndex();
    String user = who <= 0 ? null : members.get(who - 1).id();
    Comparator<Phrase> comparator =
        Comparator.comparing(Phrase::date).thenComparing(Phrase::author);
    if (order.getSelectedIndex() == 0) comparator = comparator.reversed();
    List<Phrase> visible =
        records.stream()
            .filter(f -> !f.date().isAfter(app.service.today()))
            .filter(
                f ->
                    period.getSelectedIndex() == 0
                        || !f.date().isBefore(week) && f.date().isBefore(week.plusDays(7)))
            .filter(f -> user == null || f.author().equals(user))
            .filter(f -> !withMemo.isSelected() || !f.memo().isBlank())
            .filter(
                f ->
                    (f.memo()
                            + " "
                            + f.date()
                            + " "
                            + member(f.author()).label()
                            + " "
                            + ArtAssets.instrumentName(f.instrument()))
                        .toLowerCase(Locale.ROOT)
                        .contains(q))
            .sorted(comparator)
            .toList();
    model.clear();
    visible.forEach(model::addElement);
    result.setText(visible.isEmpty() ? "찾은 기록이 없어요. 필터를 바꿔 보세요." : visible.size() + "장의 소리 일기");
    if (selected != null && visible.contains(selected)) list.setSelectedValue(selected, true);
    else if (!visible.isEmpty()) list.setSelectedIndex(0);
    else updateRecord();
  }

  private void updateRecord() {
    Phrase f = list.getSelectedValue();
    app.stopSound();
    recordPlaying = false;
    inspector.setRecord(
        f,
        f == null ? app.service.today() : f.date(),
        f == null ? new EnsembleData.Member("", "선택한 기록 없음", false) : member(f.author()),
        song.bpm(),
        song.instrument());
  }

  public void playSelected() {
    Phrase f = list.getSelectedValue();
    if (f == null) return;
    recordPlaying = true;
    app.play(List.of(new MidiEngine.Part(f, 0, 0, 100)), Model.BAR, f.bpm());
  }

  public void openRecord() {
    Phrase f = list.getSelectedValue();
    if (f != null) app.inspectRecord(f, f.date(), f.author(), () -> app.show("S14", this));
  }

  public String memoText() {
    StringBuilder out = new StringBuilder(song.name() + " · 소리 일기장\n\n");
    for (int i = 0; i < model.size(); i++) {
      Phrase f = model.get(i);
      out.append(f.date())
          .append(" · ")
          .append(member(f.author()).nickname())
          .append(" · ")
          .append(ArtAssets.instrumentName(f.instrument()))
          .append('\n')
          .append(f.memo().isBlank() ? "(소리만 남긴 날)" : f.memo())
          .append("\n\n");
    }
    return out.toString();
  }

  private void exportMemos() {
    if (model.isEmpty()) return;
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("필터에 보이는 메모 내보내기");
    chooser.setSelectedFile(new java.io.File("MMM-sound-diary.txt"));
    if (chooser.showSaveDialog(app) != JFileChooser.APPROVE_OPTION) return;
    Path path = chooser.getSelectedFile().toPath();
    if (Files.exists(path)
        && JOptionPane.showConfirmDialog(
                app, "같은 이름의 파일이 있어요. 덮어쓸까요?", "메모 내보내기", JOptionPane.OK_CANCEL_OPTION)
            != JOptionPane.OK_OPTION) return;
    String content = memoText();
    app.work(
        () -> {
          Files.writeString(path, content, StandardCharsets.UTF_8);
          return path;
        },
        p -> result.setText("메모를 저장했어요 · " + p.getFileName()));
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

  private final class DiaryCell extends JPanel implements ListCellRenderer<Phrase> {
    private Phrase value;
    private boolean selected;

    public Component getListCellRendererComponent(
        JList<? extends Phrase> l, Phrase f, int i, boolean selected, boolean focus) {
      value = f;
      this.selected = selected;
      setToolTipText(f.date() + " · " + f.memo());
      return this;
    }

    protected void paintComponent(Graphics raw) {
      Graphics2D g = (Graphics2D) raw.create();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(selected ? Theme.WASH : Theme.SURFACE);
      g.fillRoundRect(3, 3, getWidth() - 7, getHeight() - 7, 10, 10);
      g.setColor(selected ? Theme.ORANGE : Theme.LINE);
      g.drawRoundRect(3, 3, getWidth() - 7, getHeight() - 7, 10, 10);
      if (value != null) {
        ArtAssets.avatar(value.author(), 26).paintIcon(this, g, 13, 12);
        g.setFont(Theme.font(13, true));
        g.setColor(Theme.INK);
        g.drawString(value.date() + " · " + member(value.author()).nickname(), 47, 30);
        g.setFont(Theme.font(14, false));
        String words = value.memo().isBlank() ? "소리만 남긴 날" : value.memo();
        while (g.getFontMetrics().stringWidth(words) > getWidth() - 28 && words.length() > 2)
          words = words.substring(0, words.length() - 2) + "…";
        g.drawString(words, 14, 55);
        SoundShape.miniature(g, value.notes(), 14, 68, getWidth() - 32, 34);
        g.setFont(Theme.font(11, false));
        g.setColor(Theme.MUTED);
        g.drawString(
            ArtAssets.instrumentName(value.instrument()) + " · " + value.notes().size() + "개 음",
            14,
            116);
      }
      g.dispose();
    }
  }
}
