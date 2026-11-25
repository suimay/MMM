package mmm;

import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.*;

/** Wooden instrument body, chord strips, playable strings and a chromatic fretboard. */
public final class StringInstrument extends PlayableSurface {
  private static final String KEYS="ASDFGHJK";
  private static final String[] CHORDS={"Em","Am","Dm","G","C","F","Bb","Bdim"};
  private static final int[] ROOTS={28,33,38,31,36,29,34,35};
  private static final int[] SCALE={0,2,3,5,7,8,10,12};
  // Six playable chord tones, including inversions on the lowest string.
  private static final int[][] GUITAR={
      {40,47,52,55,59,64},{40,45,52,57,60,64},{41,45,50,57,62,65},{43,47,50,55,59,67},
      {43,48,52,55,60,64},{41,48,53,57,60,65},{41,46,53,58,62,65},{41,47,53,59,62,65}};
  private record Position(int column,int row) {}
  private final boolean bass;
  private int octave;
  private boolean chords=true;
  private final JLabel range=Theme.muted("");
  private final JButton lower,higher;
  public StringInstrument(boolean bass,Consumer<int[]> press,Consumer<int[]> release) {
    super(press,release);this.bass=bass;setLayout(new BorderLayout());
    getAccessibleContext().setAccessibleName(bass?"베이스 4줄 연주":"기타 6줄 연주");
    JPanel bar=new JPanel(new BorderLayout());bar.setOpaque(false);bar.setBorder(BorderFactory.createEmptyBorder(8,14,6,14));
    JLabel title=Theme.label(bass?"FINGER BASS  ·  베이스":"ACOUSTIC  ·  기타",15);title.setForeground(bass?new Color(255,234,200):new Color(75,42,24));bar.add(title,BorderLayout.WEST);
    JToggleButton chord=new JToggleButton("코드",true),note=new JToggleButton("노트");
    ButtonGroup modes=new ButtonGroup();modes.add(chord);modes.add(note);
    chord.setFocusable(false);note.setFocusable(false);
    chord.addActionListener(e->{releaseAll();chords=true;repaint();requestFocusInWindow();});
    note.addActionListener(e->{releaseAll();chords=false;repaint();requestFocusInWindow();});
    lower=Theme.secondary("‹",()->shiftOctave(-1));higher=Theme.secondary("›",()->shiftOctave(1));
    lower.setFocusable(false);higher.setFocusable(false);lower.setToolTipText("한 옥타브 낮게");higher.setToolTipText("한 옥타브 높게");
    JPanel controls=Theme.row(lower,range,higher,chord,note);controls.setOpaque(false);bar.add(controls,BorderLayout.EAST);add(bar,BorderLayout.NORTH);
    getInputMap().put(KeyStroke.getKeyStroke("LEFT"),"lower");getActionMap().put("lower",new AbstractAction(){public void actionPerformed(java.awt.event.ActionEvent e){shiftOctave(-1);}});
    getInputMap().put(KeyStroke.getKeyStroke("RIGHT"),"higher");getActionMap().put("higher",new AbstractAction(){public void actionPerformed(java.awt.event.ActionEvent e){shiftOctave(1);}});
    updateRange();
  }
  public boolean bass(){return bass;}
  public void shiftOctave(int direction) {
    int next=Math.max(bass?-24:-36,Math.min(bass?72:48,octave+12*Integer.signum(direction)));
    if(next!=octave){releaseAll();octave=next;updateRange();repaint();}requestFocusInWindow();
  }
  private void updateRange(){range.setText((octave==0?"기본 음역":(octave>0?"+":"")+octave/12+" 옥타브"));range.setForeground(new Color(84,54,34));lower.setEnabled(octave>(bass?-24:-36));higher.setEnabled(octave<(bass?72:48));}
  private int strings(){return bass?4:6;}
  private int boardTop(){return 92;}
  private int boardBottom(){return Math.max(boardTop()+65,getHeight()-18);}
  private int rowY(int row){return boardTop()+24+row*Math.max(1,boardBottom()-boardTop()-48)/Math.max(1,strings()-1);}
  private int[] chord(int index) {
    if(bass)return new int[]{ROOTS[index]+octave};
    return Arrays.stream(GUITAR[index]).filter(p->p>=0).map(p->p+octave).toArray();
  }
  private int stringPitch(int row,int column) {
    if(!chords){int[] open=bass?new int[]{43,38,33,28}:new int[]{64,59,55,50,45,40};return open[row]+column+octave;}
    if(bass){int third=(column==0||column==1||column==2||column==7)?3:4;int[] intervals={12,column==7?6:7,third,0};return ROOTS[column]+intervals[row]+octave;}
    int p=GUITAR[column][5-row];return p<0?-1:p+octave;
  }
  @Override protected int[] notesForKey(int code) {
    int i=KEYS.indexOf(code);if(i<0)return new int[0];
    return chords?chord(i):new int[]{(bass?28:40)+SCALE[i]+octave};
  }
  private Position positionAt(Point point) {
    if(point==null)return null;
    int count=chords?8:13,left=42,width=Math.max(1,getWidth()-66);
    if(point.x<left||point.x>=left+width||point.y<52||point.y>boardBottom())return null;
    int column=Math.min(count-1,(point.x-left)*count/width);
    if(point.y<boardTop())return new Position(column,-1);
    int row=0;for(int i=1;i<strings();i++)if(Math.abs(point.y-rowY(i))<Math.abs(point.y-rowY(row)))row=i;
    return new Position(column,row);
  }
  @Override protected int pointerTarget(Point point) {
    Position position=positionAt(point);return position==null?0:1+position.column+(position.row+1)*13;
  }
  @Override protected int[] notesAt(Point point) {
    Position position=positionAt(point);if(position==null)return new int[0];
    if(position.row<0)return chords?chord(position.column):new int[]{(bass?28:40)+position.column+octave};
    int pitch=stringPitch(position.row,position.column);return pitch<0?new int[0]:new int[]{pitch};
  }
  private boolean activeCell(int row,int column) {
    Position pointer=positionAt(pressedPoint());
    if(pointer!=null && pointer.column==column) {
      if(pointer.row==row)return true;
      if(pointer.row<0 && (chords && !bass || row==strings()-1))return true;
    }
    if(chords)return keyHeld(KEYS.charAt(column)) && (!bass || row==strings()-1);
    for(int i=0;i<SCALE.length;i++)if(column==SCALE[i] && row==strings()-1 && keyHeld(KEYS.charAt(i)))return true;
    return false;
  }
  @Override protected void paintComponent(Graphics raw) {
    super.paintComponent(raw);Graphics2D g=(Graphics2D)raw.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
    int w=getWidth(),h=getHeight(),top=boardTop(),bottom=boardBottom();
    g.setPaint(new GradientPaint(0,0,bass?new Color(108,43,20):new Color(245,203,129),w,h,bass?new Color(219,149,51):new Color(196,128,58)));g.fillRoundRect(0,0,w,h,16,16);
    for(int y=2;y<h;y+=4){g.setColor(new Color(99,46,16,16+(y%13)));g.drawLine(0,y,w,y+(y%7)-3);}
    if(bass){Path2D guard=new Path2D.Double();guard.moveTo(w*.72,0);guard.lineTo(w,0);guard.lineTo(w,top+85);guard.lineTo(w*.5,top+85);guard.closePath();g.setColor(new Color(232,225,195,195));g.fill(guard);}
    else {
      int r=Math.max(75,Math.min(155,h/2));g.setColor(new Color(88,43,19));g.fillOval(w/2-r,top-r/2,r*2,r*2);
      g.setStroke(new BasicStroke(4));g.setColor(new Color(225,189,120));g.drawOval(w/2-r+9,top-r/2+9,r*2-18,r*2-18);g.setColor(new Color(34,23,18));g.fillOval(w/2-r+16,top-r/2+16,r*2-32,r*2-32);
    }
    g.setPaint(new GradientPaint(0,top,new Color(85,43,25),0,bottom,new Color(36,24,21)));g.fillRect(0,top,w,Math.max(0,bottom-top+8));
    for(int y=top+2;y<bottom;y+=7){g.setColor(new Color(197,144,92,13));g.drawLine(0,y,w,y+2);}
    int count=chords?8:13,left=42,width=Math.max(1,w-66);
    for(int column=0;column<count;column++) {
      int x=left+column*width/count,end=left+(column+1)*width/count,cx=(x+end)/2;
      boolean active=false;for(int row=0;row<strings();row++)active|=activeCell(row,column);
      if(chords){g.setColor(active?new Color(65,139,189,180):new Color(177,123,63,85));g.fillRoundRect(x+4,57,Math.max(1,end-x-8),bottom-51,18,18);}
      else {g.setColor(new Color(204,190,157,190));g.setStroke(new BasicStroke(column==0?5:2));g.drawLine(x,top,x,bottom+8);if(Set.of(3,5,7,9,12).contains(column)){g.setColor(new Color(220,209,175,130));g.fillOval(cx-4,(top+bottom)/2-4,8,8);}}
      g.setColor(new Color(72,38,21,180));g.fillRoundRect(x+9,60,Math.max(1,end-x-18),25,8,8);
      g.setFont(Theme.font(chords?18:13,chords));g.setColor(new Color(255,235,204));centered(g,chords?CHORDS[column]:Integer.toString(column),cx,78);
      if(chords){g.setFont(Theme.font(10,true));g.setColor(new Color(239,226,200));centered(g,String.valueOf(KEYS.charAt(column)),cx,bottom-4);}
    }
    for(int row=0;row<strings();row++) {
      int y=rowY(row);float thickness=(bass?2.5f:1.2f)+row*(bass?.65f:.3f);
      g.setStroke(new BasicStroke(thickness+2));g.setColor(new Color(5,5,5,130));g.drawLine(0,y+3,w,y+3);
      g.setStroke(new BasicStroke(thickness));g.setColor(new Color(197,192,174));g.drawLine(0,y,w,y);
      g.setStroke(new BasicStroke(1));g.setColor(new Color(248,243,222));g.drawLine(0,y-1,w,y-1);
      g.setFont(Theme.font(11,true));g.setColor(new Color(242,227,199));g.drawString(Integer.toString(row+1),12,y-6);
      for(int column=0;column<count;column++)if(activeCell(row,column)){int cx=left+(2*column+1)*width/(2*count);g.setColor(Theme.ORANGE);g.fillOval(cx-7,y-7,14,14);}
    }
    if(hasFocus()){g.setColor(Theme.ORANGE);g.setStroke(new BasicStroke(2));g.drawRoundRect(1,1,w-3,h-3,14,14);}g.dispose();
  }
}
