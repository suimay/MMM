package mmm;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.*;

/** Small journal design system: quiet controls, deliberate spacing, one accent. */
public final class Theme {
  public static final Color PAPER = new Color(246, 243, 235),
      SURFACE = new Color(255, 253, 248),
      INK = new Color(46, 45, 40),
      ORANGE = new Color(211, 91, 49),
      LINE = new Color(226, 220, 207),
      MUTED = new Color(126, 120, 108),
      WASH = new Color(239, 232, 218);

  public static Font font(int size, boolean bold) {
    return new Font("Apple SD Gothic Neo", bold ? Font.BOLD : Font.PLAIN, size);
  }

  public static void install() {
    FontUIResource body = new FontUIResource(font(14, false));
    for (Object key : UIManager.getDefaults().keySet().toArray())
      if (key.toString().endsWith(".font")) UIManager.put(key, body);
    for (String key :
        new String[] {
          "Panel.background",
          "OptionPane.background",
          "CheckBox.background",
          "RadioButton.background",
          "Slider.background",
          "TabbedPane.background"
        }) UIManager.put(key, PAPER);
    UIManager.put("Label.foreground", INK);
    UIManager.put("TextField.background", SURFACE);
    UIManager.put("PasswordField.background", SURFACE);
    UIManager.put("TextArea.background", SURFACE);
    UIManager.put("List.background", PAPER);
    UIManager.put("List.selectionBackground", WASH);
    UIManager.put("List.selectionForeground", INK);
    UIManager.put("Table.background", SURFACE);
    UIManager.put("Table.gridColor", LINE);
    UIManager.put("Table.selectionBackground", WASH);
    UIManager.put("OptionPane.okButtonText", "확인");
    UIManager.put("OptionPane.cancelButtonText", "취소");
    UIManager.put(
        "TextField.border",
        new CompoundBorder(new LineBorder(LINE), new EmptyBorder(7, 10, 7, 10)));
  }

  public static JPanel row(Component... components) {
    JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
    for (Component c : components) p.add(c);
    return p;
  }

  public static JPanel column() {
    JPanel p = new JPanel();
    p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
    return p;
  }

  public static JLabel label(String text, int size) {
    JLabel l = new JLabel(text);
    l.setFont(font(size, true));
    return l;
  }

  public static JLabel muted(String text) {
    JLabel l = new JLabel(text);
    l.setForeground(MUTED);
    l.setFont(font(12, false));
    return l;
  }

  public static JButton button(String text, Runnable action) {
    return new JournalButton(text, action, 0);
  }

  public static JButton primary(String text, Runnable action) {
    return new JournalButton(text, action, 1);
  }

  public static JButton secondary(String text, Runnable action) {
    return new JournalButton(text, action, 2);
  }

  public static JButton dark(String text, Runnable action) {
    return new JournalButton(text, action, 3);
  }

  private static final class JournalButton extends JButton {
    final int kind;

    JournalButton(String text, Runnable action, int kind) {
      super(text);
      this.kind = kind;
      setFont(font(14, false));
      setForeground(kind == 1 || kind == 3 ? SURFACE : INK);
      setBorder(new EmptyBorder(8, 13, 8, 13));
      setContentAreaFilled(false);
      setOpaque(false);
      setRolloverEnabled(true);
      addActionListener(e -> action.run());
    }

    @Override
    protected void paintComponent(Graphics raw) {
      Graphics2D g = (Graphics2D) raw.create();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      Color fill = kind == 1 ? ORANGE : kind == 3 ? INK : kind == 2 ? SURFACE : null;
      if (getModel().isRollover() || getModel().isPressed())
        fill = kind == 1 ? ORANGE.darker() : kind == 3 ? INK.brighter() : WASH;
      if (fill != null) {
        g.setColor(fill);
        g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
      }
      if (kind == 2) {
        g.setColor(LINE);
        g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
      }
      if (hasFocus()) {
        g.setColor(ORANGE);
        g.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 6, 6);
      }
      g.dispose();
      super.paintComponent(raw);
    }
  }

  public static JPanel card(LayoutManager layout) {
    return new JPanel(layout) {
      {
        setOpaque(false);
        setBorder(new EmptyBorder(16, 18, 16, 18));
      }

      @Override
      protected void paintComponent(Graphics raw) {
        Graphics2D g = (Graphics2D) raw.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(SURFACE);
        g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
        g.setColor(LINE);
        g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
        g.dispose();
        super.paintComponent(raw);
      }
    };
  }

  public static JPanel page(String id, String title, String subtitle) {
    JPanel p = new JPanel(new BorderLayout(16, 22));
    p.setBorder(new EmptyBorder(26, 30, 24, 30));
    p.putClientProperty("screenId", id);
    p.getAccessibleContext().setAccessibleDescription(id);
    JPanel heading = column();
    heading.add(label(title, 26));
    heading.add(Box.createVerticalStrut(5));
    heading.add(muted(subtitle));
    p.add(heading, BorderLayout.NORTH);
    return p;
  }

  public static void slider(JSlider slider) {
    slider.setOpaque(false);
    slider.setUI(
        new BasicSliderUI(slider) {
          @Override
          public void paintTrack(Graphics raw) {
            Graphics2D g = (Graphics2D) raw.create();
            g.setColor(LINE);
            g.fillRoundRect(
                trackRect.x, trackRect.y + trackRect.height / 2 - 2, trackRect.width, 4, 4, 4);
            g.dispose();
          }

          @Override
          public void paintThumb(Graphics raw) {
            Graphics2D g = (Graphics2D) raw.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(slider.isEnabled() ? ORANGE : LINE);
            g.fillOval(thumbRect.x + 2, thumbRect.y + thumbRect.height / 2 - 6, 12, 12);
            g.dispose();
          }
        });
  }
}
