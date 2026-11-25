package mmm.server;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import ch.qos.logback.classic.*;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;

final class ActivityCapture extends AppenderBase<ILoggingEvent> implements AutoCloseable {
  private final Logger logger=(Logger)LoggerFactory.getLogger("mmm.activity");
  final List<String> lines=new CopyOnWriteArrayList<>();
  ActivityCapture(){setContext(logger.getLoggerContext());start();logger.addAppender(this);}
  @Override protected void append(ILoggingEvent event){lines.add(event.getFormattedMessage());}
  String trace(String id) throws InterruptedException {
    long until=System.nanoTime()+3_000_000_000L;
    while(System.nanoTime()<until){
      for(String line:lines)if(line.endsWith("traceId="+id))return line;
      Thread.sleep(10);
    }
    throw new AssertionError("Missing activity log for trace "+id);
  }
  @Override public void close(){logger.detachAppender(this);stop();}
}
