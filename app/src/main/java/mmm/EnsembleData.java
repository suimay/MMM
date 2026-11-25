package mmm;

import java.time.*;
import java.util.*;
import mmm.Model.*;

/** Attribution and MIDI routing shared by the board, inspector and mixer. */
public final class EnsembleData {
  private EnsembleData() {}

  public record Member(String id, String nickname, boolean owner) {
    public String label() {
      return nickname + " · " + id;
    }
  }

  public static Phrase find(List<Phrase> records, String user, LocalDate date) {
    return records.stream()
        .filter(f -> f.author().equals(user) && f.date().equals(date))
        .findFirst()
        .orElse(null);
  }

  public static int channel(int index) {
    if (index < 0 || index >= 15) throw new IllegalArgumentException("합주는 최대 15파트입니다.");
    return index < 9 ? index : index + 1;
  }

  public static List<MidiEngine.Part> parts(
      List<Phrase> records,
      List<Member> members,
      Map<String, Integer> levels,
      LocalDate date,
      boolean week) {
    for (int level : levels.values())
      if (level < 0 || level > 100) throw new IllegalArgumentException("파트 음량은 0~100입니다.");
    List<MidiEngine.Part> parts = new ArrayList<>();
    LocalDate start = week ? Model.monday(date) : date;
    for (int i = 0; i < members.size(); i++) {
      String user = members.get(i).id();
      if (levels.getOrDefault(user, 0) <= 0) continue;
      for (int d = 0; d < (week ? 7 : 1); d++) {
        Phrase f = find(records, user, start.plusDays(d));
        if (f != null)
          parts.add(new MidiEngine.Part(f, d * Model.BAR, channel(i), levels.get(user)));
      }
    }
    return List.copyOf(parts);
  }

  public record AttributedNote(
      String author, LocalDate date, int instrument, int index, NoteEvent note) {}

  public static List<AttributedNote> notes(
      List<Phrase> records, String user, LocalDate date, boolean week) {
    LocalDate start = week ? Model.monday(date) : date;
    return records.stream()
        .filter(
            f ->
                f.author().equals(user)
                    && (week
                        ? !f.date().isBefore(start) && f.date().isBefore(start.plusDays(7))
                        : f.date().equals(date)))
        .sorted(Comparator.comparing(Phrase::date))
        .flatMap(
            f ->
                java.util.stream.IntStream.range(0, f.notes().size())
                    .mapToObj(
                        i ->
                            new AttributedNote(
                                f.author(), f.date(), f.instrument(), i, f.notes().get(i))))
        .toList();
  }
}
