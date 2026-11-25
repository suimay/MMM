package mmm;

import java.time.*;
import java.util.*;

/** Shared immutable data for drawing, editing, storage and MIDI. */
public final class Model {
  private Model() {}

  public static final int PPQ = 480, BAR = 1920, GRID = 120;

  public record NoteEvent(int start, int duration, int pitch, int velocity) {
    public NoteEvent {
      if (start < 0
          || start >= BAR
          || duration > BAR
          || duration < GRID
          || start % GRID != 0
          || duration % GRID != 0
          || start + duration > BAR)
        throw new IllegalArgumentException("음은 1/4박 단위이며 4박 안에 있어야 합니다.");
      if (pitch < 48 || pitch > 84 || velocity < 1 || velocity > 127)
        throw new IllegalArgumentException("음 높이 또는 세기가 범위를 벗어났습니다.");
    }
  }

  public record Phrase(
      String songId,
      String author,
      LocalDate date,
      int version,
      int bpm,
      int instrument,
      String memo,
      List<NoteEvent> notes) {
    public Phrase {
      Objects.requireNonNull(date);
      Objects.requireNonNull(songId);
      Objects.requireNonNull(author);
      notes = List.copyOf(notes);
      Objects.requireNonNull(memo);
      if (bpm < 40 || bpm > 200 || instrument < 0 || instrument > 127 || version < 0)
        throw new IllegalArgumentException("BPM은 40~200, 악기는 MIDI 범위여야 합니다.");
      if (memo.codePointCount(0, memo.length()) > 20)
        throw new IllegalArgumentException("메모는 20자까지입니다.");
      if (notes.size() > 128) throw new IllegalArgumentException("한 마디에는 최대 128개 음을 넣을 수 있습니다.");
    }

    public String key() {
      return songId + "/" + author + "/" + date;
    }

    public Phrase versioned(int value) {
      return new Phrase(songId, author, date, value, bpm, instrument, memo, notes);
    }
  }

  public record Song(
      String id,
      String name,
      String owner,
      boolean shared,
      int bpm,
      int instrument,
      Set<String> members) {
    public Song {
      members = Set.copyOf(members);
      if (name.isBlank()
          || name.length() > 40
          || bpm < 40
          || bpm > 200
          || instrument < 0
          || instrument > 127
          || !members.contains(owner))
        throw new IllegalArgumentException("곡명은 1~40자, BPM은 40~200입니다.");
    }

    @Override
    public String toString() {
      return name + (shared ? "  · 공동 모의" : "  · 개인");
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
          || instrument > 127
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
