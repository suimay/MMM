# MMM 사용자 흐름·Java 수업 요소 검토

검토일: 2026-11-01. 구현을 맡은 에이전트와 별도의 검토 에이전트가 코드를 읽고 실제 Swing 실행 캡처를 확인했습니다. 검토자는 구현 코드를 수정하지 않았습니다.

이번 검토의 기준은 **사용자가 하려는 행동이 쉽게 보이고, 그 행동이 실제 이벤트·데이터·소리로 연결되는가**입니다. 수업 요소의 수뿐 아니라 각 요소를 사용하는 이유를 확인했습니다.

## 주요 행동 → 이벤트 → 상태 → 피드백

| 주요 화면과 사용자 행동 | 실제 Swing 요소 | 이벤트·연결 | 바뀌는 상태 | 사용자에게 돌아오는 피드백 |
|---|---|---|---|---|
| S01: m 하나를 선택하고 글자 높이·너비를 바꾼 뒤 듣기 | JPanel 기반 LogoCanvas, JSlider, JButton, JComboBox, JLabel(ImageIcon) | MouseListener 또는 InputMap/ActionMap으로 선택, ChangeListener로 값 변경, ActionListener로 감상, ItemListener로 악기 변경 | 선택한 음의 MIDI pitch·duration·instrument | 글자의 높이·너비, 도레미 음 이름·길이 표시가 바뀜. 같은 값으로 만든 MIDI 음이 재생되고 현재 연주하는 m이 강조됨 |
| S01·S06: 도레미파솔라시도 연주 | JPanel 기반 Piano, 악기 JComboBox | KeyListener·MouseListener로 누름/뗌, FocusListener로 눌린 음 해제 | 건반별 활성 음, 편집에서는 NoteEvent | C4부터 C5까지 익숙한 8개 음이 실제 소리로 남음. 누른 건반이 강조되고 편집 화면에서는 소리 덩어리로 연결됨 |
| S07: 소리 모양을 눌러 음을 확인하고 한 음 듣기 | NoteCanvas, JSplitPane, JLabel, JButton, JTextPane, 선택적으로 펼치는 JTable | MouseListener·방향키 ActionMap으로 선택, ListSelectionListener로 목록 선택 동기화, ActionListener로 감상 | inspected index, 선택 음의 임시 감상 상태 | 선택한 덩어리의 테두리와 우측 도레미·시작 박·길이·세기가 함께 바뀜. 메모도 같은 화면에서 보며 원본은 바뀌지 않음 |
| S05: 수요일 등 원하는 요일부터 개인 기록 이어 듣기 | 요일 JButton, DayCard, JSlider, JCheckBox | ActionListener → MidiEngine.playFrom, ChangeListener로 위치 이동 | 전체 주간 시퀀스의 시작 tick과 반복 시작점 | 해당 요일부터 일요일까지 재생. 현재 재생 날짜가 강조되고 위치·마디·박 상태가 표시됨. 기록 열기·오늘 편집은 별도 버튼으로 유지 |
| S08·S09: 요일을 눌러 그날 전체 합주 듣기 | JTable/JTableHeader, JToggleButton과 ButtonGroup, JList, JSlider, JCheckBox | 헤더 MouseListener 또는 요일 버튼 ActionListener → playDay, 참여자 ListSelectionListener, 음량 ChangeListener·MouseWheelListener | 선택 날짜, 하루 재생 범위, 참여자별 채널·음량 | 그날의 참여자 파트가 같은 박자로 재생. 날짜 칸은 한 사람의 기록 확인, 요일은 모두의 합주라는 구분을 안내. 합주 화면에서 작성자·악기·현재 음을 확인 가능 |
| S14: 여러 날의 메모를 모아 찾고 소리와 함께 읽기 | JTextField, JComboBox, JCheckBox, JList/DefaultListModel, JSplitPane, RecordInspector, JFileChooser | DocumentListener로 검색, ItemListener로 기간·작성자·정렬·메모 필터, ListSelectionListener로 상세 변경, 더블클릭·Enter로 기록 열기 | 필터에 보이는 기록·선택 기록 | 날짜·작성자·메모·악기·소리 그림이 한 카드에 모임. 찾은 일기 수와 선택 상세가 바뀌고, 보이는 기록만 TXT로 내보냄 |

## 사용 편의를 위해 검토한 결정

- **처음에는 체험부터:** MMM 로고의 모양과 실제 소리를 연결해 앱의 의미를 보여줍니다. 선택 음 듣기와 세 음 이어 듣기를 구분하고, 같은 화면에서 익숙한 도레미 건반도 사용할 수 있습니다.
- **상세 화면의 탭 왕복 제거:** 소리 모양·음 정보·메모를 한 화면에 두었습니다. 전체 음 목록은 필요할 때 펼치므로 표를 확인하는 기능도 남아 있습니다.
- **듣기와 편집 분리:** 개인 주간에서 요일을 누르면 바로 듣고, 기록 열기와 오늘 편집은 별도 동작으로 둡니다. 감상을 시도했는데 편집 화면으로 이동하는 혼동을 줄입니다.
- **가로와 세로의 의미 유지:** 공동 주간의 한 행은 한 사람의 일주일, 한 열은 같은 날의 여러 사람입니다. 요일 합주와 참여자 주간 재생을 따로 제공합니다.
- **선택 가능하되 읽기 전용:** 친구 기록은 덩어리와 음 목록을 선택하고 들을 수 있으나 저장·드래그 편집은 제공하지 않습니다. 저장 권한은 기존 서비스에서도 작성자를 검사합니다.
- **좁은 영역은 스크롤:** S08 미리보기는 소리 → 선택 정보 → 메모 순서의 세로 배치입니다. 상세의 권장 높이를 보존한 채 스크롤하며, 음 목록을 펼치면 내용 높이도 늘어납니다. S07·S14의 가로 상세는 음 정보의 최소 너비를 보존합니다.
- **미래와 빈 기록 구분:** 미래 날짜 재생·상세 진입을 막고, 기록 없는 날은 쉼으로 표시합니다. 선택 음이 없으면 선택 음 듣기 버튼은 비활성화됩니다.
- **필터의 결과를 그대로 내보내기:** 소리 일기장은 현재 접근 가능한 곡의 기록을 바탕으로 탐색합니다. 기간·작성자·검색 결과를 TXT 내보내기에도 동일하게 적용합니다.

## 검토 중 발견해 수정한 문제

| 발견한 문제 | 반영된 수정과 확인 |
|---|---|
| 읽기 전용 소리 그림에 클릭 선택이 없는데 음을 선택하라는 안내가 있었음 | 읽기 전용 선택 리스너와 방향키 선택을 추가하고 음 정보와 연결. 편집 리스너는 분리 |
| 넓은 시작 화면에서 로고를 그리는 영역과 클릭 좌표 계산이 달랐음 | 그리기와 같은 중앙 배치 cell·left 값으로 선택 위치 계산 |
| 세 음 재생 중 건반을 누르거나 값을 바꾸면 기존 시퀀스와 새 체험이 섞일 수 있었음 | 시퀀스를 정지한 뒤 새 건반 체험을 시작하고, 슬라이더 변경 시 이전 시퀀스 정지 |
| S08 미리보기 높이가 줄어 소리 그림이 눌리거나 사라졌음 | RecordInspector를 Scrollable로 구성하고 세로 스크롤·권장 높이·펼침 높이 증가 적용 |
| S14 상세의 고정 분할 위치 때문에 음 정보가 매우 좁아졌음 | 고정 분할 위치 제거, 음 정보 최소 너비 보존. 갱신된 캡처에서 음 이름·수치·듣기 버튼을 확인 |
| 주간 합주에서 다른 날짜의 음을 듣는 동안 선택 날짜의 덩어리가 강조될 수 있었음 | 실제 재생일과 상세의 날짜가 같을 때만 합주 playhead를 상세에 전달 |
| S09 미래 주간에서 빈 미래 상세를 열 수 있었음 | 미래 날짜의 상세 진입 가드 추가 |
| S08 전체 합주가 MIDI 멜로디 채널 수를 넘으면 예외가 발생할 수 있었음 | 15파트 범위 안내와 참여자별 듣기 대안 제공 |

## 컴포넌트·지원 API·이벤트의 구분

발표 정리표에서는 다음을 구분합니다.

| 분류 | 예 | 발표에서 설명할 내용 |
|---|---|---|
| 화면에 놓이는 Swing 컴포넌트 | JButton, JSlider, JTable, JList, JTree, JSplitPane, JTextPane, JComboBox | 사용자가 무엇을 보고 조작하는지 |
| 이미지 자산과 표시 | ImageIcon을 넣은 JLabel, 악기·아바타 PNG | 글만으로 구별하기 어려운 작성자·악기를 어떻게 식별하는지 |
| 배치 관리자 | BorderLayout, GridLayout, BoxLayout, CardLayout | 창 크기와 작업 순서에 맞게 화면을 어떻게 배치하는지 |
| 모델·보조 API | TableModel, DefaultListModel, TableRowSorter, UndoManager, Graphics2D, PrinterJob, MIDI Sequencer | 화면 상태·그리기·실행 취소·출력·재생을 어떻게 지원하는지 |
| 이벤트와 액션 연결 | ActionListener, ChangeListener, DocumentListener, ItemListener, ListSelectionListener, MouseListener, KeyListener, FocusListener, InputMap/ActionMap | 사용자 행동이 어떤 상태 변화를 일으키는지 |

`JLabel(ImageIcon)`은 이미지 활용 표시이며 별개의 Swing 컴포넌트 클래스로 중복 집계하지 않습니다. `Graphics2D`, 배치 관리자, 모델, MIDI API를 시각 컴포넌트 수에 더하지 않습니다. 운영체제 기본 리스너와 앱이 등록한 리스너도 구분합니다. 최종 수치는 실제 실행에서 수집한 [컴포넌트·이벤트 정리표](components.md)를 따릅니다.

## 확인 자료와 범위

- 코드: ProloguePanel, Piano, RecordInspector, NoteCanvas, PersonalWeekPanel, DayCard, SharedWeekPanel, DayPlaybackBar, EnsemblePanel, JournalPanel, MidiEngine.playFrom, MMM 라우팅.
- 갱신 캡처: [시작 체험](S01-start.png), [친구 기록 선택 상세](S07-friend.png), [개인 주간](S05-personal.png), [공동 주간](S08-shared.png), [소리 일기장](S14-journal.png).
- 기본 검사 101개와 실제 Swing 흐름 검사 75개의 통과 로그를 확인했습니다. Swing 검사는 임시 데이터와 실제 창·컴포넌트·리스너를 사용하는 코드 통합 검사이며 외부 운영체제 마우스 자동화는 아닙니다.
- 위 수정 후 코드와 갱신 캡처에서 추가로 진행을 막는 흐름·상태·권한·정보 폭 문제는 발견하지 못했습니다. 작은 화면에서는 상세의 메모·전체 음 목록까지 세로로 스크롤해 읽습니다.
- 공동 기능은 같은 기기의 로컬 시연 저장소 범위입니다. 원격 서버 동기화나 실제 여러 사람의 동시 네트워크 합주를 검증한 결과로 해석하지 않습니다.
