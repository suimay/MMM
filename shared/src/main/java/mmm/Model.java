package mmm;

import java.time.*;
import java.util.*;

/** Shared immutable data for drawing, editing, storage and MIDI. */
public final class Model {
  private Model() {}

  public static final int PPQ = 480, BAR = 1920, GRID = 120;
  public static final int MIN_PITCH = 0, MAX_PITCH = 127;
  /** Application instrument identifier for a General MIDI percussion kit. */
  public static final int DRUMS = 128;

  public enum Articulation { NORMAL, STACCATO }

  public enum ExpressionKind { CRESCENDO }

  public record NoteEvent(int start, int duration, int pitch, int velocity, Articulation articulation) {
    public NoteEvent(int start, int duration, int pitch, int velocity) {
      this(start, duration, pitch, velocity, Articulation.NORMAL);
    }

    public NoteEvent {
      Objects.requireNonNull(articulation);
      if (start < 0
          || start >= BAR
          || duration > BAR
          || duration < GRID
          || start % GRID != 0
          || duration % GRID != 0
          || start + duration > BAR)
        throw new IllegalArgumentException("음은 1/4박 단위이며 4박 안에 있어야 합니다.");
      if (pitch < MIN_PITCH || pitch > MAX_PITCH || velocity < 1 || velocity > 127)
        throw new IllegalArgumentException("음 높이 또는 세기가 범위를 벗어났습니다.");
    }

    public int gateDuration() {
      return articulation == Articulation.STACCATO ? duration / 2 : duration;
    }
  }

  public record RestEvent(int start, int duration) {
    public RestEvent {
      if (start < 0 || start >= BAR || duration < GRID || duration > BAR
          || start % GRID != 0 || duration % GRID != 0 || start + duration > BAR)
        throw new IllegalArgumentException("쉼은 1/4박 단위이며 4박 안에 있어야 합니다.");
    }
  }

  public record ExpressionRange(int start, int end, ExpressionKind kind,
                                int startVelocity, int endVelocity) {
    public ExpressionRange {
      Objects.requireNonNull(kind);
      if (start < 0 || end > BAR || start >= end || start % GRID != 0 || end % GRID != 0
          || startVelocity < 1 || endVelocity > 127 || startVelocity >= endVelocity)
        throw new IllegalArgumentException("크레센도 구간과 세기를 확인하세요. 시작 세기는 끝 세기보다 작아야 합니다.");
    }

    public int velocityAt(int tick) {
      return (int) Math.round(startVelocity + (endVelocity - startVelocity)
          * (tick - start) / (double) (end - start));
    }
  }

  public static boolean overlaps(int start, int end, int otherStart, int otherEnd) {
    return start < otherEnd && otherStart < end;
  }

  public static int effectiveVelocity(NoteEvent note, List<ExpressionRange> expressions) {
    for (ExpressionRange range : expressions)
      if (note.start() >= range.start() && note.start() < range.end())
        return range.velocityAt(note.start());
    return note.velocity();
  }

  public record Phrase(
      String songId,
      String author,
      LocalDate date,
      int version,
      int bpm,
      int instrument,
      String memo,
      List<NoteEvent> notes,
      List<RestEvent> rests,
      List<ExpressionRange> expressions) {
    public Phrase(String songId, String author, LocalDate date, int version, int bpm,
                  int instrument, String memo, List<NoteEvent> notes) {
      this(songId, author, date, version, bpm, instrument, memo, notes, List.of(), List.of());
    }

    public Phrase {
      Objects.requireNonNull(date);
      Objects.requireNonNull(songId);
      Objects.requireNonNull(author);
      notes = List.copyOf(notes);
      rests = List.copyOf(rests);
      expressions = List.copyOf(expressions);
      Objects.requireNonNull(memo);
      if (bpm < 40 || bpm > 200 || instrument < 0 || instrument > DRUMS || version < 0)
        throw new IllegalArgumentException("BPM은 40~200, 악기는 MIDI 범위여야 합니다.");
      if (memo.codePointCount(0, memo.length()) > 20)
        throw new IllegalArgumentException("메모는 20자까지입니다.");
      if (notes.size() > 128) throw new IllegalArgumentException("한 마디에는 최대 128개 음을 넣을 수 있습니다.");
      if (memo.indexOf('\n') >= 0 || memo.indexOf('\r') >= 0)
        throw new IllegalArgumentException("메모는 한 줄로 입력하세요.");
      validateTimeline(notes, rests, expressions);
    }

    public String key() {
      return songId + "/" + author + "/" + date;
    }

    public Phrase versioned(int value) {
      return new Phrase(songId, author, date, value, bpm, instrument, memo, notes, rests, expressions);
    }

    public int effectiveVelocity(NoteEvent note) {
      return Model.effectiveVelocity(note, expressions);
    }

    public boolean empty() {
      return notes.isEmpty() && rests.isEmpty();
    }

    public List<NoteEvent> visualNotes() {
      return notes.stream().map(n -> new NoteEvent(n.start(), n.duration(), n.pitch(),
          effectiveVelocity(n), n.articulation())).toList();
    }
  }

  public static void validateTimeline(List<NoteEvent> notes, List<RestEvent> rests,
                                      List<ExpressionRange> expressions) {
    if (rests.size() > 16 || expressions.size() > 16)
      throw new IllegalArgumentException("한 마디의 쉼 또는 표현 구간이 너무 많습니다.");
    for (int i = 0; i < rests.size(); i++) {
      RestEvent r = rests.get(i);
      for (NoteEvent n : notes)
        if (overlaps(r.start(), r.start() + r.duration(), n.start(), n.start() + n.duration()))
          throw new IllegalArgumentException("쉼과 음은 겹칠 수 없습니다.");
      for (int j = i + 1; j < rests.size(); j++) {
        RestEvent other = rests.get(j);
        if (overlaps(r.start(), r.start() + r.duration(), other.start(), other.start() + other.duration()))
          throw new IllegalArgumentException("쉼 구간은 서로 겹칠 수 없습니다.");
      }
    }
    for (int i = 0; i < expressions.size(); i++)
      for (int j = i + 1; j < expressions.size(); j++) {
        ExpressionRange a = expressions.get(i), b = expressions.get(j);
        if (overlaps(a.start(), a.end(), b.start(), b.end()))
          throw new IllegalArgumentException("표현 구간은 서로 겹칠 수 없습니다.");
      }
  }

  public record Song(
      String id,
      String name,
      String owner,
      boolean shared,
      int bpm,
      int instrument,
      Set<String> members,
      int version) {
    public Song(String id, String name, String owner, boolean shared, int bpm, int instrument, Set<String> members) {
      this(id, name, owner, shared, bpm, instrument, members, 0);
    }
    public Song {
      members = Set.copyOf(members);
      if (name.isBlank()
          || name.length() > 40
          || bpm < 40
          || bpm > 200
          || instrument < 0
          || instrument > DRUMS
          || !members.contains(owner) || version < 0)
        throw new IllegalArgumentException("곡명은 1~40자, BPM은 40~200입니다.");
    }

    @Override
    public String toString() {
      return name + (shared ? "  · 공동" : "  · 개인");
    }
  }

  public record Account(String id, String nickname, String salt, String hash) {}

  public record Settings(
      int volume, int instrument, boolean countIn, boolean metronome, int countInBeats) {
    public Settings(int volume, int instrument, boolean countIn) {
      this(volume, instrument, countIn, false, 4);
    }

    public Settings {
      if (volume < 0
          || volume > 100
          || instrument < 0
          || instrument > DRUMS
          || countInBeats < 1
          || countInBeats > 8) throw new IllegalArgumentException("설정 범위 오류");
    }
  }

  public static int snap(long tick) {
    return (int) (Math.round(tick / (double) GRID) * GRID);
  }

  public static LocalDate monday(LocalDate date) {
    return date.minusDays(date.getDayOfWeek().getValue() - 1);
  }
}
