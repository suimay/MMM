package mmm.server;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import mmm.MMM;

final class SwingProbe {
  interface Task {void run() throws Exception;}
  static void edt(Task task) throws Exception {
    AtomicReference<Throwable> error=new AtomicReference<>();
    SwingUtilities.invokeAndWait(()->{try{task.run();}catch(Throwable ex){error.set(ex);}});
    if(error.get()!=null)throw new Exception(error.get());
  }
  static Object field(Object target,String name) throws Exception {Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
  static void set(Object target,String name,Object value) throws Exception {Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
  static void invoke(Object target,String name,Class<?>[] types,Object... args) throws Exception {Method m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(target,args);}
  static <T> T find(Container root,Class<T> type){
    if(type.isInstance(root))return type.cast(root);
    for(Component c:root.getComponents())if(c instanceof Container container){T value=find(container,type);if(value!=null)return value;}return null;
  }
  static AbstractButton button(Container root,String text){
    for(Component c:root.getComponents()) {
      if(c instanceof AbstractButton b&&text.equals(b.getText()))return b;
      if(c instanceof Container container){AbstractButton value=button(container,text);if(value!=null)return value;}
    }return null;
  }
  static void idle(MMM frame) throws Exception {
    long end=System.nanoTime()+20_000_000_000L;
    while(System.nanoTime()<end){
      AtomicBoolean busy=new AtomicBoolean();edt(()->{
        busy.set((boolean)field(frame,"busy"));
        for(Window window:Window.getWindows())if(window instanceof JDialog dialog&&dialog.isVisible())busy.set(true);
      });
      if(!busy.get()){Thread.sleep(80);edt(()->busy.set((boolean)field(frame,"busy")));if(!busy.get())return;}
      Thread.sleep(20);
    }throw new AssertionError("Swing remote request timeout");
  }
  static void capture(MMM frame,String name) throws Exception {
    frame.validate();BufferedImage image=new BufferedImage(frame.getWidth(),frame.getHeight(),BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics=image.createGraphics();frame.paint(graphics);graphics.dispose();
    Path folder=Path.of("..","app","build","evidence");Files.createDirectories(folder);javax.imageio.ImageIO.write(image,"png",folder.resolve(name+".png").toFile());
  }
}
