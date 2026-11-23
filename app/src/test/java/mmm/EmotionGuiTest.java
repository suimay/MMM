package mmm;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.time.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.*;
import mmm.Model.*;

public final class EmotionGuiTest {
  private static int checks;
  private static MMM app;

  private static void check(boolean condition, String label) {
    if (!condition) throw new AssertionError(label);
    checks++;
    System.out.println("PASS " + label);
  }

  private static AbstractButton button(Container parent, String label) {
    for (Component component : parent.getComponents()) {
      if (component instanceof AbstractButton b && label.equals(b.getText())) return b;
      if (component instanceof Container child) {
        AbstractButton found = button(child, label);
        if (found != null) return found;
      }
    }
    return null;
  }

  private static void edt(Runnable action) throws Exception {
    AtomicReference<Throwable> error = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
      try { action.run(); } catch (Throwable ex) { error.set(ex); }
    });
    if (error.get() != null) throw new AssertionError(error.get());
  }

  private static <T> T find(Container parent, Class<T> type) {
    if (type.isInstance(parent)) return type.cast(parent);
    for (Component component : parent.getComponents())
      if (component instanceof Container child) {
        T found = find(child, type);
        if (found != null) return found;
      }
    return null;
  }

  private static void capture(Window window, String filename) throws Exception {
    Path folder = Path.of(System.getProperty("mmm.capture.dir", "build/emotion-captures"));
    Files.createDirectories(folder);
    BufferedImage image = new BufferedImage(window.getWidth(), window.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    window.paint(graphics);
    graphics.dispose();
    ImageIO.write(image, "png", folder.resolve(filename).toFile());
  }

  public static void main(String[] args) throws Exception {
    if (GraphicsEnvironment.isHeadless())
      throw new IllegalStateException("실제 데스크톱 또는 Xvfb 디스플레이가 필요합니다.");
    Thread watchdog = new Thread(() -> {
      try { Thread.sleep(45000); } catch (InterruptedException ignored) { return; }
      System.err.println("Emotion GUI test timeout");
      System.exit(2);
    });
    watchdog.setDaemon(true);
    watchdog.start();
    Path folder = Files.createTempDirectory("mmm-emotion-gui-");
    LocalDate day = LocalDate.of(2026, 11, 23);
    Clock clock = Clock.fixed(day.atTime(12, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
    MusicService service = new MusicService(new LocalRepository(folder), clock, false);
    EmotionCatalog catalog = EmotionCatalog.load();
    Song song = new Song("emotion-test", "화음 실험", "tester", false, 110, 24, Set.of("tester"));
    Phrase base = new Phrase(song.id(), "tester", day, 0, 110, 24, "오늘의 소리",
        List.of(new NoteEvent(0, 480, 72, 90)));
    try {
      edt(() -> { Theme.install(); app = new MMM(service, folder); });
      long readyDeadline = System.currentTimeMillis() + 15000;
      AtomicReference<Boolean> busy = new AtomicReference<>(true);
      while (busy.get() && System.currentTimeMillis() < readyDeadline) {
        edt(() -> busy.set(app.getGlassPane().isVisible()));
        if (busy.get()) Thread.sleep(40);
      }
      check(!busy.get(), "application startup finishes");
      edt(() -> {
        EditorPanel editor = new EditorPanel(app, song, base);
        app.show("S06", editor);
        app.editor = editor;
        app.validate();
      });
      AtomicReference<Throwable> dialogError = new AtomicReference<>();
      edt(() -> {
        app.midi.loop(true);
        javax.swing.Timer observer = new javax.swing.Timer(80, null);
        observer.addActionListener(event -> {
          for (Window window : Window.getWindows()) {
            if (!(window instanceof JDialog dialog) || !dialog.isShowing()
                || !(dialog.getContentPane() instanceof EmotionPalette palette)) continue;
            observer.stop();
            try {
              palette.selectTag("nostalgia_longing");
              check(palette.chordList.getModel().getSize() == 3, "nostalgia filter has three candidates");
              check(palette.chordList.getModel().getElementAt(0).symbol().equals("Cm"), "candidates preserve catalog order");
              palette.chordList.setSelectedIndex(1);
              check(palette.chordList.getSelectedValue().symbol().equals("Cmaj7"), "select actual Cmaj7 row");
              palette.startBeat.setValue(4.75);
              check(((Number) palette.durationBeats.getValue()).doubleValue() == 0.25,
                  "duration clamps at final quarter beat");
              palette.startBeat.setValue(2.0);
              palette.durationBeats.setValue(2.0);
              capture(dialog, "palette.png");
              JTabbedPane tabs = find(palette, JTabbedPane.class);
              tabs.setSelectedIndex(1);
              JEditorPane evidence = find(palette, JEditorPane.class);
              check(evidence.getText().contains("2.89") && evidence.getText().contains("0.82")
                  && evidence.getText().contains("10.1177/0305735614552006"), "evidence matches selected chord and source");
              capture(dialog, "evidence.png");
              tabs.setSelectedIndex(0);
              dialog.setSize(800, 600);
              dialog.validate();
              capture(dialog, "palette-minimum.png");
              palette.preview();
              check(!app.midi.looping(), "preview temporarily disables repetition");
              palette.stopPreview();
              check(!app.midi.playing(), "stop leaves no running preview");
              button(palette, "이 화음 넣기").doClick();
            } catch (Throwable ex) {
              dialogError.set(ex);
              dialog.dispose();
            }
          }
        });
        observer.start();
        button(app.editor, "감정으로 화음 찾기").doClick();
        observer.stop();
      });
      if (dialogError.get() != null) throw new AssertionError(dialogError.get());
      edt(() -> {
        EditorPanel editor = app.editor;
        Phrase changed = editor.snapshot();
        check(changed.notes().size() == 5, "chord adds four notes without replacing melody");
        check(changed.notes().getFirst().equals(base.notes().getFirst()), "existing note preserved");
        check(changed.notes().subList(1, 5).stream().allMatch(n -> n.start() == 480 && n.duration() == 960),
            "chosen beat and duration reach all chord notes");
        check(changed.notes().subList(1, 5).stream().map(NoteEvent::pitch).toList().equals(List.of(60, 64, 67, 71)),
            "Cmaj7 has correct simultaneous pitches");
        check(changed.bpm() == 110 && changed.instrument() == 24 && changed.memo().equals(base.memo()),
            "editor BPM instrument and memo preserved");
        check(editor.dirty, "chord insertion marks unsaved draft");
        check(app.midi.looping(), "palette restores previous loop setting");
        editor.undo();
        check(editor.snapshot().equals(base) && !editor.dirty, "one undo restores entire prior phrase");
        editor.redo();
        check(editor.snapshot().equals(changed), "one redo restores whole chord");
        try {
          editor.insertChord(catalog.chord("c_major"), 1800, 480);
          throw new AssertionError("bar overflow accepted");
        } catch (IllegalArgumentException expected) {
          check(editor.snapshot().equals(changed), "invalid insertion leaves phrase intact");
        }
        try {
          LocalRepository repository = new LocalRepository(folder.resolve("roundtrip"));
          repository.createSong(song);
          Phrase saved = repository.save(changed, 0);
          check(new LocalRepository(folder.resolve("roundtrip")).phrases(song.id()).contains(saved),
              "inserted chord persists through existing repository format");
          capture(app, "editor-with-chord.png");
        } catch (Exception ex) { throw new RuntimeException(ex); }
        List<NoteEvent> crowded = java.util.Collections.nCopies(126, new NoteEvent(0, 120, 60, 72));
        EditorPanel full = new EditorPanel(app, song,
            new Phrase(song.id(), "tester", day, 0, 110, 24, "", crowded));
        try {
          full.insertChord(catalog.chord("c_major"), 0, 480);
          throw new AssertionError("note limit accepted");
        } catch (IllegalArgumentException expected) {
          check(full.notes.size() == 126 && !full.dirty, "128-note limit rejects atomically");
        } finally { full.dispose(); }
      });
      System.out.println("PASS: " + checks + " emotion GUI and editor integration checks");
    } finally {
      edt(() -> {
        if (app != null) {
          if (app.editor != null) app.editor.dispose();
          app.midi.close();
          app.dispose();
        }
      });
    }
    System.exit(0);
  }
}
