package mmm;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import javax.imageio.ImageIO;
import javax.imageio.*;
import javax.imageio.metadata.*;
import javax.imageio.stream.*;
import javax.sound.midi.*;
import javax.swing.*;
import mmm.Model.*;
import static mmm.GuiFlowTest.*;

/** Drives real input listeners with a controlled monotonic clock, including held frames. */
public final class LiveSoundGuiTest {
  private static final LocalDate DAY=LocalDate.of(2026,11,17);
  private static final AtomicLong time=new AtomicLong();
  private static Song song;
  private static Object field(Object object,String name) throws Exception {Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
  private static void setField(Object object,String name,Object value) throws Exception {Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
  private static long nanos(long ticks){return Math.round(ticks*60_000_000_000.0/(90*Model.PPQ));}
  private static void at(long ticks){time.set(nanos(ticks));}
  private static void open(int program) throws Exception {
    app.edit(new Phrase(song.id(),"tester",DAY,0,90,program,"",List.of()),DAY);time.set(0);
    setField(app.editor,"nanoTime",(LongSupplier)time::get);app.validate();
  }
  private static void key(JComponent surface,int code,boolean down) {
    KeyEvent event=new KeyEvent(surface,down?KeyEvent.KEY_PRESSED:KeyEvent.KEY_RELEASED,System.currentTimeMillis(),0,code,(char)code);
    for(KeyListener listener:surface.getKeyListeners())if(down)listener.keyPressed(event);else listener.keyReleased(event);
  }
  private static void tick() throws Exception {Method method=EditorPanel.class.getDeclaredMethod("tick");method.setAccessible(true);method.invoke(app.editor);}
  private static BufferedImage picture(JComponent component) {
    BufferedImage image=new BufferedImage(component.getWidth(),component.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();component.paint(g);g.dispose();return image;
  }
  private static long cyan(BufferedImage image) {
    long count=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){Color c=new Color(image.getRGB(x,y));if(c.getGreen()>150 && c.getBlue()>150 && c.getRed()<140)count++;}return count;
  }
  private static void animation(List<BufferedImage> frames) throws Exception {
    ImageWriter writer=ImageIO.getImageWritersByFormatName("gif").next();
    try(ImageOutputStream output=ImageIO.createImageOutputStream(Path.of("build","evidence","live-sound-growth.gif").toFile())) {
      writer.setOutput(output);writer.prepareWriteSequence(null);
      for(int i=0;i<frames.size();i++) {
        var metadata=writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(frames.get(i)),null);
        String format=metadata.getNativeMetadataFormatName();IIOMetadataNode tree=(IIOMetadataNode)metadata.getAsTree(format);
        IIOMetadataNode control=(IIOMetadataNode)tree.getElementsByTagName("GraphicControlExtension").item(0);if(control==null){control=new IIOMetadataNode("GraphicControlExtension");tree.appendChild(control);}control.setAttribute("disposalMethod","none");control.setAttribute("userInputFlag","FALSE");control.setAttribute("transparentColorFlag","FALSE");control.setAttribute("delayTime",i==frames.size()-1?"65":"10");control.setAttribute("transparentColorIndex","0");
        if(i==0){IIOMetadataNode extensions=new IIOMetadataNode("ApplicationExtensions"),loop=new IIOMetadataNode("ApplicationExtension");loop.setAttribute("applicationID","NETSCAPE");loop.setAttribute("authenticationCode","2.0");loop.setUserObject(new byte[]{1,0,0});extensions.appendChild(loop);tree.appendChild(extensions);}
        metadata.setFromTree(format,tree);writer.writeToSequence(new IIOImage(frames.get(i),null,metadata),null);
      }
      writer.endWriteSequence();
    }finally{writer.dispose();}
  }
  public static void main(String[] args) throws Exception {
    try {
      Thread watchdog=new Thread(()->{try{Thread.sleep(90000);System.err.println("Live sound GUI timeout");System.exit(2);}catch(InterruptedException ignored){}});watchdog.setDaemon(true);watchdog.start();
      root=Files.createTempDirectory("mmm-live-sound-gui-");Clock clock=Clock.fixed(DAY.atTime(12,0).atZone(ZoneId.of("Asia/Seoul")).toInstant(),ZoneId.of("Asia/Seoul"));
      MusicService service=new MusicService(new LocalRepository(root),clock,false);service.register("tester","실시간 검사","test1234".toCharArray(),"test1234".toCharArray());service.login("tester","test1234".toCharArray());song=service.create("누른 만큼 그리기",false,90,0);
      edt(()->{Theme.install();app=new MMM(service,root);});idle();
      edt(()->{
        set("current",song);open(0);
        check(((JComboBox<?>)field(app.editor,"mode")).getSelectedIndex()==2,"New editor defaults to held-duration input");
        ((JSlider)field(app.editor,"inputVelocity")).setValue(45);key(app.editor.piano,'A',true);
        check(app.editor.notes.isEmpty() && app.editor.pendingNotes().size()==1 && app.editor.dirty,"Note appears immediately before release and is guarded as a draft");
        var beginning=app.editor.pendingNotes().get(0);double initialWidth=app.editor.canvas.liveShape(beginning).getBounds2D().getWidth();
        List<BufferedImage> frames=new ArrayList<>();for(int frame:new int[]{1,30,60,90,120,180,240}){at(frame);tick();frames.add(picture(app.editor.canvas));}
        var held=app.editor.pendingNotes().get(0);Rectangle2D shortShape=app.editor.canvas.liveShape(held).getBounds2D();
        check(shortShape.getWidth()>initialWidth*20 && app.editor.notes.isEmpty(),"Tail grows while the key remains down, without a stored placeholder");
        check(cyan(picture(app.editor.canvas))>50,"Held note is painted cyan on the actual editing canvas");
        capture("live-sound-piano-short");for(int frame:new int[]{300,360,420,480}){at(frame);tick();frames.add(picture(app.editor.canvas));}animation(frames);capture("live-sound-piano-long");key(app.editor.piano,'A',false);
        check(app.editor.pendingNotes().isEmpty() && app.editor.notes.get(0).duration()==480 && app.editor.notes.get(0).velocity()==45,"Release freezes measured duration and input strength");
        ((JSlider)field(app.editor,"inputVelocity")).setValue(110);key(app.editor.piano,'D',true);at(1200);tick();var high=app.editor.pendingNotes().get(0);Rectangle2D longShape=app.editor.canvas.liveShape(high).getBounds2D();
        check(longShape.getWidth()>shortShape.getWidth()*2,"Long hold produces a longer visual tail");
        check(longShape.getHeight()>shortShape.getHeight() && longShape.getCenterY()<shortShape.getCenterY(),"Strength changes thickness and higher pitch raises the body");
        key(app.editor.piano,'D',false);check(app.editor.notes.size()==2 && app.editor.notes.get(1).start()==480 && app.editor.notes.get(1).duration()==720,"Sequential input continues from the previous release");
        Phrase beforeUndo=app.editor.snapshot();app.editor.undo();check(app.editor.notes.size()==1,"Held note is one undo operation");app.editor.redo();check(app.editor.snapshot().equals(beforeUndo),"Redo restores the same duration and strength");
        app.editor.memo.setText("누른 만큼 저장");dialog("OK");app.saveEditor(app::week);
      });idle();
      Phrase saved=service.phrases(song.id()).get(0);check(new LocalRepository(root).phrases(song.id()).get(0).equals(saved),"Measured lengths and strengths survive local save/reopen");
      Sequence sequence=MidiEngine.sequence(List.of(new MidiEngine.Part(saved,0,0,100)),Model.BAR);Set<Long> off=new HashSet<>();
      for(Track track:sequence.getTracks())for(int i=0;i<track.size();i++)if(track.get(i).getMessage() instanceof ShortMessage m && m.getCommand()==ShortMessage.NOTE_OFF)off.add(track.get(i).getTick());
      check(off.contains(480L)&&off.contains(1200L),"MIDI note-off times match the visual and saved lengths");
      edt(()->{
        open(24);check(app.editor.liveView.isShowing() && app.editor.guitar.isShowing(),"Guitar surface and live drawing are visible together");
        key(app.editor.guitar,'S',true);at(240);tick();check(app.editor.pendingNotes().size()==6,"All six held chord notes appear immediately");
        long shortPixels=cyan(picture(app.editor.liveView));at(720);tick();
        check(cyan(picture(app.editor.liveView))>shortPixels,"Chord tails visibly grow in the performance-side drawing");
        capture("live-sound-guitar");ImageIO.write(picture(app.editor.liveView),"png",Path.of("build","evidence","live-sound-view.png").toFile());
        key(app.editor.guitar,'S',false);check(app.editor.notes.size()==6 && app.editor.notes.stream().allMatch(n->n.start()==0&&n.duration()==720),"A held chord releases with six equal measured durations");
        app.editor.undo();check(app.editor.notes.isEmpty(),"One undo removes the complete held chord");app.editor.redo();check(app.editor.notes.size()==6,"Chord redo restores all six notes");
        open(0);key(app.editor.piano,'A',true);at(240);key(app.editor.piano,'D',true);at(480);key(app.editor.piano,'A',false);
        check(app.editor.pendingNotes().size()==1 && app.editor.pendingNotes().get(0).pitch()==64,"Releasing one key leaves the other note growing");
        at(720);key(app.editor.piano,'D',false);check(app.editor.notes.size()==2&&app.editor.notes.get(1).start()==240&&app.editor.notes.get(1).duration()==480,"Overlapping holds preserve their independent start/release times");
        open(0);key(app.editor.piano,'A',true);at(3000);tick();
        check(app.editor.pendingNotes().isEmpty()&&app.editor.notes.size()==1&&app.editor.notes.get(0).duration()==Model.BAR,"Four-beat boundary finalizes the held note and clears pending state");
        open(0);key(app.editor.piano,'A',true);at(500);app.editor.stop();
        check(app.editor.notes.size()==1&&app.editor.notes.get(0).duration()==480&&app.editor.pendingNotes().isEmpty(),"Stop finalizes and quantizes pending duration without dropping the draft");
        open(0);((JComboBox<?>)field(app.editor,"mode")).setSelectedIndex(1);button(app.editor,"녹음 시작").doClick();time.set((Long)field(app.editor,"origin"));tick();key(app.editor.piano,'A',true);
        check(app.editor.pendingNotes().size()==1,"Four-beat recording also previews a note before release");time.addAndGet(nanos(720));tick();
        check(app.editor.pendingNotes().get(0).duration()==720,"Recording preview follows the running transport");key(app.editor.piano,'A',false);app.editor.stop();check(app.editor.notes.get(0).duration()==720,"Recorded duration matches its live tail");
        open(33);check(app.editor.liveView.isShowing()&&app.editor.bass.isShowing(),"Bass also keeps the drawing visible");open(Model.DRUMS);key(app.editor.drums,'A',true);at(240);key(app.editor.drums,'A',false);
        check(app.editor.liveView.isShowing()&&app.editor.notes.get(0).duration()==240,"Held-duration drum input shares the visual timing");
        app.setSize(1000,720);app.validate();check(app.editor.liveView.getWidth()>=200&&app.editor.drums.getWidth()>=500,"Small window retains usable instrument and drawing widths");
        for(int pitch:DrumKit.PITCHES)check(new Rectangle(app.editor.drums.getSize()).contains(app.editor.drums.padCenter(pitch)),"Small window keeps drum target in bounds: "+pitch);
        capture("live-sound-minimum");app.editor.dispose();app.midi.close();app.dispose();
      });
      System.out.println("PASS: "+checks+" live duration, glow, held chords, history, storage, MIDI and layout checks");System.exit(0);
    }catch(Throwable failure){failure.printStackTrace();if(app!=null)edt(()->{if(app.editor!=null)app.editor.dispose();app.midi.close();app.dispose();});System.exit(1);}
  }
}
