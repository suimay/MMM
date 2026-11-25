package mmm;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.undo.*;
import mmm.Model.*;

/** S06: capture -> edit -> preview, all using one immutable-event draft. */
public final class EditorPanel extends JPanel {
  private final MMM app;
  private final Song song;
  private final Phrase base;
  public final List<NoteEvent> notes = new ArrayList<>();
  public boolean dirty;
  public final JTextField memo = new JTextField(20);
  private final JSpinner bpm;
  private final JComboBox<String> instrument = new JComboBox<>(MMM.INSTRUMENT_NAMES);
  private final JCheckBox pentatonic = new JCheckBox("펜타토닉 · 어울리는 음", true);
  private final JComboBox<String> mode =
      new JComboBox<>(new String[] {"단계 입력 · 시간 제한 없이", "실시간 입력 · 4박 녹음"});
  private final JSlider length = new JSlider(1, 16, 4),
      pitch = new JSlider(48, 84, 60),
      velocity = new JSlider(1, 127, 90);
  public final NoteCanvas canvas;
  public final Piano piano;
  private final JLabel status = new JLabel("건반을 클릭하거나 A S D F G H J K를 눌러 보세요.");
  private List<NoteEvent> beforeEdit;

  private void rememberBefore() {
    if (beforeEdit == null) beforeEdit = List.copyOf(notes);
  }

  private final Map<Integer, Long> active = new HashMap<>();
  private boolean recording, waiting, updating;
  private long origin;
  private int cursor;
  private final javax.swing.Timer timer;
  private final UndoManager history = new UndoManager();
  private State lastState;
  private final JLabel saveState = new JLabel();
  private final JLabel selectedInfo = new JLabel("캔버스에서 음을 선택하세요");
  private long lastClick = Long.MIN_VALUE;
  private final JButton undo = Theme.button("실행 취소", () -> undo()),
      redo = Theme.button("다시 실행", () -> redo());

  private record State(List<NoteEvent> notes, String memo, int bpm, int instrument, int cursor) {}

  private State state() {
    return new State(
        List.copyOf(notes),
        memo.getText(),
        (int) bpm.getValue(),
        instrument.getSelectedIndex(),
        cursor);
  }

  private void commitEdit() {
    if (updating || lastState == null) return;
    State before = lastState, after = state();
    if (before.equals(after)) return;
    lastState = after;
    history.addEdit(
        new AbstractUndoableEdit() {
          public void undo() {
            super.undo();
            restore(before);
          }

          public void redo() {
            super.redo();
            restore(after);
          }
        });
    dirty = true;
  }

  private void restore(State s) {
    updating = true;
    notes.clear();
    notes.addAll(s.notes());
    memo.setText(s.memo());
    bpm.setValue(s.bpm());
    instrument.setSelectedIndex(s.instrument());
    cursor = s.cursor();
    canvas.selected = -1;
    lastState = s;
    updating = false;
    refreshDirty();
    canvas.repaint();
  }

  private void refreshDirty() {
    try {
      dirty = !snapshot().equals(base);
    } catch (IllegalArgumentException ex) {
      dirty = true;
    }
  }

  public void undo() {
    stop();
    commitEdit();
    if (history.canUndo()) history.undo();
  }

  public void redo() {
    stop();
    if (history.canRedo()) history.redo();
  }

  public EditorPanel(MMM app, Song song, Phrase base) {
    super(new BorderLayout(12, 12));
    this.app = app;
    this.song = song;
    this.base = base;
    notes.addAll(base.notes());
    cursor = notes.stream().mapToInt(n -> n.start() + n.duration()).max().orElse(0);
    bpm = new JSpinner(new SpinnerNumberModel(base.bpm(), 40, 200, 1));
    bpm.setEnabled(!song.shared());
    instrument.setSelectedIndex(MMM.instrumentIndex(base.instrument()));
    memo.setText(base.memo());
    JPanel p = new JPanel(new BorderLayout(10, 10));
    p.setBorder(BorderFactory.createEmptyBorder(14, 22, 14, 22));
    add(p);
    p.add(
        Theme.row(
            Theme.label("S06 · 오늘의 한 마디", 26),
            new JLabel(base.date() + " · " + song.name() + " · 4/4")),
        BorderLayout.NORTH);
    JPanel center = new JPanel(new BorderLayout(10, 8));
    p.add(center, BorderLayout.CENTER);
    JPanel toolbar = Theme.column();
    toolbar.add(Theme.row(new JLabel("악기"), instrument, new JLabel("BPM"), bpm, pentatonic, mode));
    toolbar.add(
        Theme.row(
            Theme.button("녹음 시작", this::record),
            Theme.button("정지", this::stop),
            Theme.button("다시 연주", this::clear),
            status));
    center.add(toolbar, BorderLayout.NORTH);
    canvas =
        new NoteCanvas(
            () -> notes,
            true,
            (i, n) -> {
              rememberBefore();
              notes.set(i, n);
              dirty = true;
              select(i);
            },
            this::select);
    canvas.gestureFinished = this::commitEdit;
    canvas.setMinimumSize(new Dimension(450, 150));
    canvas.getInputMap().put(KeyStroke.getKeyStroke("DELETE"), "delete-note");
    canvas.getInputMap().put(KeyStroke.getKeyStroke("BACK_SPACE"), "delete-note");
    canvas
        .getActionMap()
        .put(
            "delete-note",
            new AbstractAction() {
              public void actionPerformed(ActionEvent e) {
                delete();
              }
            });
    JPanel drawing = new JPanel(new BorderLayout(10, 8));
    drawing.add(canvas);
    JPanel inspector = Theme.column();
    inspector.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
    inspector.add(selectedInfo);
    inspector.add(new JLabel("길이 · 1/4박 단위"));
    inspector.add(sized(length));
    inspector.add(new JLabel("높낮이 · C3~C6"));
    inspector.add(sized(pitch));
    inspector.add(new JLabel("세기 · 1~127"));
    inspector.add(sized(velocity));
    inspector.add(Theme.button("선택 음 삭제", this::delete));
    JRadioButton rest = new JRadioButton("쉼으로"),
        shorter = new JRadioButton("짧게 끊기", true),
        louder = new JRadioButton("점점 세게");
    ButtonGroup expressions = new ButtonGroup();
    expressions.add(rest);
    expressions.add(shorter);
    expressions.add(louder);
    inspector.add(rest);
    inspector.add(shorter);
    inspector.add(louder);
    inspector.add(
        Theme.button(
            "표현 적용",
            () -> {
              stop();
              rememberBefore();
              if (rest.isSelected()) {
                delete();
                return;
              }
              if (shorter.isSelected()) {
                if (canvas.selected < 0) {
                  status.setText("먼저 음을 선택해 주세요.");
                  return;
                }
                int i = canvas.selected;
                NoteEvent n = notes.get(i);
                notes.set(
                    i,
                    new NoteEvent(
                        n.start(),
                        Math.max(120, Model.snap(n.duration() / 2)),
                        n.pitch(),
                        n.velocity()));
              } else {
                for (int i = 0; i < notes.size(); i++) {
                  NoteEvent n = notes.get(i);
                  notes.set(
                      i,
                      new NoteEvent(
                          n.start(),
                          n.duration(),
                          n.pitch(),
                          Math.min(127, 45 + n.start() * 70 / Model.BAR)));
                }
              }
              commitEdit();
              canvas.repaint();
            }));
    JScrollPane inspectorScroll = new JScrollPane(inspector);
    inspectorScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    inspectorScroll.setBorder(BorderFactory.createLineBorder(Theme.LINE));
    inspectorScroll.setPreferredSize(new Dimension(250, 300));
    inspectorScroll.getVerticalScrollBar().setUnitIncrement(16);
    drawing.add(inspectorScroll, BorderLayout.EAST);
    center.add(drawing);
    JPanel input = new JPanel(new BorderLayout(0, 4));
    piano = new Piano(pentatonic::isSelected, this::press, this::release);
    piano.setPreferredSize(new Dimension(800, 76));
    input.add(piano, BorderLayout.NORTH);
    input.add(
        Theme.row(
            new JLabel("메모"),
            memo,
            Theme.button(
                "쉼 1박",
                () -> {
                  cursor = Math.min(Model.BAR, cursor + 480);
                  commitEdit();
                  status.setText("다음 입력 위치: " + cursor / 480.0 + "박");
                }),
            saveState),
        BorderLayout.SOUTH);
    center.add(input, BorderLayout.SOUTH);
    JPanel footer = Theme.column();
    footer.add(new Transport(app, "MIDI 미리 듣기", this::preview));
    footer.add(
        Theme.row(
            Theme.button("주간으로", app::week),
            undo,
            redo,
            Theme.button("편집 전 듣기", this::compare),
            Theme.button(
                "MIDI 내보내기",
                () -> {
                  try {
                    app.exportMidi(
                        List.of(new MidiEngine.Part(snapshot(), 0, 0, 100)),
                        Model.BAR,
                        (int) bpm.getValue());
                  } catch (Exception ex) {
                    app.error(ex);
                  }
                }),
            Theme.primary("저장", () -> app.saveEditor(app::week))));
    p.add(footer, BorderLayout.SOUTH);
    ChangeListener changed =
        e -> {
          if (updating || canvas.selected < 0 || canvas.selected >= notes.size()) return;
          rememberBefore();
          int i = canvas.selected;
          NoteEvent n = notes.get(i);
          int duration = Math.min(length.getValue() * Model.GRID, Model.BAR - n.start());
          notes.set(i, new NoteEvent(n.start(), duration, pitch.getValue(), velocity.getValue()));
          dirty = true;
          select(i);
          canvas.repaint();
          if (!length.getValueIsAdjusting()
              && !pitch.getValueIsAdjusting()
              && !velocity.getValueIsAdjusting()) commitEdit();
        };
    length.addChangeListener(changed);
    pitch.addChangeListener(changed);
    velocity.addChangeListener(changed);
    bpm.addChangeListener(
        e -> {
          if (updating) return;
          stop();
          dirty = true;
          commitEdit();
        });
    instrument.addActionListener(
        e -> {
          if (updating) return;
          stop();
          dirty = true;
          commitEdit();
        });
    memo.getDocument()
        .addDocumentListener(
            new DocumentListener() {
              public void insertUpdate(DocumentEvent e) {
                if (!updating) {
                  dirty = true;
                  commitEdit();
                }
              }

              public void removeUpdate(DocumentEvent e) {
                if (!updating) {
                  dirty = true;
                  commitEdit();
                }
              }

              public void changedUpdate(DocumentEvent e) {
                if (!updating) {
                  dirty = true;
                  commitEdit();
                }
              }
            });
    select(-1);
    saveState.setText(base.version() == 0 ? "새 기록" : "✓ 저장됨");
    lastState = state();
    int command = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    bind(KeyStroke.getKeyStroke(KeyEvent.VK_Z, command), "undo", this::undo);
    bind(
        KeyStroke.getKeyStroke(KeyEvent.VK_Z, command | InputEvent.SHIFT_DOWN_MASK),
        "redo",
        this::redo);
    bind(KeyStroke.getKeyStroke(KeyEvent.VK_S, command), "save", () -> app.saveEditor(app::week));
    piano.getInputMap().put(KeyStroke.getKeyStroke("SPACE"), "preview");
    piano
        .getActionMap()
        .put(
            "preview",
            new AbstractAction() {
              public void actionPerformed(ActionEvent e) {
                if (app.midi.playing()) stop();
                else preview();
              }
            });
    timer = new javax.swing.Timer(30, e -> tick());
    timer.start();
  }

  private JSlider sized(JSlider s) {
    s.setPreferredSize(new Dimension(200, 28));
    s.setMaximumSize(new Dimension(230, 32));
    return s;
  }

  private void select(int i) {
    boolean valid = i >= 0 && i < notes.size();
    length.setEnabled(valid);
    pitch.setEnabled(valid);
    velocity.setEnabled(valid);
    if (!valid) {
      selectedInfo.setText("캔버스에서 음을 선택하세요");
      return;
    }
    updating = true;
    NoteEvent n = notes.get(i);
    selectedInfo.setText(
        NoteCanvas.noteName(n.pitch()) + " · " + n.duration() / 480.0 + "박 · 세기 " + n.velocity());
    length.setValue(n.duration() / Model.GRID);
    pitch.setValue(n.pitch());
    velocity.setValue(n.velocity());
    updating = false;
  }

  private long ticks() {
    return Math.round(
        (System.nanoTime() - origin) * (int) bpm.getValue() * Model.PPQ / 60_000_000_000.0);
  }

  private void press(int p) {
    app.midi.on(p, MMM.PROGRAMS[instrument.getSelectedIndex()]);
    if (waiting) return;
    if (mode.getSelectedIndex() == 0) {
      if (cursor >= Model.BAR) {
        status.setText("4박이 찼어요. 음을 다듬거나 ‘다시 연주’를 눌러 주세요.");
        return;
      }
      if (notes.size() >= 128) return;
      int d = Math.min(480, Model.BAR - cursor);
      notes.add(new NoteEvent(cursor, d, p, 90));
      canvas.selected = notes.size() - 1;
      select(canvas.selected);
      cursor += d;
      dirty = true;
      commitEdit();
      canvas.repaint();
    } else if (recording) active.putIfAbsent(p, Math.max(0, ticks()));
  }

  private void release(int p) {
    app.midi.off(p);
    Long start = active.remove(p);
    if (start != null) {
      int s = Math.min(Model.BAR - Model.GRID, Math.max(0, Model.snap(start)));
      int end = Math.min(Model.BAR, Math.max(s + Model.GRID, Model.snap(ticks())));
      if (notes.size() < 128) {
        notes.add(new NoteEvent(s, end - s, p, 90));
        dirty = true;
        canvas.repaint();
      }
    }
  }

  private void record() {
    if (mode.getSelectedIndex() == 0) {
      status.setText("단계 입력은 건반을 누를 때마다 1박씩 들어갑니다.");
      piano.requestFocusInWindow();
      return;
    }
    if (!notes.isEmpty()
        && JOptionPane.showConfirmDialog(
                this, "현재 음을 지우고 녹음할까요?", "D05 · 다시 녹음", JOptionPane.OK_CANCEL_OPTION)
            != JOptionPane.OK_OPTION) return;
    stop();
    notes.clear();
    beforeEdit = null;
    dirty = true;
    cursor = 0;
    lastClick = Long.MIN_VALUE;
    waiting = app.settings.countIn();
    recording = !waiting;
    origin =
        System.nanoTime()
            + (waiting
                ? Math.round(app.settings.countInBeats() * 60_000_000_000.0 / (int) bpm.getValue())
                : 0);
    piano.requestFocusInWindow();
  }

  private void tick() {
    undo.setEnabled(history.canUndo());
    redo.setEnabled(history.canRedo());
    saveState.setText(
        (dirty ? "● 미저장" : base.version() == 0 ? "새 기록" : "✓ 저장됨")
            + " · 메모 "
            + memo.getText().codePointCount(0, memo.getText().length())
            + "/20자");
    if ((waiting || recording) && app.settings.metronome()) {
      long beat = Math.floorDiv(ticks(), 480);
      if (beat != lastClick) {
        lastClick = beat;
        app.midi.click(Math.floorMod(beat, 4) == 0);
      }
    }

    if (waiting) {
      long t = ticks();
      status.setText("준비 · " + Math.max(1, (int) Math.ceil(-t / 480.0)) + "  (시각 카운트인)");
      if (t >= 0) {
        waiting = false;
        recording = true;
      }
    }
    if (recording) {
      canvas.playhead = Math.min(Model.BAR - 1, ticks());
      status.setText("녹음 중 · " + Math.min(4, 1 + ticks() / 480) + " / 4박");
      if (ticks() >= Model.BAR) {
        stop();
        status.setText("녹음 완료 · 음을 드래그해 다듬어 보세요.");
      }
    } else canvas.playhead = app.midi.position();
    canvas.repaint();
  }

  private void compare() {
    try {
      stop();
      Phrase now = snapshot();
      Phrase before =
          new Phrase(
              now.songId(),
              now.author(),
              now.date(),
              now.version(),
              now.bpm(),
              now.instrument(),
              now.memo(),
              beforeEdit == null ? notes : beforeEdit);
      app.play(List.of(new MidiEngine.Part(before, 0, 0, 100)), Model.BAR, now.bpm());
    } catch (Exception ex) {
      app.error(ex);
    }
  }

  public void preview() {
    try {
      stop();
      app.play(
          List.of(new MidiEngine.Part(snapshot(), 0, 0, 100)), Model.BAR, (int) bpm.getValue());
    } catch (Exception ex) {
      app.error(ex);
    }
  }

  private void bind(KeyStroke key, String name, Runnable action) {
    getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(key, name);
    getActionMap()
        .put(
            name,
            new AbstractAction() {
              public void actionPerformed(ActionEvent e) {
                action.run();
              }
            });
  }

  public void stop() {
    waiting = false;
    for (int p : new ArrayList<>(active.keySet())) release(p);
    recording = false;
    if (piano != null) piano.releaseAll();
    app.midi.stop();
    commitEdit();
  }

  public void dispose() {
    stop();
    timer.stop();
  }

  private void clear() {
    if (!notes.isEmpty()
        && JOptionPane.showConfirmDialog(
                this, "현재 초안의 음을 지울까요? 메모는 유지됩니다.", "D05 · 다시 연주", JOptionPane.OK_CANCEL_OPTION)
            != JOptionPane.OK_OPTION) return;
    stop();
    notes.clear();
    beforeEdit = null;
    cursor = 0;
    canvas.selected = -1;
    dirty = true;
    canvas.repaint();
    commitEdit();
    status.setText("새로운 소리를 기다리고 있어요.");
  }

  private void delete() {
    if (canvas.selected >= 0 && canvas.selected < notes.size()) {
      rememberBefore();
      notes.remove(canvas.selected);
      canvas.selected = -1;
      cursor = notes.stream().mapToInt(n -> n.start() + n.duration()).max().orElse(0);
      dirty = true;
      commitEdit();
      canvas.repaint();
    }
  }

  public Phrase snapshot() {
    return new Phrase(
        base.songId(),
        base.author(),
        base.date(),
        base.version(),
        (int) bpm.getValue(),
        MMM.PROGRAMS[instrument.getSelectedIndex()],
        memo.getText(),
        notes);
  }
}
