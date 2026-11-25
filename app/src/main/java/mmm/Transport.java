package mmm;

import java.awt.*;
import javax.swing.*;

/** Familiar desktop transport: scrub, pause, loop, and explicit state. */
public final class Transport extends JPanel {
  private final javax.swing.Timer timer;
  private boolean refreshing;

  public Transport(MMM app, String label, Runnable play) {
    super(new FlowLayout(FlowLayout.LEFT, 8, 5));
    JButton start = Theme.primary(label, play),
        pause = Theme.button("일시정지", app.midi::pauseResume),
        stop = Theme.button("정지", app::stopSound);
    JCheckBox loop = new JCheckBox("반복");
    loop.addActionListener(e -> app.midi.loop(loop.isSelected()));
    app.midi.loop(false);
    JSlider scrub = new JSlider(0, 1000, 0);
    scrub.setPreferredSize(new Dimension(200, 28));
    scrub.setToolTipText("재생 위치 이동");
    JLabel status = new JLabel("정지 · 1마디 1박");
    status.setPreferredSize(new Dimension(185, 25));
    scrub.addChangeListener(
        e -> {
          if (!refreshing) app.midi.seek(app.midi.length() * scrub.getValue() / 1000);
        });
    add(start);
    add(pause);
    add(stop);
    add(loop);
    add(scrub);
    add(status);
    timer =
        new javax.swing.Timer(
            60,
            e -> {
              long pos = app.midi.cursor(), length = app.midi.length();
              if (!scrub.getValueIsAdjusting()) {
                refreshing = true;
                scrub.setValue(length == 0 ? 0 : (int) (1000 * pos / length));
                refreshing = false;
              }
              pause.setText(app.midi.paused() ? "계속 재생" : "일시정지");
              pause.setEnabled(app.midi.playing() || app.midi.paused());
              status.setText(
                  (app.midi.playing() ? "재생 중" : app.midi.paused() ? "일시정지" : "정지")
                      + " · "
                      + (pos / Model.BAR + 1)
                      + "마디 "
                      + (pos % Model.BAR / Model.PPQ + 1)
                      + "박");
            });
  }

  @Override
  public void addNotify() {
    super.addNotify();
    timer.start();
  }

  @Override
  public void removeNotify() {
    timer.stop();
    super.removeNotify();
  }
}
