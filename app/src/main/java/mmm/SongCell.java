package mmm;

import java.awt.*;
import java.util.*;
import javax.swing.*;
import mmm.Model.*;

public final class SongCell extends JPanel implements ListCellRenderer<Song> {
  private Song song;
  private java.util.List<Phrase> records;
  private boolean selected;
  private final Map<String, java.util.List<Phrase>> library;

  public SongCell(Map<String, java.util.List<Phrase>> library) {
    this.library = library;
    setOpaque(false);
  }

  @Override
  public Component getListCellRendererComponent(
      JList<? extends Song> list, Song value, int index, boolean selected, boolean focus) {
    song = value;
    records = library.getOrDefault(value.id(), java.util.List.of());
    this.selected = selected;
    return this;
  }

  @Override
  protected void paintComponent(Graphics raw) {
    Graphics2D g = (Graphics2D) raw.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    int w = getWidth(), h = getHeight();
    g.setColor(selected ? Theme.WASH : Theme.SURFACE);
    g.fillRoundRect(0, 4, w - 1, h - 9, 12, 12);
    g.setColor(selected ? Theme.ORANGE : Theme.LINE);
    g.drawRoundRect(0, 4, w - 1, h - 9, 12, 12);
    Phrase latest = records.isEmpty() ? null : records.get(records.size() - 1);
    if (latest != null) SoundShape.miniature(g, latest.notes(), 20, 26, 128, 54);
    g.setColor(Theme.INK);
    g.setFont(Theme.font(19, true));
    g.drawString(song.name(), 174, 43);
    g.setColor(Theme.MUTED);
    g.setFont(Theme.font(12, false));
    g.drawString(
        (song.shared() ? song.members().size() + "명이 함께 · " : "나만의 기록 · ")
            + records.size()
            + "마디 · "
            + song.bpm()
            + " BPM",
        174,
        68);
    if (latest != null) g.drawString(latest.date() + "  " + latest.memo(), 174, 91);
    g.setFont(Theme.font(20, false));
    g.drawString("→", w - 36, 61);
    g.dispose();
  }
}
