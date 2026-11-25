package mmm;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Exercise the real hit areas, note callbacks and keyboard ownership. */
public final class PianoTest {
  private static int checks;
  private static void check(boolean value,String description) {
    if(!value)throw new AssertionError(description);checks++;
  }
  private static void key(Piano piano,int code,boolean down) {
    KeyEvent event=new KeyEvent(piano,down?KeyEvent.KEY_PRESSED:KeyEvent.KEY_RELEASED,
        System.currentTimeMillis(),0,code,(char)code);
    for(KeyListener listener:piano.getKeyListeners())if(down)listener.keyPressed(event);else listener.keyReleased(event);
  }
  private static void mouse(Piano piano,int id,int x,int y) {
    piano.dispatchEvent(new MouseEvent(piano,id,System.currentTimeMillis(),
        id==MouseEvent.MOUSE_DRAGGED?InputEvent.BUTTON1_DOWN_MASK:0,x,y,1,false,
        id==MouseEvent.MOUSE_DRAGGED?MouseEvent.NOBUTTON:MouseEvent.BUTTON1));
  }
  private static void click(Piano piano,int x,int y) {
    mouse(piano,MouseEvent.MOUSE_PRESSED,x,y);mouse(piano,MouseEvent.MOUSE_RELEASED,x,y);
  }
  public static void main(String[] args) throws Exception {
    SwingUtilities.invokeAndWait(()->{
      try {
        Theme.install();AtomicBoolean pentatonic=new AtomicBoolean();List<String> events=new ArrayList<>();
        Piano piano=new Piano(pentatonic::get,p->events.add("on"+p),p->events.add("off"+p));
        for(int width:List.of(442,882,1762)) {
          piano.setSize(width,112);events.clear();int boundary=1+(width-2)*8/22;
          click(piano,boundary,20);
          check(events.equals(List.of("on61","off61")),"Upper black key must take precedence at width "+width);
          events.clear();click(piano,boundary,100);
          check(events.equals(List.of("on62","off62")),"Lower white key remains playable beneath black key at width "+width);
        }
        piano.setSize(882,112);events.clear();
        click(piano,8,100);click(piano,873,100);
        check(events.equals(List.of("on48","off48","on84","off84")),"C3 and C6 octave endpoints");
        events.clear();click(piano,401,20);click(piano,561,20);
        check(events.equals(List.of("on65","off65","on72","off72")),"No black key between E/F or B/C");
        int[] codes={KeyEvent.VK_W,KeyEvent.VK_E,KeyEvent.VK_T,KeyEvent.VK_Y,KeyEvent.VK_U};
        int[] pitches={61,63,66,68,70};
        for(int i=0;i<codes.length;i++) {
          events.clear();key(piano,codes[i],true);key(piano,codes[i],true);key(piano,codes[i],false);
          check(events.equals(List.of("on"+pitches[i],"off"+pitches[i])),"Sharp shortcut and repeat guard "+(char)codes[i]);
        }
        events.clear();key(piano,KeyEvent.VK_A,true);key(piano,KeyEvent.VK_K,true);piano.releaseAll();
        check(events.subList(0,2).equals(List.of("on60","on72")) && events.size()==4,"Existing A/K shortcuts remain C4/C5");
        events.clear();key(piano,KeyEvent.VK_W,true);mouse(piano,MouseEvent.MOUSE_PRESSED,321,20);
        key(piano,KeyEvent.VK_W,false);
        check(events.equals(List.of("on61")),"Mouse ownership keeps the typed note sounding");
        mouse(piano,MouseEvent.MOUSE_RELEASED,321,20);
        check(events.equals(List.of("on61","off61")),"Last input releases a shared pitch exactly once");
        events.clear();mouse(piano,MouseEvent.MOUSE_PRESSED,300,100);mouse(piano,MouseEvent.MOUSE_DRAGGED,321,20);
        mouse(piano,MouseEvent.MOUSE_RELEASED,321,20);
        check(events.equals(List.of("on60","off60","on61","off61")),"Dragging between white and black keys releases old pitch");
        events.clear();key(piano,KeyEvent.VK_A,true);key(piano,KeyEvent.VK_W,true);
        for(FocusListener listener:piano.getFocusListeners())listener.focusLost(new FocusEvent(piano,FocusEvent.FOCUS_LOST));
        check(new HashSet<>(events).equals(Set.of("on60","on61","off60","off61")),"Focus loss releases white/black chord");
        events.clear();click(piano,-10,20);click(piano,890,20);
        check(events.isEmpty(),"Outside keyboard cannot create a note");
        pentatonic.set(true);click(piano,321,20);click(piano,410,100);key(piano,KeyEvent.VK_W,true);key(piano,KeyEvent.VK_W,false);
        check(events.isEmpty(),"Pentatonic mode excludes sharps and F");
        key(piano,KeyEvent.VK_F,true);key(piano,KeyEvent.VK_F,false);
        check(events.equals(List.of("on67","off67")),"Pentatonic F shortcut retains its G4 mapping");
        pentatonic.set(false);piano.releaseAll();
        BufferedImage image=new BufferedImage(882,112,BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics=image.createGraphics();piano.paint(graphics);graphics.dispose();
        check(new Color(image.getRGB(321,20)).getRed()<50 && new Color(image.getRGB(330,73)).getRed()>240,
            "Visible black and white key layers match the playable regions");
        Path directory=Path.of("build","evidence");Files.createDirectories(directory);
        ImageIO.write(image,"png",directory.resolve("piano-keyboard.png").toFile());
        events.clear();key(piano,KeyEvent.VK_W,true);piano.shiftOctave(-1);
        check(events.equals(List.of("on61","off61")),"Changing octave releases held pitch before remapping");
        key(piano,KeyEvent.VK_W,false);key(piano,KeyEvent.VK_A,true);key(piano,KeyEvent.VK_A,false);
        check(events.equals(List.of("on61","off61","on48","off48")),"Typing follows the shifted octave without stuck notes");
        while(piano.canShift(-1))piano.shiftOctave(-1);
        check(piano.firstPitch()==0 && piano.lastPitch()==36 && piano.rangeLabel().equals("C-1–C2"),"Lowest register");
        piano.shiftOctave(-1);check(piano.firstPitch()==0,"Lower navigation stops at MIDI zero");
        Set<Integer> reachable=new TreeSet<>();
        while(true) {
          events.clear();
          for(int x=6;x<880;x+=8){click(piano,x,20);click(piano,x,100);}
          for(String event:events)if(event.startsWith("on"))reachable.add(Integer.parseInt(event.substring(2)));
          if(!piano.canShift(1))break;
          piano.shiftOctave(1);
        }
        check(reachable.size()==128 && reachable.contains(0) && reachable.contains(127),"All 128 MIDI pitches reachable across pages");
        check(piano.firstPitch()==96 && piano.lastPitch()==127 && piano.rangeLabel().equals("C7–G9"),"Highest register is trimmed at G9");
        events.clear();click(piano,873,100);check(events.equals(List.of("on127","off127")),"Highest visible white key plays G9");
        piano.shiftOctave(1);check(piano.firstPitch()==96,"Upper navigation stays inside MIDI range");
        events.clear();key(piano,KeyEvent.VK_W,true);key(piano,KeyEvent.VK_W,false);
        check(events.equals(List.of("on109","off109")),"Black shortcut follows highest register");
        pentatonic.set(true);events.clear();key(piano,KeyEvent.VK_K,true);key(piano,KeyEvent.VK_K,false);
        check(events.equals(List.of("on124","off124")),"Pentatonic typing stays valid at top register");
        pentatonic.set(false);piano.getActionMap().get("default-range").actionPerformed(null);
        check(piano.firstPitch()==48 && piano.pitch(0)==60,"Home returns to middle register");
      }catch(Exception error){throw new RuntimeException(error);}
    });
    System.out.println("PASS: "+checks+" piano hit area, chromatic input, ownership and scale checks");
  }
}
