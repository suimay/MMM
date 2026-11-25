package mmm.server;

import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.security.SecureRandom;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import mmm.*;
import mmm.Api.*;
import mmm.Model.*;

/** Each mutation holds the song row lock through authorization and its complete transaction. */
@Service
@Transactional
public class CollaborationService {
  private final JdbcTemplate db;
  private final Clock clock;
  private final ObjectMapper json;
  private final Duration sessionDuration;
  private static final ZoneId SEOUL=ZoneId.of("Asia/Seoul");
  private static final PasswordCodec.Digest DUMMY=PasswordCodec.create("invalid-password-check".toCharArray());
  public CollaborationService(JdbcTemplate db,Clock clock,ObjectMapper json,
      @Value("${mmm.session-duration:PT12H}") Duration sessionDuration) {
    this.db=db;this.clock=clock;this.json=json;this.sessionDuration=sessionDuration;
    if(sessionDuration.isNegative()||sessionDuration.isZero()||sessionDuration.compareTo(Duration.ofDays(30))>0)
      throw new IllegalArgumentException("세션 만료 설정 범위 오류");
  }
  public LocalDate today(){return LocalDate.now(clock.withZone(SEOUL));}
  private Timestamp now(){return Timestamp.from(clock.instant());}

  public UserDto signup(Signup request) {
    ActivityLog.attemptedUser(request.loginId());
    char[] pass=Objects.requireNonNull(request.password()).toCharArray();
    try {
      PasswordCodec.validate(request.loginId(),request.nickname(),pass);
      PasswordCodec.Digest digest=PasswordCodec.create(pass);
      db.update("INSERT INTO app_users(login_id,nickname,password_salt,password_hash,created_at) VALUES (?,?,?,?,?)",
          request.loginId(),request.nickname(),digest.salt(),digest.hash(),now());
      db.update("INSERT INTO user_settings VALUES (?,80,0,true,false,4)",request.loginId());
      ActivityLog.user(request.loginId());ActivityLog.afterCommit(true,null,false);
      return new UserDto(request.loginId(),request.nickname());
    } finally {Arrays.fill(pass,'\0');}
  }
  public AuthResult login(Login request) {
    ActivityLog.attemptedUser(request.loginId());
    if(request.loginId()==null||request.password()==null||request.password().length()>256)throw ApiFailure.invalid("아이디와 비밀번호를 확인하세요.");
    List<Map<String,Object>> rows=db.queryForList("SELECT * FROM app_users WHERE login_id=?",request.loginId());
    Map<String,Object> row=rows.isEmpty()?Map.of():rows.get(0);
    char[] password=request.password().toCharArray(); boolean valid;
    try {valid=PasswordCodec.verify(password,(String)row.getOrDefault("password_salt",DUMMY.salt()),(String)row.getOrDefault("password_hash",DUMMY.hash()));}
    finally {Arrays.fill(password,'\0');}
    if(!valid||rows.isEmpty())throw new ApiFailure(401,"INVALID_CREDENTIALS","아이디 또는 비밀번호를 확인하세요.");
    ActivityLog.user(request.loginId());
    byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
    String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Instant expires=clock.instant().plus(sessionDuration);
    db.update("INSERT INTO auth_sessions(token_hash,user_id,expires_at) VALUES (?,?,?)",PasswordCodec.sha256(token),request.loginId(),Timestamp.from(expires));
    ActivityLog.afterCommit(true,null,false);
    return new AuthResult(token,new UserDto(request.loginId(),(String)row.get("nickname")),today(),expires);
  }
  public UserDto authenticate(String authorization) {
    String hash=tokenHash(authorization);
    List<UserDto> rows=db.query("SELECT u.login_id,u.nickname FROM auth_sessions s JOIN app_users u ON u.login_id=s.user_id WHERE s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>?",
        (rs,n)->new UserDto(rs.getString(1),rs.getString(2)),hash,now());
    if(rows.isEmpty())throw ApiFailure.unauthorized();ActivityLog.user(rows.get(0).id());return rows.get(0);
  }
  private String tokenHash(String authorization) {
    if(authorization==null||!authorization.matches("Bearer [A-Za-z0-9_-]{43}"))throw ApiFailure.unauthorized();
    return PasswordCodec.sha256(authorization.substring(7));
  }
  public void logout(String authorization) {
    authenticate(authorization);
    db.update("UPDATE auth_sessions SET revoked_at=? WHERE token_hash=?",now(),tokenHash(authorization));
    ActivityLog.afterCommit(true,null,false);
  }
  private record SongRow(UUID id,String title,String owner,SongType type,int bpm,int instrument,int version,Instant created) {}
  private static final RowMapper<SongRow> SONG=(rs,n)->new SongRow(rs.getObject("id",UUID.class),rs.getString("title"),rs.getString("owner_id"),
      SongType.valueOf(rs.getString("type")),rs.getInt("bpm"),rs.getInt("instrument"),rs.getInt("version"),rs.getTimestamp("created_at").toInstant());
  private SongRow song(UUID id,boolean lock) {
    List<SongRow> rows=db.query("SELECT * FROM songs WHERE id=?"+(lock?" FOR UPDATE":""),SONG,id);
    if(rows.isEmpty())throw ApiFailure.missing();return rows.get(0);
  }
  private SongRow access(String actor,UUID id,boolean lock) {
    SongRow row=song(id,lock);
    Integer count=db.queryForObject("SELECT count(*) FROM memberships WHERE song_id=? AND user_id=? AND active",Integer.class,id,actor);
    if(count==null||count==0)throw ApiFailure.forbidden();return row;
  }
  private SongDto dto(SongRow row) {
    Set<String> members=new LinkedHashSet<>(db.queryForList("SELECT user_id FROM memberships WHERE song_id=? AND active ORDER BY user_id",String.class,row.id()));
    return new SongDto(row.id().toString(),row.title(),row.owner(),row.type(),row.bpm(),row.instrument(),row.version(),members,row.created().atZone(SEOUL).toLocalDate());
  }
  public List<SongDto> songs(String actor) {
    return db.query("SELECT s.* FROM songs s JOIN memberships m ON m.song_id=s.id WHERE m.user_id=? AND m.active ORDER BY s.title,s.id",SONG,actor)
        .stream().map(this::dto).toList();
  }
  public SongDto create(String actor,CreateSong body) {
    if(body.type()==null)throw ApiFailure.invalid("곡 유형을 선택하세요.");
    UUID id=UUID.randomUUID();
    ActivityLog.song(id);
    new Song(id.toString(),Objects.requireNonNull(body.title()).strip(),actor,body.type()==SongType.SHARED,body.bpm(),body.instrument(),Set.of(actor),1);
    db.update("INSERT INTO songs VALUES (?,?,?,?,?,?,1,?)",id,actor,body.title().strip(),body.type().name(),body.bpm(),body.instrument(),now());
    db.update("INSERT INTO memberships VALUES (?,?,'OWNER',true,?)",id,actor,now());
    ActivityLog.afterCommit(true,1,false);
    return dto(song(id,false));
  }
  public SongDto update(String actor,UUID id,UpdateSong body) {
    ActivityLog.expectedVersion(body.expectedVersion());
    SongRow row=access(actor,id,true);
    ActivityLog.version(row.version());
    if(!row.owner().equals(actor))throw ApiFailure.forbidden();
    if(row.version()!=body.expectedVersion())throw ApiFailure.conflict("곡 설정이 바뀌었습니다. 최신 곡을 다시 불러오세요.");
    new Song(id.toString(),Objects.requireNonNull(body.title()).strip(),actor,row.type()==SongType.SHARED,body.bpm(),body.instrument(),Set.of(actor),row.version()+1);
    db.update("UPDATE songs SET title=?,bpm=?,instrument=?,version=version+1 WHERE id=?",body.title().strip(),body.bpm(),body.instrument(),id);
    ActivityLog.afterCommit(true,row.version()+1,false);
    return dto(song(id,false));
  }
  public List<MemberDto> members(String actor,UUID id) {
    access(actor,id,false);
    return db.query("SELECT u.login_id,u.nickname,m.role FROM memberships m JOIN app_users u ON u.login_id=m.user_id WHERE m.song_id=? AND m.active ORDER BY m.role DESC,u.login_id",
        (rs,n)->new MemberDto(rs.getString(1),rs.getString(2),rs.getString(3)),id);
  }
  public InviteDto invite(String actor,UUID id) {
    SongRow row=access(actor,id,true);
    if(!row.owner().equals(actor)||row.type()!=SongType.SHARED)throw ApiFailure.forbidden();
    String code="MMM-"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT);
    Instant expires=clock.instant().plus(Duration.ofDays(7));
    db.update("INSERT INTO invite_codes VALUES (?,?,?,true)",PasswordCodec.sha256(code),id,Timestamp.from(expires));
    ActivityLog.afterCommit(true,null,false);
    return new InviteDto(code,expires);
  }
  public SongDto redeem(String actor,Redeem body) {
    if(body.code()==null||body.code().length()>80)throw ApiFailure.invalid("초대 코드를 확인하세요.");
    String hash=PasswordCodec.sha256(body.code().strip());
    List<UUID> ids=db.queryForList("SELECT song_id FROM invite_codes WHERE code_hash=? AND active",UUID.class,hash);
    if(ids.isEmpty())throw new ApiFailure(400,"INVALID_INVITE","유효하지 않은 초대 코드입니다.");
    UUID id=ids.get(0);SongRow row=song(id,true);
    ActivityLog.song(id);
    Instant expires=db.queryForObject("SELECT expires_at FROM invite_codes WHERE code_hash=? AND active",Timestamp.class,hash).toInstant();
    if(!clock.instant().isBefore(expires))throw new ApiFailure(400,"EXPIRED_INVITE","만료된 초대 코드입니다.");
    if(row.type()!=SongType.SHARED)throw ApiFailure.forbidden();
    List<String> active=db.queryForList("SELECT user_id FROM memberships WHERE song_id=? AND active",String.class,id);
    if(active.contains(actor))throw new ApiFailure(409,"ALREADY_MEMBER","이미 참여 중인 곡입니다.");
    if(active.size()>=15)throw new ApiFailure(409,"MEMBER_LIMIT","합주는 최대 15명까지 지원합니다.");
    db.update("INSERT INTO memberships VALUES (?,?,'MEMBER',true,?) ON CONFLICT(song_id,user_id) DO UPDATE SET active=true,joined_at=EXCLUDED.joined_at",id,actor,now());
    ActivityLog.afterCommit(true,null,false);
    return dto(song(id,false));
  }
  public void leave(String actor,UUID id) {
    SongRow row=access(actor,id,true);
    if(row.owner().equals(actor))throw ApiFailure.forbidden();
    db.update("UPDATE memberships SET active=false WHERE song_id=? AND user_id=?",id,actor);
    ActivityLog.afterCommit(true,null,false);
  }
  public Settings settings(String actor) {
    return db.queryForObject("SELECT * FROM user_settings WHERE user_id=?",(rs,n)->new Settings(rs.getInt("volume"),rs.getInt("instrument"),rs.getBoolean("count_in"),rs.getBoolean("metronome"),rs.getInt("count_in_beats")),actor);
  }
  public Settings settings(String actor,Settings body) {
    db.update("UPDATE user_settings SET volume=?,instrument=?,count_in=?,metronome=?,count_in_beats=? WHERE user_id=?",
        body.volume(),body.instrument(),body.countIn(),body.metronome(),body.countInBeats(),actor);
    ActivityLog.afterCommit(true,null,false);
    return body;
  }
  private Phrase readPhrase(ResultSet rs,int ignored) throws SQLException {
    UUID id=rs.getObject("id",UUID.class);
    List<NoteEvent> notes=db.query("SELECT * FROM notes WHERE record_id=? ORDER BY order_no",(r,n)->new NoteEvent(r.getInt("start_tick"),r.getInt("duration_tick"),r.getInt("pitch"),r.getInt("velocity"),Articulation.valueOf(r.getString("articulation"))),id);
    List<RestEvent> rests=db.query("SELECT * FROM rests WHERE record_id=? ORDER BY order_no",(r,n)->new RestEvent(r.getInt("start_tick"),r.getInt("duration_tick")),id);
    List<ExpressionRange> expressions=db.query("SELECT * FROM expression_ranges WHERE record_id=? ORDER BY order_no",(r,n)->new ExpressionRange(r.getInt("start_tick"),r.getInt("end_tick"),ExpressionKind.valueOf(r.getString("kind")),r.getInt("start_velocity"),r.getInt("end_velocity")),id);
    return new Phrase(rs.getObject("song_id").toString(),rs.getString("author_id"),rs.getObject("record_date",LocalDate.class),rs.getInt("version"),rs.getInt("bpm"),rs.getInt("instrument"),rs.getString("memo"),notes,rests,expressions);
  }
  @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public List<PhraseDto> records(String actor,UUID id,LocalDate from,LocalDate to) {
    access(actor,id,false);
    if(from==null||to==null||to.isBefore(from)||ChronoUnit.DAYS.between(from,to)>365)throw ApiFailure.invalid("조회 범위는 1~366일입니다.");
    return db.query("SELECT * FROM bar_records WHERE song_id=? AND record_date BETWEEN ? AND ? ORDER BY record_date,author_id",this::readPhrase,id,from,to)
        .stream().map(PhraseDto::from).toList();
  }
  public PhraseDto save(String actor,UUID songId,LocalDate date,SaveBody body) {
    ActivityLog.record(date,body.requestId(),body.expectedVersion(),body.notes().size(),body.rests().size(),body.expressions().size());
    ActivityLog.event(body.expectedVersion()==0?ActivityLog.Event.RECORD_CREATE:ActivityLog.Event.RECORD_UPDATE);
    SongRow song=access(actor,songId,true);
    String fingerprint=PasswordCodec.sha256(songId+"\n"+date+"\n"+WireJson.write(body));
    List<Map<String,Object>> previous=db.queryForList("SELECT content_hash,response_json::text FROM save_requests WHERE user_id=? AND request_id=?",actor,body.requestId());
    if(!previous.isEmpty()) {
      Map<String,Object> result=previous.get(0);
      if(!fingerprint.equals(result.get("content_hash")))throw new ApiFailure(409,"REQUEST_ID_REUSED","같은 요청 식별자로 다른 내용을 저장할 수 없습니다.");
      try {
        PhraseDto saved=json.readValue((String)result.get("response_json"),PhraseDto.class);
        ActivityLog.event(ActivityLog.Event.RECORD_REPLAY);ActivityLog.afterCommit(false,saved.version(),true);
        return saved;
      }
      catch(Exception ex){throw new IllegalStateException("저장 응답 복원 실패",ex);}
    }
    if(!date.equals(today()))throw new ApiFailure(403,"TODAY_ONLY","서버의 오늘 기록만 저장할 수 있습니다. 초안을 유지했습니다.");
    Phrase draft=body.model(songId.toString(),actor,date);
    if(draft.empty())throw ApiFailure.invalid("음 또는 명시적인 쉼을 입력하세요.");
    if(song.type()==SongType.SHARED&&draft.bpm()!=song.bpm())throw ApiFailure.invalid("공동 곡은 곡의 공통 BPM을 사용합니다.");
    List<Map<String,Object>> current=db.queryForList("SELECT id,version FROM bar_records WHERE song_id=? AND author_id=? AND record_date=?",songId,actor,date);
    int version=current.isEmpty()?0:((Number)current.get(0).get("version")).intValue();
    ActivityLog.version(version);
    if(version!=body.expectedVersion())throw ApiFailure.conflict("다른 기기에서 저장본이 바뀌었습니다. 초안을 유지했습니다. 최신 저장본을 확인하세요.");
    UUID id=current.isEmpty()?UUID.randomUUID():(UUID)current.get(0).get("id");
    if(current.isEmpty())db.update("INSERT INTO bar_records VALUES (?,?,?,?,?,?,?,?,2)",id,songId,actor,date,draft.memo(),draft.bpm(),draft.instrument(),version+1);
    else db.update("UPDATE bar_records SET memo=?,bpm=?,instrument=?,version=version+1 WHERE id=?",draft.memo(),draft.bpm(),draft.instrument(),id);
    db.update("DELETE FROM notes WHERE record_id=?",id);db.update("DELETE FROM rests WHERE record_id=?",id);db.update("DELETE FROM expression_ranges WHERE record_id=?",id);
    for(int i=0;i<draft.notes().size();i++) {
      NoteEvent n=draft.notes().get(i);
      db.update("INSERT INTO notes VALUES (?,?,?,?,?,?,?)",id,i,n.start(),n.duration(),n.pitch(),n.velocity(),n.articulation().name());
    }
    for(int i=0;i<draft.rests().size();i++) {RestEvent r=draft.rests().get(i);db.update("INSERT INTO rests VALUES (?,?,?,?)",id,i,r.start(),r.duration());}
    for(int i=0;i<draft.expressions().size();i++) {ExpressionRange e=draft.expressions().get(i);db.update("INSERT INTO expression_ranges VALUES (?,?,?,?,?,?,?)",id,i,e.start(),e.end(),e.kind().name(),e.startVelocity(),e.endVelocity());}
    PhraseDto saved=PhraseDto.from(draft.versioned(version+1));
    try {db.update("INSERT INTO save_requests VALUES (?,?,?,?,?,?::jsonb)",actor,body.requestId(),fingerprint,id,version+1,json.writeValueAsString(saved));}
    catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalStateException("저장 응답 변환 실패",ex);}
    ActivityLog.afterCommit(true,version+1,false);
    return saved;
  }
}
