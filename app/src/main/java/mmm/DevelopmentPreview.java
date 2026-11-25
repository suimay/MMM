package mmm;

import java.nio.channels.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import javax.swing.*;

/** Local, seeded development workspace. The launcher rebuilds on exit code 75. */
public final class DevelopmentPreview {
  public static final int RELOAD_EXIT=75;
  public static void main(String[] args) throws Exception {
    Path data=Path.of(System.getProperty("user.home"),".mmm","development-preview");
    LocalDate day=LocalDate.now(ZoneId.of("Asia/Seoul"));
    for(String arg:args) {
      if(arg.startsWith("--data="))data=Path.of(arg.substring(7));
      else if(arg.startsWith("--date="))day=LocalDate.parse(arg.substring(7));
      else throw new IllegalArgumentException("알 수 없는 미리보기 옵션");
    }
    Files.createDirectories(data);
    // A second launcher must not race a running preview's view state or data.
    FileChannel lockChannel=FileChannel.open(data.resolve("preview-instance.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
    FileLock lock=lockChannel.tryLock();
    if(lock==null) {
      lockChannel.close();
      JOptionPane.showMessageDialog(null,"개발 미리보기가 이미 열려 있습니다. 작업 표시줄에서 MMM 창을 선택하세요.","개발 미리보기",JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    Runtime.getRuntime().addShutdownHook(new Thread(()->{try{lock.release();lockChannel.close();}catch(Exception ignored){}}));
    Properties view=new Properties();Path state=data.resolve("view.properties");
    if(Files.exists(state))try(var reader=Files.newBufferedReader(state)){view.load(reader);}
    Clock clock=Clock.fixed(day.atTime(12,0).atZone(ZoneId.of("Asia/Seoul")).toInstant(),ZoneId.of("Asia/Seoul"));
    MusicService service=new MusicService(new LocalRepository(data),clock,true);
    service.seed();service.login("demo","demo1234".toCharArray());
    Path directory=data;
    SwingUtilities.invokeLater(()->{
      Theme.install();MMM app=new MMM(service,directory);
      app.enableDevelopmentPreview(state,()->System.exit(RELOAD_EXIT));
      javax.swing.Timer ready=new javax.swing.Timer(60,null);
      ready.addActionListener(event->{if(!app.working()){ready.stop();app.restoreDevelopmentPreview(view);}});
      ready.start();
    });
  }
}
