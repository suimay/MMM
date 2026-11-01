package mmm;

import java.awt.*;
import java.time.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.*;
import mmm.Model.*;

/** Sound, note properties and memo stay together; selection never edits stored notes. */
public final class RecordInspector extends JPanel implements Scrollable {
  private final MMM app;
  private final boolean compact;
  private Phrase phrase;
  public Runnable onAudition = () -> {};
  private boolean auditioning, syncing;
  private int auditionStart;
  private final JLabel author = new JLabel(),
      instrument = new JLabel(),
      summary = Theme.muted("날짜를 선택해 주세요."),
      selected = Theme.label("소리를 선택해 주세요", 18),
      start = new JLabel("시작 박  —"),
      duration = new JLabel("길이  —"),
      velocity = new JLabel("세기  —");
  public final JTextPane memo = new JTextPane();
  private final NoteTable model = new NoteTable();
  public final JTable noteTable = new JTable(model);
  public final NoteCanvas canvas;
  public final JButton listen;
  public final JToggleButton showNotes = new JToggleButton("전체 음 목록 펼치기");

  public RecordInspector(MMM app) {
    this(app, false);
  }

  public RecordInspector(MMM app, boolean compact) {
    super(new BorderLayout(8, 10));
    this.app = app;
    this.compact = compact;
    setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

    JPanel profile = new JPanel(new BorderLayout(8, 3));
    author.setFont(Theme.font(16, true));
    profile.add(author, BorderLayout.NORTH);
    profile.add(instrument);
    profile.add(summary, BorderLayout.SOUTH);
    add(profile, BorderLayout.NORTH);
    canvas =
        new NoteCanvas(
            () -> phrase == null ? List.of() : phrase.notes(), false, null, this::selectNote);
    canvas.setMinimumSize(new Dimension(160, 135));
    canvas.setPreferredSize(new Dimension(compact ? 270 : 550, compact ? 150 : 230));
    JPanel info = Theme.card(new BorderLayout(0, 7));
    JPanel values = new JPanel(new GridLayout(0, compact ? 2 : 1, 10, 9));
    values.add(start);
    values.add(duration);
    values.add(velocity);
    info.add(selected, BorderLayout.NORTH);
    info.add(values);
    listen = Theme.secondary("선택 음 듣기", this::playSelected);
    listen.setEnabled(false);
    info.add(Theme.row(listen), BorderLayout.SOUTH);
    JPanel sound = new JPanel(new BorderLayout(6, 6));
    sound.add(Theme.muted("소리를 클릭하면 음 정보가 보여요 · ← →로도 선택"), BorderLayout.NORTH);
    if (compact) {
      JPanel stack = new JPanel(new BorderLayout(6, 6));
      stack.add(canvas);
      stack.add(info, BorderLayout.SOUTH);
      sound.add(stack);
    } else {
      info.setPreferredSize(new Dimension(230, 200));
      JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvas, info);
      split.setResizeWeight(.76);
      info.setMinimumSize(new Dimension(205, 140));
      split.setContinuousLayout(true);
      split.setBorder(null);
      sound.add(split);
    }
    JPanel middle = new JPanel(new BorderLayout(6, 8));
    middle.add(sound);
    memo.setEditable(false);
    memo.setBackground(Theme.SURFACE);
    memo.setFont(Theme.font(14, false));
    memo.setMargin(new Insets(9, 12, 9, 12));
    JPanel words = new JPanel(new BorderLayout(0, 4));
    words.add(Theme.muted("이 날의 메모"), BorderLayout.NORTH);
    JScrollPane memoScroll = new JScrollPane(memo);
    memoScroll.setPreferredSize(new Dimension(200, compact ? 65 : 82));
    words.add(memoScroll);
    middle.add(words, BorderLayout.SOUTH);
    add(middle);
    noteTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    noteTable.setAutoCreateRowSorter(true);
    noteTable.setRowHeight(27);
    noteTable
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (syncing || e.getValueIsAdjusting()) return;
              int row = noteTable.getSelectedRow();
              selectNote(row < 0 ? -1 : noteTable.convertRowIndexToModel(row));
            });
    JScrollPane notes = new JScrollPane(noteTable);
    notes.setPreferredSize(new Dimension(200, 105));
    notes.setVisible(false);
    JPanel extra = new JPanel(new BorderLayout());
    showNotes.setFont(Theme.font(12, false));
    showNotes.addItemListener(
        e -> {
          notes.setVisible(showNotes.isSelected());

          showNotes.setText(showNotes.isSelected() ? "전체 음 목록 접기" : "전체 음 목록 펼치기");
          revalidate();
          repaint();
        });
    extra.add(showNotes, BorderLayout.NORTH);
    extra.add(notes);
    add(extra, BorderLayout.SOUTH);
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(
        compact ? 286 : 780,
        (compact ? 550 : 480) + (showNotes != null && showNotes.isSelected() ? 115 : 0));
  }

  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredSize();
  }

  public boolean getScrollableTracksViewportWidth() {
    return true;
  }

  public boolean getScrollableTracksViewportHeight() {
    return getParent() instanceof JViewport v && v.getHeight() >= getPreferredSize().height;
  }

  public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
    return 18;
  }

  public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
    return Math.max(18, visible.height - 30);
  }

  public void selectNote(int index) {
    if (phrase == null || index < 0 || index >= phrase.notes().size()) index = -1;
    canvas.inspected = index;
    syncing = true;
    if (index < 0) noteTable.clearSelection();
    else {
      int row = noteTable.convertRowIndexToView(index);
      if (row >= 0) noteTable.setRowSelectionInterval(row, row);
    }
    syncing = false;
    listen.setEnabled(index >= 0);
    if (index < 0) {
      selected.setText("소리를 선택해 주세요");
      start.setText("시작 박  —");
      duration.setText("길이  —");
      velocity.setText("세기  —");
    } else {
      NoteEvent n = phrase.notes().get(index);
      selected.setText(Piano.solfege(n.pitch()) + " · " + NoteCanvas.noteName(n.pitch()));
      start.setText("시작  " + number(n.start() / 480.0 + 1) + "박");
      duration.setText("길이  " + number(n.duration() / 480.0) + "박");
      velocity.setText("세기  " + n.velocity() + " / 127");
    }
    canvas.repaint();
  }

  private static String number(double v) {
    return v == (int) v ? Integer.toString((int) v) : Double.toString(v);
  }

  public void setRecord(
      Phrase value, LocalDate date, EnsembleData.Member member, int bpm, int defaultInstrument) {
    phrase = value;
    auditioning = false;
    author.setText(member.nickname() + " · " + member.id());
    author.setIcon(ArtAssets.avatar(member.id(), 34));
    int program = value == null ? defaultInstrument : value.instrument();
    instrument.setIcon(ArtAssets.instrument(program, 28));
    instrument.setText(ArtAssets.instrumentName(program));
    summary.setText(
        date
            + " · "
            + (value == null ? bpm : value.bpm())
            + " BPM · "
            + (value == null ? 0 : value.notes().size())
            + "개 음 · 감상");
    memo.setText(
        value == null ? "기록 없음 · 쉼 한 마디" : value.memo().isBlank() ? "소리만 남긴 날이에요." : value.memo());
    memo.setCaretPosition(0);
    canvas.playhead = -1;
    syncing = true;
    model.fireTableDataChanged();
    syncing = false;
    selectNote(-1);
  }

  public Phrase record() {
    return phrase;
  }

  public void playSelected() {
    if (phrase == null || canvas.inspected < 0 || canvas.inspected >= phrase.notes().size()) return;
    NoteEvent n = phrase.notes().get(canvas.inspected);
    Phrase single =
        new Phrase(
            phrase.songId(),
            phrase.author(),
            phrase.date(),
            phrase.version(),
            phrase.bpm(),
            phrase.instrument(),
            phrase.memo(),
            List.of(new NoteEvent(0, n.duration(), n.pitch(), n.velocity())));
    auditioning = true;
    auditionStart = n.start();
    onAudition.run();
    app.play(List.of(new MidiEngine.Part(single, 0, 0, 100)), n.duration(), phrase.bpm());
  }

  public void updatePlayback(long tick) {
    if (auditioning) {
      long position = app.midi.position();
      canvas.playhead = position < 0 ? -1 : auditionStart + position;
      if (position < 0) auditioning = false;
    } else canvas.playhead = tick;
    canvas.repaint();
  }

  private final class NoteTable extends AbstractTableModel {
    private final String[] columns = {"음", "시작 박", "길이(박)", "세기"};

    public int getRowCount() {
      return phrase == null ? 0 : phrase.notes().size();
    }

    public int getColumnCount() {
      return columns.length;
    }

    public String getColumnName(int c) {
      return columns[c];
    }

    public Class<?> getColumnClass(int c) {
      return c == 0 ? String.class : c == 3 ? Integer.class : Double.class;
    }

    public Object getValueAt(int r, int c) {
      NoteEvent n = phrase.notes().get(r);
      return switch (c) {
        case 0 -> Piano.solfege(n.pitch()) + " · " + NoteCanvas.noteName(n.pitch());
        case 1 -> n.start() / 480.0 + 1;
        case 2 -> n.duration() / 480.0;
        default -> n.velocity();
      };
    }
  }
}
