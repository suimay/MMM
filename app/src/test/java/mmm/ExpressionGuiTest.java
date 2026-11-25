package mmm;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.*;
import java.util.List;
import javax.swing.*;
import mmm.Model.*;
import static mmm.GuiFlowTest.*;

/** Exercises actual editor listeners, undo, dialogs, save/navigation and readonly views. */
public final class ExpressionGuiTest {
  static final LocalDate DAY = LocalDate.of(2026, 11, 16);
  static EditorPanel editor;
  static Song song;
  static Object field(Object object, String name) throws Exception {
    Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
  }
  static void setField(Object object, String name, Object value) throws Exception {
    Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); f.set(object, value);
  }
  static void invoke(Object object, String name) throws Exception {
    Method m = object.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(object);
  }
  static void select(int index) {
    app.validate();
    NoteCanvas c = editor.canvas;
    Point2D p = c.noteCenter(editor.notes.get(index));
    mouse(c, MouseEvent.MOUSE_PRESSED, (int) p.getX() + 12, (int) p.getY());
    mouse(c, MouseEvent.MOUSE_RELEASED, (int) p.getX() + 12, (int) p.getY());
  }
  static void mouse(Component component, int id, int x, int y) {
    MouseEvent event = new MouseEvent(component, id, System.currentTimeMillis(),
        id == MouseEvent.MOUSE_DRAGGED ? InputEvent.BUTTON1_DOWN_MASK : 0,
        x, y, 1, false, id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1);
    component.dispatchEvent(event);
  }
  static void expression(int type) throws Exception {
    Method m = EditorPanel.class.getDeclaredMethod("applyExpression", int.class);
    m.setAccessible(true); m.invoke(editor, type);
  }
  static void inFrame(Component c, String message) {
    Rectangle bounds = SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), app.getContentPane());
    boolean visible = c.isShowing() && bounds.width > 0 && bounds.height > 0
        && new Rectangle(app.getContentPane().getSize()).contains(bounds);
    for (Container parent = c.getParent(); parent != null && parent != app.getContentPane(); parent = parent.getParent()) {
      Rectangle clip = SwingUtilities.convertRectangle(parent, new Rectangle(parent.getSize()), app.getContentPane());
      visible &= clip.contains(bounds);
    }
    check(visible, message);
  }
  public static void main(String[] args) throws Exception {
    try {
      Thread watchdog = new Thread(() -> {
        try { Thread.sleep(90000); System.err.println("Expression GUI timeout"); System.exit(2); }
        catch (InterruptedException ignored) { }
      });
      watchdog.setDaemon(true); watchdog.start();
      root = Files.createTempDirectory("mmm-v02-gui-");
      Clock clock = Clock.fixed(DAY.atTime(12, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
      MusicService service = new MusicService(new LocalRepository(root), clock, false);
      service.register("tester", "검증 사용자", "test1234".toCharArray(), "test1234".toCharArray());
      service.login("tester", "test1234".toCharArray());
      song = service.create("오늘의 표현", false, 90, 0);
      edt(() -> { Theme.install(); app = new MMM(service, root); });
      idle();
      edt(() -> {
        set("current", song);
        set("week", DAY.minusWeeks(1));
        app.rememberSelection(DAY.minusDays(5), service.user());
        app.week();
      });
      idle();
      edt(() -> {
        PersonalWeekPanel weekPanel = find(app.getContentPane(), PersonalWeekPanel.class);
        check(weekPanel.selectedDate().equals(DAY.minusDays(5)), "weekly selected date restored on entry");
        Phrase base = new Phrase(song.id(), service.user(), DAY, 0, 90, 0, "",
            List.of(new NoteEvent(0, 480, 60, 90), new NoteEvent(480, 480, 64, 90), new NoteEvent(960, 480, 67, 90)));
        app.edit(base, DAY); editor = app.editor;
        check(!editor.dirty, "opening draft is clean");
        button(editor.piano.getParent(),"›").doClick();
        check(editor.piano.firstPitch()==60 && !editor.dirty,"Octave button changes view without editing the draft");
        key(editor.piano,KeyEvent.VK_A,'a',true);key(editor.piano,KeyEvent.VK_A,'a',false);
        check(editor.notes.get(editor.notes.size()-1).pitch()==72,"Typing records the pitch shown after moving octave");
        editor.undo();button(editor.piano.getParent(),"‹").doClick();
        check(editor.notes.size()==3 && !editor.dirty && editor.piano.firstPitch()==48,"Undo restores notes independently of keyboard navigation");
        select(0);
        check(editor.canvas.selected == 0 && !editor.dirty, "selection alone never dirties draft");
        Point2D originalPoint = editor.canvas.noteCenter(editor.notes.get(0));
        mouse(editor.canvas, MouseEvent.MOUSE_PRESSED, (int) originalPoint.getX() + 12, (int) originalPoint.getY());
        mouse(editor.canvas, MouseEvent.MOUSE_DRAGGED, (int) originalPoint.getX() + 13, (int) originalPoint.getY());
        mouse(editor.canvas, MouseEvent.MOUSE_RELEASED, (int) originalPoint.getX() + 13, (int) originalPoint.getY());
        check(!editor.dirty, "sub-grid drag with unchanged data stays clean");
        editor.memo.setText("🎵".repeat(20));
        check(editor.memo.getText().codePointCount(0, editor.memo.getText().length()) == 20, "20 emoji accepted");
        editor.memo.setText("🎵".repeat(21));
        check(editor.memo.getText().codePointCount(0, editor.memo.getText().length()) == 20, "21 emoji rejected in editor");
        editor.memo.setText("");
        editor.memo.setText("바꾸기 전");
        editor.memo.setText("바꾼 메모");
        editor.undo();
        check(editor.memo.getText().equals("바꾸기 전"), "memo replacement undone as a single operation");
        editor.redo();
        check(editor.memo.getText().equals("바꾼 메모"), "memo replacement redone as a single operation");
        editor.memo.setText("");
        select(0);
        expression(0);
        check(editor.notes.get(0).duration() == 480 && editor.notes.get(0).articulation() == Articulation.STACCATO,
            "staccato action preserves logical length");
        editor.undo();
        check(editor.notes.get(0).articulation() == Articulation.NORMAL, "undo staccato");
        editor.redo();
        check(editor.notes.get(0).articulation() == Articulation.STACCATO, "redo staccato");
        select(0);
        Point2D tip = editor.canvas.tailPoint(editor.notes.get(0));
        mouse(editor.canvas, MouseEvent.MOUSE_PRESSED, (int) tip.getX(), (int) tip.getY());
        mouse(editor.canvas, MouseEvent.MOUSE_DRAGGED, (int) tip.getX() - 40, (int) tip.getY());
        mouse(editor.canvas, MouseEvent.MOUSE_RELEASED, (int) tip.getX() - 40, (int) tip.getY());
        check(editor.notes.get(0).articulation() == Articulation.STACCATO && editor.notes.get(0).duration() < 480,
            "tail resize preserves articulation");
        editor.undo();
        check(editor.notes.get(0).duration() == 480, "one undo restores whole drag");
        select(1);
        expression(2);
        check(editor.notes.size() == 2 && editor.rests.equals(List.of(new RestEvent(480, 480))), "convert preserves explicit silent interval");
        editor.undo();
        check(editor.rests.isEmpty() && editor.notes.size() == 3, "undo restores note from rest");
        editor.redo();
        check(editor.rests.size() == 1, "redo rest conversion");
        JSlider length = (JSlider) field(editor, "length");
        Rectangle2D rest = editor.canvas.restBounds(editor.rests.get(0));
        mouse(editor.canvas, MouseEvent.MOUSE_PRESSED, (int) rest.getCenterX(), (int) rest.getCenterY());
        mouse(editor.canvas, MouseEvent.MOUSE_RELEASED, (int) rest.getCenterX(), (int) rest.getCenterY());
        check(length.isEnabled() && !((JSlider) field(editor, "pitch")).isEnabled(), "rest inspector edits duration, no pitch");
        length.setValue(2);
        check(editor.rests.get(0).duration() == 240, "rest length slider updates model");
        editor.undo();
        check(editor.rests.get(0).duration() == 480, "undo rest length");
        rest = editor.canvas.restBounds(editor.rests.get(0));
        mouse(editor.canvas, MouseEvent.MOUSE_PRESSED, (int) rest.getCenterX(), (int) rest.getCenterY());
        editor.canvas.getActionMap().get("delete-note").actionPerformed(new ActionEvent(editor.canvas, 0, "delete"));
        check(editor.rests.isEmpty() && editor.notes.size() == 2, "rest delete returns interval to unentered gap");
        editor.undo();
        check(editor.rests.equals(List.of(new RestEvent(480, 480))), "undo deleted rest");
        select(0);
        editor.canvas.getActionMap().get("RIGHT").actionPerformed(new ActionEvent(editor.canvas, 0, "RIGHT"));
        check(editor.notes.get(0).start() == 0, "note move across explicit rest rejected");
        length.setValue(5);
        check(editor.notes.get(0).duration() == 480 && length.getValue() == 4, "invalid length restores slider and model together");
        select(1);
        ExpressionRange range = new ExpressionRange(0, 1440, ExpressionKind.CRESCENDO, 40, 100);
        editor.addCrescendo(range);
        check(editor.expressions.equals(List.of(range)) && editor.snapshot().effectiveVelocity(editor.notes.get(1)) == 80,
            "crescendo uses selected range dynamics");
        editor.undo();
        check(editor.expressions.isEmpty(), "undo expression range");
        editor.redo();
        check(editor.expressions.size() == 1, "redo expression range");
        editor.addRest();
        check(editor.rests.size() == 2 && editor.rests.get(1).start() == 1440, "step rest stored at input cursor");
        editor.addRest();
        check(editor.rests.size() == 2, "step rest cannot exceed four beats");
        editor.undo();
        check(editor.rests.size() == 1, "undo explicit step rest");
        editor.redo();
        select(0);
        JSlider velocity = (JSlider) field(editor, "velocity");
        velocity.setValue(70);
        check(editor.canvas.selected == 0 && editor.notes.get(0).velocity() == 70, "slider preserves selected note");
        app.setSize(1000, 720); app.validate();
        inFrame(editor.piano, "keyboard inside 1000x720 frame");
        inFrame(button(editor.piano.getParent(),"‹"),"Lower octave button inside minimum window");
        inFrame(button(editor.piano.getParent(),"›"),"Higher octave button inside minimum window");
        while(editor.piano.canShift(-1))button(editor.piano.getParent(),"‹").doClick();
        check(!button(editor.piano.getParent(),"‹").isEnabled(),"Lower button disabled at minimum register");
        while(editor.piano.canShift(1))button(editor.piano.getParent(),"›").doClick();
        check(!button(editor.piano.getParent(),"›").isEnabled(),"Higher button disabled at maximum register");
        capture("piano-high-register");editor.piano.resetRange();
        check(editor.canvas.getHeight() >= 112, "compact keyboard preserves room for pitch editing at minimum size");
        inFrame(button(editor, "하루 저장"), "save inside 1000x720 frame");
        inFrame(button(editor, "정지"), "stop inside 1000x720 frame");
        inFrame(button(editor, "표현 ···"), "expression actions inside 1000x720 frame");
        capture("v02-editor-minimum");
        app.setSize(1280, 860); app.validate();
        editor.memo.setText("쉼과 표현을 남긴 날");
        capture("v02-editor");
        Container keyboard=editor.piano.getParent();
        java.awt.image.BufferedImage keyboardImage=new java.awt.image.BufferedImage(keyboard.getWidth(),keyboard.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
        Graphics2D keyboardGraphics=keyboardImage.createGraphics();keyboard.paint(keyboardGraphics);keyboardGraphics.dispose();
        javax.imageio.ImageIO.write(keyboardImage,"png",Path.of("build","evidence","piano-range-keyboard.png").toFile());
        dialog("OK");
        app.saveEditor(app::week);
      });
      idle();
      edt(() -> {
        check(get("screen").equals("S05") && get("week").equals(DAY.minusWeeks(1)), "save returns to original week");
        PersonalWeekPanel weekPanel = find(app.getContentPane(), PersonalWeekPanel.class);
        check(weekPanel.selectedDate().equals(DAY.minusDays(5)), "save restores original selected date");
      });
      Phrase saved = new LocalRepository(root).phrases(song.id()).get(0);
      check(saved.rests().size() == 2 && saved.expressions().size() == 1
          && saved.notes().get(0).articulation() == Articulation.STACCATO, "restart restores entire v0.2 record");
      edt(() -> {
        app.inspectRecord(saved, DAY, service.user(), app::week);
        RecordInspector inspector = find(app.getContentPane(), RecordInspector.class);
        app.validate();
        check(inspector.canvas.restSource.get().equals(saved.rests()), "readonly shows stored rests");
        check(inspector.canvas.expressionSource.get().equals(saved.expressions()), "readonly shows stored crescendo");
        Rectangle2D r = inspector.canvas.restBounds(saved.rests().get(0));
        mouse(inspector.canvas, MouseEvent.MOUSE_PRESSED, (int) r.getCenterX(), (int) r.getCenterY());
        check(((JLabel) field(inspector, "selected")).getText().contains("쉼"), "readonly rest selectable");
        check(inspector.record().equals(saved), "readonly selection never mutates record");
        capture("v02-readonly");
        app.edit(saved, DAY); editor = app.editor;
        editor.addCrescendo(new ExpressionRange(1440, 1920, ExpressionKind.CRESCENDO, 30, 80));
        check(editor.dirty, "new expression marks unsaved");
        editor.undo();
        check(!editor.dirty, "undo all changes restores clean baseline");
        editor.redo();
        check(editor.dirty, "redo expression marks unsaved again");
        editor.undo();
        check(!editor.dirty, "second undo clean");
        editor.memo.setText("초안 유지");
        dialog("계속 편집");
        app.guard(() -> { throw new AssertionError("continue navigated"); });
        check(editor.memo.getText().equals("초안 유지"), "unsaved continue preserves v0.2 draft");
        editor.dirty = false;
        JPanel scorePage = new JPanel(new BorderLayout());
        scorePage.add(new JScrollPane(new ScorePanel("쉼과 표현", List.of(saved))));
        app.show("S11", scorePage);
        capture("v02-score-screen");
        app.edit(null, DAY); editor = app.editor; app.validate();
        ((JComboBox<?>) field(editor, "mode")).setSelectedIndex(1);
        app.settings = new Settings(80, 0, true, true, 4);
        invoke(editor, "record");
        check((boolean) field(editor, "waiting") && editor.notes.isEmpty(), "count-in starts before recording");
        key(editor.piano, KeyEvent.VK_A, 'a', true);
        key(editor.piano, KeyEvent.VK_A, 'a', false);
        check(editor.notes.isEmpty(), "count-in input never saved as notes");
        setField(editor, "origin", System.nanoTime() - 100_000_000L);
        invoke(editor, "tick");
        check((boolean) field(editor, "recording") && !(boolean) field(editor, "waiting"), "count-in transitions to recording");
        key(editor.piano, KeyEvent.VK_A, 'a', true);
        key(editor.piano, KeyEvent.VK_A, 'a', true);
        setField(editor, "origin", System.nanoTime() - 3_000_000_000L);
        invoke(editor, "tick");
        check(editor.notes.size() == 1 && editor.notes.get(0).start() + editor.notes.get(0).duration() == Model.BAR,
            "realtime release clipped at bar boundary without repeated notes");
        check(!(boolean) field(editor, "recording") && ((java.util.Map<?, ?>) field(editor, "active")).isEmpty(),
            "bar end clears active realtime notes");
        editor.undo();
        check(editor.notes.isEmpty(), "one undo removes realtime capture");
        editor.redo();
        check(editor.notes.size() == 1, "redo restores realtime capture");
        dialog("OK");
        invoke(editor, "record");
        setField(editor, "waiting", false);
        setField(editor, "recording", true);
        setField(editor, "origin", System.nanoTime() - 3_000_000_000L);
        key(editor.piano, KeyEvent.VK_A, 'a', true);
        key(editor.piano, KeyEvent.VK_A, 'a', false);
        check(editor.notes.isEmpty(), "key press after bar boundary cannot create a spurious final note");
        editor.dispose();
        app.stopSound(); app.midi.close(); app.dispose();
      });
      System.out.println("PASS: " + checks + " v0.2 real Swing editor and navigation checks; data=" + root);
      System.exit(0);
    } catch (Throwable failure) {
      failure.printStackTrace();
      if (app != null) edt(() -> { if (app.editor != null) app.editor.dispose(); app.midi.close(); app.dispose(); });
      System.exit(1);
    }
  }
}
