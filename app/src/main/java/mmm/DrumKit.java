package mmm;

import java.awt.*;
import java.awt.geom.*;
import java.util.function.Consumer;
import javax.swing.*;

/** A complete kit arranged as cymbals, rack toms, hi-hat, snare, kick and floor tom. */
public final class DrumKit extends PlayableSurface {
  public static final int[] PITCHES={36,38,42,46,48,45,41,49,51};
  public static final String[] NAMES={"킥","스네어","닫힌 하이햇","열린 하이햇","하이 탐","미드 탐","플로어 탐","크래시","라이드"};
  private static final String KEYS="ASDFGHJKL";
  private record Piece(int pitch,String name,String shortcut,double x,double y,double w,double h,boolean cymbal) {
    Ellipse2D shape(){return new Ellipse2D.Double(x,y,w,h);}
  }
  private static final Piece[] PIECES={
      new Piece(49,"크래시","K",5,5,220,95,true),new Piece(51,"라이드","L",770,5,220,95,true),
      new Piece(42,"하이햇","D / F",20,140,190,90,true),
      new Piece(48,"하이 탐","G",275,40,170,125,false),new Piece(45,"미드 탐","H",490,40,180,125,false),
      new Piece(38,"스네어","S",200,205,210,125,false),new Piece(41,"플로어 탐","J",700,195,230,140,false),
      new Piece(36,"킥","A",420,175,230,160,false)};
  public DrumKit(Consumer<int[]> press,Consumer<int[]> release) {
    super(press,release);setLayout(new BorderLayout());setBackground(new Color(34,36,39));
    getAccessibleContext().setAccessibleName("드럼 키트 연주");
    JPanel heading=new JPanel(new BorderLayout());heading.setOpaque(false);heading.setBorder(BorderFactory.createEmptyBorder(12,18,8,18));
    JLabel title=Theme.label("DRUMS  ·  스탠다드 키트",16);title.setForeground(new Color(238,233,220));heading.add(title);
    JLabel hint=Theme.muted("A 킥 · S 스네어 · D/F 하이햇 · G/H/J 탐 · K/L 심벌");hint.setForeground(new Color(190,185,173));heading.add(hint,BorderLayout.EAST);add(heading,BorderLayout.NORTH);
  }
  public static String noteName(int pitch){for(int i=0;i<PITCHES.length;i++)if(PITCHES[i]==pitch)return NAMES[i];return "타악기 "+pitch;}
  private double scale(){return Math.max(.01,Math.min((getWidth()-24)/1000.0,(getHeight()-54)/380.0));}
  private double left(){return (getWidth()-1000*scale())/2;}
  @Override protected int[] notesAt(Point point) {
    double x=(point.x-left())/scale(),y=(point.y-48)/scale();
    for(int i=PIECES.length-1;i>=0;i--)if(PIECES[i].shape().contains(x,y))
      return new int[]{PIECES[i].pitch==42 && y>PIECES[i].y+PIECES[i].h*.60?46:PIECES[i].pitch};
    return new int[0];
  }
  @Override protected int[] notesForKey(int code){int index=KEYS.indexOf(code);return index<0?new int[0]:new int[]{PITCHES[index]};}
  public Point padCenter(int pitch) {
    for(Piece piece:PIECES)if(piece.pitch==pitch || pitch==46 && piece.pitch==42)
      return new Point((int)Math.round(left()+(piece.x+piece.w/2)*scale()),(int)Math.round(48+(piece.y+piece.h*(pitch==46?.80:.40))*scale()));
    throw new IllegalArgumentException("Unknown drum pad");
  }
  @Override protected void paintComponent(Graphics raw) {
    super.paintComponent(raw);Graphics2D g=(Graphics2D)raw.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
    g.setPaint(new GradientPaint(0,0,new Color(24,26,29),0,getHeight(),new Color(57,59,65)));g.fillRoundRect(0,0,getWidth(),getHeight(),16,16);
    double scale=scale();g.translate(left(),48);g.scale(scale,scale);
    for(Piece p:PIECES) {
      double cx=p.x+p.w/2,cy=p.y+p.h/2;
      g.setStroke(new BasicStroke(7));g.setColor(new Color(12,13,15));g.draw(new Line2D.Double(cx,cy,cx+18,370));
      g.setStroke(new BasicStroke(3));g.setColor(new Color(137,140,142));g.draw(new Line2D.Double(cx-2,cy,cx+16,367));
    }
    for(Piece p:PIECES) {
      double cx=p.x+p.w/2,cy=p.y+p.h/2;boolean active=sounding(p.pitch)||(p.pitch==42&&sounding(46));
      if(p.cymbal) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx,cy), (float)(p.w*.6),new float[]{0,.12f,.24f,.55f,.85f,1},new Color[]{new Color(116,92,36),new Color(238,219,138),new Color(170,137,61),new Color(244,222,137),new Color(167,136,67),new Color(102,83,39)}));g.fill(p.shape());
        for(int r=1;r<34;r++){double inset=r*p.w/80;g.setColor(new Color(r%2==0?255:75,r%2==0?237:61,r%2==0?160:26,45));g.setStroke(new BasicStroke(1));g.draw(new Ellipse2D.Double(p.x+inset,p.y+inset*p.h/p.w,p.w-2*inset,p.h-2*inset*p.h/p.w));}
        g.setColor(new Color(89,76,44));g.fill(new Ellipse2D.Double(cx-15,cy-7,30,14));g.setColor(new Color(212,210,195));g.fill(new RoundRectangle2D.Double(cx-3,cy-27,6,24,3,3));g.fill(new RoundRectangle2D.Double(cx-18,cy-24,36,6,3,3));
      } else {
        g.setPaint(new GradientPaint((float)p.x,0,new Color(26,27,28),(float)(p.x+p.w),0,new Color(112,114,116)));g.fill(new Ellipse2D.Double(p.x-5,p.y+17,p.w+10,p.h+13));
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx-p.w*.12,cy-p.h*.1),(float)(p.w*.65),new float[]{0,.74f,1},new Color[]{new Color(243,240,228),new Color(205,204,197),new Color(146,147,143)}));g.fill(p.shape());
        for(int ring=0;ring<4;ring++){g.setColor(ring%2==0?new Color(224,226,224):new Color(64,66,67));g.setStroke(new BasicStroke(2));g.draw(new Ellipse2D.Double(p.x-ring*2,p.y-ring*2,p.w+ring*4,p.h+ring*4));}
        for(int bolt=0;bolt<10;bolt++){double angle=bolt*Math.PI*2/10;double bx=cx+Math.cos(angle)*(p.w/2+5),by=cy+Math.sin(angle)*(p.h/2+5);g.setColor(new Color(231,233,230));g.fill(new RoundRectangle2D.Double(bx-4,by-4,8,12,3,3));}
        if(p.pitch==36){g.setColor(new Color(124,125,121));g.setStroke(new BasicStroke(2));g.draw(new Ellipse2D.Double(p.x+28,p.y+20,p.w-56,p.h-40));g.fill(new Ellipse2D.Double(cx+42,cy+40,20,17));}
      }
      if(active){g.setColor(Theme.ORANGE);g.setStroke(new BasicStroke(7));g.draw(p.shape());}
      g.setColor(p.cymbal?new Color(42,35,23):new Color(59,59,55));
      g.setFont(Theme.font((int)Math.max(18,12/scale),true));centered(g,p.name,(int)cx,(int)(cy+p.h*.14));
      g.setFont(Theme.font((int)Math.max(15,10/scale),false));centered(g,p.shortcut,(int)cx,(int)(cy+p.h*.14+24/Math.max(.65,scale)));
    }
    g.dispose();
  }
}
