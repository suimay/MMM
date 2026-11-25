package mmm.server;

import java.util.concurrent.*;
import java.util.*;
import ch.qos.logback.classic.*;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.*;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import static org.junit.jupiter.api.Assertions.*;

/** Suspend the real configured console destination without blocking test stdout. */
final class PausedConsole extends Filter<ILoggingEvent> implements AutoCloseable {
  private final AsyncAppender async;
  private final Appender<ILoggingEvent> console;
  private final List<Filter<ILoggingEvent>> originalFilters;
  private final CountDownLatch entered=new CountDownLatch(1),released=new CountDownLatch(1);
  PausedConsole(Logger logger,String asyncName,String consoleName) {
    async=assertInstanceOf(AsyncAppender.class,logger.getAppender(asyncName));
    assertTrue(async.isNeverBlock(),"A full console queue must not suspend HTTP requests");
    console=async.getAppender(consoleName);assertNotNull(console);
    originalFilters=console.getCopyOfAttachedFiltersList();
    setContext(logger.getLoggerContext());start();console.addFilter(this);
  }
  @Override public FilterReply decide(ILoggingEvent event) {
    entered.countDown();
    try {released.await();}catch(InterruptedException ex){Thread.currentThread().interrupt();}
    return FilterReply.DENY;
  }
  void awaitPaused() throws InterruptedException {assertTrue(entered.await(3,TimeUnit.SECONDS),"Console worker did not pause");}
  void assertFull(){assertEquals(0,async.getRemainingCapacity(),"Test must saturate the console queue");}
  @Override public void close() {
    released.countDown();console.clearAllFilters();originalFilters.forEach(console::addFilter);stop();
  }
}
