package mmm;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.*;

public final class EmotionCatalog {
  private static final String RESOURCE_ROOT = "/mmm/emotion/";
  private static final String TAG_HEADER =
      "id\tlabel_ko\tlabel_en\tdimension\tdefinition_ko\tavailability";
  private static final String SOURCE_HEADER =
      "id\tauthors\tyear\ttitle\tdoi\tparticipants_n\ttask\tscope\tlimitations";
  private static final String EVIDENCE_HEADER =
      "id\tsource_id\tchord_quality\tmeasure\tmean\tsd\tscale_min\tscale_max\taggregation\tlocator";
  private static final String CHORD_HEADER =
      "id\tsymbol\tquality\tdisplay_name\tmidi_notes\tinstrument_program\tbpm\tduration_ticks\tvelocity"
          + "\tinversion\tvoicing\tstimulus_context\tvalidation_status\tequivalence_to_research_stimulus";
  private static final String LINK_HEADER =
      "chord_id\ttag_id\tevidence_id\tsupport_status\tapplication_status";
  private static final Set<String> PROPERTY_KEYS =
      Set.of("schema_version", "dataset_kind", "last_reviewed", "listening_validation");
  private static final Set<String> DIMENSIONS =
      Set.of(
          "happiness_joy",
          "melancholy_sadness",
          "nostalgia_longing",
          "tension",
          "valence",
          "tenderness",
          "unmeasured");
  private static final Map<String, List<Integer>> ROOT_POSITION_INTERVALS =
      Map.of(
          "major", List.of(0, 4, 7),
          "minor", List.of(0, 3, 7),
          "major_seventh", List.of(0, 4, 7, 11),
          "minor_seventh", List.of(0, 3, 7, 10),
          "augmented", List.of(0, 4, 8),
          "diminished", List.of(0, 3, 6),
          "dominant_seventh", List.of(0, 4, 7, 10));

  public record Metadata(
      String schemaVersion,
      String datasetKind,
      LocalDate lastReviewed,
      String listeningValidation) {}

  public record Tag(
      String id,
      String labelKo,
      String labelEn,
      String dimension,
      String definitionKo,
      String availability) {}

  public record Source(
      String id,
      String authors,
      int year,
      String title,
      String doi,
      Integer participantsN,
      String task,
      String scope,
      String limitations) {}

  public record Evidence(
      String id,
      String sourceId,
      String chordQuality,
      String measure,
      Double mean,
      Double sd,
      Double scaleMin,
      Double scaleMax,
      String aggregation,
      String locator) {}

  public record Chord(
      String id,
      String symbol,
      String quality,
      String displayName,
      List<Integer> notes,
      int program,
      int bpm,
      int durationTicks,
      int velocity,
      String inversion,
      String voicing,
      String stimulusContext,
      String validationStatus,
      String equivalenceToResearchStimulus) {
    public Chord {
      notes = List.copyOf(notes);
    }
  }

  public record Link(
      String chordId,
      String tagId,
      String evidenceId,
      String supportStatus,
      String applicationStatus) {}

  private final Metadata metadata;
  private final List<Tag> tags;
  private final List<Source> sources;
  private final List<Evidence> evidence;
  private final List<Chord> chords;
  private final List<Link> links;
  private final Map<String, Tag> tagIndex;
  private final Map<String, Source> sourceIndex;
  private final Map<String, Evidence> evidenceIndex;
  private final Map<String, Chord> chordIndex;

  private EmotionCatalog(
      Metadata metadata,
      List<Tag> tags,
      List<Source> sources,
      List<Evidence> evidence,
      List<Chord> chords,
      List<Link> links)
      throws IOException {
    this.metadata = metadata;
    this.tags = List.copyOf(tags);
    this.sources = List.copyOf(sources);
    this.evidence = List.copyOf(evidence);
    this.chords = List.copyOf(chords);
    this.links = List.copyOf(links);
    tagIndex = index(tags, Tag::id, "tags.tsv");
    sourceIndex = index(sources, Source::id, "sources.tsv");
    evidenceIndex = index(evidence, Evidence::id, "evidence.tsv");
    chordIndex = index(chords, Chord::id, "chords.tsv");
    validate();
  }

  public static EmotionCatalog load() throws IOException {
    return loadFrom(
        file -> {
          InputStream stream = EmotionCatalog.class.getResourceAsStream(RESOURCE_ROOT + file);
          if (stream == null) throw new IOException("감정 카탈로그 리소스가 없습니다: " + file);
          return stream;
        });
  }

  static EmotionCatalog load(Path directory) throws IOException {
    Objects.requireNonNull(directory, "directory");
    return loadFrom(file -> Files.newInputStream(directory.resolve(file)));
  }

  public Metadata metadata() {
    return metadata;
  }

  public List<Tag> tags() {
    return tags;
  }

  public List<Source> sources() {
    return sources;
  }

  public List<Evidence> evidence() {
    return evidence;
  }

  public List<Chord> chords() {
    return chords;
  }

  public List<Link> links() {
    return links;
  }

  public Tag tag(String id) {
    return lookup(tagIndex, id, "태그");
  }

  public Source source(String id) {
    return lookup(sourceIndex, id, "출처");
  }

  public Evidence evidence(String id) {
    return lookup(evidenceIndex, id, "근거");
  }

  public Chord chord(String id) {
    return lookup(chordIndex, id, "화음");
  }

  public List<Chord> candidates(String tagId) {
    if (tagId == null || tagId.isBlank()) return chords;
    Tag tag = tagIndex.get(tagId);
    if (tag == null || !tag.availability().equals("candidate")) return List.of();
    Set<String> chordIds = new HashSet<>();
    for (Link link : links)
      if (link.tagId().equals(tagId) && usable(link)) chordIds.add(link.chordId());
    return chords.stream().filter(chord -> chordIds.contains(chord.id())).toList();
  }

  public List<Link> linksFor(String chordId) {
    return links.stream().filter(link -> link.chordId().equals(chordId)).toList();
  }

  public List<Tag> tagsFor(String chordId) {
    Set<String> tagIds = new HashSet<>();
    for (Link link : linksFor(chordId)) if (usable(link)) tagIds.add(link.tagId());
    return tags.stream().filter(tag -> tagIds.contains(tag.id())).toList();
  }

  public List<Model.NoteEvent> previewNotes(String chordId) {
    Chord chord = chord(chordId);
    return chord.notes().stream()
        .map(pitch -> new Model.NoteEvent(0, chord.durationTicks(), pitch, chord.velocity()))
        .toList();
  }

  private static boolean usable(Link link) {
    return link.supportStatus().equals("direct_measure")
        && link.applicationStatus().equals("candidate");
  }

  private void validate() throws IOException {
    for (Tag tag : tags) {
      require(
          Set.of("candidate", "pending_validation").contains(tag.availability()),
          "tags.tsv: 지원하지 않는 availability: " + tag.id());
      require(DIMENSIONS.contains(tag.dimension()), "tags.tsv: 알 수 없는 차원: " + tag.id());
      require(
          !tag.availability().equals("candidate") || !tag.dimension().equals("unmeasured"),
          "tags.tsv: 측정되지 않은 차원은 추천 후보가 될 수 없습니다: " + tag.id());
    }
    for (Source source : sources) {
      require(
          source.year() >= 1000 && source.year() <= 9999,
          "sources.tsv: 발행 연도 범위 오류: " + source.id());
      require(source.participantsN() > 0, "sources.tsv: 참가자 수 범위 오류: " + source.id());
    }
    for (Evidence item : evidence) {
      require(
          sourceIndex.containsKey(item.sourceId()),
          "evidence.tsv: 존재하지 않는 출처: " + item.id());
      require(
          ROOT_POSITION_INTERVALS.containsKey(item.chordQuality()),
          "evidence.tsv: 알 수 없는 화음 종류: " + item.id());
      require(
          DIMENSIONS.contains(item.measure()) && !item.measure().equals("unmeasured"),
          "evidence.tsv: 알 수 없는 측정 차원: " + item.id());
      require(item.scaleMin() < item.scaleMax(), "evidence.tsv: 척도 범위 오류: " + item.id());
      require(
          item.mean() >= item.scaleMin() && item.mean() <= item.scaleMax(),
          "evidence.tsv: 평균이 원문 척도 범위를 벗어났습니다: " + item.id());
      require(item.sd() >= 0, "evidence.tsv: 음수 표준편차: " + item.id());
    }
    for (Chord chord : chords) {
      require(
          chord.program() >= 0 && chord.program() <= 127,
          "chords.tsv: MIDI 악기 범위 오류: " + chord.id());
      require(
          chord.bpm() >= 40 && chord.bpm() <= 200,
          "chords.tsv: BPM 범위 오류: " + chord.id());
      require(chord.inversion().equals("root_position"), "chords.tsv: 지원하지 않는 전위: " + chord.id());
      require(chord.voicing().equals("close"), "chords.tsv: 지원하지 않는 음 배치: " + chord.id());
      require(
          chord.stimulusContext().equals("standalone"),
          "chords.tsv: 단일 화음 맥락이 필요합니다: " + chord.id());
      require(
          chord.validationStatus().equals("not_validated"),
          "chords.tsv: 초기 샘플의 청취 검증 상태를 명시해야 합니다: " + chord.id());
      require(
          chord.equivalenceToResearchStimulus().equals("not_equivalent"),
          "chords.tsv: 앱 샘플을 연구 자극과 동일하다고 표시할 수 없습니다: " + chord.id());
      List<Integer> intervals = ROOT_POSITION_INTERVALS.get(chord.quality());
      require(intervals != null, "chords.tsv: 알 수 없는 화음 종류: " + chord.id());
      require(!chord.notes().isEmpty(), "chords.tsv: 화음 음이 없습니다: " + chord.id());
      int root = chord.notes().getFirst();
      require(
          chord.notes().stream().map(pitch -> pitch - root).toList().equals(intervals),
          "chords.tsv: 화음 종류·기본위치·밀집배치와 MIDI 음이 다릅니다: " + chord.id());
      try {
        for (int pitch : chord.notes())
          new Model.NoteEvent(0, chord.durationTicks(), pitch, chord.velocity());
      } catch (IllegalArgumentException ex) {
        throw new IOException("chords.tsv: 재생할 수 없는 음: " + chord.id(), ex);
      }
    }
    Set<List<String>> seenLinks = new HashSet<>();
    Set<String> linkedTags = new HashSet<>();
    for (Link link : links) {
      String description = link.chordId() + "/" + link.tagId() + "/" + link.evidenceId();
      require(
          seenLinks.add(List.of(link.chordId(), link.tagId(), link.evidenceId())),
          "links.tsv: 중복 연결: " + description);
      Chord chord = chordIndex.get(link.chordId());
      Tag tag = tagIndex.get(link.tagId());
      Evidence item = evidenceIndex.get(link.evidenceId());
      require(chord != null, "links.tsv: 존재하지 않는 화음: " + description);
      require(tag != null, "links.tsv: 존재하지 않는 태그: " + description);
      require(item != null, "links.tsv: 존재하지 않는 근거: " + description);
      require(usable(link), "links.tsv: 지원하지 않는 근거·적용 상태: " + description);
      require(
          tag.availability().equals("candidate"),
          "links.tsv: 검증 대기 태그를 추천에 연결할 수 없습니다: " + description);
      require(
          chord.quality().equals(item.chordQuality()),
          "links.tsv: 화음과 근거의 화음 종류가 다릅니다: " + description);
      require(
          tag.dimension().equals(item.measure()),
          "links.tsv: 태그와 근거의 측정 차원이 다릅니다: " + description);
      linkedTags.add(tag.id());
    }
    for (Tag tag : tags)
      require(
          !tag.availability().equals("candidate") || linkedTags.contains(tag.id()),
          "tags.tsv: 추천 후보에 연결된 근거가 없습니다: " + tag.id());
  }

  private static EmotionCatalog loadFrom(ResourceSource source) throws IOException {
    Metadata metadata = readMetadata(source);
    List<Tag> tags = new ArrayList<>();
    for (Row row : table(source, "tags.tsv", TAG_HEADER))
      tags.add(new Tag(row.value(0), row.value(1), row.value(2), row.value(3), row.value(4), row.value(5)));
    List<Source> sources = new ArrayList<>();
    for (Row row : table(source, "sources.tsv", SOURCE_HEADER))
      sources.add(
          new Source(
              row.value(0), row.value(1), row.integer(2), row.value(3), row.value(4),
              row.integer(5), row.value(6), row.value(7), row.value(8)));
    List<Evidence> evidence = new ArrayList<>();
    for (Row row : table(source, "evidence.tsv", EVIDENCE_HEADER))
      evidence.add(
          new Evidence(
              row.value(0), row.value(1), row.value(2), row.value(3), row.number(4),
              row.number(5), row.number(6), row.number(7), row.value(8), row.value(9)));
    List<Chord> chords = new ArrayList<>();
    for (Row row : table(source, "chords.tsv", CHORD_HEADER))
      chords.add(
          new Chord(
              row.value(0), row.value(1), row.value(2), row.value(3), row.notes(4),
              row.integer(5), row.integer(6), row.integer(7), row.integer(8), row.value(9),
              row.value(10), row.value(11), row.value(12), row.value(13)));
    List<Link> links = new ArrayList<>();
    for (Row row : table(source, "links.tsv", LINK_HEADER))
      links.add(new Link(row.value(0), row.value(1), row.value(2), row.value(3), row.value(4)));
    return new EmotionCatalog(metadata, tags, sources, evidence, chords, links);
  }

  private static Metadata readMetadata(ResourceSource source) throws IOException {
    String file = "catalog.properties";
    List<String> lines = lines(source, file);
    Map<String, String> properties = new HashMap<>();
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i).strip();
      if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue;
      int separator = line.indexOf('=');
      require(separator > 0, file + ":" + (i + 1) + ": key=value 형식이 필요합니다.");
      String key = line.substring(0, separator).strip();
      String value = line.substring(separator + 1).strip();
      require(PROPERTY_KEYS.contains(key), file + ": 알 수 없는 속성: " + key);
      require(!value.isEmpty(), file + ": 비어 있는 속성: " + key);
      require(properties.putIfAbsent(key, value) == null, file + ": 중복 속성: " + key);
    }
    require(properties.keySet().equals(PROPERTY_KEYS), file + ": 필수 속성이 없습니다.");
    require(properties.get("schema_version").equals("1.0.0"), file + ": 지원하지 않는 스키마 버전입니다.");
    require(properties.get("dataset_kind").equals("literature_seed"), file + ": 지원하지 않는 데이터 종류입니다.");
    require(
        properties.get("listening_validation").equals("not_started"),
        file + ": 초기 문헌 데이터의 청취 검증 상태가 올바르지 않습니다.");
    try {
      return new Metadata(
          properties.get("schema_version"), properties.get("dataset_kind"),
          LocalDate.parse(properties.get("last_reviewed")), properties.get("listening_validation"));
    } catch (DateTimeException ex) {
      throw new IOException(file + ": 검토 날짜는 YYYY-MM-DD 형식이어야 합니다.", ex);
    }
  }

  private static List<Row> table(ResourceSource source, String file, String header) throws IOException {
    List<String> lines = lines(source, file);
    require(!lines.isEmpty() && lines.getFirst().equals(header), file + ": TSV 헤더가 올바르지 않습니다.");
    int columns = header.split("\t", -1).length;
    List<Row> rows = new ArrayList<>();
    for (int i = 1; i < lines.size(); i++) {
      if (lines.get(i).isBlank()) continue;
      String[] cells = lines.get(i).split("\t", -1);
      require(cells.length == columns, file + ":" + (i + 1) + ": TSV 열 개수가 다릅니다.");
      rows.add(new Row(file, i + 1, Arrays.stream(cells).map(String::strip).toList()));
    }
    require(!rows.isEmpty(), file + ": 데이터 행이 없습니다.");
    return rows;
  }

  private static List<String> lines(ResourceSource source, String file) throws IOException {
    CharsetDecoder decoder =
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(source.open(file), decoder))) {
      List<String> lines = new ArrayList<>();
      String line;
      while ((line = reader.readLine()) != null) lines.add(line);
      if (!lines.isEmpty() && lines.getFirst().startsWith("\ufeff"))
        lines.set(0, lines.getFirst().substring(1));
      return lines;
    } catch (CharacterCodingException ex) {
      throw new IOException(file + ": 올바른 UTF-8 파일이 아닙니다.", ex);
    }
  }

  private static <T> Map<String, T> index(List<T> items, Function<T, String> id, String file)
      throws IOException {
    Map<String, T> index = new HashMap<>();
    for (T item : items) {
      String key = id.apply(item);
      require(index.putIfAbsent(key, item) == null, file + ": 중복 ID: " + key);
    }
    return Map.copyOf(index);
  }

  private static <T> T lookup(Map<String, T> index, String id, String kind) {
    T value = id == null ? null : index.get(id);
    if (value == null) throw new IllegalArgumentException("존재하지 않는 " + kind + ": " + id);
    return value;
  }

  private static void require(boolean condition, String message) throws IOException {
    if (!condition) throw new IOException(message);
  }

  @FunctionalInterface
  private interface ResourceSource {
    InputStream open(String file) throws IOException;
  }

  private record Row(String file, int line, List<String> fields) {
    String value(int column) throws IOException {
      String value = fields.get(column);
      require(!value.isEmpty(), location(column) + "필수 값이 비어 있습니다.");
      return value;
    }

    int integer(int column) throws IOException {
      try {
        return Integer.parseInt(value(column));
      } catch (NumberFormatException ex) {
        throw new IOException(location(column) + "정수가 필요합니다.", ex);
      }
    }

    double number(int column) throws IOException {
      try {
        double value = Double.parseDouble(value(column));
        require(Double.isFinite(value), location(column) + "유한한 수가 필요합니다.");
        return value;
      } catch (NumberFormatException ex) {
        throw new IOException(location(column) + "숫자가 필요합니다.", ex);
      }
    }

    List<Integer> notes(int column) throws IOException {
      List<Integer> notes = new ArrayList<>();
      for (String pitch : value(column).split(",", -1)) {
        try {
          notes.add(Integer.parseInt(pitch.strip()));
        } catch (NumberFormatException ex) {
          throw new IOException(location(column) + "MIDI 음은 쉼표로 구분한 정수여야 합니다.", ex);
        }
      }
      return List.copyOf(notes);
    }

    String location(int column) {
      return file + ":" + line + ":" + (column + 1) + ": ";
    }
  }
}
