package mmm;

import java.io.IOException;
import java.util.*;
import mmm.Model.*;

/** Replace this adapter with HTTP calls to Spring Boot; keep Swing and MIDI unchanged. */
public interface Repository {
  List<Song> songs(String user) throws IOException;

  Optional<Account> account(String id) throws IOException;

  void register(Account account) throws IOException;

  void createSong(Song song) throws IOException;

  void join(String songId, String user) throws IOException;

  List<Phrase> phrases(String songId) throws IOException;

  Phrase save(Phrase phrase, int expectedVersion) throws IOException;

  Settings settings(String user) throws IOException;

  void settings(String user, Settings value) throws IOException;

  void seedDemo(java.time.LocalDate today) throws IOException;

  Song updateSong(Song expected, String name, int bpm, int instrument, String actor)
      throws IOException;

  void leave(String id, String actor) throws IOException;

  String invite(String id, String actor, java.time.Instant expires) throws IOException;

  void redeem(String code, String actor, java.time.Instant now) throws IOException;
}
