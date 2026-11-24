package mmm.server;

import java.time.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.context.annotation.*;

@SpringBootApplication
public class ServerApplication {
  public static void main(String[] args) { SpringApplication.run(ServerApplication.class,args); }
  @Bean public Clock serverClock() { return Clock.system(ZoneId.of("Asia/Seoul")); }
}
