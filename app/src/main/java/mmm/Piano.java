package mmm;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.function.*;
import javax.swing.*;

/** An octave-paged MIDI keyboard; mouse and typing share note ownership. */
public final class Piano extends JPanel {
  private static final int[] SHARPS = {1,3,6,8,10};
  private static final String WHITE_SHORTCUTS = "ASDFGHJK", BLACK_SHORTCUTS = "WETYU";
  private static final Color WHITE_KEY = new Color(255,255,253), KEY_LINE = new Color(178,176,170),
      BLACK_KEY = new Color(28,28,27), DISABLED_WHITE = new Color(237,235,229), DISABLED_BLACK = new Color(77,76,72);
  private final Map<Integer, Integer> held = new HashMap<>();
  private final IntConsumer press, release;
  private final BooleanSupplier pentatonic;
  private int mouse = -1, hover = -1;
  private int firstPitch=48;
  private int[] whites=whitePitches(firstPitch);

  public Piano(BooleanSupplier pentatonic, IntConsumer press, IntConsumer release) {
    this.pentatonic = pentatonic;
    this.press = press;
    this.release = release;
    setFocusable(true);
    setPreferredSize(new Dimension(900, 122));
    setMinimumSize(new Dimension(300, 84));
    setToolTipText("피아노 건반");
    getAccessibleContext().setAccessibleName("피아노 건반 "+rangeLabel());
    getAccessibleContext().setAccessibleDescription("흰 건반 A S D F G H J K, 검은 건반 W E T Y U. 좌우 방향키로 음역 이동, Home으로 기본 음역.");
    addKeyListener(
        new KeyAdapter() {
          public void keyPressed(KeyEvent e) {
            if (e.isControlDown() || e.isMetaDown() || e.isAltDown()) return;
            int i = WHITE_SHORTCUTS.indexOf(e.getKeyCode()), sharp = BLACK_SHORTCUTS.indexOf(e.getKeyCode());
            int p = i >= 0 ? pitch(i) : sharp >= 0 ? sharpPitch(sharp) : -1;
            if (p >= 0 && allowed(p) && !held.containsKey(e.getKeyCode())) {
              boolean already = down(p);
              held.put(e.getKeyCode(), p);
              if (!already) press.accept(p);
              repaint();
            }
          }

          public void keyReleased(KeyEvent e) {
            Integer p = held.remove(e.getKeyCode());
            if (p != null) {
              if (!down(p)) release.accept(p);
              repaint();
            }
          }
        });
    addMouseListener(
        new MouseAdapter() {
          public void mousePressed(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e)) return;
            requestFocusInWindow();
            setMouse(playableAt(e.getPoint()));
          }

          public void mouseReleased(MouseEvent e) {
            if (SwingUtilities.isLeftMouseButton(e)) setMouse(-1);
          }

          public void mouseExited(MouseEvent e) {
            hover = -1;
            setMouse(-1);
          }
        });
    addMouseMotionListener(new MouseMotionAdapter() {
      public void mouseMoved(MouseEvent e) {hover=playableAt(e.getPoint());repaint();}
      public void mouseDragged(MouseEvent e) {
        if ((e.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) != 0) setMouse(playableAt(e.getPoint()));
      }
    });
    addFocusListener(
        new FocusAdapter() {
          public void focusLost(FocusEvent e) {
            releaseAll();
          }
        });
    bind("LEFT","lower-octave",()->shiftOctave(-1));
    bind("RIGHT","higher-octave",()->shiftOctave(1));
    bind("HOME","default-range",this::resetRange);
  }

  private void bind(String key,String name,Runnable action) {
    getInputMap().put(KeyStroke.getKeyStroke(key),name);
    getActionMap().put(name,new AbstractAction(){public void actionPerformed(ActionEvent event){action.run();}});
  }
  private static int[] whitePitches(int first) {
    return java.util.stream.IntStream.rangeClosed(first,Math.min(Model.MAX_PITCH,first+36))
        .filter(p->switch(p%12){case 0,2,4,5,7,9,11 -> true;default -> false;}).toArray();
  }
  public int firstPitch(){return firstPitch;}
  public int lastPitch(){return Math.min(Model.MAX_PITCH,firstPitch+36);}
  public String rangeLabel(){return NoteCanvas.noteName(firstPitch())+"–"+NoteCanvas.noteName(lastPitch());}
  public boolean canShift(int direction){return direction<0?firstPitch>0:direction>0 && firstPitch<96;}
  public void shiftOctave(int direction){setFirstPitch(Math.max(0,Math.min(96,firstPitch+Integer.signum(direction)*12)));}
  public void resetRange(){setFirstPitch(48);}
  private void setFirstPitch(int value) {
    if(value==firstPitch)return;
    releaseAll();hover=-1;int previous=firstPitch;firstPitch=value;whites=whitePitches(value);
    getAccessibleContext().setAccessibleName("피아노 건반 "+rangeLabel());
    firePropertyChange("pitchRange",previous,value);repaint();
  }
  private int sharpPitch(int i){return firstPitch+12+SHARPS[i];}

  @Override public Dimension getPreferredSize() {
    Dimension size=super.getPreferredSize();
    Window window=SwingUtilities.getWindowAncestor(this);
    if(window!=null && window.getHeight()>0 && window.getHeight()<780)
      return new Dimension(size.width,Math.min(size.height,84));
    return size;
  }

  private boolean down(int p) {return mouse == p || held.containsValue(p);}
  private boolean allowed(int p) {
    return !pentatonic.getAsBoolean() || switch(Math.floorMod(p,12)){case 0,2,4,7,9 -> true;default -> false;};
  }
  private void setMouse(int next) {
    if(next==mouse)return;
    int previous=mouse;mouse=-1;
    if(previous>=0 && !down(previous))release.accept(previous);
    if(next>=0) {
      boolean already=down(next);mouse=next;
      if(!already)press.accept(next);
    }
    repaint();
  }

  /** Shared geometry keeps visible keys and mouse hit testing aligned at any size. */
  private Rectangle whiteBounds(int i) {
    int width=Math.max(0,getWidth()-2), x=1+i*width/whites.length, end=1+(i+1)*width/whites.length;
    return new Rectangle(x,1,Math.max(0,end-x),Math.max(0,getHeight()-2));
  }
  private Rectangle blackBounds(int i) {
    Rectangle white=whiteBounds(i);
    int width=Math.max(1,(int)Math.round(white.width*.60));
    return new Rectangle(white.x+white.width-width/2,1,width,Math.max(0,(int)Math.round((getHeight()-2)*.62)));
  }
  private boolean sharpAfter(int i) {
    return i<whites.length-1 && switch(whites[i]%12){case 0,2,5,7,9 -> true;default -> false;};
  }
  private int physicalAt(Point point) {
    for(int i=0;i<whites.length;i++)if(sharpAfter(i) && blackBounds(i).contains(point))return whites[i]+1;
    for(int i=0;i<whites.length;i++)if(whiteBounds(i).contains(point))return whites[i];
    return -1;
  }
  private int playableAt(Point point) {int p=physicalAt(point);return p>=0 && allowed(p)?p:-1;}

  @Override public String getToolTipText(MouseEvent event) {
    int p=physicalAt(event.getPoint());
    if(p<0)return null;
    return solfege(p)+" · "+NoteCanvas.noteName(p)+(allowed(p)?"":" · 5음계 모드에서는 쉬는 건반");
  }

  public int pitch(int i) {
    return firstPitch+12
        + (pentatonic.getAsBoolean()
                ? new int[] {0, 2, 4, 7, 9, 12, 14, 16}
                : new int[] {0, 2, 4, 5, 7, 9, 11, 12})
            [i];
  }

  public void releaseAll() {
    Set<Integer> pitches=new HashSet<>(held.values());
    if(mouse>=0)pitches.add(mouse);
    held.clear();
    mouse = -1;
    for(int p:pitches)release.accept(p);
    repaint();
  }

  @Override
  protected void paintComponent(Graphics raw) {
    super.paintComponent(raw);
    Graphics2D g = (Graphics2D) raw.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    for(int i=0;i<whites.length;i++) {
      int p=whites[i];Rectangle key=whiteBounds(i);
      g.setColor(down(p)?Theme.ORANGE:!allowed(p)?DISABLED_WHITE:hover==p?Theme.WASH:WHITE_KEY);
      g.fillRect(key.x,key.y,key.width,key.height);
      g.setColor(KEY_LINE);g.drawRect(key.x,key.y,key.width,key.height);
      String shortcut=shortcut(p);
      g.setColor(down(p)?Color.WHITE:allowed(p)?Theme.INK:Theme.MUTED);
      centered(g,solfege(p),key,getHeight()-26,Theme.font(12,!shortcut.isEmpty()));
      if(!shortcut.isEmpty())centered(g,shortcut,key,getHeight()-10,Theme.font(10,true));
      else if(p%12==0)centered(g,NoteCanvas.noteName(p),key,getHeight()-10,Theme.font(10,false));
    }
    for(int i=0;i<whites.length;i++)if(sharpAfter(i)) {
      int p=whites[i]+1;Rectangle key=blackBounds(i);
      g.setColor(down(p)?Theme.ORANGE:!allowed(p)?DISABLED_BLACK:hover==p?new Color(61,59,55):BLACK_KEY);
      g.fillRect(key.x,key.y,key.width,key.height);
      g.setColor(new Color(10,10,10));g.drawRect(key.x,key.y,key.width-1,key.height-1);
      String shortcut=shortcut(p);
      if(!shortcut.isEmpty()) {
        g.setColor(down(p)?Color.WHITE:new Color(225,222,215));
        centered(g,shortcut,key,key.y+key.height-10,Theme.font(10,false));
      }
    }
    if (hasFocus()) {
      g.setColor(Theme.ORANGE);
      g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
    }
    g.dispose();
  }

  private String shortcut(int p) {
    for(int i=0;i<WHITE_SHORTCUTS.length();i++)if(pitch(i)==p)return Character.toString(WHITE_SHORTCUTS.charAt(i));
    if(!pentatonic.getAsBoolean())for(int i=0;i<SHARPS.length;i++)if(sharpPitch(i)==p)return Character.toString(BLACK_SHORTCUTS.charAt(i));
    return "";
  }
  private static void centered(Graphics2D g,String text,Rectangle key,int baseline,Font font) {
    g.setFont(font);g.drawString(text,key.x+(key.width-g.getFontMetrics().stringWidth(text))/2,baseline);
  }

  public static String solfege(int pitch) {
    return new String[] {"도", "도♯", "레", "레♯", "미", "파", "파♯", "솔", "솔♯", "라", "라♯", "시"}
        [Math.floorMod(pitch, 12)];
  }
}
