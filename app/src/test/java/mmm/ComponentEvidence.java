package mmm;

import java.awt.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import javax.swing.text.*;

/**
 * Captures the actual instantiated Swing tree and app-owned listeners for presentation evidence.
 */
public final class ComponentEvidence {
  record Snapshot(
      String id,
      String image,
      Map<String, Integer> types,
      Map<String, Integer> events,
      Set<String> layouts) {}

  private static final Map<String, Snapshot> snapshots = new TreeMap<>();
  private static final Class<?>[] TYPES = {
    JFrame.class,
    JDialog.class,
    JPasswordField.class,
    JTextField.class,
    JTextPane.class,
    JEditorPane.class,
    JTextArea.class,
    JCheckBox.class,
    JRadioButton.class,
    JToggleButton.class,
    JButton.class,
    JComboBox.class,
    JSlider.class,
    JSpinner.class,
    JTable.class,
    JTree.class,
    JList.class,
    JTabbedPane.class,
    JSplitPane.class,
    JScrollPane.class,
    JProgressBar.class,
    JToolBar.class,
    JMenuBar.class,
    JMenu.class,
    JMenuItem.class,
    JPopupMenu.class,
    JOptionPane.class,
    JLabel.class,
    JPanel.class
  };

  public static void record(String id, String image, Container root) throws Exception {
    Map<String, Integer> types = new TreeMap<>(), events = new TreeMap<>();
    Set<String> layouts = new TreeSet<>();
    walk(root, types, events, layouts);
    snapshots.putIfAbsent(id, new Snapshot(id, image, types, events, layouts));
    write();
  }

  private static void add(Map<String, Integer> map, String key, int count) {
    if (count > 0) map.merge(key, count, Integer::sum);
  }

  private static int appListeners(java.util.EventListener[] listeners) {
    return (int)
        Arrays.stream(listeners).filter(l -> l.getClass().getName().startsWith("mmm.")).count();
  }

  private static void event(
      Map<String, Integer> map, String name, java.util.EventListener[] listeners) {
    add(map, name, appListeners(listeners));
  }

  private static void walk(
      Component c, Map<String, Integer> types, Map<String, Integer> events, Set<String> layouts) {
    String className = c.getClass().getName();
    if (!className.startsWith("com.apple.") && !className.startsWith("javax.swing.plaf."))
      for (Class<?> type : TYPES)
        if (type.isInstance(c)) {
          add(types, type.getSimpleName(), 1);
          break;
        }
    if (c instanceof JLabel l && l.getIcon() instanceof ImageIcon)
      add(types, "JLabel(ImageIcon)", 1);
    event(events, "KeyListener", c.getKeyListeners());
    event(events, "MouseListener", c.getMouseListeners());
    event(events, "MouseMotionListener", c.getMouseMotionListeners());
    event(events, "MouseWheelListener", c.getMouseWheelListeners());
    event(events, "FocusListener", c.getFocusListeners());
    if (c instanceof AbstractButton b) {
      event(events, "ActionListener", b.getActionListeners());
      event(events, "ItemListener", b.getItemListeners());
    }
    if (c instanceof JComboBox<?> box) {
      event(events, "ActionListener", box.getActionListeners());
      event(events, "ItemListener", box.getItemListeners());
    }
    if (c instanceof JSlider slider) event(events, "ChangeListener", slider.getChangeListeners());
    if (c instanceof JSpinner spinner)
      event(events, "ChangeListener", spinner.getChangeListeners());
    if (c instanceof JTabbedPane tabs) event(events, "ChangeListener", tabs.getChangeListeners());
    if (c instanceof JList<?> list)
      event(events, "ListSelectionListener", list.getListSelectionListeners());
    if (c instanceof JTable table
        && table.getSelectionModel() instanceof DefaultListSelectionModel m) {
      event(events, "ListSelectionListener", m.getListSelectionListeners());
      if (table.getColumnModel().getSelectionModel() instanceof DefaultListSelectionModel cm)
        event(events, "ListSelectionListener", cm.getListSelectionListeners());
    }
    if (c instanceof JTree tree)
      event(events, "TreeSelectionListener", tree.getTreeSelectionListeners());
    if (c instanceof JEditorPane editor)
      event(events, "HyperlinkListener", editor.getHyperlinkListeners());
    if (c instanceof JTextComponent input
        && input.getDocument() instanceof AbstractDocument document)
      event(events, "DocumentListener", document.getDocumentListeners());
    if (c instanceof Window window) {
      event(events, "WindowListener", window.getWindowListeners());
      event(events, "WindowFocusListener", window.getWindowFocusListeners());
    }
    if (c instanceof Container container) {
      LayoutManager layout = container.getLayout();
      if (layout != null)
        layouts.add(layout instanceof BoxLayout ? "BoxLayout" : layout.getClass().getSimpleName());
      for (Component child : container.getComponents()) walk(child, types, events, layouts);
    }
  }

  private static String entries(Map<String, Integer> values) {
    return values.entrySet().stream()
        .map(e -> e.getKey() + " ×" + e.getValue())
        .collect(java.util.stream.Collectors.joining(", "));
  }

  private static void write() throws Exception {
    StringBuilder text =
        new StringBuilder(
            "# MMM 화면별 Java 컴포넌트·이벤트 소개표\n\n"
                + "실제 Swing 실행 중 생성된 컴포넌트 트리와 `mmm.*` 코드가 등록한 리스너를 수집했습니다. 공통 메뉴·작업 표시와 접혀 있는 설정을"
                + " 포함하며, 운영체제 기본 리스너는 이벤트 수에서 제외했습니다. 목록/표 셀 렌더러처럼 필요 시 재사용되는 객체는 트리 수에 전부 들어가지 않아"
                + " 역할 설명을 함께 보세요.\n\n"
                + "## PPT에 넣을 요약\n\n"
                + "| 화면 | 사용자 기능·시연 행동 | 주요 컴포넌트 | 이벤트 | 실행 캡처 |\n"
                + "|---|---|---|---|---|\n");
    for (ComponentGuide.Page page : ComponentGuide.PAGES) {
      Snapshot shot = snapshots.get(page.id());
      text.append("| ")
          .append(page)
          .append(" | ")
          .append(page.action())
          .append(" | ")
          .append(page.components())
          .append(" | ")
          .append(page.events())
          .append(" | ")
          .append(shot == null ? "캡처 대기" : "[보기](" + shot.image() + ")")
          .append(" |\n");
    }
    text.append(
        "\n"
            + "## 합주 발표 시연 순서\n\n"
            + "1. S08: 별(샘플)의 날짜 칸 선택 → 작성자·악기·소리 모양 확인.\n"
            + "2. 기록 자세히 → S07의 음 정보 탭에서 C3·시작 박·길이·세기 확인 → 선택 음 듣기.\n"
            + "3. 돌아가기 → 함께 듣기 → S09에서 내 소리/친구 소리/모두 비교.\n"
            + "4. 재생 중 체크박스·슬라이더로 파트 음소거/음량을 바꾸고, 반복·정지·위치 이동 시연.\n"
            + "5. 수업 요소 안내에서 JTree 항목 선택 → JEditorPane 설명과 이벤트 연결 소개.\n\n"
            + "추가 캡처: [친구 기록](S07-friend.png), [실시간 합주](S09-playing.png), [개별 음"
            + " 정보](S09-part-detail.png), [JTree 수업 안내](S13-components.png).\n");
    for (Snapshot snapshot : snapshots.values()) {
      text.append("\n## ")
          .append(snapshot.id())
          .append(" 실제 구성\n\n")
          .append("![")
          .append(snapshot.id())
          .append("](")
          .append(snapshot.image())
          .append(")\n\n")
          .append("- 컴포넌트: ")
          .append(entries(snapshot.types()))
          .append("\n- 앱 코드가 등록한 이벤트: ")
          .append(entries(snapshot.events()))
          .append("\n- 배치 관리자: ")
          .append(String.join(", ", snapshot.layouts()))
          .append("\n");
    }
    Files.writeString(Path.of("docs/components.md"), text);
  }
}
