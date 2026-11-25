package mmm;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.sound.midi.*;
import mmm.Model.*;

public final class PitchRangeTest {
  private static int checks;
  private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
  public static void main(String[] args) throws Exception {
    for(int pitch=0;pitch<128;pitch++)new NoteEvent(0,120,pitch,90);
    check(true,"All MIDI pitches accepted");
    for(int pitch:List.of(-1,128)) {
      boolean rejected=false;try{new NoteEvent(0,120,pitch,90);}catch(IllegalArgumentException expected){rejected=true;}
      check(rejected,"Invalid pitch rejected");
    }
    Path folder=Files.createTempDirectory("mmm-wide-pitch-");
    MusicService service=new MusicService(new LocalRepository(folder),Clock.systemUTC(),false);
    service.register("wideuser","음역 검사","test1234".toCharArray(),"test1234".toCharArray());service.login("wideuser","test1234".toCharArray());
    Song song=service.create("전체 음역",false,90,0);
    Phrase saved=service.save(new Phrase(song.id(),"wideuser",service.today(),0,90,0,"최저와 최고",
        List.of(new NoteEvent(0,480,0,90),new NoteEvent(960,480,127,90))));
    check(new LocalRepository(folder).phrases(song.id()).get(0).equals(saved),"Extremes survive local disk roundtrip");
    Sequence sequence=MidiEngine.sequence(List.of(new MidiEngine.Part(saved,0,0,100)),1920);
    Set<Integer> pitches=new HashSet<>();
    for(Track track:sequence.getTracks())for(int i=0;i<track.size();i++)
      if(track.get(i).getMessage() instanceof ShortMessage message && message.getCommand()==ShortMessage.NOTE_ON)pitches.add(message.getData1());
    check(pitches.equals(Set.of(0,127)),"MIDI output retains exact lowest and highest pitches");
    NoteCanvas canvas=new NoteCanvas(saved::notes,false,null,null);canvas.setSize(900,300);
    for(NoteEvent note:saved.notes())check(new Rectangle(0,0,900,300).contains(canvas.noteShape(note).getBounds()),"Wide pitch glyph remains inside editor");
    BufferedImage thumbnail=new BufferedImage(280,120,BufferedImage.TYPE_INT_RGB);Graphics2D g=thumbnail.createGraphics();
    g.setColor(Color.WHITE);g.fillRect(0,0,280,120);SoundShape.miniature(g,saved.notes(),10,15,240,80);g.dispose();
    int ink=0;boolean outside=false;
    for(int y=0;y<120;y++)for(int x=0;x<280;x++)if((thumbnail.getRGB(x,y)&0xffffff)!=0xffffff){ink++;outside|=y<15||y>=95;}
    check(ink>0 && !outside,"Wide pitch thumbnails remain in their vertical bounds");
    ScorePanel score=new ScorePanel("전체 음역",List.of(saved));score.setSize(score.getPreferredSize());
    check(score.getHeight()>500,"Score allocates room for extreme ledger lines");
    BufferedImage image=new BufferedImage(score.getWidth(),score.getHeight(),BufferedImage.TYPE_INT_RGB);g=image.createGraphics();score.paint(g);g.dispose();
    Path evidence=Path.of("build","evidence");Files.createDirectories(evidence);ImageIO.write(image,"png",evidence.resolve("wide-pitch-score.png").toFile());
    PageFormat format=new PageFormat();Paper paper=new Paper();paper.setSize(600,800);paper.setImageableArea(24,24,552,752);format.setPaper(paper);
    g=image.createGraphics();check(score.print(g,format,0)==Printable.PAGE_EXISTS && score.print(g,format,1)==Printable.NO_SUCH_PAGE,"Wide score paginates");g.dispose();
    check(saved.notes().get(0).pitch()==0 && saved.notes().get(1).pitch()==127,"Rendering preserves extreme pitches");
    System.out.println("PASS: "+checks+" full MIDI range, local save, rendering and score checks");
  }
}
