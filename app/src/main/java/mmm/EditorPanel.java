package mmm;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.undo.*;
import javax.swing.text.*;
import mmm.Model.*;

/** S06: capture -> edit -> preview, all using one immutable-event draft. */
public final class EditorPanel extends JPanel {
  private final MMM app;
  private final Song song;
  private Phrase base;
  public final List<NoteEvent> notes = new ArrayList<>();
  public final List<RestEvent> rests = new ArrayList<>();
  public final List<ExpressionRange> expressions = new ArrayList<>();
  public boolean dirty;
  public final JTextField memo = new JTextField(20);
  private final JSpinner bpm;
  private final JComboBox<String> instrument = new JComboBox<>(MMM.INSTRUMENT_NAMES);
  private final JButton chordFinder = Theme.button("감정으로 화음 찾기", this::findChord);
  private final JCheckBox pentatonic = new JCheckBox("5음계 · 어울리는 음만", false);
  private final JComboBox<String> mode =
      new JComboBox<>(new String[] {"단계 입력 · 1박씩", "실시간 입력 · 4박 녹음", "누른 길이대로 · 바로 입력"});
  private final JSlider inputVelocity = new JSlider(1,127,90);
  private final JSlider length = new JSlider(1, 16, 4),
      pitch = new JSlider(Model.MIN_PITCH, Model.MAX_PITCH, 60),
      velocity = new JSlider(1, 127, 90);
  public final NoteCanvas canvas;
  public final Piano piano;
  public final StringInstrument bass, guitar;
  public final DrumKit drums;
  public final LiveSoundView liveView;
  private JPanel stage, performances, keyboard;
  private final JButton performanceToggle=Theme.secondary("음 편집",this::togglePerformance);
  private final JButton newTake=Theme.button("새로 연주",this::clear);
  private boolean performanceView=true;
  private final JPanel pitchControls=new JPanel(new CardLayout());
  private final JComboBox<String> drumChoice=new JComboBox<>(DrumKit.NAMES);
  private JLabel pitchCaption;
  private final JLabel keyboardRange=Theme.muted("");
  private final JLabel status = new JLabel("건반을 클릭하거나 A–K · W E T Y U로 연주해 보세요.");
  private List<NoteEvent> beforeEdit;
  private List<RestEvent> beforeRests;
  private List<ExpressionRange> beforeExpressions;

  private void rememberBefore() {
    if (beforeEdit == null) {
      beforeEdit = List.copyOf(notes);
      beforeRests = List.copyOf(rests);
      beforeExpressions = List.copyOf(expressions);
    }
  }

  private final Map<Integer, Long> active = new HashMap<>();
  private final Map<Integer,Integer> activeVelocity=new HashMap<>();
  private boolean heldSession;
  private int heldStart;
  private boolean recording, waiting, updating;
  private long origin;
  private java.util.function.LongSupplier nanoTime=System::nanoTime;
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

  private record State(List<NoteEvent> notes, List<RestEvent> rests,
                       List<ExpressionRange> expressions, String memo, int bpm, int instrument, int cursor) {}

  private State state() {
    return new State(
        List.copyOf(notes),
        List.copyOf(rests),
        List.copyOf(expressions),
        memo.getText(),
        (int) bpm.getValue(),
        instrument.getSelectedIndex(),
        cursor);
  }

  private void commitEdit() {
    if (updating || lastState == null) return;
    State before = lastState, after = state();
    if (before.equals(after)) { refreshDirty(); return; }
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
    refreshDirty();
  }

  private void restore(State s) {
    updating = true;
    notes.clear();
    notes.addAll(s.notes());
    rests.clear();
    rests.addAll(s.rests());
    expressions.clear();
    expressions.addAll(s.expressions());
    memo.setText(s.memo());
    bpm.setValue(s.bpm());
    instrument.setSelectedIndex(s.instrument());
    cursor = s.cursor();
    canvas.selected = -1;
    canvas.selectedRest = -1;
    select(-1);
    lastState = s;
    updating = false;
    updatePerformance();
    refreshDirty();
    canvas.repaint();
  }

  private void refreshDirty() {
    try {
      dirty = !active.isEmpty() || !snapshot().equals(base);
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
    mode.setSelectedIndex(2);
    notes.addAll(base.notes());
    rests.addAll(base.rests());
    expressions.addAll(base.expressions());
    resetCursor();
    bpm = new JSpinner(new SpinnerNumberModel(base.bpm(), 40, 200, 1));
    bpm.setEnabled(!song.shared());
    MMM.selectInstrument(instrument,base.instrument());
    instrument.setRenderer(
        (list, value, index, selected, focus) -> {
          int i = index < 0 ? instrument.getSelectedIndex() : index;
          JLabel label =
              new JLabel(
                  value, ArtAssets.instrument(MMM.instrumentProgram(instrument,Math.max(0,i)), 24), JLabel.LEFT);
          label.setOpaque(true);
          label.setBackground(selected ? Theme.WASH : Theme.SURFACE);
          label.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
          return label;
        });
    memo.setText(base.memo());
    ((AbstractDocument) memo.getDocument()).setDocumentFilter(new DocumentFilter() {
      public void insertString(FilterBypass fb, int offset, String text, AttributeSet attrs)
          throws BadLocationException {
        replace(fb, offset, 0, text, attrs);
      }
      public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
          throws BadLocationException {
        String old = fb.getDocument().getText(0, fb.getDocument().getLength());
        String next = old.substring(0, offset) + (text == null ? "" : text) + old.substring(offset + length);
        if (next.codePointCount(0, next.length()) > 20 || next.contains("\n") || next.contains("\r")) {
          status.setText("메모는 공백 포함 한 줄 20자까지 입력할 수 있어요.");
          Toolkit.getDefaultToolkit().beep();
          return;
        }
        boolean wasUpdating = updating;
        updating = true;
        try {
          super.replace(fb, offset, length, text, attrs);
        } finally {
          updating = wasUpdating;
        }
        if (!wasUpdating) commitEdit();
      }
    });
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
    tools.add(Theme.row(newTake,performanceToggle,undo, redo, Theme.button("편집 전 듣기", this::compare)), BorderLayout.EAST);
    top.add(tools);
    JPanel options =
        Theme.row(
            mode,
            pentatonic,
            Theme.secondary("녹음 시작", this::record),
            Theme.button("다시 연주", this::clear));
    Theme.slider(inputVelocity);inputVelocity.setPreferredSize(new Dimension(100,24));
    inputVelocity.setToolTipText("연주 세기 · 키보드와 마우스는 누르는 압력을 측정하지 않습니다.");
    options.add(new JLabel("연주 세기"));options.add(inputVelocity);
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
    top.add(Theme.row(disclose, chordFinder,
        Theme.muted("4/4 · 한 마디 · 1/4박 맞춤")));
    top.add(options);
    center.add(top, BorderLayout.NORTH);
    canvas =
        new NoteCanvas(
            () -> notes,
            true,
            (i, n) -> {
              List<NoteEvent> next = new ArrayList<>(notes);
              next.set(i, n);
              if (!validTimeline(next, rests, expressions)) return;
              rememberBefore();
              notes.set(i, n);
              dirty = true;
              select(i);
            },
            this::select);
    canvas.gestureFinished = this::commitEdit;
    canvas.liveSource=this::pendingNotes;
    canvas.restSource = () -> rests;
    canvas.expressionSource = () -> expressions;
    canvas.restSelect = this::selectRest;
    canvas.restEdit = (i, r) -> {
      List<RestEvent> next = new ArrayList<>(rests);
      next.set(i, r);
      if (!validTimeline(notes, next, expressions)) return;
      rememberBefore();
      rests.set(i, r);
      selectRest(i);
      dirty = true;
    };
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
    JPanel inspector = new JPanel(new BorderLayout(0, 5));
    selectedInfo.setFont(Theme.font(16, true));
    JButton expression = Theme.button("표현 ···", () -> {});
    expression.setFont(Theme.font(12, false));
    expression.setBorder(BorderFactory.createEmptyBorder(4, 3, 4, 0));
    expression.addActionListener(
        e -> {
          JPopupMenu actions = new JPopupMenu();
          item(actions, "짧게 끊기", () -> applyExpression(0));
          item(actions, "보통 음으로", this::normalArticulation);
          item(actions, "점점 세게", () -> applyExpression(1));
          item(actions, "크레센도 지우기", this::clearExpressions);
          item(actions, "쉼으로", () -> applyExpression(2));
          actions.addSeparator();
          item(actions, "선택 음 삭제", this::delete);
          actions.show(expression, 0, expression.getHeight());
        });
    JPanel selectionHeading = new JPanel(new BorderLayout(4, 0));
    selectionHeading.add(selectedInfo);
    selectionHeading.add(expression, BorderLayout.EAST);
    inspector.add(selectionHeading, BorderLayout.NORTH);
    JPanel properties = Theme.column();
    properties.add(field("길이", lengthValue, length));
    Theme.slider(pitch);pitchControls.add(pitch,"pitch");pitchControls.add(drumChoice,"drum");
    properties.add(field("높낮이", pitchValue, pitchControls));
    properties.add(field("세기", velocityValue, velocity));
    JScrollPane propertyScroll = new JScrollPane(properties);
    propertyScroll.setBorder(null);
    propertyScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    propertyScroll.getVerticalScrollBar().setUnitIncrement(14);
    inspector.add(propertyScroll);
    inspectorCards.add(inspector, "selected");
    drawing.add(inspectorCards, BorderLayout.EAST);
    stage=new JPanel(new CardLayout());stage.add(drawing,"notes");
    performances=new JPanel(new CardLayout());
    bass=new StringInstrument(true,this::pressGroup,this::releaseGroup);
    guitar=new StringInstrument(false,this::pressGroup,this::releaseGroup);
    drums=new DrumKit(this::pressGroup,this::releaseGroup);
    performances.add(bass,"bass");performances.add(guitar,"guitar");performances.add(drums,"drums");
    liveView=new LiveSoundView(()->notes.stream().map(n->new NoteEvent(n.start(),n.duration(),n.pitch(),Model.effectiveVelocity(n,expressions),n.articulation())).toList(),this::pendingNotes);
    JPanel livePerformance=new JPanel(new BorderLayout(10,0));livePerformance.add(performances);livePerformance.add(liveView,BorderLayout.EAST);
    stage.add(livePerformance,"performance");center.add(stage);
    JPanel input = new JPanel(new BorderLayout(0, 7));
    JPanel caption = new JPanel(new BorderLayout());
    status.setFont(Theme.font(12, false));
    status.setForeground(Theme.MUTED);
    caption.add(status);
    caption.add(keyboardRange, BorderLayout.EAST);
    input.add(caption, BorderLayout.NORTH);
    piano = new Piano(pentatonic::isSelected, this::press, this::release);
    piano.setPreferredSize(new Dimension(800, 112));
    pentatonic.addActionListener(e -> {piano.releaseAll();piano.repaint();});
    JButton lower=Theme.secondary("‹",()->{piano.shiftOctave(-1);piano.requestFocusInWindow();});
    JButton higher=Theme.secondary("›",()->{piano.shiftOctave(1);piano.requestFocusInWindow();});
    lower.setToolTipText("한 옥타브 낮게 · ←");higher.setToolTipText("한 옥타브 높게 · →");
    lower.getAccessibleContext().setAccessibleName("낮은 음역으로 이동");
    higher.getAccessibleContext().setAccessibleName("높은 음역으로 이동");
    for(JButton arrow:List.of(lower,higher)) {
      arrow.setFont(Theme.font(26,false));arrow.setPreferredSize(new Dimension(38,84));arrow.setFocusable(false);
    }
    Runnable updateRange=()->{
      if(instrumentProgram()!=33 && instrumentProgram()!=24 && instrumentProgram()!=Model.DRUMS)
        keyboardRange.setText(piano.rangeLabel()+" · A–K / W E T Y U · ← → 음역 이동");
      lower.setEnabled(piano.canShift(-1));higher.setEnabled(piano.canShift(1));
    };
    piano.addPropertyChangeListener("pitchRange",event->updateRange.run());updateRange.run();
    keyboard=new JPanel(new BorderLayout(7,0));
    keyboard.add(lower,BorderLayout.WEST);keyboard.add(piano);keyboard.add(higher,BorderLayout.EAST);
    input.add(keyboard);
    JPanel memoRow = new JPanel(new BorderLayout(12, 0));
    memoRow.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
    memoRow.add(Theme.muted("오늘의 메모"), BorderLayout.WEST);
    memoRow.add(memo);
    JButton rest =
        Theme.button(
            "쉼 1박",
            this::addRest);
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
          if (updating) return;
          if (canvas.selectedRest >= 0 && canvas.selectedRest < rests.size()) {
            int i = canvas.selectedRest;
            RestEvent r = rests.get(i);
            RestEvent next = new RestEvent(r.start(), Math.min(length.getValue() * Model.GRID, Model.BAR - r.start()));
            List<RestEvent> candidate = new ArrayList<>(rests);
            candidate.set(i, next);
            if (!validTimeline(notes, candidate, expressions)) { selectRest(i); return; }
            rememberBefore();
            rests.set(i, next);
            selectRest(i);
            canvas.repaint();
            if (!length.getValueIsAdjusting()) commitEdit();
            return;
          }
          if (updating || canvas.selected < 0 || canvas.selected >= notes.size()) return;
          int i = canvas.selected;
          NoteEvent n = notes.get(i);
          int duration = Math.min(length.getValue() * Model.GRID, Model.BAR - n.start());
          NoteEvent next = new NoteEvent(n.start(), duration, pitch.getValue(), velocity.getValue(), n.articulation());
          List<NoteEvent> candidate = new ArrayList<>(notes);
          candidate.set(i, next);
          if (!validTimeline(candidate, rests, expressions)) { select(i); return; }
          rememberBefore();
          notes.set(i, next);
          dirty = true;
          select(i);
          canvas.repaint();
          if (!length.getValueIsAdjusting()
              && !pitch.getValueIsAdjusting()
              && !velocity.getValueIsAdjusting()) commitEdit();
        };
    length.addChangeListener(changed);
    pitch.addChangeListener(changed);
    drumChoice.addActionListener(event->{
      if(updating || canvas.selected<0)return;
      int index=drumChoice.getSelectedIndex();
      if(index>=0)pitch.setValue(index<DrumKit.PITCHES.length?DrumKit.PITCHES[index]:(Integer)drumChoice.getClientProperty("old-drum-pitch"));
    });
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
          performanceView=true;updatePerformance();
          dirty = true;
          commitEdit();
          SwingUtilities.invokeLater(this::focusInstrument);
        });
    mode.addActionListener(e->{stop();updatePerformance();focusInstrument();});
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
    updatePerformance();
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
    for(PlayableSurface surface:List.of(bass,guitar,drums)) {
      surface.getInputMap().put(KeyStroke.getKeyStroke("SPACE"),"preview");
      surface.getActionMap().put("preview",new AbstractAction(){public void actionPerformed(ActionEvent e){preview();}});
    }
    timer = new javax.swing.Timer(30, e -> tick());
    timer.start();
  }

  private void item(JPopupMenu menu, String title, Runnable action) {
    JMenuItem i = new JMenuItem(title);
    i.addActionListener(e -> action.run());
    menu.add(i);
  }

  private JPanel field(String name, JLabel value, JComponent control) {
    JPanel panel = new JPanel(new BorderLayout(0, 3));
    panel.setMaximumSize(new Dimension(300, 46));
    panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
    JPanel heading = new JPanel(new BorderLayout());
    JLabel caption=Theme.muted(name);if(name.equals("높낮이"))pitchCaption=caption;heading.add(caption);
    value.setFont(Theme.font(12, false));
    heading.add(value, BorderLayout.EAST);
    panel.add(heading, BorderLayout.NORTH);
    if(control instanceof JSlider slider)Theme.slider(slider);
    panel.add(control);
    return panel;
  }

  private void applyExpression(int expression) {
    stop();
    if (expression == 2) {
      convertToRest();
      return;
    }
    if (expression == 0) {
      if (canvas.selected < 0) {
        status.setText("먼저 소리를 선택해 주세요.");
        return;
      }
      int i = canvas.selected;
      NoteEvent n = notes.get(i);
      rememberBefore();
      notes.set(
          i,
          new NoteEvent(
              n.start(), n.duration(), n.pitch(), n.velocity(), Articulation.STACCATO));
    } else {
      crescendoDialog();
      return;
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
    drumChoice.setEnabled(valid);
    velocity.setEnabled(valid);
    if (inspectorCards != null)
      ((CardLayout) inspectorCards.getLayout()).show(inspectorCards, valid ? "selected" : "empty");
    if (!valid) {
      selectedInfo.setText("선택한 소리");
      return;
    }
    updating = true;
    NoteEvent n = notes.get(i);
    selectedInfo.setText("소리 · " + (instrumentProgram()==Model.DRUMS?DrumKit.noteName(n.pitch()):NoteCanvas.noteName(n.pitch())));
    lengthValue.setText(n.duration() / 480.0 + "박");
    pitchValue.setText(instrumentProgram()==Model.DRUMS?DrumKit.noteName(n.pitch()):NoteCanvas.noteName(n.pitch()));
    if(instrumentProgram()==Model.DRUMS) {
      if(drumChoice.getItemCount()>DrumKit.PITCHES.length)drumChoice.removeItemAt(DrumKit.PITCHES.length);
      int index=-1;for(int d=0;d<DrumKit.PITCHES.length;d++)if(DrumKit.PITCHES[d]==n.pitch())index=d;
      if(index<0){drumChoice.putClientProperty("old-drum-pitch",n.pitch());drumChoice.addItem(DrumKit.noteName(n.pitch()));index=DrumKit.PITCHES.length;}
      drumChoice.setSelectedIndex(index);
    }
    velocityValue.setText(Integer.toString(Model.effectiveVelocity(n, expressions)));
    velocityValue.setToolTipText("기본 세기 " + n.velocity() + " · 크레센도 구간에서는 보간된 세기로 재생");
    length.setValue(n.duration() / Model.GRID);
    pitch.setValue(n.pitch());
    velocity.setValue(n.velocity());
    updating = false;
  }

  private long ticks() {
    return Math.round(
        (nanoTime.getAsLong() - origin) * (double)(int)bpm.getValue() * Model.PPQ / 60_000_000_000.0);
  }

  private long captureTicks(){return ticks()+(heldSession?heldStart:0);}

  public List<SoundShape.LiveNote> pendingNotes() {
    long now=captureTicks();List<SoundShape.LiveNote> result=new ArrayList<>();
    for(var entry:active.entrySet()) {
      double start=Math.max(0,Math.min(Model.BAR-1,entry.getValue()));
      result.add(new SoundShape.LiveNote(start,Math.max(1,Math.min(Model.BAR-start,now-start)),entry.getKey(),activeVelocity.getOrDefault(entry.getKey(),90)));
    }
    return result;
  }

  public int instrumentProgram(){return MMM.instrumentProgram(instrument);}
  private void togglePerformance(){stop();performanceView=!performanceView;updatePerformance();}
  private void updatePerformance() {
    if(stage==null || keyboard==null)return;
    int program=instrumentProgram();boolean special=program==33||program==24||program==Model.DRUMS;
    keyboard.setVisible(!special);performanceToggle.setVisible(special);performanceToggle.setText(performanceView?"음 편집":"연주 화면");
    newTake.setVisible(true);
    pentatonic.setEnabled(!special);
    chordFinder.setEnabled(program != Model.DRUMS);
    chordFinder.setToolTipText(program == Model.DRUMS ? "화음은 피아노·베이스·기타에서 넣을 수 있어요." : "연구 근거를 확인하고 화음을 추가합니다.");
    ((CardLayout)pitchControls.getLayout()).show(pitchControls,program==Model.DRUMS?"drum":"pitch");
    if(pitchCaption!=null)pitchCaption.setText(program==Model.DRUMS?"타악기":"높낮이");
    ((CardLayout)stage.getLayout()).show(stage,special&&performanceView?"performance":"notes");
    if(special)((CardLayout)performances.getLayout()).show(performances,program==33?"bass":program==24?"guitar":"drums");
    if(program==Model.DRUMS)status.setText("드럼을 클릭하거나 A S D F G H J K L · 단계 입력은 한 번에 1/4박");
    else if(special)status.setText("코드 이름은 코드 연주 · 줄을 클릭하면 한 음 · A–K로 연주");
    else status.setText("건반을 클릭하거나 A–K · W E T Y U로 연주해 보세요.");
    if(mode.getSelectedIndex()==2)status.setText("누르면 바로 그림이 생겨요 · 누른 만큼 길게 · 떼면 1/4박 단위로 기록");
    keyboardRange.setText(program==33?"베이스 · 4줄 · 코드/노트":program==24?"기타 · 6줄 · 코드/노트":program==Model.DRUMS?"킥 · 스네어 · 하이햇 · 탐 · 심벌":piano.rangeLabel()+" · A–K / W E T Y U · ← → 음역 이동");
    select(canvas.selected);
    revalidate();repaint();
  }
  private void focusInstrument() {
    int program=instrumentProgram();
    if(program==33)bass.requestFocusInWindow();else if(program==24)guitar.requestFocusInWindow();else if(program==Model.DRUMS)drums.requestFocusInWindow();else piano.requestFocusInWindow();
  }
  private void press(int p) {pressGroup(new int[]{p});}
  private void pressGroup(int[] pitches) {
    int program=instrumentProgram(),strength=inputVelocity.getValue();for(int p:pitches)app.midi.on(p,program,strength);
    if (waiting) return;
    if(mode.getSelectedIndex()==2 && !recording) {
      if(cursor>=Model.BAR && active.isEmpty()){status.setText("4박이 찼어요. ‘새로 연주’ 또는 ‘다시 연주’로 새 입력을 시작하세요.");return;}
      if(notes.size()+active.size()+pitches.length>128)return;
      if(active.isEmpty()){heldSession=true;heldStart=cursor;origin=nanoTime.getAsLong();}
      long start=captureTicks();if(start>=Model.BAR){stop();return;}
      for(int p:pitches){active.putIfAbsent(p,start);activeVelocity.putIfAbsent(p,strength);}
      dirty=true;canvas.repaint();liveView.repaint();
    } else if (mode.getSelectedIndex() == 0) {
      if (cursor >= Model.BAR) {
        status.setText("4박이 찼어요. 음을 다듬거나 ‘다시 연주’를 눌러 주세요.");
        return;
      }
      if (notes.size()+pitches.length > 128) return;
      int d = Math.min(program==Model.DRUMS?Model.GRID:480, Model.BAR - cursor);
      List<NoteEvent> candidate = new ArrayList<>(notes);
      for(int p:pitches)candidate.add(new NoteEvent(cursor,d,p,strength));
      if (!validTimeline(candidate, rests, expressions)) return;
      notes.clear();notes.addAll(candidate);
      canvas.selectedRest = -1;
      canvas.selected = notes.size() - 1;
      select(canvas.selected);
      cursor += d;
      dirty = true;
      commitEdit();
      canvas.repaint();
    } else if (recording) {
      long tick = ticks();
      if (tick >= Model.BAR) { stop(); return; }
      if(program==Model.DRUMS) {
        int start=Math.max(0,Math.min(Model.BAR-Model.GRID,Model.snap(tick)));
        List<NoteEvent> candidate=new ArrayList<>(notes);
        for(int p:pitches)candidate.add(new NoteEvent(start,Model.GRID,p,strength));
        if(candidate.size()<=128 && validTimeline(candidate,rests,expressions)){notes.clear();notes.addAll(candidate);dirty=true;commitEdit();canvas.repaint();}
      }else if(notes.size()+active.size()+pitches.length<=128)for(int p:pitches){active.putIfAbsent(p, Math.max(0,tick));activeVelocity.putIfAbsent(p,strength);}
    }
  }
  private void releaseGroup(int[] pitches){long end=captureTicks();for(int p:pitches)release(p,end);}

  private void release(int p) {
    release(p,captureTicks());
  }
  private void release(int p,long endTick) {
    app.midi.off(p);
    Long start = active.remove(p);
    int strength=activeVelocity.getOrDefault(p,90);activeVelocity.remove(p);
    if (start != null) {
      int s = Math.min(Model.BAR - Model.GRID, Math.max(0, Model.snap(start)));
      int end = Math.min(Model.BAR, Math.max(s + Model.GRID, Model.snap(endTick)));
      if (notes.size() < 128) {
        NoteEvent n = new NoteEvent(s, end - s, p, strength);
        List<NoteEvent> candidate = new ArrayList<>(notes);
        candidate.add(n);
        if(validTimeline(candidate,rests,expressions)){notes.add(n);dirty=true;canvas.selectedRest=-1;canvas.selected=notes.size()-1;select(canvas.selected);}
      }
      if(active.isEmpty()){heldSession=false;resetCursor();commitEdit();}
      canvas.repaint();liveView.repaint();
    }
  }

  private void record() {
    if (mode.getSelectedIndex() != 1) {
      status.setText(mode.getSelectedIndex()==2?"건반이나 줄을 누르면 바로 입력됩니다. 누르는 동안 꼬리가 길어져요.":"단계 입력은 건반을 누를 때마다 1박씩 들어갑니다. 드럼은 1/4박입니다.");
      focusInstrument();
      return;
    }
    stop();
    if ((!notes.isEmpty() || !rests.isEmpty())
        && JOptionPane.showConfirmDialog(
                this, "현재 음을 지우고 녹음할까요?", "D05 · 다시 녹음", JOptionPane.OK_CANCEL_OPTION)
            != JOptionPane.OK_OPTION) return;
    notes.clear();
    rests.clear();
    expressions.clear();
    beforeEdit = null;
    beforeRests = null;
    beforeExpressions = null;
    canvas.selected = -1;
    canvas.selectedRest = -1;
    select(-1);
    dirty = true;
    cursor = 0;
    lastClick = Long.MIN_VALUE;
    waiting = app.settings.countIn();
    recording = !waiting;
    origin =
        nanoTime.getAsLong()
            + (waiting
                ? Math.round(app.settings.countInBeats() * 60_000_000_000.0 / (int) bpm.getValue())
                : 0);
    focusInstrument();
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
    } else if(heldSession && !active.isEmpty()) {
      canvas.playhead=Math.min(Model.BAR-1,captureTicks());
      status.setText("누르는 중 · "+String.format(java.util.Locale.ROOT,"%.2f",Math.min(Model.BAR,captureTicks())/(double)Model.PPQ)+" / 4박");
      if(captureTicks()>=Model.BAR){stop();status.setText("4박 입력 완료 · 음 편집에서 길이와 세기를 다듬어 보세요.");}
    } else canvas.playhead = app.midi.position();
    canvas.repaint();
    liveView.repaint();
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
              beforeEdit == null ? notes : beforeEdit,
              beforeEdit == null ? rests : beforeRests,
              beforeEdit == null ? expressions : beforeExpressions);
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
    long end=captureTicks();for (int p : new ArrayList<>(active.keySet())) release(p,end);
    heldSession=false;activeVelocity.clear();
    recording = false;
    if (piano != null) piano.releaseAll();
    if(bass!=null)bass.releaseAll();if(guitar!=null)guitar.releaseAll();if(drums!=null)drums.releaseAll();
    app.midi.stop();
    commitEdit();
  }

  public void dispose() {
    stop();
    timer.stop();
  }

  private void clear() {
    stop();
    if ((!notes.isEmpty() || !rests.isEmpty())
        && JOptionPane.showConfirmDialog(
                this, "현재 초안의 음을 지울까요? 메모는 유지됩니다.", "D05 · 다시 연주", JOptionPane.OK_CANCEL_OPTION)
            != JOptionPane.OK_OPTION) return;
    notes.clear();
    rests.clear();
    expressions.clear();
    beforeEdit = null;
    beforeRests = null;
    beforeExpressions = null;
    cursor = 0;
    canvas.selected = -1;
    canvas.selectedRest = -1;
    dirty = true;
    canvas.repaint();
    commitEdit();
    select(-1);
    status.setText("새로운 소리를 기다리고 있어요.");
  }

  private void delete() {
    if (canvas.selectedRest >= 0 && canvas.selectedRest < rests.size()) {
      rememberBefore();
      rests.remove(canvas.selectedRest);
      canvas.selectedRest = -1;
      resetCursor();
      select(-1);
      commitEdit();
      canvas.repaint();
      return;
    }
    if (canvas.selected >= 0 && canvas.selected < notes.size()) {
      rememberBefore();
      notes.remove(canvas.selected);
      canvas.selected = -1;
      resetCursor();
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
        instrumentProgram(),
        memo.getText(),
        notes, rests, expressions);
  }

  public void saved(Phrase saved) {
    base = saved;
    refreshDirty();
  }

  private boolean validTimeline(List<NoteEvent> ns, List<RestEvent> rs, List<ExpressionRange> es) {
    try {
      Model.validateTimeline(ns, rs, es);
      return true;
    } catch (IllegalArgumentException ex) {
      status.setText(ex.getMessage());
      return false;
    }
  }

  private void resetCursor() {
    cursor = Math.max(notes.stream().mapToInt(n -> n.start() + n.duration()).max().orElse(0),
        rests.stream().mapToInt(r -> r.start() + r.duration()).max().orElse(0));
  }

  void addRest() {
    stop();
    if (cursor + Model.PPQ > Model.BAR) {
      status.setText("4박을 넘는 쉼은 입력할 수 없어요.");
      return;
    }
    RestEvent r = new RestEvent(cursor, Model.PPQ);
    List<RestEvent> candidate = new ArrayList<>(rests);
    candidate.add(r);
    if (!validTimeline(notes, candidate, expressions)) return;
    rests.add(r);
    cursor += Model.PPQ;
    canvas.selected = -1;
    canvas.selectedRest = rests.size() - 1;
    selectRest(canvas.selectedRest);
    commitEdit();
    canvas.repaint();
    status.setText("쉼 1박을 남겼어요. 다음 입력 위치: " + cursor / 480.0 + "박");
  }

  void convertToRest() {
    int i = canvas.selected;
    if (i < 0 || i >= notes.size()) return;
    NoteEvent n = notes.get(i);
    List<NoteEvent> remaining = new ArrayList<>(notes);
    remaining.remove(i);
    List<RestEvent> candidate = new ArrayList<>(rests);
    candidate.add(new RestEvent(n.start(), n.duration()));
    if (!validTimeline(remaining, candidate, expressions)) return;
    rememberBefore();
    notes.remove(i);
    rests.add(candidate.get(candidate.size() - 1));
    canvas.selected = -1;
    canvas.selectedRest = rests.size() - 1;
    selectRest(canvas.selectedRest);
    commitEdit();
    canvas.repaint();
  }

  private void selectRest(int i) {
    if (i < 0 || i >= rests.size()) return;
    updating = true;
    RestEvent r = rests.get(i);
    ((CardLayout) inspectorCards.getLayout()).show(inspectorCards, "selected");
    selectedInfo.setText("선택한 쉼 · " + r.start() / 480.0 + "박부터");
    length.setEnabled(true);
    pitch.setEnabled(false);
    velocity.setEnabled(false);
    length.setValue(r.duration() / Model.GRID);
    lengthValue.setText(r.duration() / 480.0 + "박");
    pitchValue.setText("—");
    velocityValue.setText("무음");
    updating = false;
  }

  private void normalArticulation() {
    int i = canvas.selected;
    if (i < 0 || i >= notes.size()) return;
    stop();
    rememberBefore();
    NoteEvent n = notes.get(i);
    notes.set(i, new NoteEvent(n.start(), n.duration(), n.pitch(), n.velocity()));
    commitEdit();
    canvas.repaint();
  }

  private void crescendoDialog() {
    int i = canvas.selected;
    if (i < 0 || i >= notes.size()) return;
    NoteEvent n = notes.get(i);
    JSpinner start = new JSpinner(new SpinnerNumberModel(n.start() / 480.0, 0.0, 3.75, .25));
    JSpinner end = new JSpinner(new SpinnerNumberModel((n.start() + n.duration()) / 480.0, .25, 4.0, .25));
    JSpinner from = new JSpinner(new SpinnerNumberModel(45, 1, 126, 1));
    JSpinner to = new JSpinner(new SpinnerNumberModel(110, 2, 127, 1));
    JPanel fields = new JPanel(new GridLayout(4, 2, 8, 8));
    fields.add(new JLabel("시작 위치 (박, 0부터)")); fields.add(start);
    fields.add(new JLabel("끝 위치 (박)")); fields.add(end);
    fields.add(new JLabel("시작 세기")); fields.add(from);
    fields.add(new JLabel("끝 세기")); fields.add(to);
    if (JOptionPane.showConfirmDialog(this, fields, "선택 구간 크레센도", JOptionPane.OK_CANCEL_OPTION)
        != JOptionPane.OK_OPTION) return;
    try {
      start.commitEdit(); end.commitEdit(); from.commitEdit(); to.commitEdit();
      addCrescendo(new ExpressionRange((int) Math.round((double) start.getValue() * 480),
          (int) Math.round((double) end.getValue() * 480), ExpressionKind.CRESCENDO,
          (int) from.getValue(), (int) to.getValue()));
    } catch (Exception ex) { status.setText(ex.getMessage()); }
  }

  void addCrescendo(ExpressionRange range) {
    List<ExpressionRange> candidate = new ArrayList<>(expressions);
    candidate.add(range);
    if (!validTimeline(notes, rests, candidate)) return;
    rememberBefore();
    expressions.add(range);
    commitEdit();
    select(canvas.selected);
    canvas.repaint();
  }

  private void clearExpressions() {
    stop();
    rememberBefore();
    expressions.clear();
    commitEdit();
    select(canvas.selected);
    canvas.repaint();
  }

  private void findChord() { EmotionPalette.showFor(app, this); }

  public void insertChord(EmotionCatalog.Chord chord, int start, int duration) {
    if (instrumentProgram() == Model.DRUMS)
      throw new IllegalArgumentException("드럼에는 화음을 넣을 수 없습니다. 선율 악기를 선택하세요.");
    List<NoteEvent> additions = chord.notes().stream()
        .map(note -> new NoteEvent(start, duration, note, chord.velocity())).toList();
    if (notes.size() + additions.size() > 128)
      throw new IllegalArgumentException("한 마디에는 최대 128개 음을 넣을 수 있습니다.");
    List<NoteEvent> candidate = new ArrayList<>(notes);
    candidate.addAll(additions);
    Model.validateTimeline(candidate, rests, expressions);
    stop();
    rememberBefore();
    notes.addAll(additions);
    cursor = Math.max(cursor, start + duration);
    canvas.selectedRest = -1;
    canvas.selected = notes.size() - additions.size();
    select(canvas.selected);
    commitEdit();
    canvas.repaint();
    status.setText(chord.symbol() + " 화음을 넣었어요. 들어보고 다듬거나 실행 취소할 수 있어요.");
  }
}
