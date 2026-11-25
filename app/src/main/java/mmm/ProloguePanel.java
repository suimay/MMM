package mmm;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import mmm.Model.*;

/** S01: an audible identity. Letter height maps to pitch, width maps to note duration. */
public final class ProloguePanel extends JPanel {
  private static final Color GREEN = new Color(38, 65, 55);
  private final MMM app;
  private final int[] pitches = {60, 64, 67}, lengths = {480, 480, 480};
  private int selected;
  private boolean updating;
  private final JLabel caption = Theme.muted("첫 번째 음 · 도 C4 · 1박"), lengthLabel = new JLabel();
  public final JSlider pitch = new JSlider(48, 84, 60), duration = new JSlider(1, 8, 4);
  public final JComboBox<String> sounds = new JComboBox<>(MMM.INSTRUMENT_NAMES);
  public final Piano piano;
  public final LogoCanvas logo = new LogoCanvas();
  private final javax.swing.Timer timer;
  private boolean sequencePlaying;

  public ProloguePanel(MMM app, Runnable begin, Runnable sample, Runnable help) {
    super(new BorderLayout(12, 12));
    this.app = app;
    putClientProperty("screenId", "S01");
    setBorder(BorderFactory.createEmptyBorder(18, 30, 14, 30));
    JPanel title = Theme.column();
    title.add(Theme.muted("mmm / 소리로 읽는 나의 하루"));
    title.add(Box.createVerticalStrut(4));
    title.add(Theme.label("소리로 남기는, 나의 하루.", 24));
    title.add(Theme.muted("글자 하나도 소리가 됩니다. 세 개의 m을 눌러 나만의 시작을 만들어 보세요."));
    add(title, BorderLayout.NORTH);
    JPanel body = new JPanel(new BorderLayout(8, 8));
    body.add(logo);
    JPanel lower = Theme.column();
    JPanel editor = new JPanel(new BorderLayout(8, 4));
    editor.add(
        Theme.row(
            caption,
            Theme.button("선택 음 듣기", this::playSelected),
            Theme.dark("세 음 이어 듣기", this::playSequence)),
        BorderLayout.NORTH);
    JPanel sliders = new JPanel(new GridLayout(1, 2, 26, 0));
    sliders.add(sliderGroup("글자 높이 ↔ 음높이", pitch, new JLabel("낮게                        높게")));
    sliders.add(sliderGroup("글자 너비 ↔ 음의 길이", duration, lengthLabel));
    editor.add(sliders);
    lower.add(editor);
    lower.add(Theme.muted("높은 m은 높은 음, 넓은 m은 긴 음 · 소리 덩어리에도 같은 원리가 이어져요."));
    piano =
        new Piano(
            () -> false,
            n -> {
              sequencePlaying = false;
              if (app.midi.playing() || app.midi.paused()) app.midi.stop();
              app.midi.on(n, MMM.PROGRAMS[sounds.getSelectedIndex()]);
            },
            app.midi::off);
    piano.setPreferredSize(new Dimension(800, 76));
    piano.setMaximumSize(new Dimension(4000, 76));
    JLabel picture = new JLabel(ArtAssets.instrument(0, 32));
    lower.add(
        Theme.row(
            picture,
            new JLabel("체험 악기"),
            sounds,
            Theme.muted("도·레·미·파·솔·라·시·도  /  A S D F G H J K")));
    lower.add(piano);
    for (Component c : lower.getComponents())
      if (c instanceof JComponent j) j.setAlignmentX(Component.LEFT_ALIGNMENT);
    body.add(lower, BorderLayout.SOUTH);
    add(body);
    add(
        Theme.row(
            Theme.primary("오늘 기록 시작", begin),
            Theme.secondary(app.service.demo ? "샘플 바로 듣기" : "로그인", sample),
            Theme.button("처음이라면 · 1분 도움말", help)),
        BorderLayout.SOUTH);
    pitch.setName("logo-pitch");
    duration.setName("logo-duration");
    Theme.slider(pitch);
    Theme.slider(duration);
    pitch.addChangeListener(
        e -> {
          if (!updating) {
            if (app.midi.playing() || app.midi.paused()) app.midi.stop();
            pitches[selected] = pitch.getValue();
            refresh();
          }
        });
    duration.addChangeListener(
        e -> {
          if (!updating) {
            if (app.midi.playing() || app.midi.paused()) app.midi.stop();
            lengths[selected] = duration.getValue() * 120;
            refresh();
          }
        });
    sounds.addItemListener(
        e -> {
          if (e.getStateChange() == ItemEvent.SELECTED) {
            piano.releaseAll();
            app.stopSound();
            sequencePlaying = false;
            picture.setIcon(ArtAssets.instrument(MMM.PROGRAMS[sounds.getSelectedIndex()], 32));
          }
        });
    timer = new javax.swing.Timer(40, e -> logo.repaint());
    select(0);
  }

  private JPanel sliderGroup(String label, JSlider value, JLabel ends) {
    JPanel p = new JPanel(new BorderLayout(0, 2));
    p.add(new JLabel(label), BorderLayout.NORTH);
    p.add(value);
    p.add(ends, BorderLayout.SOUTH);
    return p;
  }

  public void select(int index) {
    selected = Math.max(0, Math.min(2, index));
    updating = true;
    pitch.setValue(pitches[selected]);
    duration.setValue(lengths[selected] / 120);
    updating = false;
    refresh();
  }

  private void refresh() {
    caption.setText(
        new String[] {"첫 번째", "두 번째", "세 번째"}[selected]
            + " 음 · "
            + Piano.solfege(pitches[selected])
            + " "
            + NoteCanvas.noteName(pitches[selected]));
    lengthLabel.setText("짧게       " + lengths[selected] / 480.0 + "박       길게");
    logo.repaint();
  }

  public List<MidiEngine.Part> parts() {
    List<MidiEngine.Part> out = new ArrayList<>();
    int offset = 0;
    for (int i = 0; i < 3; i++) {
      Phrase f =
          new Phrase(
              "prologue",
              "trial",
              app.service.today(),
              0,
              90,
              MMM.PROGRAMS[sounds.getSelectedIndex()],
              "",
              List.of(new NoteEvent(0, lengths[i], pitches[i], 85)));
      out.add(new MidiEngine.Part(f, offset, 0, 100));
      offset += lengths[i];
    }
    return List.copyOf(out);
  }

  public void playSelected() {
    sequencePlaying = false;
    piano.releaseAll();
    MidiEngine.Part part = parts().get(selected);
    app.play(List.of(new MidiEngine.Part(part.phrase(), 0, 0, 100)), lengths[selected], 90);
  }

  public void playSequence() {
    sequencePlaying = true;
    piano.releaseAll();
    app.play(parts(), java.util.Arrays.stream(lengths).sum(), 90);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (timer != null) timer.start();
  }

  @Override
  public void removeNotify() {
    if (timer != null) timer.stop();
    piano.releaseAll();
    super.removeNotify();
  }

  public final class LogoCanvas extends JPanel {
    LogoCanvas() {
      setPreferredSize(new Dimension(750, 235));
      setMinimumSize(new Dimension(500, 170));
      setFocusable(true);
      getAccessibleContext().setAccessibleName("세 개의 m · 클릭 또는 방향키로 선택하고 Enter로 듣기");
      addMouseListener(
          new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
              if (!SwingUtilities.isLeftMouseButton(e)) return;
              requestFocusInWindow();
              double cell = Math.min(240, getWidth() / 3.0);
              double left = (getWidth() - cell * 3) / 2;
              select((int) Math.floor((e.getX() - left) / cell));
              playSelected();
            }
          });
      addMouseMotionListener(
          new MouseMotionAdapter() {
            public void mouseMoved(MouseEvent e) {
              setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
              setToolTipText("m을 눌러 듣고 아래 슬라이더로 높이와 너비를 바꿔 보세요.");
            }
          });
      for (String key : new String[] {"LEFT", "RIGHT", "ENTER"}) {
        getInputMap().put(KeyStroke.getKeyStroke(key), key);
        getActionMap()
            .put(
                key,
                new AbstractAction() {
                  public void actionPerformed(ActionEvent e) {
                    if (key.equals("ENTER")) playSelected();
                    else select(selected + (key.equals("LEFT") ? -1 : 1));
                  }
                });
      }
    }

    protected void paintComponent(Graphics raw) {
      super.paintComponent(raw);
      Graphics2D g = (Graphics2D) raw.create();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      Font font = new Font("Helvetica Neue", Font.BOLD, 150);
      Shape glyph = font.createGlyphVector(g.getFontRenderContext(), "m").getOutline();
      Rectangle2D box = glyph.getBounds2D();
      double cell = Math.min(240, getWidth() / 3.0),
          left = (getWidth() - cell * 3) / 2,
          baseline = getHeight() - 46;
      long pos = app.midi.position();
      int offset = 0;
      for (int i = 0; i < 3; i++) {
        double w = cell * .90 * lengths[i] / 960.0,
            h = Math.min(170, getHeight() - 65) * (.35 + (pitches[i] - 48) / 36.0 * .65);
        AffineTransform t = new AffineTransform();
        t.translate(left + (i + .5) * cell - w / 2, baseline - h);
        t.scale(w / box.getWidth(), h / box.getHeight());
        t.translate(-box.getX(), -box.getY());
        boolean active =
            pos >= 0
                && (sequencePlaying ? pos >= offset && pos < offset + lengths[i] : i == selected);
        g.setColor(active ? Theme.ORANGE : GREEN);
        g.fill(t.createTransformedShape(glyph));
        if (i == selected) {
          g.setColor(hasFocus() ? Theme.ORANGE : Theme.LINE);
          g.fillRoundRect((int) (left + (i + .5) * cell - 36), (int) baseline + 14, 72, 3, 3, 3);
        }
        offset += lengths[i];
      }
      g.setColor(GREEN);
      g.setFont(Theme.font(15, false));
      String text = "음 음 음  ·  하루 한 마디";
      g.drawString(text, (getWidth() - g.getFontMetrics().stringWidth(text)) / 2, getHeight() - 7);
      g.dispose();
    }
  }
}
