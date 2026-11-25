package mmm;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;

public final class Theme {
  public static final Color PAPER = new Color(246, 243, 235),
      INK = new Color(30, 30, 29),
      ORANGE = new Color(237, 98, 40),
      LINE = new Color(213, 207, 195),
      MUTED = new Color(112, 109, 102);

  public static void install() {
    UIManager.put("OptionPane.okButtonText", "확인");
    UIManager.put("OptionPane.cancelButtonText", "취소");
    UIManager.put("Panel.background", PAPER);
    UIManager.put("Label.foreground", INK);
    UIManager.put("Button.background", Color.WHITE);
    UIManager.put("Button.foreground", INK);
    UIManager.put("TextField.background", Color.WHITE);
    UIManager.put("TabbedPane.background", PAPER);
    UIManager.put("OptionPane.background", PAPER);
    UIManager.put("CheckBox.background", PAPER);
    UIManager.put("RadioButton.background", PAPER);
    UIManager.put("Slider.background", PAPER);
    Font font = new Font("Apple SD Gothic Neo", Font.PLAIN, 16);
    for (Object key : UIManager.getDefaults().keySet().toArray())
      if (key.toString().endsWith(".font")) UIManager.put(key, font);
  }

  public static JPanel row(Component... components) {
    JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
    for (Component c : components) p.add(c);
    return p;
  }

  public static JLabel label(String text, int size) {
    JLabel l = new JLabel(text);
    l.setFont(new Font("Apple SD Gothic Neo", Font.BOLD, size));
    return l;
  }

  public static JButton button(String text, Runnable action) {
    JButton b = new JButton(text);
    b.setFocusPainted(false);
    b.setMargin(new Insets(9, 15, 9, 15));
    b.addActionListener(e -> action.run());
    return b;
  }

  public static JButton primary(String text, Runnable action) {
    JButton b = button(text, action);
    b.setBackground(ORANGE);
    b.setOpaque(true);
    b.setForeground(Color.WHITE);
    b.setBorder(new CompoundBorder(new LineBorder(ORANGE), new EmptyBorder(11, 18, 11, 18)));
    return b;
  }

  public static JPanel column() {
    JPanel p = new JPanel();
    p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
    return p;
  }

  public static JPanel page(String id, String title, String subtitle) {
    JPanel p = new JPanel(new BorderLayout(16, 18));
    p.setBorder(new EmptyBorder(26, 30, 26, 30));
    JPanel h = column();
    JLabel tag = label(id + "   /   MMM", 12);
    tag.setForeground(ORANGE);
    h.add(tag);
    h.add(Box.createVerticalStrut(8));
    h.add(label(title, 32));
    h.add(Box.createVerticalStrut(10));
    h.add(new JLabel(subtitle));
    p.add(h, BorderLayout.NORTH);
    return p;
  }
}
