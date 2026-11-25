package mmm;

import java.awt.*;
import javax.swing.*;
import javax.swing.tree.*;

/** S13 course guide: navigable screen/component explanations, not a second app flow. */
public final class ComponentGuide extends JPanel {
  public record Page(String id, String title, String components, String events, String action) {
    public String toString() {
      return id + " · " + title;
    }
  }

  public static final Page[] PAGES = {
    new Page(
        "S01",
        "시작·소리 체험",
        "JLabel, JPanel, JSlider, JButton",
        "MouseListener, MouseMotionListener, ChangeListener, ActionListener",
        "m 글자의 높이·너비로 음높이·길이를 바꾸고 세 음 이어 듣기"),
    new Page(
        "S02",
        "로그인",
        "JTextField, JPasswordField, JButton, JLabel",
        "ActionListener, WindowListener",
        "아이디·비밀번호 검증 후 로컬 로그인"),
    new Page(
        "S03",
        "회원가입",
        "JTextField, JPasswordField, JLabel, JButton",
        "ActionListener, DocumentListener",
        "입력 오류·중복 계정 검사 및 가입"),
    new Page(
        "S04",
        "곡 보관함",
        "JTabbedPane, JList, JScrollPane, JTextField, JComboBox",
        "DocumentListener, MouseListener, ActionListener",
        "검색·정렬·더블클릭·Enter로 개인/공동 곡 열기"),
    new Page(
        "S05",
        "개인 주간",
        "JPanel, JButton, JTextArea, JSlider, JCheckBox",
        "ActionListener, ChangeListener, MouseListener",
        "요일 클릭으로 그날부터 이어 듣기, 별도 버튼으로 기록 열기·오늘 편집"),
    new Page(
        "S06",
        "오늘의 한 마디",
        "JPanel, JSlider, JSpinner, JComboBox, JTextField",
        "KeyListener, MouseListener, MouseMotionListener, ChangeListener, DocumentListener",
        "덩어리·꼬리 드래그, 길이·높이·세기 조절, 비교·저장"),
    new Page(
        "S07",
        "기록 상세·감상",
        "JSplitPane, JPanel, JTable, JTextPane, JToggleButton, JLabel(ImageIcon)",
        "MouseListener, ListSelectionListener, ItemListener, ActionListener",
        "소리 클릭 → 우측 음 정보, 메모를 같은 화면에서 확인·선택 음 감상"),
    new Page(
        "S08",
        "공동 주간",
        "JTable, JSplitPane, JToggleButton, JButton",
        "ListSelectionListener, MouseListener, ActionListener",
        "요일 클릭은 그날 전체 합주, 날짜 칸 클릭은 참여자 기록, 참여자 한 주 듣기"),
    new Page(
        "S09",
        "공동 합주·파트",
        "JToolBar, JList, JCheckBox, JSlider, JTabbedPane, JPopupMenu",
        "ListSelectionListener, MouseListener, MouseMotionListener, MouseWheelListener,"
            + " ChangeListener",
        "작성자별 타임라인·연주 음 표시·실시간 파트 음소거/음량"),
    new Page(
        "S10",
        "곡·참여자 관리",
        "JTextField, JSpinner, JTable, JButton",
        "ActionListener, ChangeListener",
        "소유자만 곡 설정 변경, 초대·참여·탈퇴"),
    new Page(
        "S11",
        "악보·출력",
        "JPanel, JComboBox, JScrollPane, JFileChooser",
        "ActionListener",
        "악보 확대·페이지 인쇄·MIDI 내보내기"),
    new Page(
        "S12",
        "설정·계정",
        "JTabbedPane, JSlider, JComboBox, JCheckBox, JSpinner",
        "ActionListener, ChangeListener",
        "음량·기본 악기·카운트인·메트로놈 설정 저장"),
    new Page(
        "S13",
        "도움말·수업 요소",
        "JTree, JEditorPane, JSplitPane, JScrollPane",
        "TreeSelectionListener, HyperlinkListener",
        "주제 선택으로 화면의 기능·컴포넌트·이벤트 설명 탐색"),
    new Page(
        "S14",
        "소리 일기장",
        "JList, JTextField, JComboBox, JCheckBox, JSplitPane, JTextPane, JFileChooser",
        "DocumentListener, ItemListener, ListSelectionListener, MouseListener, ActionListener",
        "소리 그림과 메모 모음, 날짜·작성자·검색 필터, 선택 기록 감상과 메모 TXT 내보내기")
  };

  public ComponentGuide() {
    super(new BorderLayout(10, 10));
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("MMM 화면과 수업 요소");
    for (Page p : PAGES) {
      DefaultMutableTreeNode node = new DefaultMutableTreeNode(p);
      root.add(node);
      for (String c : p.components().split(", ")) node.add(new DefaultMutableTreeNode(c));
    }
    JTree tree = new JTree(new DefaultTreeModel(root));
    tree.setRootVisible(false);
    tree.setRowHeight(26);
    JEditorPane explanation = new JEditorPane("text/html", "");
    explanation.setEditable(false);
    explanation.setBackground(Theme.SURFACE);
    explanation.addHyperlinkListener(
        e -> {
          if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED)
            explanation.scrollToReference("events");
        });
    tree.addTreeSelectionListener(
        e -> {
          DefaultMutableTreeNode node =
              (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
          if (node == null) return;
          DefaultMutableTreeNode parent = node;
          while (parent != null && !(parent.getUserObject() instanceof Page))
            parent = (DefaultMutableTreeNode) parent.getParent();
          if (parent == null) return;
          Page p = (Page) parent.getUserObject();
          explanation.setText(
              "<html><body style='font-family:sans-serif;padding:18px;color:#2e2d28'><h2>"
                  + p
                  + "</h2><p>"
                  + p.action()
                  + "</p><h3>실제 컴포넌트</h3><p>"
                  + p.components()
                  + "</p><a name='events'></a><h3 id='events'>이벤트 연결</h3><p>"
                  + p.events()
                  + "</p><p>선택한 항목: <b>"
                  + node.getUserObject()
                  + "</b></p><p><a href='#events'>이벤트 연결로 이동</a></p><hr><p>화면 캡처와 실행 트리에서 수집한 표는"
                  + " 프로젝트 docs/components.md에서 확인할 수 있습니다.</p></body></html>");
          explanation.setCaretPosition(0);
        });
    JSplitPane split =
        new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(tree), new JScrollPane(explanation));
    split.setResizeWeight(.35);
    split.setDividerLocation(300);
    split.setContinuousLayout(true);
    add(split);
    tree.setSelectionRow(0);
  }
}
