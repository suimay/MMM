package mmm.server;

import java.net.URI;
import java.time.*;
import java.util.*;
import mmm.*;
import mmm.Model.*;
import static mmm.WireJson.*;

/** Credentials arrive on stdin and are never command arguments, output or local files. */
public final class ClientProcessProbe {
  public static void main(String[] args) throws Exception {
    Map<String,Object> config=object(read(new String(System.in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)));
    String user=string(config,"loginId"),song=string(config,"songId");
    MusicService service=new MusicService(new RemoteRepository(URI.create(string(config,"endpoint"))),Clock.systemUTC(),false);
    char[] password=string(config,"password").toCharArray();
    try{service.login(user,password);}finally{Arrays.fill(password,'\0');}
    service.songs();Phrase result;
    if(string(config,"action").equals("save")) {
      Phrase draft=new Phrase(song,user,service.today(),0,90,0,"별도 JVM 기록",
          List.of(new NoteEvent(0,120,60,90,Articulation.STACCATO),new NoteEvent(480,480,64,90)),
          List.of(new RestEvent(120,360)),List.of(new ExpressionRange(480,1920,ExpressionKind.CRESCENDO,40,110)));
      result=service.save(draft);
    }else result=service.phrases(song).get(0);
    System.out.print(write(Api.PhraseDto.from(result)));service.logout();
  }
}
