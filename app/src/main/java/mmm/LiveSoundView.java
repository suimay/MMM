package mmm;

import java.awt.*;
import java.awt.geom.*;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.*;
import mmm.Model.*;
import mmm.SoundShape.LiveNote;

/** Always-visible sound drawing alongside a bass, guitar or drum performance. */
public final class LiveSoundView extends JPanel {
  private final Supplier<List<NoteEvent>> notes;
  private final Supplier<List<LiveNote>> live;
  public LiveSoundView(Supplier<List<NoteEvent>> notes,Supplier<List<LiveNote>> live) {
    this.notes=notes;this.live=live;setPreferredSize(new Dimension(275,250));
    getAccessibleContext().setAccessibleName("실시간 소리 그림");
    setToolTipText("누르면 바로 나타나고, 누르는 동안 꼬리가 길어져요. 저장은 1/4박 단위입니다.");
  }
  @Override protected void paintComponent(Graphics raw) {
    super.paintComponent(raw);Graphics2D g=(Graphics2D)raw.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
    int w=getWidth(),h=getHeight();g.setColor(new Color(30,35,37));g.fillRoundRect(0,0,w,h,16,16);
    g.setFont(Theme.font(14,true));g.setColor(new Color(238,241,234));g.drawString("지금 연주한 소리",16,26);
    g.setFont(Theme.font(11,false));g.setColor(new Color(166,184,182));g.drawString("짧게 누르면 짧게, 길게 누르면 길게",16,45);
    int left=18,right=Math.max(left+1,w-18),top=80,bottom=Math.max(top+1,h-56);
    for(int beat=0;beat<=4;beat++) {
      int x=left+(right-left)*beat/4;g.setColor(new Color(62,75,77));g.drawLine(x,top-12,x,bottom+8);
      if(beat<4){g.setColor(new Color(153,169,166));g.drawString((beat+1)+"박",x+3,h-30);}
    }
    List<NoteEvent> finished=notes.get();List<LiveNote> pending=live.get();int[] range=SoundShape.pitchRange(finished);
    for(LiveNote n:pending){range[0]=Math.min(range[0],n.pitch());range[1]=Math.max(range[1],n.pitch());}
    for(NoteEvent n:finished) {
      Shape shape=shape(n.start(),n.duration(),n.pitch(),n.velocity(),left,right,top,bottom,range);
      g.setColor(new Color(64,158,159));g.fill(shape);
    }
    for(LiveNote n:pending)SoundShape.paintLive(g,shape(n.start(),n.duration(),n.pitch(),n.velocity(),left,right,top,bottom,range));
    if(finished.isEmpty()&&pending.isEmpty()) {
      g.setFont(Theme.font(12,false));g.setColor(new Color(155,174,170));g.drawString("악기를 누르면 여기서 자라요",left,Math.max(top+18,(top+bottom)/2));
    }
    g.setFont(Theme.font(10,false));g.setColor(new Color(153,169,166));g.drawString("높이 = 음높이 · 두께 = 연주 세기",16,h-10);g.dispose();
  }
  private Shape shape(double start,double duration,int pitch,int velocity,int left,int right,int top,int bottom,int[] range) {
    double x=left+start*(right-left)/Model.BAR,y=top+(range[1]-pitch)*(bottom-top)/(double)(range[1]-range[0]);
    return SoundShape.contour(x,y,Math.max(2,duration*(right-left)/Model.BAR),Math.min(18,SoundShape.thickness(velocity)*.65));
  }
}
