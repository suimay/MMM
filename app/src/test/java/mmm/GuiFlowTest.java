package mmm;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import mmm.Model.*;

/** Runs the real Swing screens and their listeners; no external UI automation or Robot. */
public final class GuiFlowTest {
  static MMM app;
  static Path root;
  static int checks;

  interface Task {
    void run() throws Exception;
  }

  static void edt(Task task) throws Exception {
    AtomicReference<Throwable> error = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            task.run();
          } catch (Throwable e) {
            error.set(e);
          }
        });
    if (error.get() != null) throw new Exception(error.get());
  }

  static Object get(String name) throws Exception {
    Field f = MMM.class.getDeclaredField(name);
    f.setAccessible(true);
    return f.get(app);
  }

  static void set(String name, Object value) throws Exception {
    Field f = MMM.class.getDeclaredField(name);
    f.setAccessible(true);
    f.set(app, value);
  }

  static void call(String name, Class<?>[] types, Object... args) throws Exception {
    Method m = MMM.class.getDeclaredMethod(name, types);
    m.setAccessible(true);
    m.invoke(app, args);
  }

  static void check(boolean b, String label) {
    if (!b) throw new AssertionError(label);
    checks++;
    System.out.println("PASS " + label);
  }

  static void idle() throws Exception {
    long until = System.currentTimeMillis() + 15000;
    while (System.currentTimeMillis() < until) {
      AtomicBoolean b = new AtomicBoolean();
      edt(() -> b.set((boolean) get("busy")));
      if (!b.get()) {
        edt(() -> {});
        return;
      }
      Thread.sleep(25);
    }
    throw new AssertionError("worker timeout");
  }

  static AbstractButton button(Container root, String text) {
    for (Component c : root.getComponents()) {
      if (c instanceof AbstractButton b && b.getText().replace("▶ ", "").equals(text)) return b;
      if (c instanceof Container container) {
        AbstractButton b = button(container, text);
        if (b != null) return b;
      }
    }
    return null;
  }

  static AbstractButton lastButton(Container root, String text) {
    AbstractButton found = null;
    for (Component c : root.getComponents()) {
      if (c instanceof AbstractButton b && b.getText().replace("▶ ", "").equals(text)) found = b;
      if (c instanceof Container container) {
        AbstractButton b = lastButton(container, text);
        if (b != null) found = b;
      }
    }
    return found;
  }

  static void click(String text) throws Exception {
    AbstractButton b = button(app.getContentPane(), text);
    if (b == null) throw new AssertionError("Missing button " + text);
    b.doClick();
  }

  static void dialog(String choice) {
    javax.swing.Timer timer = new javax.swing.Timer(50, null);
    timer.addActionListener(
        e -> {
          for (Window window : Window.getWindows())
            if (window instanceof JDialog d && d.isVisible()) {
              AbstractButton b = button(d, choice.equals("OK") ? "확인" : choice);
              if (b != null) {
                timer.stop();
                b.doClick();
                return;
              }
            }
        });
    timer.start();
  }

  static void capture(String name) throws Exception {
    app.validate();
    BufferedImage image =
        new BufferedImage(app.getWidth(), app.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    app.paint(g);
    g.dispose();
    ImageIO.write(image, "png", Path.of("docs", name + ".png").toFile());
  }

  static void key(Piano piano, int code, char value, boolean down) {
    KeyEvent event =
        new KeyEvent(
            piano,
            down ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
            System.currentTimeMillis(),
            0,
            code,
            value);
    for (KeyListener l : piano.getKeyListeners()) {
      if (down) l.keyPressed(event);
      else l.keyReleased(event);
    }
  }

  public static void main(String[] args) throws Exception {
    try {
      Thread watchdog =
          new Thread(
              () -> {
                try {
                  Thread.sleep(90000);
                  System.err.println("GUI test timeout");
                  System.exit(2);
                } catch (InterruptedException ignored) {
                }
              });
      watchdog.setDaemon(true);
      watchdog.start();
      root = Files.createTempDirectory("mmm-gui-integration-");
      LocalDate day = LocalDate.of(2026, 11, 1);
      Clock clock =
          Clock.fixed(
              day.atTime(12, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant(),
              ZoneId.of("Asia/Seoul"));
      MusicService service = new MusicService(new LocalRepository(root), clock, true);
      edt(
          () -> {
            Theme.install();
            app = new MMM(service, root);
          });
      idle();
      edt(
          () -> {
            check(app.isShowing(), "real JFrame visible");
            capture("S01-start");
            check(app.midi.ready(), "real MIDI synthesizer and sequencer opened");
          });
      service.login("demo", "demo1234".toCharArray());
      service.repository.createSong(
          new Song("flow", "핵심 경로 테스트", "demo", false, 90, 0, Set.of("demo")));
      edt(
          () -> {
            set(
                "current",
                service.songs().stream()
                    .filter(s -> s.id().equals("flow"))
                    .findFirst()
                    .orElseThrow());
            app.week();
          });
      idle();
      edt(
          () -> {
            check(get("screen").equals("S05"), "S05 loaded");
            click("오늘 편집");
            check(app.editor != null, "S06 opened");
            app.validate();
            Piano piano = app.editor.piano;
            key(piano, KeyEvent.VK_A, 'a', true);
            key(piano, KeyEvent.VK_A, 'a', true);
            check(app.editor.notes.size() == 1, "duplicate keyPressed suppressed");
            key(piano, KeyEvent.VK_A, 'a', false);
            key(piano, KeyEvent.VK_D, 'd', true);
            key(piano, KeyEvent.VK_D, 'd', false);
            check(app.editor.notes.size() == 2, "step input creates notes");
            NoteCanvas canvas = app.editor.canvas;
            java.awt.geom.Point2D point = canvas.noteCenter(app.editor.notes.get(0));
            int x = (int) point.getX() + 15, y = (int) point.getY();
            MouseEvent press =
                new MouseEvent(
                    canvas,
                    MouseEvent.MOUSE_PRESSED,
                    System.currentTimeMillis(),
                    0,
                    x,
                    y,
                    1,
                    false);
            for (MouseListener l : canvas.getMouseListeners()) l.mousePressed(press);
            MouseEvent drag =
                new MouseEvent(
                    canvas,
                    MouseEvent.MOUSE_DRAGGED,
                    System.currentTimeMillis(),
                    0,
                    x + 60,
                    y - 35,
                    0,
                    false);
            for (MouseMotionListener l : canvas.getMouseMotionListeners()) l.mouseDragged(drag);
            check(
                app.editor.notes.get(0).pitch() != 60, "visual drag edits shared NoteEvent pitch");
            for (MouseListener listener : canvas.getMouseListeners())
              listener.mouseReleased(
                  new MouseEvent(
                      canvas,
                      MouseEvent.MOUSE_RELEASED,
                      System.currentTimeMillis(),
                      0,
                      x + 60,
                      y - 35,
                      1,
                      false));
            int editedPitch = app.editor.notes.get(0).pitch();
            app.editor.undo();
            check(app.editor.notes.get(0).pitch() == 60, "undo restores pre-drag notes");
            app.editor.redo();
            check(app.editor.notes.get(0).pitch() == editedPitch, "redo restores edited notes");
            canvas.selected = 0;
            NoteEvent resizeBefore = app.editor.notes.get(0);
            java.awt.geom.Point2D tail = canvas.tailPoint(resizeBefore);
            for (MouseListener listener : canvas.getMouseListeners())
              listener.mousePressed(
                  new MouseEvent(
                      canvas,
                      MouseEvent.MOUSE_PRESSED,
                      System.currentTimeMillis(),
                      0,
                      (int) tail.getX(),
                      (int) tail.getY(),
                      1,
                      false));
            for (MouseMotionListener listener : canvas.getMouseMotionListeners())
              listener.mouseDragged(
                  new MouseEvent(
                      canvas,
                      MouseEvent.MOUSE_DRAGGED,
                      System.currentTimeMillis(),
                      0,
                      (int) tail.getX() + 35,
                      (int) tail.getY(),
                      0,
                      false));
            for (MouseListener listener : canvas.getMouseListeners())
              listener.mouseReleased(
                  new MouseEvent(
                      canvas,
                      MouseEvent.MOUSE_RELEASED,
                      System.currentTimeMillis(),
                      0,
                      (int) tail.getX() + 35,
                      (int) tail.getY(),
                      1,
                      false));
            check(
                app.editor.notes.get(0).duration() > resizeBefore.duration(),
                "tail handle changes NoteEvent duration");
            check(
                app.editor.notes.get(0).pitch() == resizeBefore.pitch()
                    && app.editor.notes.get(0).start() == resizeBefore.start(),
                "tail resize preserves start and pitch");
            app.editor.undo();
            check(
                app.editor.notes.get(0).equals(resizeBefore), "undo restores entire resized glyph");
            app.editor.memo.setText("핵심 경로 검증");
            click("편집 전 듣기");
            click("들어보기");
            app.midi.pauseResume();
            check(app.midi.paused(), "pause transport");
            app.midi.seek(960);
            check(app.midi.cursor() == 960, "seek while paused");
            app.midi.pauseResume();
            check(app.midi.playing(), "resume transport");
            java.awt.geom.Point2D picked = canvas.noteCenter(app.editor.notes.get(0));
            for (MouseListener listener : canvas.getMouseListeners())
              listener.mousePressed(
                  new MouseEvent(
                      canvas,
                      MouseEvent.MOUSE_PRESSED,
                      System.currentTimeMillis(),
                      0,
                      (int) picked.getX() + 15,
                      (int) picked.getY(),
                      1,
                      false));
            for (MouseListener listener : canvas.getMouseListeners())
              listener.mouseReleased(
                  new MouseEvent(
                      canvas,
                      MouseEvent.MOUSE_RELEASED,
                      System.currentTimeMillis(),
                      0,
                      (int) picked.getX() + 15,
                      (int) picked.getY(),
                      1,
                      false));
            java.lang.reflect.Method tick = EditorPanel.class.getDeclaredMethod("tick");
            tick.setAccessible(true);
            tick.invoke(app.editor);
            capture("S06-editor");
            app.setSize(1060, 780);
            app.validate();
            check(app.editor.piano.isShowing(), "keyboard available at minimum window size");
            check(
                app.editor.piano.getLocationOnScreen().y + app.editor.piano.getHeight()
                    < app.getLocationOnScreen().y + app.getHeight(),
                "keyboard stays inside minimum frame");
            check(
                button(app.editor, "표현 ···").isShowing(),
                "selected properties available at minimum size");
            check(
                button(app.editor, "표현 ···").getLocationOnScreen().y
                        + button(app.editor, "표현 ···").getHeight()
                    < app.editor.piano.getLocationOnScreen().y,
                "selected property actions stay above keyboard");
            capture("S06-minimum");
            app.setSize(1180, 850);
            app.validate();
            dialog("계속 편집");
            app.guard(
                () -> {
                  throw new AssertionError("cancel should not navigate");
                });
            check(app.editor.dirty, "D04 continue preserves draft");
            dialog("계속 편집");
            app.dispatchEvent(new WindowEvent(app, WindowEvent.WINDOW_CLOSING));
            check(app.isShowing() && app.editor.dirty, "window close protects unsaved draft");
            key(piano, KeyEvent.VK_F, 'f', true);
            for (FocusListener l : piano.getFocusListeners())
              l.focusLost(new FocusEvent(piano, FocusEvent.FOCUS_LOST));
            Field held = Piano.class.getDeclaredField("held");
            held.setAccessible(true);
            check(((Map<?, ?>) held.get(piano)).isEmpty(), "focus loss clears held keys");
            dialog("OK");
            app.saveEditor(app::week);
          });
      idle();
      edt(
          () -> {
            check(get("screen").equals("S05"), "D03 saved and refreshed S05");
            capture("S05-saved");
            lastButton(app.getContentPane(), "저장본 감상").doClick();
            check(get("screen").equals("S07"), "S07 read-only opened");
            click("감상하기");
            capture("S07-listen");
            app.stopSound();
          });
      MusicService restored = new MusicService(new LocalRepository(root), clock, true);
      restored.login("demo", "demo1234".toCharArray());
      Phrase saved =
          restored.phrases("flow").stream()
              .filter(p -> p.date().equals(day))
              .findFirst()
              .orElseThrow();
      check(
          saved.memo().equals("핵심 경로 검증") && saved.notes().size() == 3,
          "fresh repository restores notes and memo");
      edt(
          () -> {
            app.week();
          });
      idle();
      edt(
          () -> {
            click("오늘 편집");
            app.editor.memo.setText("실패 초안");
            Path file = root.resolve("mmm.properties");
            Files.move(file, root.resolve("backup.properties"));
            Files.createDirectory(file);
            dialog("OK");
            app.saveEditor(app::week);
            dialog("OK");
          });
      idle();
      edt(
          () -> {
            check(
                app.editor != null && app.editor.dirty && app.editor.memo.getText().equals("실패 초안"),
                "D05 IO failure retains editor and memo");
            Files.delete(root.resolve("mmm.properties"));
            Files.move(root.resolve("backup.properties"), root.resolve("mmm.properties"));
            dialog("저장하고 이동");
            dialog("OK");
            app.guard(app::week);
          });
      idle();
      edt(
          () -> {
            check(get("screen").equals("S05"), "retry succeeds after IO recovery");
            app.midi.close();
            app.dispose();
          });
      // A new JFrame and a new repository represent an application restart.
      edt(() -> app = new MMM(restored, root));
      idle();
      edt(
          () -> {
            set(
                "current",
                restored.songs().stream()
                    .filter(s -> s.id().equals("flow"))
                    .findFirst()
                    .orElseThrow());
            app.week();
          });
      idle();
      edt(
          () -> {
            click("오늘 편집");
            check(app.editor.memo.getText().equals("실패 초안"), "restart restores latest save in S06");
            check(app.editor.notes.size() == 3, "restart restores all notes");
            app.editor.memo.setText("버릴 초안");
            dialog("버리고 이동");
            app.guard(app::week);
          });
      idle();
      check(
          restored.phrases("flow").stream()
              .anyMatch(f -> f.date().equals(day) && f.memo().equals("실패 초안")),
          "D04 discard leaves saved data unchanged");
      edt(
          () -> {
            call("library", new Class<?>[] {});
          });
      idle();
      edt(
          () -> {
            check(get("screen").equals("S04"), "S04 library accessible");
            capture("S04-library");
            set(
                "current",
                restored.songs().stream().filter(s -> s.shared()).findFirst().orElseThrow());
            app.week();
          });
      idle();
      edt(
          () -> {
            check(get("screen").equals("S08"), "S08 shared week accessible");
            capture("S08-shared");
            click("오늘 공동 합주");
            check(get("screen").equals("S09"), "S09 ensemble accessible");
            capture("S09-ensemble");
            click("모두 재생");
            app.stopSound();
            call("placeholder", new Class<?>[] {boolean.class}, true);
            check(get("screen").equals("S10"), "S10 management accessible");
            capture("S10-management");
            call("placeholder", new Class<?>[] {boolean.class}, false);
          });
      idle();
      edt(
          () -> {
            check(get("screen").equals("S11"), "S11 score accessible");
            capture("S11-score");
            call("settings", new Class<?>[] {});
            check(get("screen").equals("S12"), "S12 settings accessible");
            call("help", new Class<?>[] {});
            check(get("screen").equals("S13"), "S13 help accessible");
            call("login", new Class<?>[] {String.class}, "demo");
            check(get("screen").equals("S02"), "S02 login accessible");
            call("signup", new Class<?>[] {});
            check(get("screen").equals("S03"), "S03 signup accessible");
            app.midi.close();
            app.dispose();
          });
      System.out.println("PASS: " + checks + " real Swing workflow checks; data=" + root);
      System.exit(0);
    } catch (Throwable e) {
      e.printStackTrace();
      if (app != null)
        edt(
            () -> {
              if (app.editor != null) app.editor.dispose();
              app.midi.close();
              app.dispose();
            });
      System.exit(1);
    }
  }
}
