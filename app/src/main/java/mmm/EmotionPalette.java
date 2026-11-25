package mmm;

import java.awt.*;
import java.awt.event.*;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import mmm.Model.*;

public final class EmotionPalette extends JPanel {
  public record Placement(int start, int duration) {
    public Placement {
      new NoteEvent(start, duration, 60, 72);
    }
  }

  private record Filter(String id, String label) {
    public String toString() {
      return label;
    }
  }

  private final EmotionCatalog catalog;
  private final MidiEngine midi;
  private final boolean previousLoop;
  private final BiConsumer<EmotionCatalog.Chord, Placement> insert;
  private final JComboBox<Filter> filter = new JComboBox<>();
  private final DefaultListModel<EmotionCatalog.Chord> choices = new DefaultListModel<>();
  final JList<EmotionCatalog.Chord> chordList = new JList<>(choices);
  final JSpinner startBeat = new JSpinner(new SpinnerNumberModel(1.0, 1.0, 4.75, 0.25));
  final JSpinner durationBeats = new JSpinner(new SpinnerNumberModel(1.0, 0.25, 4.0, 0.25));
  private final JLabel title = Theme.label("", 30);
  private final JLabel tags = new JLabel();
  private final JTextArea description = textArea();
  private final JEditorPane evidence = new JEditorPane("text/html", "");
  private final JLabel status = Theme.muted("화음을 선택한 뒤 들어보세요.");
  private final JButton preview = Theme.primary("화음 듣기 · 4초", this::preview);
  private final JButton add = Theme.primary("이 화음 넣기", this::insertSelected);

  public EmotionPalette(
      EmotionCatalog catalog,
      MidiEngine midi,
      BiConsumer<EmotionCatalog.Chord, Placement> insert) {
    super(new BorderLayout(14, 16));
    this.catalog = catalog;
    this.midi = midi;
    this.insert = insert;
    previousLoop = midi.looping();
    setBorder(BorderFactory.createEmptyBorder(24, 26, 20, 26));
    setPreferredSize(new Dimension(880, 650));
    JPanel heading = Theme.column();
    heading.add(Theme.label("어떤 분위기로 시작할까요?", 25));
    heading.add(Box.createVerticalStrut(7));
    heading.add(Theme.muted("태그는 소리의 분위기를 찾는 말입니다. 사람마다 다르게 들릴 수 있어요."));
    heading.add(Theme.muted("문헌에서 가져온 후보예요. 앱 소리는 아직 청취 실험으로 검증하지 않았어요."));
    add(heading, BorderLayout.NORTH);

    filter.addItem(new Filter("", "모든 분위기"));
    for (EmotionCatalog.Tag tag : catalog.tags())
      if (tag.availability().equals("candidate"))
        filter.addItem(new Filter(tag.id(), tag.labelKo()));
    filter.setName("emotion-filter");
    filter.getAccessibleContext().setAccessibleName("분위기 태그 선택");
    JPanel browser = new JPanel(new BorderLayout(0, 10));
    browser.setPreferredSize(new Dimension(220, 360));
    browser.add(filter, BorderLayout.NORTH);
    chordList.setName("emotion-chords");
    chordList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    chordList.setFixedCellHeight(54);
    chordList.setCellRenderer(
        (list, value, index, selected, focus) -> {
          JLabel label =
              new JLabel(
                  "<html><b>" + html(value.symbol()) + "</b> · "
                      + html(value.displayName()) + "</html>");
          label.setOpaque(true);
          label.setBackground(selected ? Theme.WASH : Theme.SURFACE);
          label.setForeground(Theme.INK);
          label.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
          return label;
        });
    browser.add(new JScrollPane(chordList));
    JPanel details = new JPanel(new BorderLayout());
    JTabbedPane tabs = new JTabbedPane();
    JPanel sound = Theme.card(new BorderLayout(0, 14));
    JPanel labels = Theme.column();
    labels.add(title);
    labels.add(Box.createVerticalStrut(9));
    tags.setFont(Theme.font(15, true));
    tags.setForeground(Theme.ORANGE);
    labels.add(tags);
    sound.add(labels, BorderLayout.NORTH);
    description.setBackground(Theme.SURFACE);
    sound.add(new JScrollPane(description));
    sound.add(Theme.row(preview, Theme.button("정지", this::stopPreview)), BorderLayout.SOUTH);
    tabs.addTab("화음 고르기", sound);
    evidence.setEditable(false);
    evidence.setBackground(Theme.SURFACE);
    evidence.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
    evidence.setFont(Theme.font(13, false));
    evidence.addHyperlinkListener(
        event -> {
          if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            stopPreview();
            try {
              if (!Desktop.isDesktopSupported()
                  || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                throw new IllegalStateException("이 환경에서는 링크를 직접 열 수 없습니다.");
              Desktop.getDesktop().browse(URI.create(event.getURL().toString()));
            } catch (Exception ex) {
              status.setText("논문 주소: " + event.getURL());
            }
          }
        });
    tabs.addTab("연구 근거", new JScrollPane(evidence));
    details.add(tabs);
    JPanel center = new JPanel(new BorderLayout(16, 0));
    center.add(browser, BorderLayout.WEST);
    center.add(details);
    add(center);

    JPanel footer = new JPanel(new BorderLayout(0, 8));
    status.setName("emotion-status");
    footer.add(status, BorderLayout.NORTH);
    if (insert != null) {
      startBeat.setName("chord-start-beat");
      durationBeats.setName("chord-duration-beats");
      startBeat.addChangeListener(e -> constrainDuration());
      footer.add(
          Theme.row(
              new JLabel("시작 박"), startBeat,
              new JLabel("길이(박)"), durationBeats, add),
          BorderLayout.CENTER);
      footer.add(
          Theme.muted("기존 음에 더합니다. 편집기의 악기·BPM으로 연주되며 실행 취소할 수 있어요."),
          BorderLayout.SOUTH);
    } else {
      footer.add(
          Theme.muted("오늘 편집 → 감정으로 화음 찾기에서 화음을 기록에 넣을 수 있어요."),
          BorderLayout.SOUTH);
    }
    add(footer, BorderLayout.SOUTH);
    filter.addActionListener(e -> refreshCandidates());
    chordList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showSelected(); });
    getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "stop-preview");
    getActionMap().put("stop-preview", new AbstractAction() {
      public void actionPerformed(ActionEvent event) { stopPreview(); }
    });
    refreshCandidates();
  }

  private static JTextArea textArea() {
    JTextArea text = new JTextArea();
    text.setEditable(false);
    text.setLineWrap(true);
    text.setWrapStyleWord(true);
    text.setFont(Theme.font(14, false));
    text.setMargin(new Insets(7, 10, 7, 10));
    return text;
  }

  public void selectTag(String id) {
    for (int i = 0; i < filter.getItemCount(); i++)
      if (filter.getItemAt(i).id().equals(id == null ? "" : id)) {
        filter.setSelectedIndex(i);
        return;
      }
    throw new IllegalArgumentException("선택할 수 없는 태그입니다: " + id);
  }

  private void refreshCandidates() {
    stopPreview();
    choices.clear();
    Filter selected = (Filter) filter.getSelectedItem();
    for (EmotionCatalog.Chord chord : catalog.candidates(selected == null ? "" : selected.id()))
      choices.addElement(chord);
    if (!choices.isEmpty()) chordList.setSelectedIndex(0);
    else showSelected();
  }

  private void showSelected() {
    stopPreview();
    EmotionCatalog.Chord chord = chordList.getSelectedValue();
    preview.setEnabled(chord != null);
    add.setEnabled(chord != null);
    if (chord == null) {
      title.setText("화음을 골라 주세요");
      tags.setText("");
      description.setText("");
      evidence.setText("");
      return;
    }
    title.setText(chord.symbol());
    tags.setText(catalog.tagsFor(chord.id()).stream()
        .map(EmotionCatalog.Tag::labelKo).collect(Collectors.joining(" · ")));
    String notes = switch (chord.notes().getFirst() == 60 ? chord.quality() : "other_root") {
      case "major" -> "도(C4) · 미(E4) · 솔(G4)";
      case "minor" -> "도(C4) · 미♭(E♭4) · 솔(G4)";
      case "major_seventh" -> "도(C4) · 미(E4) · 솔(G4) · 시(B4)";
      case "minor_seventh" -> "도(C4) · 미♭(E♭4) · 솔(G4) · 시♭(B♭4)";
      case "augmented" -> "도(C4) · 미(E4) · 솔♯(G♯4)";
      case "diminished" -> "도(C4) · 미♭(E♭4) · 솔♭(G♭4)";
      case "dominant_seventh" -> "도(C4) · 미(E4) · 솔(G4) · 시♭(B♭4)";
      default -> chord.notes().stream().map(NoteCanvas::noteName).collect(Collectors.joining(" · "));
    };
    description.setText(
        notes
            + "\n" + instrumentName(chord.program())
            + " · " + NoteCanvas.noteName(chord.notes().getFirst()) + "부터 · 기본위치 · " + seconds(chord) + "초"
            + "\n직접 들어보고 마음에 드는 소리를 골라 보세요.");
    description.setCaretPosition(0);
    preview.setText("화음 듣기 · " + seconds(chord) + "초");
    evidence.setText(evidenceHtml(chord));
    evidence.setCaretPosition(0);
    status.setText("선택: " + chord.symbol() + " · 화음 듣기로 비교해 보세요.");
  }

  private String evidenceHtml(EmotionCatalog.Chord chord) {
    StringBuilder out = new StringBuilder(
        "<html><body style='margin:14px;color:#2e2d28'>"
            + "<h2>" + html(chord.symbol()) + " · 연구 근거</h2>"
            + "<p><b>문헌에 근거한 후보 · 앱 청취 검증 전</b></p>"
            + "<p>아래 수치는 원문의 화음별 연구 조건에 걸쳐 집계한 평균입니다. "
            + "현재 피아노 샘플의 점수·확률·추천 순위가 아닙니다.</p>");
    for (EmotionCatalog.Link link : catalog.linksFor(chord.id())) {
      EmotionCatalog.Tag tag = catalog.tag(link.tagId());
      EmotionCatalog.Evidence finding = catalog.evidence(link.evidenceId());
      EmotionCatalog.Source source = catalog.source(finding.sourceId());
      String doi = source.doi().startsWith("https://") ? source.doi() : "https://doi.org/" + source.doi();
      out.append("<h3>").append(html(tag.labelKo())).append("</h3><p>")
          .append(html(tag.definitionKo())).append("</p><p>원문 평균 ")
          .append(number(finding.mean())).append(" · 표준편차 ")
          .append(number(finding.sd())).append(" · ")
          .append(number(finding.scaleMin())).append("–")
          .append(number(finding.scaleMax())).append("점 척도<br>")
          .append(finding.measure().equals("valence") ? "부정적인 → 긍정적인"
              : finding.measure().equals("tension") ? "이완된 → 긴장된" : "거의 없거나 전혀 없음 → 매우 강함")
          .append("</p><p>").append(html(aggregationLabel(finding.aggregation()))).append("</p><p>")
          .append(html(source.authors())).append(" (").append(source.year()).append(") · N=")
          .append(source.participantsN()).append("<br>")
          .append(html(source.title())).append("<br><a href='").append(html(doi))
          .append("'>논문 DOI 열기</a> · ").append(html(finding.locator())).append("</p>");
    }
    out.append("<hr><p>그리움 후보 Cm·Cmaj7·Cm7의 작은 평균 차이로 순위를 매기지 않습니다.</p>")
        .append("<p>음색·음역·전위·앞뒤 화음과 듣는 사람의 경험에 따라 인상이 달라질 수 있습니다. ")
        .append("이 태그는 사용자의 현재 감정을 추정하지 않습니다.</p>")
        .append("<p>앱 샘플: GM ").append(chord.program()).append(" ")
        .append(html(instrumentName(chord.program()))).append(", ")
        .append(html(NoteCanvas.noteName(chord.notes().getFirst()))).append(" 기본위치 밀집배치, BPM ")
        .append(chord.bpm()).append(", ").append(chord.durationTicks()).append(" tick, 입력 velocity ")
        .append(chord.velocity()).append("(전체 음량 설정 적용). 기기별 MIDI 음색은 다를 수 있으며 ")
        .append("연구 원본 음원과 동일하지 않습니다.</p></body></html>");
    return out.toString();
  }

  private static String number(Double value) {
    return value == null ? "미보고" : String.format(Locale.ROOT, "%.2f", value);
  }

  private static String seconds(EmotionCatalog.Chord chord) {
    double value = chord.durationTicks() * 60.0 / (Model.PPQ * chord.bpm());
    return value == (int) value ? Integer.toString((int) value) : String.format(Locale.ROOT, "%.2f", value);
  }

  private static String instrumentName(int program) {
    for (int i = 0; i < MMM.PROGRAMS.length; i++)
      if (MMM.PROGRAMS[i] == program) return MMM.INSTRUMENT_NAMES[i];
    return "악기 " + (program + 1);
  }

  private static String aggregationLabel(String value) {
    return value.replace("timbres=", "음색: ").replace("inversions=", "전위: ")
        .replace("piano", "피아노").replace("strings", "현악")
        .replace("root_position", "기본위치").replace("first_inversion", "제1전위")
        .replace("second_inversion", "제2전위").replace("third_inversion", "제3전위")
        .replace(";", " / ").replace(",", " · ");
  }

  private static String html(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
  }

  private void constrainDuration() {
    double remaining = 5.0 - ((Number) startBeat.getValue()).doubleValue();
    SpinnerNumberModel model = (SpinnerNumberModel) durationBeats.getModel();
    if (((Number) model.getValue()).doubleValue() > remaining) model.setValue(remaining);
    model.setMaximum(remaining);
  }

  public void preview() {
    EmotionCatalog.Chord chord = chordList.getSelectedValue();
    if (chord == null) return;
    try {
      Phrase phrase = new Phrase("emotion-preview", "preview", LocalDate.of(2000, 1, 1),
          0, chord.bpm(), chord.program(), "", catalog.previewNotes(chord.id()));
      midi.loop(false);
      midi.play(List.of(new MidiEngine.Part(phrase, 0, 0, 100)), chord.durationTicks(), chord.bpm());
      status.setText(chord.symbol() + " · " + seconds(chord) + "초 미리 듣기 · Esc로 정지");
    } catch (Exception ex) {
      status.setText("소리를 재생할 수 없어요. " + midi.status());
    }
  }

  private void insertSelected() {
    EmotionCatalog.Chord chord = chordList.getSelectedValue();
    if (insert == null || chord == null) return;
    try {
      startBeat.commitEdit();
      durationBeats.commitEdit();
      int start = (int) Math.round((((Number) startBeat.getValue()).doubleValue() - 1) * Model.PPQ);
      int duration = (int) Math.round(((Number) durationBeats.getValue()).doubleValue() * Model.PPQ);
      Placement placement = new Placement(start, duration);
      stopPreview();
      insert.accept(chord, placement);
    } catch (Exception ex) {
      status.setText("화음을 넣지 못했어요. " + ex.getMessage());
    }
  }

  public void stopPreview() {
    midi.stop();
    status.setText("미리 듣기를 멈췄어요. 화음을 선택해 다시 들을 수 있어요.");
  }

  public void close() {
    stopPreview();
    midi.loop(previousLoop);
  }

  public static void showFor(MMM app, EditorPanel editor) {
    editor.stop();
    try {
      JDialog dialog = new JDialog(app, "감정으로 화음 찾기", true);
      EmotionPalette panel = new EmotionPalette(EmotionCatalog.load(), app.midi,
          (chord, placement) -> {
            editor.insertChord(chord, placement.start(), placement.duration());
            dialog.dispose();
          });
      dialog.setContentPane(panel);
      dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      dialog.addWindowListener(new WindowAdapter() {
        public void windowClosed(WindowEvent event) { panel.close(); }
      });
      dialog.addWindowFocusListener(new WindowAdapter() {
        public void windowLostFocus(WindowEvent event) { panel.stopPreview(); }
      });
      dialog.pack();
      dialog.setMinimumSize(new Dimension(800, 600));
      dialog.setLocationRelativeTo(app);
      dialog.setVisible(true);
    } catch (Exception ex) {
      app.error(ex);
    }
  }

  public static void main(String[] args) throws Exception {
    EmotionCatalog catalog = EmotionCatalog.load();
    SwingUtilities.invokeLater(() -> {
      Theme.install();
      JFrame frame = new JFrame("MMM · 감정과 화음 연구 노트");
      MidiEngine midi = new MidiEngine();
      EmotionPalette panel = new EmotionPalette(catalog, midi, null);
      frame.setContentPane(panel);
      frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      frame.addWindowListener(new WindowAdapter() {
        public void windowClosed(WindowEvent event) { panel.close(); midi.close(); }
      });
      frame.addWindowFocusListener(new WindowAdapter() {
        public void windowLostFocus(WindowEvent event) { panel.stopPreview(); }
      });
      frame.pack();
      frame.setMinimumSize(new Dimension(800, 600));
      frame.setLocationRelativeTo(null);
      frame.setVisible(true);
      new SwingWorker<Void, Void>() {
        protected Void doInBackground() { midi.open(); return null; }
        protected void done() {
          panel.status.setText(midi.ready() ? "준비됐어요. 화음을 선택해 들어보세요." : midi.status());
        }
      }.execute();
    });
  }
}
