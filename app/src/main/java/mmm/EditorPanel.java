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
  private final JLabel selectedInfo = new JLabel("선택한 소리");
  private JPanel inspectorCards;
  private JLabel lengthValue = new JLabel(),
      pitchValue = new JLabel(),
      velocityValue = new JLabel();
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
    select(-1);
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
    instrument.setRenderer(
        (list, value, index, selected, focus) -> {
          int i = index < 0 ? instrument.getSelectedIndex() : index;
          JLabel label =
              new JLabel(
                  value, ArtAssets.instrument(MMM.PROGRAMS[Math.max(0, i)], 24), JLabel.LEFT);
          label.setOpaque(true);
          label.setBackground(selected ? Theme.WASH : Theme.SURFACE);
          label.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
          return label;
        });
    memo.setText(base.memo());
    JPanel p = new JPanel(new BorderLayout(18, 16));
    p.setBorder(BorderFactory.createEmptyBorder(24, 30, 20, 30));
    add(p);
    JPanel heading = new JPanel(new BorderLayout());
    JPanel titles = Theme.column();
    titles.add(Theme.label("오늘은 어떤 소리였나요?", 26));
    titles.add(Box.createVerticalStrut(4));
    titles.add(
        Theme.muted(
            base.date().getMonthValue()
                + "월 "
                + base.date().getDayOfMonth()
                + "일 · "
                + song.name()));
    heading.add(titles);
    JButton more = Theme.button("더 보기 ···", () -> {});
    more.addActionListener(
        e -> {
          JPopupMenu menu = new JPopupMenu();
          item(menu, "다시 연주", this::clear);
          item(
              menu,
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
              });
          menu.show(more, 0, more.getHeight());
        });
    heading.add(more, BorderLayout.EAST);
    p.add(heading, BorderLayout.NORTH);
    JPanel center = new JPanel(new BorderLayout(12, 12));
    p.add(center);
    JPanel top = Theme.column();
    JPanel tools = new JPanel(new BorderLayout());
    tools.add(Theme.row(new JLabel("악기"), instrument, new JLabel("BPM"), bpm), BorderLayout.WEST);
    tools.add(Theme.row(undo, redo, Theme.button("편집 전 듣기", this::compare)), BorderLayout.EAST);
    top.add(tools);
    JPanel options =
        Theme.row(
            mode,
            pentatonic,
            Theme.secondary("녹음 시작", this::record),
            Theme.button("다시 연주", this::clear));
    JToggleButton disclose = new JToggleButton("연주 설정 ▾");
    disclose.setFont(Theme.font(12, false));
    disclose.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
    disclose.setContentAreaFilled(false);
    options.setVisible(false);
    disclose.addActionListener(
        e -> {
          options.setVisible(disclose.isSelected());
          disclose.setText(disclose.isSelected() ? "연주 설정 ▴" : "연주 설정 ▾");
          center.revalidate();
        });
    top.add(Theme.row(disclose, Theme.muted("4/4 · 한 마디 · 1/4박 맞춤")));
    top.add(options);
    center.add(top, BorderLayout.NORTH);
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
    canvas.setMinimumSize(new Dimension(450, 160));
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
    JPanel drawing = new JPanel(new BorderLayout(20, 0));
    JPanel canvasCard = Theme.card(new BorderLayout());
    canvasCard.setBorder(BorderFactory.createEmptyBorder(4, 3, 4, 3));
    canvasCard.add(canvas);
    drawing.add(canvasCard);
    inspectorCards = new JPanel(new CardLayout());
    inspectorCards.setPreferredSize(new Dimension(236, 300));
    inspectorCards.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.LINE),
            BorderFactory.createEmptyBorder(10, 20, 8, 0)));
    JPanel empty = new JPanel(new GridBagLayout());
    empty.add(
        new JLabel(
            "<html><div style='width:165px;color:#7e786c'>소리 하나를 선택해 보세요.<br><br>꼬리가 길수록"
                + " 오래,<br>덩어리가 두꺼울수록 세게,<br>위에 있을수록 높게 들려요.</div></html>"));
    inspectorCards.add(empty, "empty");
    JPanel inspector = Theme.column();
    selectedInfo.setFont(Theme.font(16, true));
    inspector.add(selectedInfo);
    inspector.add(Box.createVerticalStrut(8));
    inspector.add(field("길이", lengthValue, length));
    inspector.add(field("높낮이", pitchValue, pitch));
    inspector.add(field("세기", velocityValue, velocity));
    JButton expression = Theme.button("표현 ···", () -> {});
    expression.addActionListener(
        e -> {
          JPopupMenu actions = new JPopupMenu();
          item(actions, "짧게 끊기", () -> applyExpression(0));
          item(actions, "점점 세게", () -> applyExpression(1));
          item(actions, "쉼으로", () -> applyExpression(2));
          actions.addSeparator();
          item(actions, "선택 음 삭제", this::delete);
          actions.show(expression, 0, expression.getHeight());
        });
    inspector.add(Theme.row(expression));
    inspectorCards.add(inspector, "selected");
    drawing.add(inspectorCards, BorderLayout.EAST);
    center.add(drawing);
    JPanel input = new JPanel(new BorderLayout(0, 7));
    JPanel caption = new JPanel(new BorderLayout());
    status.setFont(Theme.font(12, false));
    status.setForeground(Theme.MUTED);
    caption.add(status);
    caption.add(Theme.muted("A S D F G H J K · 클릭 후 연주"), BorderLayout.EAST);
    input.add(caption, BorderLayout.NORTH);
    piano = new Piano(pentatonic::isSelected, this::press, this::release);
    piano.setPreferredSize(new Dimension(800, 66));
    input.add(piano);
    JPanel memoRow = new JPanel(new BorderLayout(12, 0));
    memoRow.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
    memoRow.add(Theme.muted("오늘의 메모"), BorderLayout.WEST);
    memoRow.add(memo);
    JButton rest =
        Theme.button(
            "쉼 1박",
            () -> {
              cursor = Math.min(Model.BAR, cursor + 480);
              commitEdit();
              status.setText("다음 입력 위치: " + cursor / 480.0 + "박");
            });
    memoRow.add(rest, BorderLayout.EAST);
    input.add(memoRow, BorderLayout.SOUTH);
    center.add(input, BorderLayout.SOUTH);
    JPanel footer = new JPanel(new BorderLayout(14, 0));
    footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE));
    footer.add(new Transport(app, "들어보기", this::preview));
    saveState.setFont(Theme.font(12, false));
    saveState.setForeground(Theme.MUTED);
    footer.add(
        Theme.row(saveState, Theme.primary("하루 저장", () -> app.saveEditor(app::week))),
        BorderLayout.EAST);
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

  private void item(JPopupMenu menu, String title, Runnable action) {
    JMenuItem i = new JMenuItem(title);
    i.addActionListener(e -> action.run());
    menu.add(i);
  }

  private JPanel field(String name, JLabel value, JSlider slider) {
    JPanel panel = new JPanel(new BorderLayout(0, 3));
    panel.setMaximumSize(new Dimension(300, 46));
    panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
    JPanel heading = new JPanel(new BorderLayout());
    heading.add(Theme.muted(name));
    value.setFont(Theme.font(12, false));
    heading.add(value, BorderLayout.EAST);
    panel.add(heading, BorderLayout.NORTH);
    Theme.slider(slider);
    panel.add(slider);
    return panel;
  }

  private void applyExpression(int expression) {
    stop();
    rememberBefore();
    if (expression == 2) {
      delete();
      return;
    }
    if (expression == 0) {
      if (canvas.selected < 0) {
        status.setText("먼저 소리를 선택해 주세요.");
        return;
      }
      int i = canvas.selected;
      NoteEvent n = notes.get(i);
      notes.set(
          i,
          new NoteEvent(
              n.start(), Math.max(120, Model.snap(n.duration() / 2)), n.pitch(), n.velocity()));
    } else
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
    commitEdit();
    select(canvas.selected);
    canvas.repaint();
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
    if (inspectorCards != null)
      ((CardLayout) inspectorCards.getLayout()).show(inspectorCards, valid ? "selected" : "empty");
    if (!valid) {
      selectedInfo.setText("선택한 소리");
      return;
    }
    updating = true;
    NoteEvent n = notes.get(i);
    selectedInfo.setText("선택한 소리 · " + NoteCanvas.noteName(n.pitch()));
    lengthValue.setText(n.duration() / 480.0 + "박");
    pitchValue.setText(NoteCanvas.noteName(n.pitch()));
    velocityValue.setText(Integer.toString(n.velocity()));
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
    select(-1);
    status.setText("새로운 소리를 기다리고 있어요.");
  }

  private void delete() {
    if (canvas.selected >= 0 && canvas.selected < notes.size()) {
      rememberBefore();
      notes.remove(canvas.selected);
      canvas.selected = -1;
      cursor = notes.stream().mapToInt(n -> n.start() + n.duration()).max().orElse(0);
      dirty = true;
      select(-1);
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
