package mmm;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Original, local instrument illustrations and avatars; no network image dependency. */
public final class ArtAssets {
  private ArtAssets() {}

  private static final Map<String, BufferedImage> cache = new HashMap<>();

  public static String instrumentName(int program) {
    return switch (program) {
      case 0 -> "피아노";
      case 12 -> "마림바";
      case 24 -> "기타";
      case 10 -> "오르골";
      default -> "MIDI 악기 " + (program + 1);
    };
  }

  public static ImageIcon instrument(int program, int size) {
    return icon("instrument-" + program, size, () -> drawInstrument(program));
  }

  public static ImageIcon avatar(String user, int size) {
    return icon("avatar-" + user, size, () -> drawAvatar(user));
  }

  private interface Picture {
    BufferedImage create();
  }

  private static synchronized ImageIcon icon(String name, int size, Picture fallback) {
    BufferedImage original =
        cache.computeIfAbsent(
            name,
            key -> {
              try (var stream = ArtAssets.class.getResourceAsStream("/images/" + key + ".png")) {
                if (stream != null) return ImageIO.read(stream);
              } catch (IOException ignored) {
              }
              return fallback.create();
            });
    return new ImageIcon(original.getScaledInstance(size, size, Image.SCALE_SMOOTH));
  }

  private static Graphics2D graphics(BufferedImage image) {
    Graphics2D g = image.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    return g;
  }

  private static BufferedImage drawAvatar(String user) {
    BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = graphics(image);
    Color[] colors = {new Color(235, 215, 192), new Color(213, 224, 217), new Color(220, 215, 233)};
    g.setColor(colors[Math.floorMod(user.hashCode(), colors.length)]);
    g.fillOval(0, 0, 128, 128);
    g.setColor(Theme.INK);
    g.setFont(Theme.font(45, true));
    String text =
        user.equals("demo")
            ? "나"
            : user.equals("star")
                ? "별"
                : user.equals("moon")
                    ? "달"
                    : user.substring(0, Math.min(1, user.length())).toUpperCase(Locale.ROOT);
    FontMetrics metrics = g.getFontMetrics();
    g.drawString(text, (128 - metrics.stringWidth(text)) / 2, 79);
    g.dispose();
    return image;
  }

  private static BufferedImage drawInstrument(int program) {
    BufferedImage image = new BufferedImage(192, 192, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = graphics(image);
    g.setColor(Theme.WASH);
    g.fillRoundRect(0, 0, 192, 192, 30, 30);
    g.setStroke(new BasicStroke(3));
    if (program == 24) {
      g.rotate(-.55, 96, 96);
      g.setColor(new Color(191, 130, 81));
      g.fillOval(39, 83, 89, 89);
      g.fillOval(48, 58, 71, 71);
      g.setColor(Theme.INK);
      g.fillRoundRect(75, 19, 18, 94, 5, 5);
      g.fillOval(69, 96, 31, 31);
      g.setColor(Theme.SURFACE);
      for (int i = 0; i < 4; i++) g.drawLine(79 + i * 3, 23, 79 + i * 3, 146);
    } else if (program == 12) {
      for (int i = 0; i < 7; i++) {
        g.setColor(i % 2 == 0 ? new Color(156, 102, 63) : new Color(198, 143, 91));
        g.fillRoundRect(29 + i * 20, 40 + i * 4, 16, 110 - i * 7, 5, 5);
      }
      g.setColor(Theme.INK);
      g.drawLine(40, 155, 146, 67);
      g.drawLine(146, 155, 45, 65);
      g.fillOval(136, 57, 19, 19);
      g.fillOval(35, 55, 19, 19);
    } else if (program == 10) {
      g.setColor(new Color(180, 134, 98));
      g.fillRoundRect(31, 70, 130, 87, 10, 10);
      g.setColor(Theme.INK);
      g.fillRoundRect(42, 85, 108, 42, 4, 4);
      g.setColor(Theme.SURFACE);
      for (int i = 0; i < 10; i++) g.fillRect(47 + i * 10, 90, 4, 27 - i % 3 * 6);
      g.setColor(Theme.ORANGE);
      g.drawLine(86, 68, 86, 36);
      g.fillOval(73, 34, 25, 16);
      g.drawLine(161, 118, 175, 118);
      g.drawLine(175, 118, 175, 99);
    } else {
      g.setColor(Theme.INK);
      g.fillRoundRect(23, 48, 146, 107, 12, 12);
      g.setColor(Theme.SURFACE);
      for (int i = 0; i < 8; i++) g.fillRoundRect(30 + i * 17, 86, 15, 61, 2, 2);
      g.setColor(Theme.INK);
      for (int i : new int[] {0, 1, 3, 4, 5}) g.fillRect(41 + i * 17, 85, 10, 34);
      g.setColor(Theme.ORANGE);
      g.fillOval(34, 61, 9, 9);
    }
    g.dispose();
    return image;
  }

  public static void main(String[] args) throws Exception {
    Path directory = Path.of(args[0]);
    Files.createDirectories(directory);
    for (int p : new int[] {0, 12, 24, 10})
      ImageIO.write(
          drawInstrument(p), "png", directory.resolve("instrument-" + p + ".png").toFile());
    for (String user : java.util.List.of("demo", "star", "moon"))
      ImageIO.write(drawAvatar(user), "png", directory.resolve("avatar-" + user + ".png").toFile());
    System.out.println("Created 7 original local image assets.");
  }
}
