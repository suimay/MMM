package mmm;

import java.awt.*;
import java.time.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.*;
import mmm.Model.*;

/** Reusable read-only author/voice/note inspector; no save controls or edit listeners. */
public final class RecordInspector extends JPanel {
  private final MMM app;
  private Phrase phrase;
  public Runnable onAudition = () -> {};
  private boolean auditioning;
  private int auditionStart;
  private final JLabel author = new JLabel(),
      instrument = new JLabel(),
      summary = Theme.muted("날짜를 선택해 주세요."),
      selected = Theme.muted("음을 선택하면 한 음씩 들어볼 수 있어요.");
  private final JTextPane memo = new JTextPane();
  private final NoteTable model = new NoteTable();
  public final JTable noteTable = new JTable(model);
  public final NoteCanvas canvas;
  public final JTabbedPane tabs = new JTabbedPane();

  public RecordInspector(MMM app) {
    super(new BorderLayout(8, 10));
    this.app = app;
    setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
    JPanel profile = new JPanel(new BorderLayout(10, 4));
    author.setFont(Theme.font(16, true));
    profile.add(author, BorderLayout.NORTH);
    profile.add(instrument);
    profile.add(summary, BorderLayout.SOUTH);
    add(profile, BorderLayout.NORTH);
    canvas = new NoteCanvas(() -> phrase == null ? List.of() : phrase.notes(), false, null, null);
    canvas.setPreferredSize(new Dimension(460, 210));
    tabs.addTab("소리 모양", canvas);
    noteTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    noteTable.setAutoCreateRowSorter(true);
    noteTable.setRowHeight(29);
    noteTable
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (e.getValueIsAdjusting()) return;
              int row = noteTable.getSelectedRow();
              canvas.inspected = row < 0 ? -1 : noteTable.convertRowIndexToModel(row);
              if (phrase != null && canvas.inspected >= 0) {
                NoteEvent n = phrase.notes().get(canvas.inspected);
                selected.setText(
                    NoteCanvas.noteName(n.pitch())
                        + " · 시작 "
                        + (n.start() / 480.0 + 1)
                        + "박 · 길이 "
                        + n.duration() / 480.0
                        + "박 · 세기 "
                        + n.velocity());
              }
              canvas.repaint();
            });
    tabs.addTab("음 정보", new JScrollPane(noteTable));
    memo.setEditable(false);
    memo.setMargin(new Insets(12, 12, 12, 12));
    memo.setBackground(Theme.SURFACE);
    tabs.addTab("오늘의 메모", new JScrollPane(memo));
    tabs.addChangeListener(
        e ->
            selected.setText(
                tabs.getTitleAt(tabs.getSelectedIndex()) + " · 읽기 전용 · 음 정보에서 음 하나를 선택해 보세요."));
    add(tabs);
    JPanel controls = Theme.column();
    controls.add(selected);
    controls.add(Theme.row(Theme.button("선택 음 듣기", this::playSelected)));
    add(controls, BorderLayout.SOUTH);
  }

  public void setRecord(
      Phrase value, LocalDate date, EnsembleData.Member member, int bpm, int defaultInstrument) {
    phrase = value;
    auditioning = false;
    author.setText(member.nickname() + " · " + member.id());
    author.setIcon(ArtAssets.avatar(member.id(), 38));
    int program = value == null ? defaultInstrument : value.instrument();
    instrument.setIcon(ArtAssets.instrument(program, 34));
    instrument.setText(ArtAssets.instrumentName(program));
    summary.setText(
        date
            + " · "
            + (value == null ? bpm : value.bpm())
            + " BPM · "
            + (value == null ? 0 : value.notes().size())
            + "개 음 · 읽기 전용");
    memo.setText(value == null ? "기록 없음 · 쉼 한 마디" : value.memo());
    canvas.inspected = -1;
    canvas.playhead = -1;
    model.fireTableDataChanged();
    selected.setText(value == null ? "이 날은 쉼 한 마디입니다." : "음을 선택하면 한 음씩 들어볼 수 있어요.");
    canvas.repaint();
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
        case 0 -> NoteCanvas.noteName(n.pitch());
        case 1 -> n.start() / 480.0 + 1;
        case 2 -> n.duration() / 480.0;
        default -> n.velocity();
      };
    }
  }
}
