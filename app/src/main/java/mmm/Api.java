package mmm;

import java.time.*;
import java.util.*;
import mmm.Model.*;

/** Versioned HTTP contract. Public DTOs never contain salts, hashes or database credentials. */
public final class Api {
  private Api() {}
  public static final int SCHEMA = 2;
  public enum SongType { PERSONAL, SHARED }
  public record UserDto(String id, String nickname) {}
  public record Signup(String loginId, String nickname, String password) {
    @Override public String toString() { return "Signup[redacted]"; }
  }
  public record Login(String loginId, String password) {
    @Override public String toString() { return "Login[redacted]"; }
  }
  public record AuthResult(String token, UserDto user, LocalDate serverToday, Instant expiresAt) {
    @Override public String toString() { return "AuthResult[redacted]"; }
  }
  public record Me(UserDto user, LocalDate serverToday) {}
  public record CreateSong(String title, SongType type, int bpm, int instrument) {}
  public record UpdateSong(String title, int bpm, int instrument, Integer expectedVersion) {
    public UpdateSong {
      Objects.requireNonNull(expectedVersion,"expectedVersion이 필요합니다.");
      if(expectedVersion<1)throw new IllegalArgumentException("곡 버전을 확인하세요.");
    }
  }
  public record SongDto(String id, String title, String ownerId, SongType type, int bpm,
                        int instrument, int version, Set<String> members, LocalDate createdDate) {
    public SongDto { members = Set.copyOf(members); }
    public Song model() { return new Song(id, title, ownerId, type == SongType.SHARED, bpm, instrument, members, version); }
  }
  public record MemberDto(String id, String nickname, String role) {}
  public record InviteDto(String code, Instant expiresAt) {}
  public record Redeem(String code) {}
  public record ErrorDto(String code, String message) {}
  public record NoteDto(int startTick, int durationTick, int pitch, int velocity, Articulation articulation) {
    public NoteEvent model() { return new NoteEvent(startTick, durationTick, pitch, velocity, articulation); }
    public static NoteDto from(NoteEvent n) { return new NoteDto(n.start(), n.duration(), n.pitch(), n.velocity(), n.articulation()); }
  }
  public record RestDto(int startTick, int durationTick) {
    public RestEvent model() { return new RestEvent(startTick, durationTick); }
    public static RestDto from(RestEvent r) { return new RestDto(r.start(), r.duration()); }
  }
  public record ExpressionDto(int startTick, int endTick, ExpressionKind kind, int startVelocity, int endVelocity) {
    public ExpressionRange model() { return new ExpressionRange(startTick, endTick, kind, startVelocity, endVelocity); }
    public static ExpressionDto from(ExpressionRange e) { return new ExpressionDto(e.start(), e.end(), e.kind(), e.startVelocity(), e.endVelocity()); }
  }
  public record SaveBody(UUID requestId, Integer expectedVersion, String memo, int bpm, int instrument,
                         List<NoteDto> notes, List<RestDto> rests, List<ExpressionDto> expressions, int schemaVersion) {
    public SaveBody {
      Objects.requireNonNull(requestId, "requestId가 필요합니다.");
      Objects.requireNonNull(expectedVersion,"expectedVersion이 필요합니다.");
      if(expectedVersion<0)throw new IllegalArgumentException("기록 버전을 확인하세요.");
      notes = List.copyOf(notes); rests = List.copyOf(rests); expressions = List.copyOf(expressions);
      if (schemaVersion != SCHEMA) throw new IllegalArgumentException("지원하지 않는 기록 버전입니다.");
    }
    public Phrase model(String songId, String author, LocalDate date) {
      return new Phrase(songId, author, date, expectedVersion, bpm, instrument, memo,
          notes.stream().map(NoteDto::model).toList(), rests.stream().map(RestDto::model).toList(),
          expressions.stream().map(ExpressionDto::model).toList());
    }
    public static SaveBody from(Phrase p, UUID requestId, int expectedVersion) {
      return new SaveBody(requestId, expectedVersion, p.memo(), p.bpm(), p.instrument(),
          p.notes().stream().map(NoteDto::from).toList(), p.rests().stream().map(RestDto::from).toList(),
          p.expressions().stream().map(ExpressionDto::from).toList(), SCHEMA);
    }
  }
  public record PhraseDto(String songId, String authorId, LocalDate date, int version, String memo,
                          int bpm, int instrument, List<NoteDto> notes, List<RestDto> rests,
                          List<ExpressionDto> expressions, int schemaVersion) {
    public PhraseDto { notes=List.copyOf(notes); rests=List.copyOf(rests); expressions=List.copyOf(expressions); }
    public Phrase model() {
      return new SaveBody(UUID.randomUUID(), version, memo, bpm, instrument, notes, rests, expressions, schemaVersion)
          .model(songId, authorId, date);
    }
    public static PhraseDto from(Phrase p) {
      SaveBody body=SaveBody.from(p, UUID.randomUUID(), p.version());
      return new PhraseDto(p.songId(), p.author(), p.date(), p.version(), p.memo(), p.bpm(), p.instrument(),
          body.notes(), body.rests(), body.expressions(), SCHEMA);
    }
  }
}
