package mmm;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.time.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.*;
import mmm.Model.*;

/** S09: attributed, synchronized parts; the selected composer's exact notes are inspectable. */
public final class EnsemblePanel extends JPanel {
  private boolean auditioning;
  private final MMM app;
  private final Song song;
  private final List<Phrase> records;
  private final List<EnsembleData.Member> members;
  private final LocalDate week;
  public final JComboBox<LocalDate> dates = new JComboBox<>();
  public final JCheckBox whole = new JCheckBox("이 주 전체 · 7마디");
  public final JList<EnsembleData.Member> list;
  public final RecordInspector inspector;
  public final JTabbedPane tabs = new JTabbedPane();
  private final Map<String, JCheckBox> enabled = new LinkedHashMap<>();
  private final Map<String, JSlider> volume = new LinkedHashMap<>();
  private final List<Lane> lanes = new ArrayList<>();
  private final javax.swing.Timer timer;
  private final JLabel playing = Theme.muted("재생하면 누가 어떤 음을 연주하는지 표시됩니다.");

  public EnsemblePanel(
      MMM app,
      Song song,
      List<Phrase> records,
      List<EnsembleData.Member> members,
      LocalDate week,
      LocalDate preferred) {
    super(new BorderLayout(12, 12));
    this.app = app;
    this.song = song;
    this.records = List.copyOf(records);
    this.members = List.copyOf(members.stream().limit(15).toList());
    this.week = week;
    setBorder(BorderFactory.createEmptyBorder(24, 30, 20, 30));
    JPanel title = Theme.column();
    title.add(Theme.label("함께, 같은 박자로", 26));
    title.add(Theme.muted(song.name() + " · " + song.bpm() + " BPM · 참여자별 악기와 소리를 비교하세요 · 로컬 모의"));
    for (int d = 0; d < 7; d++) {
      LocalDate day = week.plusDays(d);
      if (!day.isAfter(app.service.today())) dates.addItem(day);
    }
    if (dates.getItemCount() == 0) dates.addItem(app.service.today());
    dates.setSelectedItem(
        preferred != null
                && !preferred.isBefore(week)
                && !preferred.isAfter(week.plusDays(6))
                && !preferred.isAfter(app.service.today())
            ? preferred
            : dates.getItemAt(dates.getItemCount() - 1));
    JToolBar tools = new JToolBar();
    tools.setFloatable(false);
    tools.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 5));
    for (Component item :
        Theme.row(
                new JLabel("날짜"),
                dates,
                whole,
                Theme.button("내 소리만", () -> preset(0)),
                Theme.button("친구 소리만", () -> preset(1)),
                Theme.button("모두 선택", () -> preset(2)))
            .getComponents()) tools.add(item);
    title.add(tools);
    for (Component child : title.getComponents())
      if (child instanceof JComponent component) component.setAlignmentX(Component.LEFT_ALIGNMENT);
    add(title, BorderLayout.NORTH);
    list = new JList<>(new Vector<>(this.members));
    list.setFixedCellHeight(70);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(
        (l, value, index, selected, focus) -> {
          JLabel label =
              new JLabel(
                  "<html>"
                      + escape(value.nickname())
                      + "<br><small>"
                      + escape(value.id())
                      + (value.id().equals(app.service.user()) ? " · 나" : " · 친구")
                      + "</small></html>",
                  ArtAssets.avatar(value.id(), 38),
                  JLabel.LEFT);
          label.setOpaque(true);
          label.setBackground(selected ? Theme.WASH : Theme.PAPER);
          label.setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));
          return label;
        });
    inspector = new RecordInspector(app);
    inspector.onAudition = () -> auditioning = true;
    JPanel timeline = Theme.column();
    for (int i = 0; i < this.members.size(); i++) {
      EnsembleData.Member member = this.members.get(i);
      JCheckBox check = new JCheckBox(member.nickname(), true);
      JSlider slider = new JSlider(0, 100, 80);
      slider.setPreferredSize(new Dimension(130, 25));
      Theme.slider(slider);
      slider.addMouseWheelListener(
          e ->
              slider.setValue(
                  Math.max(0, Math.min(100, slider.getValue() - e.getWheelRotation() * 2))));
      enabled.put(member.id(), check);
      volume.put(member.id(), slider);
      Lane lane = new Lane(member, i);
      lanes.add(lane);
      JPanel row = new JPanel(new BorderLayout(8, 0));
      row.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
      row.setMaximumSize(new Dimension(3000, 122));
      row.setPreferredSize(new Dimension(650, 122));
      JPanel controls = new JPanel(new BorderLayout());
      controls.add(new JLabel(ArtAssets.avatar(member.id(), 34)), BorderLayout.WEST);
      controls.add(check);
      JPanel level = Theme.row(new JLabel("음량"), slider);
      controls.add(level, BorderLayout.EAST);
      row.add(controls, BorderLayout.NORTH);
      row.add(lane);
      timeline.add(row);
      check.addActionListener(e -> applyLevel(member.id()));
      slider.addChangeListener(e -> applyLevel(member.id()));
    }
    tabs.addTab("합주 타임라인", new JScrollPane(timeline));
    tabs.addTab("선택 참여자 기록", inspector);
    tabs.addChangeListener(e -> updateInspector());
    list.addListSelectionListener(
        e -> {
          if (!e.getValueIsAdjusting()) {
            updateInspector();
            lanes.forEach(JPanel::repaint);
          }
        });
    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(list), tabs);
    split.setDividerLocation(190);
    split.setResizeWeight(.16);
    split.setContinuousLayout(true);
    split.setBorder(null);
    add(split);
    JPanel footer = Theme.column();
    footer.add(playing);
    footer.add(new Transport(app, "모두 재생", this::playMix));
    footer.add(
        Theme.row(
            Theme.secondary("선택 기록 자세히", this::openDetail),
            Theme.button("MIDI 내보내기", () -> app.exportMidi(exportParts(), length(), song.bpm())),
            Theme.button("공동 주간", app::week)));
    add(footer, BorderLayout.SOUTH);
    dates.addActionListener(
        e -> {
          app.stopSound();
          refresh();
        });
    whole.addActionListener(
        e -> {
          app.stopSound();
          dates.setEnabled(!whole.isSelected());
          refresh();
        });
    list.setSelectedIndex(0);
    refresh();
    timer = new javax.swing.Timer(40, e -> tick());
  }

  private static String escape(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private LocalDate date() {
    return (LocalDate) dates.getSelectedItem();
  }

  private int length() {
    return (whole.isSelected() ? 7 : 1) * Model.BAR;
  }

  public Map<String, Integer> levels() {
    Map<String, Integer> levels = new LinkedHashMap<>();
    for (var member : members)
      levels.put(
          member.id(),
          enabled.get(member.id()).isSelected() ? volume.get(member.id()).getValue() : 0);
    return levels;
  }

  public List<MidiEngine.Part> exportParts() {
    return EnsembleData.parts(
        records, members, levels(), whole.isSelected() ? week : date(), whole.isSelected());
  }

  public void playMix() {
    auditioning = false;
    Map<String, Integer> levels = levels();
    Map<String, Integer> full = new LinkedHashMap<>();
    levels.forEach((k, v) -> full.put(k, 100));
    Map<Integer, Integer> channels = new HashMap<>();
    for (int i = 0; i < members.size(); i++)
      channels.put(EnsembleData.channel(i), levels.get(members.get(i).id()));
    app.playMix(
        EnsembleData.parts(
            records, members, full, whole.isSelected() ? week : date(), whole.isSelected()),
        length(),
        song.bpm(),
        channels);
  }

  public void preset(int which) {
    for (var member : members) {
      boolean own = member.id().equals(app.service.user());
      enabled.get(member.id()).setSelected(which == 2 || which == 0 && own || which == 1 && !own);
      applyLevel(member.id());
    }
  }

  private void applyLevel(String user) {
    for (int i = 0; i < members.size(); i++)
      if (members.get(i).id().equals(user))
        app.midi.partLevel(
            EnsembleData.channel(i),
            enabled.get(user).isSelected() ? volume.get(user).getValue() : 0);
    lanes.forEach(JPanel::repaint);
  }

  private void refresh() {
    updateInspector();
    lanes.forEach(JPanel::repaint);
  }

  private void updateInspector() {
    EnsembleData.Member member = list == null ? null : list.getSelectedValue();
    if (member != null && inspector != null)
      inspector.setRecord(
          EnsembleData.find(records, member.id(), date()),
          date(),
          member,
          song.bpm(),
          song.instrument());
  }

  public void openDetail() {
    EnsembleData.Member member = list.getSelectedValue();
    if (member != null)
      app.inspectRecord(
          EnsembleData.find(records, member.id(), date()),
          date(),
          member.id(),
          () -> app.show("S09", this));
  }

  private void tick() {
    long pos = auditioning ? -1 : app.midi.position();
    inspector.updatePlayback(pos < 0 ? -1 : pos % Model.BAR);
    List<String> sounding = new ArrayList<>();
    if (pos >= 0) {
      LocalDate day = whole.isSelected() ? week.plusDays(pos / Model.BAR) : date();
      long inBar = pos % Model.BAR;
      for (var member : members) {
        if (levels().get(member.id()) == 0) continue;
        Phrase phrase = EnsembleData.find(records, member.id(), day);
        if (phrase != null) {
          String notes =
              phrase.notes().stream()
                  .filter(n -> inBar >= n.start() && inBar < n.start() + n.duration())
                  .map(n -> NoteCanvas.noteName(n.pitch()))
                  .distinct()
                  .collect(java.util.stream.Collectors.joining("·"));
          if (!notes.isEmpty()) sounding.add(member.nickname() + " " + notes);
        }
      }
    }
    playing.setText(
        auditioning && app.midi.playing()
            ? "선택한 참여자의 개별 음 미리 듣기"
            : pos < 0
                ? "정지 · 참여자를 선택해 악기·메모·개별 음을 확인하세요."
                : sounding.isEmpty() ? "지금은 쉼" : "지금 연주 중 · " + String.join(" / ", sounding));
    lanes.forEach(JPanel::repaint);
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

  private final class Lane extends JPanel {
    private final EnsembleData.Member member;
    private final int index;

    Lane(EnsembleData.Member member, int index) {
      this.member = member;
      this.index = index;
      setBackground(Theme.SURFACE);
      setPreferredSize(new Dimension(650, 74));
      MouseAdapter hover =
          new MouseAdapter() {
            public void mouseMoved(MouseEvent e) {
              Phrase phrase = EnsembleData.find(records, member.id(), date());
              setToolTipText(
                  member.nickname()
                      + " · "
                      + (phrase == null
                          ? "쉼"
                          : ArtAssets.instrumentName(phrase.instrument())
                              + " · "
                              + phrase.notes().stream()
                                  .map(n -> NoteCanvas.noteName(n.pitch()))
                                  .distinct()
                                  .collect(java.util.stream.Collectors.joining(" "))));
            }

            private void popup(MouseEvent e) {
              if (!e.isPopupTrigger()) return;
              list.setSelectedIndex(index);
              JPopupMenu menu = new JPopupMenu();
              JMenuItem inspect = new JMenuItem("이 참여자의 기록 자세히");
              inspect.addActionListener(a -> openDetail());
              menu.add(inspect);
              JMenuItem solo = new JMenuItem("이 파트만 듣기");
              solo.addActionListener(
                  a -> {
                    for (var m : members) {
                      enabled.get(m.id()).setSelected(m.id().equals(member.id()));
                      applyLevel(m.id());
                    }
                    playMix();
                  });
              menu.add(solo);
              menu.show(Lane.this, e.getX(), e.getY());
            }

            public void mousePressed(MouseEvent e) {
              popup(e);
            }

            public void mouseReleased(MouseEvent e) {
              popup(e);
            }
          };
      addMouseListener(hover);
      addMouseMotionListener(hover);
      addMouseListener(
          new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
              list.setSelectedIndex(index);
              if (whole.isSelected()) {
                int bar =
                    Math.max(0, Math.min(6, (e.getX() - 18) * 7 / Math.max(1, getWidth() - 36)));
                LocalDate day = week.plusDays(bar);
                if (!day.isAfter(app.service.today())) dates.setSelectedItem(day);
              }
              if (e.getClickCount() == 2) {
                tabs.setSelectedComponent(inspector);
                updateInspector();
              }
            }
          });
    }

    protected void paintComponent(Graphics raw) {
      super.paintComponent(raw);
      Graphics2D g = (Graphics2D) raw.create();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int width = Math.max(1, getWidth() - 36), bars = whole.isSelected() ? 7 : 1;
      g.setFont(Theme.font(11, false));
      g.setColor(Theme.MUTED);
      for (int b = 0; b < bars; b++) {
        LocalDate day = whole.isSelected() ? week.plusDays(b) : date();
        if (whole.isSelected())
          g.drawString(day.getMonthValue() + "/" + day.getDayOfMonth(), 18 + b * width / bars, 13);
        else
          for (int beat = 0; beat < 4; beat++) {
            int x = 18 + beat * width / 4;
            g.drawString((beat + 1) + "박", x + 3, 13);
            g.setColor(Theme.LINE);
            g.drawLine(x, 17, x, getHeight() - 4);
            g.setColor(Theme.MUTED);
          }
        g.setColor(Theme.LINE);
        g.drawLine(18 + b * width / bars, 17, 18 + b * width / bars, getHeight() - 4);
        g.setColor(Theme.MUTED);
        Phrase phrase = EnsembleData.find(records, member.id(), day);
        if (phrase == null) continue;
        for (NoteEvent n : phrase.notes()) {
          double x = 18 + (b * Model.BAR + n.start()) * width / (double) (bars * Model.BAR),
              y = 25 + (84 - n.pitch()) * (getHeight() - 38) / 36.0;
          Shape shape =
              SoundShape.contour(
                  x,
                  y,
                  n.duration() * width / (double) (bars * Model.BAR),
                  Math.min(13, SoundShape.thickness(n.velocity()) * .4));
          long pos = auditioning ? -1 : app.midi.position();
          boolean active =
              pos >= b * Model.BAR + n.start()
                  && pos < b * Model.BAR + n.start() + n.duration()
                  && levels().get(member.id()) > 0;
          SoundShape.paint(g, shape, false, active, levels().get(member.id()) == 0);
        }
        if (!whole.isSelected()) {
          g.drawString(
              ArtAssets.instrumentName(phrase.instrument()), Math.max(18, getWidth() - 95), 13);
        }
      }
      long pos = auditioning ? -1 : app.midi.position();
      if (pos >= 0) {
        int x = 18 + (int) (pos * width / length());
        g.setColor(Theme.ORANGE);
        g.drawLine(x, 17, x, getHeight() - 1);
      }
      g.dispose();
    }
  }
}
