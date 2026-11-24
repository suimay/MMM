package mmm;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.*;

/** Shared input ownership for fretted instruments and the drum kit. */
public abstract class PlayableSurface extends JPanel {
  private final Consumer<int[]> press,release;
  private final Map<Integer,int[]> keys=new HashMap<>();
  private final Map<Integer,Integer> owners=new HashMap<>();
  private int[] mouse=new int[0];
  private Point mousePoint;
  private int mouseTarget;
  protected PlayableSurface(Consumer<int[]> press,Consumer<int[]> release) {
    this.press=press;this.release=release;setFocusable(true);setPreferredSize(new Dimension(1000,350));
    addKeyListener(new KeyAdapter(){
      public void keyPressed(KeyEvent event) {
        if(event.isControlDown()||event.isAltDown()||event.isMetaDown()||keys.containsKey(event.getKeyCode()))return;
        int[] notes=notesForKey(event.getKeyCode());
        if(notes.length>0){keys.put(event.getKeyCode(),notes);start(notes);}
      }
      public void keyReleased(KeyEvent event){int[] notes=keys.remove(event.getKeyCode());if(notes!=null)finish(notes);}
    });
    addMouseListener(new MouseAdapter(){
      public void mousePressed(MouseEvent event){if(SwingUtilities.isLeftMouseButton(event)){requestFocusInWindow();mouse(event.getPoint());}}
      public void mouseReleased(MouseEvent event){if(SwingUtilities.isLeftMouseButton(event))mouse(null);}
      public void mouseExited(MouseEvent event){mouse(null);}
    });
    addMouseMotionListener(new MouseMotionAdapter(){public void mouseDragged(MouseEvent event){if((event.getModifiersEx()&InputEvent.BUTTON1_DOWN_MASK)!=0)mouse(event.getPoint());}});
    addFocusListener(new FocusAdapter(){public void focusLost(FocusEvent event){releaseAll();}});
  }
  protected abstract int[] notesAt(Point point);
  protected abstract int[] notesForKey(int code);
  /** Distinguishes playable locations that happen to have the same pitch. */
  protected int pointerTarget(Point point){return 0;}
  protected final Point pressedPoint(){return mousePoint==null?null:new Point(mousePoint);}
  protected final boolean keyHeld(int code){return keys.containsKey(code);}
  private void start(int[] notes) {
    int[] fresh=Arrays.stream(notes).distinct().filter(p->!owners.containsKey(p)).toArray();
    for(int p:notes)owners.merge(p,1,Integer::sum);
    if(fresh.length>0)press.accept(fresh);repaint();
  }
  private void finish(int[] notes) {
    java.util.List<Integer> ended=new ArrayList<>();
    for(int p:notes){Integer count=owners.get(p);if(count==null)continue;if(count==1){owners.remove(p);ended.add(p);}else owners.put(p,count-1);}
    if(!ended.isEmpty())release.accept(ended.stream().mapToInt(Integer::intValue).toArray());repaint();
  }
  private void mouse(Point point){
    int[] next=point==null?new int[0]:notesAt(point);int target=point==null?0:pointerTarget(point);
    mousePoint=next.length==0?null:new Point(point);
    if(Arrays.equals(mouse,next)&&mouseTarget==target){repaint();return;}
    int[] old=mouse;mouse=next;mouseTarget=target;finish(old);if(next.length>0)start(next);
  }
  public void releaseAll(){int[] notes=owners.keySet().stream().mapToInt(Integer::intValue).toArray();owners.clear();keys.clear();mouse=new int[0];mousePoint=null;mouseTarget=0;if(notes.length>0)release.accept(notes);repaint();}
  protected boolean sounding(int pitch){return owners.containsKey(pitch);}
  protected static void centered(Graphics2D g,String text,int x,int y){g.drawString(text,x-g.getFontMetrics().stringWidth(text)/2,y);}
}
