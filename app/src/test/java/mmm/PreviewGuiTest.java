package mmm;

import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import mmm.Model.*;
import static mmm.GuiFlowTest.*;

/** Preview reload follows the same unsaved-change guard as navigation. */
public final class PreviewGuiTest {
  public static void main(String[] args) throws Exception {
    try {
      Thread watchdog=new Thread(()->{try{Thread.sleep(60000);System.err.println("Preview GUI timeout");System.exit(2);}catch(InterruptedException ignored){}});
      watchdog.setDaemon(true);watchdog.start();
      Path directory=Files.createTempDirectory("mmm-preview-gui-");root=directory;
      LocalDate day=LocalDate.of(2026,11,16);
      Clock clock=Clock.fixed(day.atTime(12,0).atZone(ZoneId.of("Asia/Seoul")).toInstant(),ZoneId.of("Asia/Seoul"));
      MusicService service=new MusicService(new LocalRepository(directory),clock,true);
      service.seed();service.login("demo","demo1234".toCharArray());
      Path state=directory.resolve("view.properties");AtomicInteger restarted=new AtomicInteger();
      edt(()->{Theme.install();app=new MMM(service,directory);app.enableDevelopmentPreview(state,restarted::incrementAndGet);});
      idle();edt(()->app.restoreDevelopmentPreview(new Properties()));idle();
      edt(()->{
        check(app.getTitle().contains("개발 미리보기"),"Preview is visibly identified");
        check(get("screen").equals("S06") && app.editor!=null,"Preview opens the editor directly");
        check(service.user().equals("demo"),"Preview uses the local sample account");
        check(button(app,"변경 반영 · F5")!=null,"Reload button is visible");
        check(app.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke("F5")).equals("development-reload"),"F5 binds the same reload action");
        var activeField=EditorPanel.class.getDeclaredField("active");activeField.setAccessible(true);
        @SuppressWarnings("unchecked") Map<Integer,Long> active=(Map<Integer,Long>)activeField.get(app.editor);
        var recordingField=EditorPanel.class.getDeclaredField("recording");recordingField.setAccessible(true);recordingField.setBoolean(app.editor,true);
        var originField=EditorPanel.class.getDeclaredField("origin");originField.setAccessible(true);originField.setLong(app.editor,System.nanoTime()-250_000_000L);
        active.put(85,0L);dialog("계속 편집");button(app,"변경 반영 · F5").doClick();
        check(app.isShowing() && restarted.get()==0 && app.editor.dirty && app.editor.notes.stream().anyMatch(n->n.pitch()==85),"Reload finishes pending recording before testing for unsaved music");
        app.editor.undo();
        app.editor.memo.setText("미리보기 초안");dialog("계속 편집");button(app,"변경 반영 · F5").doClick();
        check(app.isShowing() && app.editor.dirty && restarted.get()==0,"Cancel reload preserves the working draft");
        check(!Files.exists(state),"Canceled reload does not replace saved view state");
        dialog("버리고 이동");button(app,"변경 반영 · F5").doClick();
        check(!app.isShowing() && restarted.get()==1,"Confirmed reload closes before requesting a rebuild");
      });
      Properties view=new Properties();try(var reader=Files.newBufferedReader(state)){view.load(reader);}
      check(view.getProperty("screen").equals("S06") && view.getProperty("song").equals("first"),"Current screen and song are retained");
      check(view.stringPropertyNames().stream().noneMatch(key->key.contains("password")||key.contains("token")||key.contains("memo")),"View state excludes credentials and unsaved music");
      edt(()->{app=new MMM(service,directory);app.enableDevelopmentPreview(state,restarted::incrementAndGet);});
      idle();edt(()->app.restoreDevelopmentPreview(view));idle();
      edt(()->{
        check(get("screen").equals("S06") && !app.editor.memo.getText().equals("미리보기 초안"),"Reload restores the editor from saved music");
        app.editor.memo.setText("미리보기 저장");dialog("저장하고 이동");
        javax.swing.Timer confirmation=new javax.swing.Timer(30,null);
        confirmation.addActionListener(event->{for(Window window:Window.getWindows())if(window instanceof JDialog d && d.isVisible() && d.getTitle().startsWith("D03")) {
          AbstractButton ok=button(d,"확인");if(ok!=null){confirmation.stop();ok.doClick();}
        }});confirmation.start();
        button(app,"변경 반영 · F5").doClick();
      });
      idle();
      check(restarted.get()==2,"Save and reload waits for successful persistence");
      Phrase saved=service.phrases("first").stream().filter(p->p.author().equals("demo")&&p.date().equals(day)).findFirst().orElseThrow();
      check(saved.memo().equals("미리보기 저장"),"Saved preview music survives reload");
      edt(()->{app=new MMM(service,directory);app.enableDevelopmentPreview(state,restarted::incrementAndGet);});
      idle();edt(()->app.restoreDevelopmentPreview(view));idle();
      edt(()->{check(app.editor.memo.getText().equals("미리보기 저장"),"New preview shows the saved memo");capture("development-preview");app.editor.dispose();app.stopSound();app.midi.close();app.dispose();});
      System.out.println("PASS: "+checks+" development preview reload, persistence and draft checks");System.exit(0);
    }catch(Throwable failure){failure.printStackTrace();if(app!=null)edt(()->{if(app.editor!=null)app.editor.dispose();app.midi.close();app.dispose();});System.exit(1);}
  }
}
