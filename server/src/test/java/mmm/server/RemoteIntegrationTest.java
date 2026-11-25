package mmm.server;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.sound.midi.*;
import com.sun.net.httpserver.HttpServer;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import mmm.*;
import mmm.Api.*;
import mmm.Model.*;
import static mmm.WireJson.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"spring.main.headless=false","logging.level.io.zonky.test.db.postgres=warn"})
@Import(RemoteIntegrationTest.TestClockConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class RemoteIntegrationTest {
  static final LocalDate DAY=LocalDate.of(2026,11,16);
  static final ZoneId SEOUL=ZoneId.of("Asia/Seoul");
  static final Instant NOON=DAY.atTime(12,0).atZone(SEOUL).toInstant();
  static final EmbeddedPostgres POSTGRES;
  static {try{POSTGRES=EmbeddedPostgres.builder().setPort(0).start();}catch(IOException ex){throw new ExceptionInInitializerError(ex);}}
  @DynamicPropertySource static void database(DynamicPropertyRegistry registry){
    registry.add("spring.datasource.url",()->POSTGRES.getJdbcUrl("postgres","postgres"));
    registry.add("spring.datasource.username",()->"postgres");registry.add("spring.datasource.password",()->"postgres");
  }
  @TestConfiguration static class TestClockConfig {@Bean @Primary MutableClock testClock(){return new MutableClock();}}
  static class MutableClock extends Clock {
    volatile Instant instant=NOON;
    public ZoneId getZone(){return SEOUL;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return instant;}
  }
  @LocalServerPort int port;
  @Autowired JdbcTemplate db;
  @Autowired MutableClock clock;
  final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  String url(){return "http://127.0.0.1:"+port;}
  record Reply(int status,Object body,String raw,HttpHeaders headers) {}
  Reply call(String method,String path,Object body,String token) throws Exception {
    String raw=body==null?null:write(body);return rawCall(method,path,raw,token);
  }
  Reply rawCall(String method,String path,String body,String token) throws Exception {
    HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(url()+"/api"+path)).timeout(Duration.ofSeconds(10));
    if(token!=null)b.header("Authorization","Bearer "+token);
    if(body==null)b.method(method,HttpRequest.BodyPublishers.noBody());else b.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(body));
    HttpResponse<String> r=http.send(b.build(),HttpResponse.BodyHandlers.ofString());
    return new Reply(r.statusCode(),r.body().isBlank()?null:read(r.body()),r.body(),r.headers());
  }
  String account(String id) throws Exception {
    assertEquals(201,call("POST","/auth/signup",new Signup(id,"이름 "+id,"test1234"),null).status());
    Reply login=call("POST","/auth/login",new Login(id,"test1234"),null);assertEquals(200,login.status());
    return string(object(login.body()),"token");
  }
  String create(String token,boolean shared) throws Exception {
    Reply reply=call("POST","/songs",new CreateSong("공동 기록",shared?SongType.SHARED:SongType.PERSONAL,90,0),token);
    assertEquals(201,reply.status(),reply.raw());return string(object(reply.body()),"id");
  }
  String invite(String token,String song) throws Exception {
    Reply reply=call("POST","/songs/"+song+"/invites",null,token);assertEquals(201,reply.status());return string(object(reply.body()),"code");
  }
  Phrase draft(String song,String author,int version,String memo) {
    return new Phrase(song,author,DAY,version,90,0,memo,
        List.of(new NoteEvent(0,120,60,90,Articulation.STACCATO),new NoteEvent(480,480,64,90)),
        List.of(new RestEvent(120,360)),List.of(new ExpressionRange(480,1920,ExpressionKind.CRESCENDO,40,110)));
  }
  String recordPath(String song){return "/songs/"+song+"/records/"+DAY+"/me";}
  MusicService client(String id) throws Exception {
    MusicService service=new MusicService(new RemoteRepository(URI.create(url())),clock,false);
    service.register(id,"이름 "+id,"test1234".toCharArray(),"test1234".toCharArray());service.login(id,"test1234".toCharArray());return service;
  }
  @BeforeEach void reset(){db.execute("TRUNCATE app_users CASCADE");clock.instant=NOON;}
  @AfterAll void close() throws Exception {POSTGRES.close();}

  @Test void accountsSessionsAndPublicDtos() throws Exception {
    String token=account("owner");
    assertEquals(409,call("POST","/auth/signup",new Signup("owner","중복","test1234"),null).status());
    assertEquals(400,call("POST","/auth/signup",new Signup("!","오류","short"),null).status());
    assertEquals(401,call("POST","/auth/login",new Login("owner","wrongpass"),null).status());
    Reply me=call("GET","/me",null,token);assertEquals(200,me.status());
    assertFalse(me.raw().contains("hash"));assertFalse(me.raw().contains("salt"));assertFalse(me.raw().contains("test1234"));
    assertEquals(DAY.toString(),string(object(me.body()),"serverToday"));
    assertEquals("no-store",me.headers().firstValue("Cache-Control").orElseThrow());
    assertNotEquals("test1234",db.queryForObject("SELECT password_hash FROM app_users",String.class));
    assertEquals(PasswordCodec.sha256(token),db.queryForObject("SELECT token_hash FROM auth_sessions",String.class));
    assertEquals(204,call("POST","/auth/logout",null,token).status());
    assertEquals(401,call("GET","/me",null,token).status());
    assertNotNull(db.queryForObject("SELECT revoked_at FROM auth_sessions",java.sql.Timestamp.class));
    assertEquals(401,call("GET","/songs",null,null).status());
    assertEquals(404,call("GET","/does-not-exist",null,token).status());
  }

  private String activity(ActivityCapture capture,Reply reply) throws Exception {
    String trace=reply.headers().firstValue("X-MMM-Trace-Id").orElseThrow();
    assertEquals(trace,UUID.fromString(trace).toString());return capture.trace(trace);
  }
  @Test void pausedFullConsoleCannotBlockSignupLoginSaveOrFileLogs() throws Exception {
    ch.qos.logback.classic.Logger root=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    ch.qos.logback.classic.Logger activityLogger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger("mmm.activity");
    String marker="console-pause-"+UUID.randomUUID();
    try(PausedConsole serverConsole=new PausedConsole(root,"SERVER_CONSOLE_ASYNC","CONSOLE");
        PausedConsole activityConsole=new PausedConsole(activityLogger,"ACTIVITY_CONSOLE_ASYNC","ACTIVITY_CONSOLE");
        ActivityCapture capture=new ActivityCapture()) {
      root.info("{} start",marker);activityLogger.info("{} start",marker);
      serverConsole.awaitPaused();activityConsole.awaitPaused();
      assertTimeoutPreemptively(Duration.ofSeconds(4),()->{
        for(int i=0;i<600;i++){root.info("{} item={}",marker,i);activityLogger.info("{} item={}",marker,i);}
      });
      serverConsole.assertFull();activityConsole.assertFull();
      assertTimeoutPreemptively(Duration.ofSeconds(8),()->{
        assertEquals(200,call("GET","/health",null,null).status());
        MusicService service=client("consoleuser");
        Song song=service.create("콘솔 정지 중",false,90,0);
        Phrase first=service.save(draft(song.id(),"consoleuser",0,"첫 저장"));
        assertEquals(2,service.save(draft(song.id(),"consoleuser",first.version(),"수정 저장")).version());
        assertEquals(1,service.phrases(song.id()).size());
        Reply failedLogin=call("POST","/auth/login",new Login("consoleuser","wrongpass"),null);
        assertEquals(401,failedLogin.status());assertTrue(activity(capture,failedLogin).contains("result=FAILURE"));
      });
      java.nio.file.Path logs=java.nio.file.Path.of(System.getProperty("MMM_LOG_DIR"));
      for(String name:List.of("server.log","activity.log")) {
        String file=java.nio.file.Files.readString(logs.resolve(name));
        assertEquals(601,file.lines().filter(line->line.contains(marker)).count(),"File log must keep every event: "+name);
      }
      String file=java.nio.file.Files.readString(logs.resolve("activity.log"));
      for(String event:List.of("SIGNUP","LOGIN","SONG_CREATE","RECORD_CREATE","RECORD_UPDATE"))
        assertTrue(file.contains("event="+event+" result=SUCCESS user=consoleuser"),event);
      assertTrue(file.contains("event=LOGIN result=FAILURE user=- attemptedUser=consoleuser"));
    }
  }
  @Test void activityLogsCommittedActionsReplayFailuresAndFiles() throws Exception {
    try(ActivityCapture capture=new ActivityCapture()) {
      Reply signup=call("POST","/auth/signup",new Signup("audituser","기록 사용자","test1234"),null);
      String line=activity(capture,signup);assertTrue(line.contains("event=SIGNUP result=SUCCESS"));assertTrue(line.contains("committed=true changed=true"));
      Reply login=call("POST","/auth/login",new Login("audituser","test1234"),null);
      line=activity(capture,login);assertTrue(line.contains("event=LOGIN result=SUCCESS user=audituser"));
      String token=string(object(login.body()),"token");
      Reply created=call("POST","/songs",new CreateSong("개인 곡",SongType.PERSONAL,90,0),token);
      String song=string(object(created.body()),"id");line=activity(capture,created);
      assertTrue(line.contains("event=SONG_CREATE result=SUCCESS"));assertTrue(line.contains("songId="+song));assertTrue(line.contains(" version=1 "));
      Reply updated=call("PATCH","/songs/"+song,new UpdateSong("새 이름",90,12,1),token);
      line=activity(capture,updated);assertTrue(line.contains("event=SONG_UPDATE result=SUCCESS"));assertTrue(line.contains("expectedVersion=1 version=2"));
      SaveBody first=SaveBody.from(draft(song,"audituser",0,"첫 기록"),UUID.randomUUID(),0);
      Reply saved=call("PUT",recordPath(song),first,token);line=activity(capture,saved);
      assertTrue(line.contains("event=RECORD_CREATE result=SUCCESS"));assertTrue(line.contains("expectedVersion=0 version=1"));assertTrue(line.contains("noteCount=2 restCount=1 expressionCount=1"));
      Reply replay=call("PUT",recordPath(song),first,token);line=activity(capture,replay);
      assertTrue(line.contains("event=RECORD_REPLAY result=REPLAY"));assertTrue(line.contains("committed=true changed=false"));assertTrue(line.contains("requestId="+first.requestId()));
      SaveBody second=SaveBody.from(draft(song,"audituser",1,"수정 기록"),UUID.randomUUID(),1);
      Reply edited=call("PUT",recordPath(song),second,token);line=activity(capture,edited);
      assertTrue(line.contains("event=RECORD_UPDATE result=SUCCESS"));assertTrue(line.contains("expectedVersion=1 version=2"));
      Reply conflict=call("PUT",recordPath(song),SaveBody.from(draft(song,"audituser",1,"충돌"),UUID.randomUUID(),1),token);
      line=activity(capture,conflict);assertTrue(line.contains("result=FAILURE"));assertTrue(line.contains("code=VERSION_CONFLICT"));assertTrue(line.contains("committed=false changed=false"));
      Reply badLogin=call("POST","/auth/login",new Login("audituser","wrongpass"),null);
      line=activity(capture,badLogin);assertTrue(line.contains("event=LOGIN result=FAILURE user=- attemptedUser=audituser"));assertTrue(line.contains("http=401 code=INVALID_CREDENTIALS"));
      Reply settings=call("PUT","/me/settings",new Settings(40,12,true,true,3),token);
      assertTrue(activity(capture,settings).contains("event=SETTINGS_UPDATE result=SUCCESS"));
      Reply logout=call("POST","/auth/logout",null,token);assertTrue(activity(capture,logout).contains("event=LOGOUT result=SUCCESS user=audituser"));
      Reply anonymous=call("GET","/songs",null,null);assertTrue(activity(capture,anonymous).contains("result=FAILURE user=- attemptedUser=-"));
      java.nio.file.Path logs=java.nio.file.Path.of(System.getProperty("MMM_LOG_DIR"));
      assertTrue(java.nio.file.Files.exists(logs.resolve("server.log")));
      String file=java.nio.file.Files.readString(logs.resolve("activity.log"));
      assertTrue(file.contains(edited.headers().firstValue("X-MMM-Trace-Id").orElseThrow()));
      assertTrue(file.contains("ACTIVITY event=RECORD_UPDATE result=SUCCESS"));
    }
  }

  @Test void activityExcludesCredentialsPrivateContentAndLogInjection() throws Exception {
    try(ActivityCapture capture=new ActivityCapture()) {
      String password="PRIVATE_PASSWORD_SECRET",memo="PRIVATE_MEMO_SECRET",title="PRIVATE_TITLE_SECRET",nickname="PRIVATE_NICK_SECRET";
      call("POST","/auth/signup",new Signup("privateuser",nickname,password),null);
      Reply login=call("POST","/auth/login",new Login("privateuser",password),null);String token=string(object(login.body()),"token");
      Reply created=call("POST","/songs",new CreateSong(title,SongType.SHARED,90,0),token);String song=string(object(created.body()),"id");
      String code=string(object(call("POST","/songs/"+song+"/invites",null,token).body()),"code");
      Reply saved=call("PUT",recordPath(song),SaveBody.from(draft(song,"privateuser",0,memo),UUID.randomUUID(),0),token);activity(capture,saved);
      Reply injection=call("POST","/auth/login",new Login("fake\nLOG_INJECTION_SENTINEL","wrongpass"),null);
      String injectedLine=activity(capture,injection);assertTrue(injectedLine.contains("attemptedUser=INVALID"));assertFalse(injectedLine.contains("LOG_INJECTION_SENTINEL"));
      Reply secretPath=call("GET","/LOG_PATH_SENTINEL?token="+token,null,null);
      assertTrue(activity(capture,secretPath).contains("route=/api/[unmatched]"));
      String text=String.join("\n",capture.lines);
      String files=java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("MMM_LOG_DIR"),"activity.log"))
          +java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("MMM_LOG_DIR"),"server.log"));
      for(String secret:List.of(password,memo,title,nickname,token,code,"LOG_INJECTION_SENTINEL","LOG_PATH_SENTINEL",
          db.queryForObject("SELECT password_salt FROM app_users WHERE login_id='privateuser'",String.class),
          db.queryForObject("SELECT password_hash FROM app_users WHERE login_id='privateuser'",String.class),PasswordCodec.sha256(token))) {
        assertFalse(text.contains(secret),"Sensitive value appeared in activity metadata");assertFalse(files.contains(secret),"Sensitive value appeared in log files");
      }
    }
  }

  @Test void commitFailureNeverEmitsSuccessfulSaveActivity() throws Exception {
    String owner=account("owner"),song=create(owner,false);
    db.execute("CREATE FUNCTION test_fail_commit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected deferred commit failure'; END $$");
    db.execute("CREATE CONSTRAINT TRIGGER test_fail_commit AFTER INSERT ON save_requests DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION test_fail_commit()");
    try(ActivityCapture capture=new ActivityCapture()) {
      Reply result=call("PUT",recordPath(song),SaveBody.from(draft(song,"owner",0,"롤백"),UUID.randomUUID(),0),owner);
      assertTrue(result.status()>=400);
      String line=activity(capture,result);assertTrue(line.contains("event=RECORD_CREATE result=FAILURE"));assertTrue(line.contains("committed=false changed=false"));
      assertEquals(0,db.queryForObject("SELECT count(*) FROM bar_records",Integer.class));assertEquals(0,db.queryForObject("SELECT count(*) FROM save_requests",Integer.class));
      assertEquals(1,capture.lines.size());
    } finally {db.execute("DROP TRIGGER test_fail_commit ON save_requests");db.execute("DROP FUNCTION test_fail_commit()");}
  }

  @Test void twoClientsShareExactPhraseAndMidi() throws Exception {
    MusicService a=client("alice"),b=client("bravo");
    Song shared=a.create("두 기기의 노래",true,90,0);assertEquals(1,shared.version());
    b.join(a.invite(shared.id()));
    assertEquals(shared.id(),b.songs().get(0).id());assertEquals(2,b.participants(shared.id()).size());
    Phrase saved=a.save(draft(shared.id(),"alice",0,"같은 소리 🎵"));
    Phrase received=b.phrases(shared.id()).get(0);
    assertEquals(saved,received);assertEquals(1,received.version());
    Sequence sequence=MidiEngine.sequence(List.of(new MidiEngine.Part(received,0,0,100)),1920);
    List<Long> offs=new ArrayList<>();
    for(Track track:sequence.getTracks())for(int i=0;i<track.size();i++)if(track.get(i).getMessage() instanceof ShortMessage m && m.getCommand()==ShortMessage.NOTE_OFF)offs.add(track.get(i).getTick());
    assertEquals(List.of(60L,960L),offs);
    assertEquals(1,db.queryForObject("SELECT count(*) FROM bar_records",Integer.class));
    assertEquals(2,db.queryForObject("SELECT count(*) FROM notes",Integer.class));
    assertEquals(1,db.queryForObject("SELECT count(*) FROM rests",Integer.class));
    assertEquals(1,db.queryForObject("SELECT count(*) FROM expression_ranges",Integer.class));
    MusicService restarted=new MusicService(new RemoteRepository(URI.create(url())),clock,false);restarted.login("bravo","test1234".toCharArray());
    assertEquals(saved,restarted.phrases(shared.id()).get(0));
  }

  @Test void fullMidiRangePersistsAndIsSharedByRemoteClients() throws Exception {
    MusicService writer=client("widewriter"),reader=client("widereader");
    Song song=writer.create("전체 음역 공유",true,90,0);reader.join(writer.invite(song.id()));
    Phrase saved=writer.save(new Phrase(song.id(),"widewriter",DAY,0,90,0,"최저와 최고",
        List.of(new NoteEvent(0,480,0,90),new NoteEvent(960,480,127,90))));
    assertEquals(saved,reader.phrases(song.id()).get(0));
    assertEquals(List.of(0,127),db.queryForList("SELECT pitch FROM notes ORDER BY pitch",Integer.class));
    String token=string(object(call("POST","/auth/login",new Login("widewriter","test1234"),null).body()),"token");
    SaveBody next=SaveBody.from(new Phrase(song.id(),"widewriter",DAY,1,90,0,"",
        List.of(new NoteEvent(0,480,0,90))),UUID.randomUUID(),1);
    for(int invalid:List.of(-1,128))
      assertEquals(400,rawCall("PUT",recordPath(song.id()),write(next).replace("\"pitch\":0","\"pitch\":"+invalid),token).status());
    assertEquals(1,db.queryForObject("SELECT version FROM bar_records",Integer.class));
  }

  @Test void pitchMigrationPreservesVersionOneRecordsAndExpandsDatabaseConstraint() throws Exception {
    String owner=account("upgradeuser"),song=create(owner,false);
    assertEquals(200,call("PUT",recordPath(song),SaveBody.from(draft(song,"upgradeuser",0,"기존 기록"),UUID.randomUUID(),0),owner).status());
    String schema="pitch_upgrade_test";
    try {
      org.flywaydb.core.Flyway.configure().dataSource(POSTGRES.getJdbcUrl("postgres","postgres"),"postgres","postgres")
          .schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("1").load().migrate();
      for(String table:List.of("app_users","songs","memberships","bar_records","notes","rests","expression_ranges","invite_codes","user_settings","auth_sessions","save_requests"))
        db.execute("INSERT INTO "+schema+"."+table+" SELECT * FROM public."+table);
      assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.execute(
          "INSERT INTO "+schema+".notes SELECT record_id,2,960,120,0,90,'NORMAL' FROM "+schema+".notes WHERE order_no=0"));
      org.flywaydb.core.Flyway migrated=org.flywaydb.core.Flyway.configure()
          .dataSource(POSTGRES.getJdbcUrl("postgres","postgres"),"postgres","postgres")
          .schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("2").load();
      migrated.migrate();assertEquals("2",migrated.info().current().getVersion().toString());
      assertEquals("기존 기록",db.queryForObject("SELECT memo FROM "+schema+".bar_records",String.class));
      assertEquals(2,db.queryForObject("SELECT count(*) FROM "+schema+".notes",Integer.class));
      db.execute("INSERT INTO "+schema+".notes SELECT record_id,2,960,120,0,90,'NORMAL' FROM "+schema+".notes WHERE order_no=0");
      db.execute("INSERT INTO "+schema+".notes SELECT record_id,3,1080,120,127,90,'NORMAL' FROM "+schema+".notes WHERE order_no=0");
      assertEquals(List.of(0,60,64,127),db.queryForList("SELECT pitch FROM "+schema+".notes ORDER BY pitch",Integer.class));
      for(int invalid:List.of(-1,128))assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.execute(
          "INSERT INTO "+schema+".notes SELECT record_id,4,1200,120,"+invalid+",90,'NORMAL' FROM "+schema+".notes WHERE order_no=0"));
    } finally {db.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");}
  }

  @Test void authorizationAndDateAreEnforcedOnRawHttp() throws Exception {
    String owner=account("owner"),member=account("member"),outsider=account("outside");
    String shared=create(owner,true),personal=create(owner,false);
    assertEquals(200,call("POST","/invites/redeem",new Redeem(invite(owner,shared)),member).status());
    assertEquals(403,call("GET","/songs/"+shared+"/members",null,outsider).status());
    assertEquals(403,call("GET","/songs/"+personal+"/records?from="+DAY+"&to="+DAY,null,member).status());
    assertEquals(403,call("PATCH","/songs/"+shared,new UpdateSong("위조",90,0,1),member).status());
    assertEquals(403,call("POST","/songs/"+shared+"/invites",null,member).status());
    SaveBody body=SaveBody.from(draft(shared,"owner",0,""),UUID.randomUUID(),0);
    assertEquals(400,rawCall("PUT",recordPath(shared),write(body).replace("\"memo\":", "\"authorId\":\"owner\",\"memo\":"),member).status());
    assertEquals(403,call("PUT","/songs/"+shared+"/records/"+DAY.minusDays(1)+"/me",body,owner).status());
    assertEquals(403,call("PUT","/songs/"+shared+"/records/"+DAY.plusDays(1)+"/me",body,owner).status());
    assertEquals(403,call("PUT",recordPath(shared),body,outsider).status());
    assertEquals(0,db.queryForObject("SELECT count(*) FROM bar_records",Integer.class));
  }

  @Test void percussionInstrumentIsSavedSharedAndExportedOnDrumChannel() throws Exception {
    MusicService writer=client("drummer"),reader=client("listener");
    Song kit=writer.create("드럼 공동 기록",true,90,Model.DRUMS);reader.join(writer.invite(kit.id()));
    Phrase saved=writer.save(new Phrase(kit.id(),"drummer",DAY,0,90,Model.DRUMS,"리듬",
        List.of(new NoteEvent(0,120,36,90),new NoteEvent(480,120,38,90),new NoteEvent(960,120,42,80))));
    Phrase received=reader.phrases(kit.id()).get(0);assertEquals(saved,received);assertEquals(128,received.instrument());
    Settings settings=new Settings(70,Model.DRUMS,true);writer.repository.settings("drummer",settings);
    assertEquals(settings,writer.repository.settings("drummer"));
    Sequence midi=MidiEngine.sequence(List.of(new MidiEngine.Part(received,0,4,100)),1920);
    List<Integer> channels=new ArrayList<>();
    for(Track track:midi.getTracks())for(int i=0;i<track.size();i++)if(track.get(i).getMessage() instanceof ShortMessage message && message.getCommand()==ShortMessage.NOTE_ON)channels.add(message.getChannel());
    assertEquals(List.of(9,9,9),channels);
    String token=string(object(call("POST","/auth/login",new Login("drummer","test1234"),null).body()),"token");
    assertEquals(400,call("POST","/songs",new CreateSong("잘못된 악기",SongType.PERSONAL,90,129),token).status());
  }

  @Test void percussionMigrationPreservesLegacyVoicesBeforeExpandingInstrumentIds() throws Exception {
    String token=account("legacyuser"),song=create(token,false),schema="percussion_upgrade_test";
    assertEquals(200,call("PATCH","/songs/"+song,new UpdateSong("기존 악기",90,12,1),token).status());
    Phrase legacy=new Phrase(song,"legacyuser",DAY,0,90,10,"오르골 기록",List.of(new NoteEvent(0,480,60,90)));
    assertEquals(200,call("PUT",recordPath(song),SaveBody.from(legacy,UUID.randomUUID(),0),token).status());
    assertEquals(200,call("PUT","/me/settings",new Settings(80,12,true),token).status());
    try {
      org.flywaydb.core.Flyway.configure().dataSource(POSTGRES.getJdbcUrl("postgres","postgres"),"postgres","postgres")
          .schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("2").load().migrate();
      for(String table:List.of("app_users","songs","memberships","bar_records","notes","rests","expression_ranges","invite_codes","user_settings","auth_sessions","save_requests"))
        db.execute("INSERT INTO "+schema+"."+table+" SELECT * FROM public."+table);
      assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.execute("UPDATE "+schema+".songs SET instrument=128"));
      org.flywaydb.core.Flyway migration=org.flywaydb.core.Flyway.configure().dataSource(POSTGRES.getJdbcUrl("postgres","postgres"),"postgres","postgres")
          .schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("3").load();migration.migrate();
      assertEquals(12,db.queryForObject("SELECT instrument FROM "+schema+".songs",Integer.class));
      assertEquals(10,db.queryForObject("SELECT instrument FROM "+schema+".bar_records",Integer.class));
      assertEquals("오르골 기록",db.queryForObject("SELECT memo FROM "+schema+".bar_records",String.class));
      assertEquals(60,db.queryForObject("SELECT pitch FROM "+schema+".notes",Integer.class));
      for(String table:List.of("songs","bar_records","user_settings")) {
        db.execute("UPDATE "+schema+"."+table+" SET instrument=128");
        assertEquals(128,db.queryForObject("SELECT instrument FROM "+schema+"."+table,Integer.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.execute("UPDATE "+schema+"."+table+" SET instrument=129"));
      }
    } finally {db.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");}
  }

  @Test void idempotencyReturnsOriginalResultAndRejectsChangedContent() throws Exception {
    String owner=account("owner"),song=create(owner,true);
    UUID id=UUID.randomUUID();SaveBody first=SaveBody.from(draft(song,"owner",0,"첫 저장"),id,0);
    Reply r1=call("PUT",recordPath(song),first,owner);assertEquals(200,r1.status(),r1.raw());
    Reply r2=call("PUT",recordPath(song),first,owner);assertEquals(r1.body(),r2.body());
    SaveBody second=SaveBody.from(draft(song,"owner",1,"다음 저장"),UUID.randomUUID(),1);
    assertEquals(200,call("PUT",recordPath(song),second,owner).status());
    assertEquals(r1.body(),call("PUT",recordPath(song),first,owner).body());
    assertEquals(409,call("PUT",recordPath(song),SaveBody.from(draft(song,"owner",0,"다른 내용"),id,0),owner).status());
    assertEquals(2,db.queryForObject("SELECT version FROM bar_records",Integer.class));
    assertEquals(2,db.queryForObject("SELECT count(*) FROM save_requests",Integer.class));
  }

  @Test void simultaneousSavesDetectConflictsAndDuplicateRequests() throws Exception {
    String owner=account("owner"),song=create(owner,true);
    SaveBody one=SaveBody.from(draft(song,"owner",0,"하나"),UUID.randomUUID(),0),two=SaveBody.from(draft(song,"owner",0,"둘"),UUID.randomUUID(),0);
    try(ExecutorService pool=Executors.newFixedThreadPool(2)) {
      CountDownLatch gate=new CountDownLatch(1);
      Future<Integer> a=pool.submit(()->{gate.await();return call("PUT",recordPath(song),one,owner).status();});
      Future<Integer> b=pool.submit(()->{gate.await();return call("PUT",recordPath(song),two,owner).status();});gate.countDown();
      assertEquals(Set.of(200,409),Set.of(a.get(),b.get()));
      SaveBody same=SaveBody.from(draft(song,"owner",1,"동일 요청"),UUID.randomUUID(),1);
      Future<Reply> x=pool.submit(()->call("PUT",recordPath(song),same,owner));Future<Reply> y=pool.submit(()->call("PUT",recordPath(song),same,owner));
      Reply rx=x.get(),ry=y.get();assertEquals(200,rx.status());assertEquals(200,ry.status());assertEquals(rx.body(),ry.body());
    }
    assertEquals(2,db.queryForObject("SELECT version FROM bar_records",Integer.class));
    assertEquals(2,db.queryForObject("SELECT count(*) FROM save_requests",Integer.class));
  }

  @Test void childInsertFailureRollsBackWholeRecordAndRequest() throws Exception {
    String owner=account("owner"),song=create(owner,false);
    SaveBody base=SaveBody.from(draft(song,"owner",0,"원본"),UUID.randomUUID(),0);
    assertEquals(200,call("PUT",recordPath(song),base,owner).status());
    db.execute("CREATE FUNCTION test_fail_expression() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected transaction failure'; END $$");
    db.execute("CREATE TRIGGER test_fail BEFORE INSERT ON expression_ranges FOR EACH ROW EXECUTE FUNCTION test_fail_expression()");
    try {
      Reply failure=call("PUT",recordPath(song),SaveBody.from(draft(song,"owner",1,"실패 초안"),UUID.randomUUID(),1),owner);
      assertTrue(failure.status()>=400);assertFalse(failure.raw().contains("injected"));
      assertEquals("원본",db.queryForObject("SELECT memo FROM bar_records",String.class));
      assertEquals(1,db.queryForObject("SELECT version FROM bar_records",Integer.class));
      assertEquals(2,db.queryForObject("SELECT count(*) FROM notes",Integer.class));
      assertEquals(1,db.queryForObject("SELECT count(*) FROM rests",Integer.class));
      assertEquals(1,db.queryForObject("SELECT count(*) FROM expression_ranges",Integer.class));
      assertEquals(1,db.queryForObject("SELECT count(*) FROM save_requests",Integer.class));
    } finally {db.execute("DROP TRIGGER test_fail ON expression_ranges");db.execute("DROP FUNCTION test_fail_expression()");}
  }

  @Test void membershipLeaveRetainsContributionsAndRevokesAccess() throws Exception {
    String owner=account("owner"),member=account("member"),song=create(owner,true),code=invite(owner,song);
    assertEquals(200,call("POST","/invites/redeem",new Redeem(code),member).status());
    assertEquals(409,call("POST","/invites/redeem",new Redeem(code),member).status());
    assertEquals(200,call("PUT",recordPath(song),SaveBody.from(draft(song,"member",0,"기여"),UUID.randomUUID(),0),member).status());
    assertEquals(204,call("DELETE","/songs/"+song+"/members/me",null,member).status());
    assertEquals(403,call("GET","/songs/"+song+"/records?from="+DAY+"&to="+DAY,null,member).status());
    assertEquals(403,call("PUT",recordPath(song),SaveBody.from(draft(song,"member",1,"금지"),UUID.randomUUID(),1),member).status());
    assertEquals(1,list(call("GET","/songs/"+song+"/records?from="+DAY+"&to="+DAY,null,owner).body()).size());
    assertEquals(403,call("DELETE","/songs/"+song+"/members/me",null,owner).status());
    assertEquals(200,call("POST","/invites/redeem",new Redeem(code),member).status());
  }

  @Test void invitationExpiryAndParticipantLimit() throws Exception {
    String owner=account("owner"),song=create(owner,true),code=invite(owner,song),member=account("member");
    clock.instant=NOON.plus(Duration.ofDays(7));
    // Re-login on the new server day; the invitation itself is expired at the exact boundary.
    String renewed=string(object(call("POST","/auth/login",new Login("member","test1234"),null).body()),"token");
    Reply expired=call("POST","/invites/redeem",new Redeem(code),renewed);assertEquals(400,expired.status());assertEquals("EXPIRED_INVITE",string(object(expired.body()),"code"));
    clock.instant=NOON;
    for(int i=0;i<14;i++)assertEquals(200,call("POST","/invites/redeem",new Redeem(code),account("part"+i)).status());
    Reply full=call("POST","/invites/redeem",new Redeem(code),member);assertEquals(409,full.status());assertEquals("MEMBER_LIMIT",string(object(full.body()),"code"));
    assertEquals(15,db.queryForObject("SELECT count(*) FROM memberships WHERE active",Integer.class));
  }

  @Test void songVersionAndSettingsAreIsolated() throws Exception {
    MusicService owner=client("owner"),other=client("other");Song song=owner.create("원본",false,90,0);
    Song changed=owner.updateSong(song,"변경",100,12);assertEquals(2,changed.version());
    assertThrows(RemoteRepository.RemoteException.class,()->owner.updateSong(song,"오래된 설정",90,0));
    Settings settings=new Settings(37,24,false,true,8);owner.repository.settings("owner",settings);
    assertEquals(settings,owner.repository.settings("owner"));assertNotEquals(settings,other.repository.settings("other"));
    assertThrows(IOException.class,()->owner.repository.account("owner"));
    assertEquals("변경",owner.songs().get(0).name());
  }

  @Test void domainBoundariesRejectBadRequestsAndAllowExplicitSilence() throws Exception {
    String owner=account("owner"),song=create(owner,true);SaveBody valid=SaveBody.from(draft(song,"owner",0,""),UUID.randomUUID(),0);
    assertEquals(400,rawCall("PUT",recordPath(song),write(valid).replace("\"memo\":\"\"","\"memo\":\""+"가".repeat(21)+"\""),owner).status());
    assertEquals(400,rawCall("PUT",recordPath(song),write(valid).replace("\"pitch\":60","\"pitch\":-1"),owner).status());
    assertEquals(400,rawCall("PUT",recordPath(song),write(valid).replace("\"startTick\":120","\"startTick\":0"),owner).status());
    assertEquals(400,rawCall("PUT",recordPath(song),write(valid).replace("\"bpm\":90","\"bpm\":100"),owner).status());
    assertEquals(400,rawCall("PUT",recordPath(song),write(valid).replace("\"schemaVersion\":2","\"schemaVersion\":1"),owner).status());
    assertEquals(400,rawCall("PUT",recordPath(song),write(valid).replace("\"startTick\":0","\"startTick\":0.5"),owner).status());
    assertEquals(400,rawCall("PUT",recordPath(song),write(valid).replace("\"bpm\":90","\"bpm\":\"90\""),owner).status());
    assertEquals(400,rawCall("PUT",recordPath(song),write(valid).replace("\"expectedVersion\":0,",""),owner).status());
    assertEquals(400,rawCall("PUT",recordPath(song),write(valid).replace("\"memo\":\"\"","\"memo\":\"\",\"memo\":\"duplicate\""),owner).status());
    assertEquals(400,call("GET","/songs/"+song+"/records?from="+DAY.minusDays(366)+"&to="+DAY,null,owner).status());
    Phrase silence=new Phrase(song,"owner",DAY,0,90,0,"🎵".repeat(20),List.of(),List.of(new RestEvent(0,1920)),List.of());
    Reply saved=call("PUT",recordPath(song),SaveBody.from(silence,UUID.randomUUID(),0),owner);assertEquals(200,saved.status(),saved.raw());
    assertEquals(0,db.queryForObject("SELECT count(*) FROM notes",Integer.class));assertEquals(1,db.queryForObject("SELECT count(*) FROM rests",Integer.class));
    assertEquals(413,rawCall("POST","/auth/login"," ".repeat(131073),null).status());
  }

  @Test void sessionExpiryAndMidnightPreserveClientDraft() throws Exception {
    MusicService service=client("owner");Song song=service.create("날짜",false,90,0);Phrase draft=draft(song.id(),"owner",0,"초안");
    clock.instant=NOON.plus(Duration.ofHours(13));
    RemoteRepository.RemoteException expired=assertThrows(RemoteRepository.RemoteException.class,()->service.save(draft));assertEquals(401,expired.status);
    assertThrows(IOException.class,()->service.reauthenticate("owner","wrongpass".toCharArray()));assertEquals("owner",service.user());
    service.reauthenticate("owner","test1234".toCharArray());
    RemoteRepository.RemoteException midnight=assertThrows(RemoteRepository.RemoteException.class,()->service.save(draft));assertEquals("TODAY_ONLY",midnight.code);
    assertEquals("초안",draft.memo());assertEquals(DAY.plusDays(1),service.today());assertEquals(0,db.queryForObject("SELECT count(*) FROM bar_records",Integer.class));
  }

  @Test void lostSaveResponseRetriesSameRequestOnce() throws Exception {
    MusicService original=client("owner");Song song=original.create("응답 유실",false,90,0);
    HttpServer proxy=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicBoolean lose=new AtomicBoolean(true);
    proxy.createContext("/",exchange->{
      try {
        byte[] body=exchange.getRequestBody().readAllBytes();
        HttpRequest.Builder request=HttpRequest.newBuilder(URI.create(url()+exchange.getRequestURI())).method(exchange.getRequestMethod(),body.length==0?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body));
        for(String header:List.of("Authorization","Content-Type"))if(exchange.getRequestHeaders().getFirst(header)!=null)request.header(header,exchange.getRequestHeaders().getFirst(header));
        HttpResponse<byte[]> upstream=http.send(request.build(),HttpResponse.BodyHandlers.ofByteArray());
        byte[] result=upstream.body();int status=upstream.statusCode();
        if(exchange.getRequestMethod().equals("PUT")&&lose.getAndSet(false)){status=503;result="{\"code\":\"RESPONSE_LOST\",\"message\":\"response unavailable\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);}
        exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(status,result.length);exchange.getResponseBody().write(result);
      }catch(Exception ex){exchange.sendResponseHeaders(502,-1);}finally{exchange.close();}
    });proxy.start();
    try {
      MusicService proxied=new MusicService(new RemoteRepository(URI.create("http://127.0.0.1:"+proxy.getAddress().getPort())),clock,false);proxied.login("owner","test1234".toCharArray());
      Phrase draft=draft(song.id(),"owner",0,"재시도");assertThrows(IOException.class,()->proxied.save(draft));
      assertEquals(1,db.queryForObject("SELECT version FROM bar_records",Integer.class));
      assertEquals(1,proxied.save(draft).version());assertEquals(1,db.queryForObject("SELECT count(*) FROM save_requests",Integer.class));
    } finally {proxy.stop(0);}
  }

  @Test void networkTimeoutDoesNotChangeDraft() throws Exception {
    HttpServer slow=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    slow.createContext("/",exchange->{try{Thread.sleep(500);exchange.sendResponseHeaders(503,-1);}catch(Exception ignored){}finally{exchange.close();}});slow.start();
    try {
      RemoteRepository repository=new RemoteRepository(URI.create("http://127.0.0.1:"+slow.getAddress().getPort()),Duration.ofMillis(100),Duration.ofMillis(100));
      long start=System.nanoTime();IOException login=assertThrows(IOException.class,()->repository.login("owner","test1234".toCharArray()));
      assertTrue(login.getMessage().contains("로그인 응답 시간"));assertFalse(login.getMessage().contains("같은 저장"));
      assertTrue(Duration.ofNanos(System.nanoTime()-start).toMillis()<1500);
      IOException signup=assertThrows(IOException.class,()->repository.signup("owner","이름","test1234".toCharArray()));
      assertTrue(signup.getMessage().contains("같은 아이디로 로그인을 먼저 시도"));
      assertFalse(signup.getMessage().contains("같은 저장"));
      assertThrows(IllegalArgumentException.class,()->new RemoteRepository(URI.create("http://192.0.2.1:8080")));
      assertThrows(IllegalArgumentException.class,()->new RemoteRepository(URI.create("https://user:pass@example.com")));
    } finally {slow.stop(0);}
  }

  @Test void independentJvmClientsSaveAndReloadOverHttp() throws Exception {
    String owner=account("writer"),reader=account("reader"),song=create(owner,true);
    assertEquals(200,call("POST","/invites/redeem",new Redeem(invite(owner,song)),reader).status());
    Object written=child("save","writer",song),received=child("read","reader",song);
    assertEquals(written,received);
    assertEquals(1,db.queryForObject("SELECT count(*) FROM bar_records",Integer.class));
  }

  @Test void httpsRejectsUntrustedAndAcceptsTrustedServerCertificate() throws Exception {
    java.nio.file.Path keyStoreFile=java.nio.file.Path.of("target","https-test-"+UUID.randomUUID()+".p12").toAbsolutePath();
    if(!java.nio.file.Files.exists(keyStoreFile)) {
      String tool=java.nio.file.Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"keytool.exe":"keytool").toString();
      Process keytool=new ProcessBuilder(tool,"-genkeypair","-alias","test","-keyalg","RSA","-keysize","2048","-validity","3","-storetype","PKCS12","-keystore",keyStoreFile.toString(),"-storepass","test-certificate-only","-dname","CN=localhost","-ext","SAN=dns:localhost,ip:127.0.0.1","-noprompt").redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
      assertTrue(keytool.waitFor(20,TimeUnit.SECONDS));assertEquals(0,keytool.exitValue());
    }
    java.security.KeyStore store=java.security.KeyStore.getInstance("PKCS12");
    try(InputStream input=java.nio.file.Files.newInputStream(keyStoreFile)){store.load(input,"test-certificate-only".toCharArray());}
    javax.net.ssl.KeyManagerFactory keys=javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());keys.init(store,"test-certificate-only".toCharArray());
    javax.net.ssl.TrustManagerFactory trust=javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());trust.init(store);
    javax.net.ssl.SSLContext tls=javax.net.ssl.SSLContext.getInstance("TLS");tls.init(keys.getKeyManagers(),trust.getTrustManagers(),null);
    com.sun.net.httpserver.HttpsServer proxy=com.sun.net.httpserver.HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0);
    proxy.setHttpsConfigurator(new com.sun.net.httpserver.HttpsConfigurator(tls));
    proxy.createContext("/",exchange->{
      try {
        byte[] body=exchange.getRequestBody().readAllBytes();
        HttpRequest.Builder request=HttpRequest.newBuilder(URI.create(url()+exchange.getRequestURI())).method(exchange.getRequestMethod(),body.length==0?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body));
        for(String header:List.of("Authorization","Content-Type"))if(exchange.getRequestHeaders().getFirst(header)!=null)request.header(header,exchange.getRequestHeaders().getFirst(header));
        HttpResponse<byte[]> response=http.send(request.build(),HttpResponse.BodyHandlers.ofByteArray());
        exchange.getResponseHeaders().add("Content-Type","application/json");
        exchange.getResponseHeaders().add("X-MMM-Today",DAY.toString());
        if(response.statusCode()==204)exchange.sendResponseHeaders(204,-1);
        else {exchange.sendResponseHeaders(response.statusCode(),response.body().length);exchange.getResponseBody().write(response.body());}
      } catch(Exception ex){exchange.sendResponseHeaders(502,-1);}finally{exchange.close();}
    });proxy.start();javax.net.ssl.SSLContext original=javax.net.ssl.SSLContext.getDefault();
    try {
      URI endpoint=URI.create("https://localhost:"+proxy.getAddress().getPort());
      assertThrows(IOException.class,()->new RemoteRepository(endpoint).login("owner","test1234".toCharArray()));
      javax.net.ssl.SSLContext.setDefault(tls);
      MusicService secure=new MusicService(new RemoteRepository(endpoint),clock,false);
      secure.register("secure","보안 연결","test1234".toCharArray(),"test1234".toCharArray());secure.login("secure","test1234".toCharArray());
      Song song=secure.create("HTTPS 기록",true,90,0);assertEquals(1,secure.save(draft(song.id(),"secure",0,"TLS")).version());
      assertEquals("TLS",secure.phrases(song.id()).get(0).memo());secure.logout();
    } finally {javax.net.ssl.SSLContext.setDefault(original);proxy.stop(0);}
  }
  private Object child(String action,String user,String song) throws Exception {
    String executable=java.nio.file.Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
    Process process=new ProcessBuilder(executable,"-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),"mmm.server.ClientProcessProbe").start();
    try {
      try(OutputStream input=process.getOutputStream()) {input.write(write(Map.of("endpoint",url(),"action",action,"loginId",user,"password","test1234","songId",song)).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
      if(!process.waitFor(20,TimeUnit.SECONDS))throw new AssertionError("client process timeout");
      String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
      String errors=new String(process.getErrorStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
      assertEquals(0,process.exitValue(),errors);return read(output);
    } finally {if(process.isAlive())process.destroyForcibly();}
  }

  @Test void swingRemoteLoginSaveConflictAndReauthentication() throws Exception {
    Assumptions.assumeTrue(Boolean.getBoolean("mmm.gui.tests"),"Run with -Dmmm.gui.tests=true on a desktop");
    MusicService gui=client("owner"),reader=client("reader");Song song=gui.create("원격 화면",true,90,0);
    reader.join(gui.invite(song.id()));Phrase base=gui.save(draft(song.id(),"owner",0,"처음"));
    gui.logout();
    AtomicReference<MMM> frame=new AtomicReference<>();AtomicReference<javax.swing.Timer> dialogTimer=new AtomicReference<>();AtomicInteger reauthenticated=new AtomicInteger();
    try {
      SwingProbe.edt(()->{
        Theme.install();MMM window=new MMM(gui,java.nio.file.Files.createTempDirectory("mmm-remote-gui-"));frame.set(window);
        javax.swing.Timer timer=new javax.swing.Timer(40,event->{
          for(java.awt.Window w:java.awt.Window.getWindows())if(w instanceof javax.swing.JDialog d&&d.isVisible()) {
            if(d.getTitle().equals("D05 · 다시 로그인")) {
              javax.swing.JPasswordField password=SwingProbe.find(d,javax.swing.JPasswordField.class);
              if(password!=null)password.setText("test1234");reauthenticated.incrementAndGet();
            }
            javax.swing.AbstractButton button=SwingProbe.button(d,d.getTitle().contains("충돌")?"초안 유지":"확인");
            if(button!=null)button.doClick();
          }
        });timer.start();dialogTimer.set(timer);
      });
      SwingProbe.idle(frame.get());
      SwingProbe.edt(()->{
        MMM window=frame.get();SwingProbe.invoke(window,"login",new Class<?>[]{String.class},"owner");
        SwingProbe.find(window,javax.swing.JPasswordField.class).setText("test1234");
        SwingProbe.button(window,"로그인").doClick();
      });
      SwingProbe.idle(frame.get());assertEquals("owner",gui.user());
      SwingProbe.edt(()->{
        MMM window=frame.get();assertEquals("S04",SwingProbe.field(window,"screen"));
        SwingProbe.set(window,"current",song);SwingProbe.set(window,"week",DAY);window.week();
      });
      SwingProbe.idle(frame.get());
      SwingProbe.edt(()->{
        MMM window=frame.get();SwingProbe.invoke(window,"edit",new Class<?>[]{Phrase.class,LocalDate.class},base,DAY);
        for(int octave=0;octave<3;octave++)SwingProbe.button(window.editor.piano.getParent(),"›").doClick();
        for(int type:List.of(java.awt.event.KeyEvent.KEY_PRESSED,java.awt.event.KeyEvent.KEY_RELEASED)) {
          java.awt.event.KeyEvent event=new java.awt.event.KeyEvent(window.editor.piano,type,System.currentTimeMillis(),0,java.awt.event.KeyEvent.VK_W,'w');
          for(java.awt.event.KeyListener listener:window.editor.piano.getKeyListeners())
            if(type==java.awt.event.KeyEvent.KEY_PRESSED)listener.keyPressed(event);else listener.keyReleased(event);
        }
        assertTrue(window.editor.notes.stream().anyMatch(note->note.pitch()==97),"Paged black key W must enter C-sharp7");
        window.editor.memo.setText("원격 화면 저장");SwingProbe.capture(window,"m2-editor");window.saveEditor(window::week);
      });
      SwingProbe.idle(frame.get());Phrase saved=reader.phrases(song.id()).get(0);assertEquals("원격 화면 저장",saved.memo());assertEquals(2,saved.version());
      assertTrue(saved.notes().stream().anyMatch(note->note.pitch()==97),"Other user must read the same saved wide-register note");
      MusicService otherWindow=new MusicService(new RemoteRepository(URI.create(url())),clock,false);otherWindow.login("owner","test1234".toCharArray());
      SwingProbe.edt(()->SwingProbe.invoke(frame.get(),"edit",new Class<?>[]{Phrase.class,LocalDate.class},saved,DAY));
      otherWindow.save(draft(song.id(),"owner",2,"다른 기기 변경"));
      SwingProbe.edt(()->{frame.get().editor.memo.setText("충돌 초안 유지");frame.get().saveEditor(frame.get()::week);});
      SwingProbe.idle(frame.get());
      SwingProbe.edt(()->{assertNotNull(frame.get().editor);assertTrue(frame.get().editor.dirty);assertEquals("충돌 초안 유지",frame.get().editor.memo.getText());});
      assertEquals(3,reader.phrases(song.id()).get(0).version());
      clock.instant=NOON.plus(Duration.ofHours(13));
      SwingProbe.edt(()->frame.get().saveEditor(frame.get()::week));
      SwingProbe.idle(frame.get());
      assertTrue(reauthenticated.get()>0);assertTrue(((RemoteRepository)gui.repository).authenticated());
      SwingProbe.edt(()->{assertEquals("충돌 초안 유지",frame.get().editor.memo.getText());assertTrue(frame.get().editor.dirty);SwingProbe.capture(frame.get(),"m2-reauthenticated-draft");});
      assertEquals(3,db.queryForObject("SELECT version FROM bar_records",Integer.class));
    } finally {
      SwingProbe.edt(()->{if(dialogTimer.get()!=null)dialogTimer.get().stop();if(frame.get()!=null){MMM window=frame.get();if(window.editor!=null)window.editor.dispose();window.midi.close();window.dispose();}});
    }
  }
}
