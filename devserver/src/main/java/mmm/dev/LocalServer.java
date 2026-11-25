package mmm.dev;

import java.nio.file.*;
import java.util.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import mmm.server.ServerApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

/** Local development host: real persistent PostgreSQL, strictly loopback-only HTTP and DB. */
public final class LocalServer {
  public static void main(String[] args) throws Exception {
    Path data=Path.of(System.getProperty("user.home"),".mmm","dev-server");int port=8080,dbPort=54329;boolean stdinControl=false;
    for(String arg:args) {
      if(arg.startsWith("--data="))data=Path.of(arg.substring(7));
      else if(arg.startsWith("--port="))port=Integer.parseInt(arg.substring(7));
      else if(arg.startsWith("--db-port="))dbPort=Integer.parseInt(arg.substring(10));
      else if(arg.equals("--stdin-control"))stdinControl=true;
      else throw new IllegalArgumentException("알 수 없는 개발 서버 옵션");
    }
    if(port<1024||port>65535||dbPort<1024||dbPort>65535||port==dbPort)throw new IllegalArgumentException("포트 범위를 확인하세요.");
    Files.createDirectories(data);
    EmbeddedPostgres postgres=EmbeddedPostgres.builder().setPort(dbPort).setDataDirectory(data.resolve("postgres").toFile())
        .setCleanDataDirectory(false).setServerConfig("listen_addresses","127.0.0.1").start();
    try {
      SpringApplication app=new SpringApplication(ServerApplication.class);
      Map<String,Object> properties=new HashMap<>();
      properties.put("spring.datasource.url",postgres.getJdbcUrl("postgres","postgres"));
      properties.put("spring.datasource.username","postgres");properties.put("spring.datasource.password","");
      properties.put("server.address","127.0.0.1");properties.put("server.port",port);
      properties.put("server.ssl.enabled",false);properties.put("logging.level.io.zonky.test.db.postgres","warn");
      app.addInitializers(context->context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("local-development",properties)));
      ConfigurableApplicationContext context=app.run();
      java.util.concurrent.atomic.AtomicBoolean closed=new java.util.concurrent.atomic.AtomicBoolean();
      Runnable close=()->{if(closed.compareAndSet(false,true)){context.close();try{postgres.close();}catch(Exception ignored){}}};
      Runtime.getRuntime().addShutdownHook(new Thread(close,"mmm-local-db-shutdown"));
      if(stdinControl) {
        Thread input=new Thread(()->{
          try(java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(System.in))) {
            String line;while((line=reader.readLine())!=null)if(line.strip().equalsIgnoreCase("stop")){close.run();System.exit(0);return;}
          }catch(Exception ignored){}
        },"mmm-dev-console");input.setDaemon(true);input.start();
      }
      System.out.println("MMM local development server: http://127.0.0.1:"+port+" (loopback only; persistent PostgreSQL)");
    } catch(Exception ex){postgres.close();throw ex;}
  }
}
