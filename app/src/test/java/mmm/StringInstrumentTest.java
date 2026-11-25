package mmm;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Regression checks for silent chord strings and pitch-based visual aliasing. */
public final class StringInstrumentTest {
  private static final String KEYS="ASDFGHJK";
  private static int checks;
  private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
  private static Point cell(int column,int row,boolean chords,boolean bass) {
    int count=chords?8:13,strings=bass?4:6;
    return new Point(42+(2*column+1)*(1200-66)/(2*count),116+row*(420-18-92-48)/(strings-1));
  }
  private static void mouse(StringInstrument surface,int type,Point point) {
    surface.dispatchEvent(new MouseEvent(surface,type,System.currentTimeMillis(),
        type==MouseEvent.MOUSE_DRAGGED?InputEvent.BUTTON1_DOWN_MASK:0,
        point.x,point.y,1,false,type==MouseEvent.MOUSE_DRAGGED?MouseEvent.NOBUTTON:MouseEvent.BUTTON1));
  }
  private static void key(StringInstrument surface,int code,boolean down) {
    KeyEvent event=new KeyEvent(surface,down?KeyEvent.KEY_PRESSED:KeyEvent.KEY_RELEASED,System.currentTimeMillis(),0,code,(char)code);
    for(KeyListener listener:surface.getKeyListeners())if(down)listener.keyPressed(event);else listener.keyReleased(event);
  }
  private static BufferedImage picture(StringInstrument surface) {
    layout(surface);
    BufferedImage image=new BufferedImage(1200,420,BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics=image.createGraphics();surface.paint(graphics);graphics.dispose();return image;
  }
  private static void layout(Container container){container.doLayout();for(Component child:container.getComponents())if(child instanceof Container nested)layout(nested);}
  private static Set<String> marked(StringInstrument surface,boolean chords,boolean bass) {
    BufferedImage image=picture(surface);Set<String> result=new HashSet<>();
    for(int column=0;column<(chords?8:13);column++)for(int row=0;row<(bass?4:6);row++) {
      Point point=cell(column,row,chords,bass);
      if(image.getRGB(point.x,point.y)==Theme.ORANGE.getRGB())result.add(column+":"+row);
    }
    return result;
  }
  private static Set<String> chordMarkers(int... columns) {
    Set<String> result=new HashSet<>();for(int column:columns)for(int row=0;row<6;row++)result.add(column+":"+row);return result;
  }
  private static JToggleButton mode(StringInstrument surface,String name) {
    return findMode(surface,name);
  }
  private static JToggleButton findMode(Container container,String name) {
    for(Component component:container.getComponents()) {
      if(component instanceof JToggleButton button && name.equals(button.getText()))return button;
      if(component instanceof Container child){JToggleButton found=findMode(child,name);if(found!=null)return found;}
    }
    return null;
  }
  private static void run() throws Exception {
    Theme.install();List<int[]> pressed=new ArrayList<>(),released=new ArrayList<>();
    StringInstrument guitar=new StringInstrument(false,pressed::add,released::add);
    guitar.setSize(1200,420);guitar.doLayout();
    int[][] chordClasses={{4,7,11},{9,0,4},{2,5,9},{7,11,2},{0,4,7},{5,9,0},{10,2,5},{11,2,5}};
    for(int column=0;column<8;column++) {
      Set<Integer> tones=new HashSet<>();for(int pitch:chordClasses[column])tones.add(pitch);
      int[] chord=guitar.notesForKey(KEYS.charAt(column));
      check(chord.length==6,"Every guitar chord has six playable strings: "+KEYS.charAt(column));
      check(Arrays.stream(chord).allMatch(p->tones.contains(p%12)),"Added low strings remain chord tones: "+KEYS.charAt(column));
      for(int row=0;row<6;row++) {
        pressed.clear();Point point=cell(column,row,true,false);
        mouse(guitar,MouseEvent.MOUSE_PRESSED,point);mouse(guitar,MouseEvent.MOUSE_RELEASED,point);
        check(pressed.size()==1 && Arrays.equals(pressed.get(0),new int[]{chord[5-row]}),"Guitar string sounds: "+KEYS.charAt(column)+" / "+(row+1));
      }
    }
    Point am=cell(1,2,true,false),dm=cell(2,2,true,false);
    pressed.clear();released.clear();mouse(guitar,MouseEvent.MOUSE_PRESSED,am);
    check(marked(guitar,true,false).equals(Set.of("1:2")),"Clicking Am A3 marks only that string and strip, not Dm or F");
    Files.createDirectories(Path.of("build","evidence"));ImageIO.write(picture(guitar),"png",Path.of("build","evidence","guitar-input-isolation.png").toFile());
    mouse(guitar,MouseEvent.MOUSE_DRAGGED,dm);
    check(pressed.size()==2 && released.size()==1 && Arrays.equals(pressed.get(0),pressed.get(1)),"Dragging to a different strip replucks the same pitch");
    check(marked(guitar,true,false).equals(Set.of("2:2")),"Same-pitch drag moves the marker to the new cell only");
    mouse(guitar,MouseEvent.MOUSE_DRAGGED,new Point(dm.x+2,dm.y+1));
    check(pressed.size()==2,"Dragging inside one cell does not repeat the note");
    mouse(guitar,MouseEvent.MOUSE_RELEASED,dm);check(marked(guitar,true,false).isEmpty(),"Mouse release clears feedback");
    key(guitar,'S',true);check(marked(guitar,true,false).equals(chordMarkers(1)),"Keyboard Am highlights only its own six strings");
    key(guitar,'D',true);check(marked(guitar,true,false).equals(chordMarkers(1,2)),"Simultaneously held chords show only the two pressed strips");
    key(guitar,'S',false);check(marked(guitar,true,false).equals(chordMarkers(2)),"Releasing one chord clears its feedback despite shared pitches");
    key(guitar,'D',false);key(guitar,'S',true);mouse(guitar,MouseEvent.MOUSE_PRESSED,dm);
    Set<String> mixed=chordMarkers(1);mixed.add("2:2");
    check(marked(guitar,true,false).equals(mixed),"Keyboard and pointer retain their own feedback");
    released.clear();key(guitar,'S',false);
    check(marked(guitar,true,false).equals(Set.of("2:2")) && released.stream().flatMapToInt(Arrays::stream).noneMatch(p->p==57),"Shared pitch stays owned by the remaining pointer only");
    mouse(guitar,MouseEvent.MOUSE_RELEASED,dm);
    check(released.stream().flatMapToInt(Arrays::stream).anyMatch(p->p==57),"Last input releases the shared voice");
    mouse(guitar,MouseEvent.MOUSE_PRESSED,am);guitar.releaseAll();check(marked(guitar,true,false).isEmpty(),"Focus and global release clear input feedback");
    mode(guitar,"노트").doClick();Point fret=cell(5,5,false,false);mouse(guitar,MouseEvent.MOUSE_PRESSED,fret);
    check(marked(guitar,false,false).equals(Set.of("5:5")),"Note mode marks the played fret, not equal pitches on other strings");
    mouse(guitar,MouseEvent.MOUSE_RELEASED,fret);key(guitar,'F',true);
    check(marked(guitar,false,false).equals(Set.of("5:5")),"Note keyboard maps feedback to its single playable fret");
    key(guitar,'F',false);mode(guitar,"코드").doClick();guitar.shiftOctave(1);
    mouse(guitar,MouseEvent.MOUSE_PRESSED,am);check(marked(guitar,true,false).equals(Set.of("1:2")),"Octave changes retain input-local feedback");guitar.releaseAll();
    StringInstrument bass=new StringInstrument(true,notes->{},notes->{});bass.setSize(1200,420);bass.doLayout();key(bass,'K',true);
    check(marked(bass,true,true).equals(Set.of("7:3")),"Bass root feedback does not light matching pitches in other chords");bass.releaseAll();
    DrumKit drums=new DrumKit(pressed::add,released::add);drums.setSize(1200,420);pressed.clear();Point kick=drums.padCenter(36);
    drums.dispatchEvent(new MouseEvent(drums,MouseEvent.MOUSE_PRESSED,0,0,kick.x,kick.y,1,false,MouseEvent.BUTTON1));
    check(pressed.size()==1 && pressed.get(0)[0]==36,"Shared pointer handling preserves drum input");drums.releaseAll();
  }
  public static void main(String[] args) throws Exception {
    SwingUtilities.invokeAndWait(()->{try{run();}catch(Exception failure){throw new RuntimeException(failure);}});
    System.out.println("PASS: "+checks+" chord strings, input-local feedback, drag and shared ownership checks");
  }
}
