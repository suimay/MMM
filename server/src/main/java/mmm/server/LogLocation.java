package mmm.server;

import java.nio.file.Path;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.stereotype.Component;

@Component
public final class LogLocation implements ApplicationRunner {
  private static final Logger LOG=LoggerFactory.getLogger(LogLocation.class);
  private final Path folder;
  public LogLocation(@Value("${mmm.logs.dir}") String folder){this.folder=Path.of(folder).toAbsolutePath().normalize();}
  @Override public void run(ApplicationArguments args){LOG.info("Activity log: {}",folder.resolve("activity.log"));LOG.info("Server log: {}",folder.resolve("server.log"));}
}
