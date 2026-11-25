package mmm;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import mmm.Api.*;
import mmm.Model.*;
import static mmm.WireJson.*;

/** HTTPS adapter. Password hashes and database credentials never cross this boundary. */
public final class RemoteRepository implements Repository {
  public static final class RemoteException extends IOException {
    public final int status; public final String code;
    public RemoteException(int status,String code,String message){super(message);this.status=status;this.code=code;}
  }
  private final URI endpoint;
  private final HttpClient client;
  private final Duration timeout;
  private volatile String token;
  private volatile String currentUser;
  private volatile LocalDate serverToday=LocalDate.now(ZoneId.of("Asia/Seoul"));
  private volatile boolean synchronizedDate;
  private final Map<String,LocalDate> createdDates=new ConcurrentHashMap<>();
  private Phrase pendingPhrase;
  private SaveBody pendingSave;
  public RemoteRepository(URI endpoint){this(endpoint,Duration.ofSeconds(5),Duration.ofSeconds(15));}
  public RemoteRepository(URI endpoint,Duration connectTimeout,Duration requestTimeout){
    String host=endpoint.getHost();
    boolean loopback=Set.of("localhost","127.0.0.1","[::1]","::1").contains(host==null?"":host.toLowerCase(Locale.ROOT));
    if(host==null||endpoint.getUserInfo()!=null||endpoint.getQuery()!=null||endpoint.getFragment()!=null
        || !("https".equalsIgnoreCase(endpoint.getScheme())||("http".equalsIgnoreCase(endpoint.getScheme())&&loopback))
        || !Set.of("","/","/api","/api/").contains(endpoint.getPath()))
      throw new IllegalArgumentException("서버 주소는 HTTPS 기본 주소를 사용하세요. 로컬 개발에서는 localhost HTTP만 허용합니다.");
    this.endpoint=URI.create(endpoint.getScheme()+"://"+endpoint.getRawAuthority());
    timeout=requestTimeout;
    client=HttpClient.newBuilder().connectTimeout(connectTimeout).followRedirects(HttpClient.Redirect.NEVER).build();
  }
  public String endpoint(){return endpoint.toString();}
  public LocalDate serverToday(){return serverToday;}
  public boolean synchronizedDate(){return synchronizedDate;}
  public boolean authenticated(){return token!=null;}
  private Object request(String method,String path,Object body,boolean authenticated) throws IOException {
    HttpRequest.Builder builder=HttpRequest.newBuilder(endpoint.resolve("/api"+path)).timeout(timeout)
        .header("Accept","application/json");
    if(authenticated){String active=token;if(active==null)throw new RemoteException(401,"UNAUTHORIZED","다시 로그인하세요. 초안은 유지됩니다.");builder.header("Authorization","Bearer "+active);}
    if(body==null)builder.method(method,HttpRequest.BodyPublishers.noBody());
    else builder.header("Content-Type","application/json; charset=utf-8").method(method,HttpRequest.BodyPublishers.ofString(write(body),StandardCharsets.UTF_8));
    CompletableFuture<HttpResponse<String>> future=client.sendAsync(builder.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    final HttpResponse<String> response;
    try {response=future.get(timeout.toMillis(),TimeUnit.MILLISECONDS);}
    catch(InterruptedException ex){future.cancel(true);Thread.currentThread().interrupt();throw new IOException("서버 요청이 중단되었습니다. 초안을 유지했습니다.",ex);}
    catch(TimeoutException ex){future.cancel(true);throw new IOException(timeoutMessage(path),ex);}
    catch(ExecutionException ex){
      if(ex.getCause() instanceof HttpTimeoutException)throw new IOException(timeoutMessage(path),ex.getCause());
      throw new IOException("서버에 연결할 수 없습니다. 연결을 확인하고 다시 시도하세요. 초안은 유지됩니다.",ex.getCause());
    }
    try {
      Optional<String> day=response.headers().firstValue("X-MMM-Today");
      if(day.isPresent()){serverToday=LocalDate.parse(day.get());synchronizedDate=true;}
      if(response.statusCode()==204)return null;
      Object value=read(response.body());
      if(response.statusCode()<200||response.statusCode()>=300){
        if(response.statusCode()==401&&authenticated)token=null;
        Map<String,Object> error=object(value);
        throw new RemoteException(response.statusCode(),string(error,"code"),string(error,"message"));
      }
      return value;
    }catch(IllegalArgumentException ex){throw new IOException("서버 응답 형식을 확인할 수 없습니다. 초안을 유지했습니다.",ex);}
  }
  private String timeoutMessage(String path) {
    if(path.equals("/auth/signup"))return "회원가입 응답 시간이 초과되었습니다. 서버 연결을 확인하세요. 가입이 완료됐을 수 있으므로 같은 아이디로 로그인을 먼저 시도하세요. 입력 내용은 유지됩니다.";
    if(path.equals("/auth/login"))return "로그인 응답 시간이 초과되었습니다. 서버 연결을 확인한 뒤 다시 로그인하세요. 입력 내용은 유지됩니다.";
    return "서버 응답 시간이 초과되었습니다. 연결을 확인하고 다시 시도하세요. 초안은 유지됩니다.";
  }
  private void actor(String user) throws IOException {
    if(currentUser==null||!currentUser.equals(user))throw new RemoteException(403,"FORBIDDEN","현재 로그인한 사용자의 작업만 요청할 수 있습니다.");
  }
  public void signup(String id,String nickname,char[] password) throws IOException {
    request("POST","/auth/signup",new Signup(id,nickname,new String(password)),false);
  }
  public String login(String id,char[] password) throws IOException {
    token=null;
    Map<String,Object> result=object(request("POST","/auth/login",new Login(id,new String(password)),false));
    token=string(result,"token");currentUser=string(object(result.get("user")),"id");
    serverToday=LocalDate.parse(string(result,"serverToday"));synchronizedDate=true;
    return currentUser;
  }
  public void refreshSession() throws IOException {request("GET","/me",null,true);}
  public void logoutRemote() throws IOException {
    try {request("POST","/auth/logout",null,true);}
    catch(RemoteException ex){if(ex.status!=401)throw ex;}
    token=null;currentUser=null;pendingPhrase=null;pendingSave=null;createdDates.clear();
  }
  private Song song(Object value) {
    Map<String,Object> m=object(value);Set<String> members=new HashSet<>();
    for(Object member:list(m.get("members")))members.add((String)member);
    String id=string(m,"id");createdDates.put(id,LocalDate.parse(string(m,"createdDate")));
    return new Song(id,string(m,"title"),string(m,"ownerId"),string(m,"type").equals("SHARED"),integer(m,"bpm"),integer(m,"instrument"),members,integer(m,"version"));
  }
  @Override public List<Song> songs(String user) throws IOException {
    actor(user);List<Song> result=new ArrayList<>();for(Object item:list(request("GET","/songs",null,true)))result.add(song(item));return List.copyOf(result);
  }
  public Song create(String title,boolean shared,int bpm,int instrument) throws IOException {
    return song(request("POST","/songs",new CreateSong(title,shared?SongType.SHARED:SongType.PERSONAL,bpm,instrument),true));
  }
  public List<EnsembleData.Member> participants(String id) throws IOException {
    List<EnsembleData.Member> members=new ArrayList<>();
    for(Object item:list(request("GET","/songs/"+UUID.fromString(id)+"/members",null,true))){Map<String,Object> m=object(item);members.add(new EnsembleData.Member(string(m,"id"),string(m,"nickname"),string(m,"role").equals("OWNER")));}
    return List.copyOf(members);
  }
  private Phrase phrase(Object value) {
    Map<String,Object> m=object(value);
    if(integer(m,"schemaVersion")!=Api.SCHEMA)throw new IllegalArgumentException("기록 버전 오류");
    List<NoteEvent> notes=new ArrayList<>();List<RestEvent> rests=new ArrayList<>();List<ExpressionRange> expressions=new ArrayList<>();
    for(Object item:list(m.get("notes"))){Map<String,Object> n=object(item);notes.add(new NoteEvent(integer(n,"startTick"),integer(n,"durationTick"),integer(n,"pitch"),integer(n,"velocity"),Articulation.valueOf(string(n,"articulation"))));}
    for(Object item:list(m.get("rests"))){Map<String,Object> r=object(item);rests.add(new RestEvent(integer(r,"startTick"),integer(r,"durationTick")));}
    for(Object item:list(m.get("expressions"))){Map<String,Object> e=object(item);expressions.add(new ExpressionRange(integer(e,"startTick"),integer(e,"endTick"),ExpressionKind.valueOf(string(e,"kind")),integer(e,"startVelocity"),integer(e,"endVelocity")));}
    return new Phrase(string(m,"songId"),string(m,"authorId"),LocalDate.parse(string(m,"date")),integer(m,"version"),integer(m,"bpm"),integer(m,"instrument"),string(m,"memo"),notes,rests,expressions);
  }
  @Override public List<Phrase> phrases(String id) throws IOException {
    UUID.fromString(id);
    if(!createdDates.containsKey(id))songs(currentUser);
    LocalDate start=createdDates.getOrDefault(id,serverToday.minusDays(365));
    List<Phrase> result=new ArrayList<>();
    // Seven-day pages keep even 15 fully populated parts below the client response limit.
    while(!start.isAfter(serverToday)) {
      LocalDate end=start.plusDays(6).isAfter(serverToday)?serverToday:start.plusDays(6);
      for(Object item:list(request("GET","/songs/"+id+"/records?from="+start+"&to="+end,null,true)))result.add(phrase(item));
      start=end.plusDays(1);
    }
    return List.copyOf(result);
  }
  @Override public synchronized Phrase save(Phrase draft,int expected) throws IOException {
    actor(draft.author());UUID.fromString(draft.songId());
    if(!draft.equals(pendingPhrase)||pendingSave==null||pendingSave.expectedVersion()!=expected){pendingPhrase=draft;pendingSave=SaveBody.from(draft,UUID.randomUUID(),expected);}
    return phrase(request("PUT","/songs/"+draft.songId()+"/records/"+draft.date()+"/me",pendingSave,true));
  }
  @Override public Settings settings(String user) throws IOException {
    actor(user);Map<String,Object> m=object(request("GET","/me/settings",null,true));
    return new Settings(integer(m,"volume"),integer(m,"instrument"),bool(m,"countIn"),bool(m,"metronome"),integer(m,"countInBeats"));
  }
  @Override public void settings(String user,Settings value) throws IOException {actor(user);request("PUT","/me/settings",value,true);}
  @Override public Song updateSong(Song expected,String title,int bpm,int instrument,String user) throws IOException {
    actor(user);return song(request("PATCH","/songs/"+UUID.fromString(expected.id()),new UpdateSong(title,bpm,instrument,expected.version()),true));
  }
  @Override public void leave(String id,String user) throws IOException {actor(user);request("DELETE","/songs/"+UUID.fromString(id)+"/members/me",null,true);}
  @Override public String invite(String id,String user,Instant ignored) throws IOException {
    actor(user);return string(object(request("POST","/songs/"+UUID.fromString(id)+"/invites",null,true)),"code");
  }
  @Override public void redeem(String code,String user,Instant ignored) throws IOException {actor(user);song(request("POST","/invites/redeem",new Redeem(code),true));}
  @Override public Optional<Account> account(String id) throws IOException {throw new IOException("원격 인증 정보는 조회할 수 없습니다.");}
  @Override public void register(Account account) throws IOException {throw new IOException("원격 회원가입 API를 사용하세요.");}
  @Override public void createSong(Song song) throws IOException {throw new IOException("원격 곡 생성 API를 사용하세요.");}
  @Override public void join(String id,String user) throws IOException {throw new IOException("유효한 초대 코드로 참여하세요.");}
  @Override public void seedDemo(LocalDate ignored) throws IOException {throw new IOException("원격 모드에는 시연 계정을 자동 생성하지 않습니다.");}
}
