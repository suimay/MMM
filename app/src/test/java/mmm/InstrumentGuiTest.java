package mmm;

import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import mmm.Model.*;
import static mmm.GuiFlowTest.*;

public final class InstrumentGuiTest {
  private static Song song;
  private static final LocalDate DAY=LocalDate.of(2026,11,16);
  private static Object field(Object object,String name) throws Exception {Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
  private static void open(int program,int version) throws Exception {app.edit(new Phrase(song.id(),"tester",DAY,version,90,program,"",List.of()),DAY);((JComboBox<?>)field(app.editor,"mode")).setSelectedIndex(0);app.validate();}
  private static void key(JComponent component,int code,boolean down) {
    KeyEvent event=new KeyEvent(component,down?KeyEvent.KEY_PRESSED:KeyEvent.KEY_RELEASED,System.currentTimeMillis(),0,code,(char)code);
    for(KeyListener listener:component.getKeyListeners())if(down)listener.keyPressed(event);else listener.keyReleased(event);
  }
  private static void tap(JComponent component,Point point) {
    for(int type:new int[]{MouseEvent.MOUSE_PRESSED,MouseEvent.MOUSE_RELEASED})component.dispatchEvent(new MouseEvent(component,type,System.currentTimeMillis(),0,point.x,point.y,1,false,MouseEvent.BUTTON1));
  }
  private static void surfacePicture(JComponent component,String name) throws Exception {
    java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(component.getWidth(),component.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
    Graphics2D g=image.createGraphics();component.paint(g);g.dispose();
    javax.imageio.ImageIO.write(image,"png",Path.of("build","evidence",name+"-surface.png").toFile());
  }
  public static void main(String[] args) throws Exception {
    try {
      Thread watchdog=new Thread(()->{try{Thread.sleep(90000);System.err.println("Instrument GUI timeout");System.exit(2);}catch(InterruptedException ignored){}});watchdog.setDaemon(true);watchdog.start();
      root=Files.createTempDirectory("mmm-instrument-gui-");Clock clock=Clock.fixed(DAY.atTime(12,0).atZone(ZoneId.of("Asia/Seoul")).toInstant(),ZoneId.of("Asia/Seoul"));
      MusicService service=new MusicService(new LocalRepository(root),clock,false);
      service.register("tester","연주 검사","test1234".toCharArray(),"test1234".toCharArray());service.login("tester","test1234".toCharArray());song=service.create("악기별 연주",false,90,0);
      edt(()->{Theme.install();app=new MMM(service,root);});idle();
      edt(()->{
        set("current",song);open(33,0);
        check(app.editor.bass.isShowing() && !app.editor.piano.isShowing(),"Bass selection shows four strings instead of piano");
        key(app.editor.bass,KeyEvent.VK_A,true);key(app.editor.bass,KeyEvent.VK_A,false);
        check(app.editor.snapshot().instrument()==33 && app.editor.notes.get(0).pitch()==28,"Bass chord strip records a low E bass note");
        javax.sound.midi.Synthesizer synth=(javax.sound.midi.Synthesizer)field(app.midi,"synth");
        check(synth.getChannels()[0].getProgram()==33,"Live synthesizer selects the fingered bass voice");
        button(app.editor.bass,"노트").doClick();tap(app.editor.bass,new Point(app.editor.bass.getWidth()-30,116));
        check(app.editor.notes.get(app.editor.notes.size()-1).pitch()==55,"Bass note view plays the clicked string and fret");
        button(app.editor.bass,"코드").doClick();capture("instrument-bass");surfacePicture(app.editor.bass,"instrument-bass");
        button(app.editor,"음 편집").doClick();check(app.editor.canvas.isShowing(),"Bass recording can be edited on the sound canvas");
        button(app.editor,"연주 화면").doClick();check(app.editor.bass.isShowing(),"Bass performance view returns");
        open(24,0);check(app.editor.guitar.isShowing(),"Guitar selection shows six-string body");
        key(app.editor.guitar,KeyEvent.VK_A,true);key(app.editor.guitar,KeyEvent.VK_A,false);
        check(app.editor.notes.size()==6 && app.editor.notes.stream().allMatch(n->n.start()==0&&n.duration()==480),"One guitar chord enters six simultaneous notes in one beat");
        app.editor.undo();check(app.editor.notes.isEmpty(),"One undo removes the whole chord");app.editor.redo();check(app.editor.notes.size()==6,"Redo restores the whole chord");
        capture("instrument-guitar");surfacePicture(app.editor.guitar,"instrument-guitar");
        button(app.editor.guitar,"노트").doClick();tap(app.editor.guitar,new Point(app.editor.guitar.getWidth()-30,116));
        check(app.editor.notes.size()==7 && app.editor.notes.get(6).pitch()==76,"Guitar note view plays the high E string at fret twelve");
        open(Model.DRUMS,0);check(app.editor.drums.isShowing()&&!app.editor.piano.isShowing(),"Drum selection shows the kit");
        for(int pitch:DrumKit.PITCHES)tap(app.editor.drums,app.editor.drums.padCenter(pitch));
        check(new HashSet<>(app.editor.notes.stream().map(NoteEvent::pitch).toList()).equals(Arrays.stream(DrumKit.PITCHES).boxed().collect(java.util.stream.Collectors.toSet())),"Every drum and open/closed hi-hat is playable");
        check(app.editor.notes.size()==9 && app.editor.notes.stream().allMatch(n->n.duration()==120),"Drum hits use quarter-beat steps");
        check(synth.getChannels()[0].getProgram()==24 && synth.getChannels()[9].getProgram()==0,"Live drum kit uses percussion without changing the guitar channel");
        capture("instrument-drums");surfacePicture(app.editor.drums,"instrument-drums");app.setSize(1000,720);app.validate();
        for(int pitch:DrumKit.PITCHES)check(new Rectangle(app.editor.drums.getSize()).contains(app.editor.drums.padCenter(pitch)),"Drum pad inside minimum window: "+pitch);
        capture("instrument-drums-minimum");app.setSize(1180,850);app.validate();
        button(app.editor,"음 편집").doClick();
        @SuppressWarnings("unchecked") JComboBox<String> choice=(JComboBox<String>)field(app.editor,"drumChoice");
        check(choice.isShowing(),"Drum editor offers named pieces instead of a pitch slider");choice.setSelectedIndex(1);
        check(app.editor.notes.get(app.editor.canvas.selected).pitch()==38,"Named piece selection changes the stored drum hit");
        app.editor.memo.setText("드럼 연주");dialog("OK");app.saveEditor(app::week);
      });idle();
      Phrase saved=service.phrases(song.id()).get(0);check(saved.instrument()==Model.DRUMS && saved.notes().size()==9,"Saved music retains kit identity and hits");
      check(new LocalRepository(root).phrases(song.id()).get(0).equals(saved),"Reopening local data restores drum sound and notes");
      edt(()->{
        open(12,saved.version());check(app.editor.snapshot().instrument()==12&&!app.editor.dirty,"Opening legacy marimba does not silently convert the record");
        @SuppressWarnings("unchecked") JComboBox<String> choice=(JComboBox<String>)field(app.editor,"instrument");choice.setSelectedIndex(1);
        check(app.editor.bass.isShowing() && app.editor.instrumentProgram()==33,"Changing instrument switches the performance surface immediately");
        app.editor.undo();check(app.editor.instrumentProgram()==12&&app.editor.piano.isShowing(),"Undo restores legacy sound and its view");
        app.editor.dispose();app.midi.close();app.dispose();
      });
      System.out.println("PASS: "+checks+" instrument surface, chord, drum input, save and layout checks");System.exit(0);
    }catch(Throwable failure){failure.printStackTrace();if(app!=null)edt(()->{if(app.editor!=null)app.editor.dispose();app.midi.close();app.dispose();});System.exit(1);}
  }
}
