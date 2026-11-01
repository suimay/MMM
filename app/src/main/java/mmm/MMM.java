package mmm;

import java.awt.*;
import java.awt.event.*;
import java.awt.print.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.function.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import mmm.Model.*;

public final class MMM extends JFrame {
  public static final String[] INSTRUMENT_NAMES = {"피아노", "마림바", "기타", "오르골"};
  public static final int[] PROGRAMS = {0, 12, 24, 10};

  public static int instrumentIndex(int p) {
    for (int i = 0; i < PROGRAMS.length; i++) if (PROGRAMS[i] == p) return i;
    return 0;
  }

  public final MusicService service;
  public final MidiEngine midi = new MidiEngine();
  public Settings settings = new Settings(80, 0, true);
  public EditorPanel editor;
  private final JPanel cards = new JPanel(new CardLayout());
  private final JLabel banner = new JLabel();
  private Song current;
  private LocalDate week;
  private String screen = "S01";
  private boolean busy;
  private JPanel glass;
  private final Path dataPath;
  private javax.swing.Timer viewerTimer;
  private final JLabel breadcrumb = Theme.muted("하루 한 마디");
  private final JButton back = Theme.button("‹ 보관함", () -> guard(this::library));
  private List<EnsembleData.Member> currentMembers = List.of();
  private Runnable detailBack = this::week;
  private final Map<String, List<Phrase>> libraryRecords = new HashMap<>();

  public MMM(MusicService service, Path dataPath) {
    super("MMM · 음음음 2.3.1");
    this.service = service;
    this.dataPath = dataPath;
    this.week = Model.monday(service.today());
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    setMinimumSize(new Dimension(1060, 780));
    setSize(1180, 850);
    setLocationRelativeTo(null);
    JPanel shell = new JPanel(new BorderLayout());
    JPanel nav = new JPanel(new BorderLayout());
    nav.setBorder(
        new CompoundBorder(new MatteBorder(0, 0, 1, 0, Theme.LINE), new EmptyBorder(8, 20, 8, 20)));
    JPanel identity =
        Theme.row(
            Theme.label("mmm", 25), new JSeparator(SwingConstants.VERTICAL), back, breadcrumb);
    nav.add(identity);
    JButton more = Theme.button("메뉴 ···", () -> {});
    more.addActionListener(
        e -> {
          JPopupMenu menu = new JPopupMenu();
          popup(menu, "시작", () -> guard(this::start));
          popup(menu, "곡 보관함", () -> guard(this::library));
          popup(menu, "설정·계정", () -> guard(this::settings));
          popup(menu, "도움말", () -> guard(this::help));
          menu.addSeparator();
          popup(menu, "모든 소리 정지 · Esc", this::stopSound);
          menu.show(more, 0, more.getHeight());
        });
    nav.add(more, BorderLayout.EAST);
    shell.add(nav, BorderLayout.NORTH);
    shell.add(cards);
    banner.setFont(Theme.font(11, false));
    banner.setBorder(new EmptyBorder(7, 24, 7, 24));
    banner.setForeground(Theme.MUTED);
    shell.add(banner, BorderLayout.SOUTH);
    setContentPane(shell);
    glass = new JPanel(new GridBagLayout());
    glass.setOpaque(false);
    JLabel working = Theme.label("처리 중 · 잠시만 기다려 주세요", 19);
    working.setOpaque(true);
    working.setBackground(Theme.PAPER);
    working.setBorder(new EmptyBorder(20, 30, 20, 30));
    GridBagConstraints waiting = new GridBagConstraints();
    waiting.gridx = 0;
    waiting.gridy = 0;
    glass.add(working, waiting);
    JProgressBar progress = new JProgressBar();
    progress.setIndeterminate(true);
    progress.setPreferredSize(new Dimension(280, 8));
    waiting.gridy = 1;
    glass.add(progress, waiting);
    glass.addMouseListener(new MouseAdapter() {});
    glass.addKeyListener(new KeyAdapter() {});
    glass.setFocusable(true);
    setGlassPane(glass);
    addWindowListener(
        new WindowAdapter() {
          public void windowClosing(WindowEvent e) {
            if (!busy)
              guard(
                  () -> {
                    if (viewerTimer != null) viewerTimer.stop();
                    if (editor != null) editor.dispose();
                    stopSound();
                    midi.close();
                    dispose();
                  });
          }
        });
    addWindowFocusListener(
        new WindowAdapter() {
          public void windowLostFocus(WindowEvent e) {
            stopSound();
          }
        });
    installMenus();
    start();
    setVisible(true);
    work(
        () -> {
          service.seed();
          midi.open();
          return null;
        },
        v -> updateBanner());
  }

  private void popup(JPopupMenu menu, String text, Runnable action) {
    JMenuItem item = new JMenuItem(text);
    item.addActionListener(e -> action.run());
    menu.add(item);
  }

  private void installMenus() {
    JMenuBar bar = new JMenuBar();
    JMenu file = new JMenu("파일"),
        edit = new JMenu("편집"),
        playback = new JMenu("재생"),
        help = new JMenu("도움말");
    int cmd = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    menu(file, "곡 보관함", KeyStroke.getKeyStroke(KeyEvent.VK_O, cmd), () -> guard(this::library));
    menu(
        file,
        "새 곡",
        KeyStroke.getKeyStroke(KeyEvent.VK_N, cmd),
        () ->
            guard(
                () -> {
                  if (service.user() == null) login(service.demo ? "demo" : "");
                  else createDialog();
                }));
    menu(
        file,
        "저장",
        KeyStroke.getKeyStroke(KeyEvent.VK_S, cmd),
        () -> {
          if (editor != null) saveEditor(this::week);
        });
    menu(file, "설정", KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, cmd), () -> guard(this::settings));
    menu(
        edit,
        "실행 취소",
        KeyStroke.getKeyStroke(KeyEvent.VK_Z, cmd),
        () -> {
          if (editor != null) editor.undo();
        });
    menu(
        edit,
        "다시 실행",
        KeyStroke.getKeyStroke(KeyEvent.VK_Z, cmd | InputEvent.SHIFT_DOWN_MASK),
        () -> {
          if (editor != null) editor.redo();
        });
    menu(playback, "모든 소리 정지", KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), this::stopSound);
    menu(help, "사용 방법", KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), () -> guard(this::help));
    bar.add(file);
    bar.add(edit);
    bar.add(playback);
    bar.add(help);
    setJMenuBar(bar);
  }

  private void menu(JMenu parent, String title, KeyStroke key, Runnable action) {
    JMenuItem item = new JMenuItem(title);
    item.setAccelerator(key);
    item.addActionListener(
        e -> {
          if (!busy) action.run();
        });
    parent.add(item);
  }

  @FunctionalInterface
  interface Job<T> {
    T run() throws Exception;
  }

  <T> void work(Job<T> task, Consumer<T> success) {
    if (busy) return;
    busy = true;
    glass.setVisible(true);
    glass.requestFocusInWindow();
    new SwingWorker<T, Void>() {
      protected T doInBackground() throws Exception {
        return task.run();
      }

      protected void done() {
        busy = false;
        glass.setVisible(false);
        try {
          success.accept(get());
        } catch (Exception e) {
          Throwable cause = e.getCause() != null ? e.getCause() : e;
          error(cause);
        }
        updateBanner();
      }
    }.execute();
  }

  private void updateBanner() {
    banner.setText(
        (service.demo ? "시연 · " + service.today() : "서울 · " + service.today())
            + "    ·    이 기기에 저장 · 공동 기능은 로컬 모의    ·    "
            + midi.status());
  }

  public void error(Throwable e) {
    JOptionPane.showMessageDialog(
        this,
        e.getMessage() + "\n편집 초안과 기존 저장본은 유지됩니다. 원인을 확인하고 같은 작업을 다시 시도하세요.",
        "D05 · 작업을 완료하지 못했어요",
        JOptionPane.ERROR_MESSAGE);
  }

  void show(String id, JPanel panel) {
    getRootPane().setDefaultButton(null);
    stopSound();
    if (viewerTimer != null) {
      viewerTimer.stop();
      viewerTimer = null;
    }
    if (editor != null) {
      editor.dispose();
      editor = null;
    }
    screen = id;
    breadcrumb.setText(
        switch (id) {
          case "S04" -> "곡 보관함";
          case "S05", "S08" -> current == null ? "한 주의 기록" : current.name();
          case "S06" -> "오늘의 한 마디";
          case "S07" -> "기록 감상";
          case "S09" -> "함께 듣기";
          case "S10" -> "곡 관리";
          case "S11" -> "악보·출력";
          case "S12" -> "설정·계정";
          case "S13" -> "도움말";
          case "S14" -> "소리 일기장";
          default -> "하루 한 마디";
        });
    back.setVisible(!id.equals("S01") && !id.equals("S04"));
    for (ActionListener listener : back.getActionListeners()) back.removeActionListener(listener);
    boolean toWeek =
        current != null && Set.of("S06", "S07", "S09", "S10", "S11", "S14").contains(id);
    back.setText(toWeek ? "‹ 주간으로" : "‹ 보관함");
    back.addActionListener(
        e -> guard(id.equals("S07") ? detailBack : toWeek ? this::week : this::library));
    cards.removeAll();
    cards.add(panel, id);
    ((CardLayout) cards.getLayout()).show(cards, id);
    cards.revalidate();
    cards.repaint();
    updateBanner();
  }

  public void stopSound() {
    if (editor != null) editor.stop();
    midi.stop();
  }

  public void guard(Runnable action) {
    if (busy) return;
    if (editor != null && editor.dirty) {
      stopSound();
      Object[] choices = {"저장하고 이동", "버리고 이동", "계속 편집"};
      int choice =
          JOptionPane.showOptionDialog(
              this,
              "아직 저장하지 않은 오늘의 소리가 있어요.",
              "D04 · 미저장 변경 처리",
              JOptionPane.DEFAULT_OPTION,
              JOptionPane.QUESTION_MESSAGE,
              null,
              choices,
              choices[2]);
      if (choice == 0) saveEditor(action);
      else if (choice == 1) {
        editor.dirty = false;
        action.run();
      }
    } else action.run();
  }

  private void start() {
    ProloguePanel next =
        new ProloguePanel(
            this,
            this::library,
            service.demo
                ? () ->
                    work(
                        () -> {
                          service.login("demo", "demo1234".toCharArray());
                          return service.repository.settings("demo");
                        },
                        value -> {
                          settings = value;
                          midi.volume(value.volume());
                          library();
                        })
                : () -> login(""),
            this::help);
    show("S01", next);
  }

  private void login(String id) {
    JPanel p = Theme.page("S02", "다시 만나 반가워요.", "이 기기에만 저장되는 로컬 데모 인증입니다. 실제 서비스 비밀번호를 사용하지 마세요.");
    JTextField user = new JTextField(id, 24);
    JPasswordField password = new JPasswordField(24);
    JLabel message =
        new JLabel(service.demo ? "시연 계정  demo / demo1234" : "처음이라면 회원가입 · 샘플 시연은 demo.command 실행");
    JPanel form = new JPanel(new GridLayout(0, 2, 15, 18));
    form.setMaximumSize(new Dimension(660, 230));
    form.add(new JLabel("아이디"));
    form.add(user);
    form.add(new JLabel("비밀번호"));
    form.add(password);
    JButton enter =
        Theme.primary(
            "로그인",
            () -> {
              String u = user.getText().strip();
              char[] pass = password.getPassword();
              work(
                  () -> {
                    try {
                      service.login(u, pass);
                      return service.repository.settings(u);
                    } finally {
                      Arrays.fill(pass, '\0');
                    }
                  },
                  s -> {
                    settings = s;
                    midi.volume(s.volume());
                    library();
                  });
              password.setText("");
            });
    JPanel c = Theme.column();
    c.add(Box.createVerticalStrut(40));
    c.add(form);
    c.add(Theme.row(message));
    c.add(Theme.row(enter, Theme.button("회원가입", this::signup)));
    p.add(c);
    show("S02", p);
    getRootPane().setDefaultButton(enter);
  }

  private void signup() {
    JPanel p = Theme.page("S03", "나의 작은 기록을 시작해요.", "로컬 계정 · 외부 서버 전송 없음");
    JTextField id = new JTextField(20), nick = new JTextField(20);
    JPasswordField pw = new JPasswordField(20), confirm = new JPasswordField(20);
    JPanel form = new JPanel(new GridLayout(0, 2, 12, 12));
    form.setMaximumSize(new Dimension(650, 280));
    form.add(new JLabel("아이디 · 영문/숫자/_ 3~24자"));
    form.add(id);
    form.add(new JLabel("닉네임 · 20자"));
    form.add(nick);
    form.add(new JLabel("비밀번호 · 8자 이상"));
    form.add(pw);
    form.add(new JLabel("비밀번호 확인"));
    form.add(confirm);
    JLabel validation = Theme.muted("모든 항목을 입력해 주세요.");
    Runnable validate =
        () -> {
          char[] a = pw.getPassword(), b = confirm.getPassword();
          try {
            validation.setText(
                !id.getText().matches("[A-Za-z0-9_]{3,24}")
                    ? "아이디: 영문·숫자·밑줄 3~24자"
                    : nick.getText().isBlank()
                        ? "닉네임을 입력해 주세요."
                        : a.length < 8
                            ? "비밀번호는 8자 이상입니다."
                            : !Arrays.equals(a, b)
                                ? "비밀번호 확인이 다릅니다."
                                : "입력 형식이 올바릅니다. 가입을 눌러 중복 여부를 확인하세요.");
          } finally {
            Arrays.fill(a, '\0');
            Arrays.fill(b, '\0');
          }
        };
    DocumentListener liveValidation =
        new DocumentListener() {
          public void insertUpdate(DocumentEvent e) {
            validate.run();
          }

          public void removeUpdate(DocumentEvent e) {
            validate.run();
          }

          public void changedUpdate(DocumentEvent e) {
            validate.run();
          }
        };
    for (JTextField input : new JTextField[] {id, nick, pw, confirm})
      input.getDocument().addDocumentListener(liveValidation);
    form.add(new JLabel("입력 확인"));
    form.add(validation);

    JPanel c = Theme.column();
    c.add(form);
    c.add(
        Theme.row(
            Theme.primary(
                "가입",
                () -> {
                  String i = id.getText().strip(), n = nick.getText().strip();
                  char[] a = pw.getPassword(), b = confirm.getPassword();
                  work(
                      () -> {
                        try {
                          service.register(i, n, a, b);
                          return i;
                        } finally {
                          Arrays.fill(a, '\0');
                          Arrays.fill(b, '\0');
                        }
                      },
                      this::login);
                }),
            Theme.button("취소", () -> login(id.getText()))));
    p.add(c);
    show("S03", p);
  }

  private void library() {
    if (service.user() == null) {
      login(service.demo ? "demo" : "");
      return;
    }
    work(
        () -> {
          List<Song> songs = service.songs();
          libraryRecords.clear();
          for (Song song : songs) libraryRecords.put(song.id(), service.phrases(song.id()));
          return songs;
        },
        songs -> {
          JPanel p = Theme.page("S04", "곡 보관함", service.user() + " 님의 소리가 한 주씩 쌓이는 곳");
          JTabbedPane tabs = new JTabbedPane();
          tabs.addTab("개인 곡", songList(songs, false));
          tabs.addTab("공동 곡 · 로컬 모의", songList(songs, true));
          p.add(tabs);
          p.add(
              Theme.row(
                  Theme.secondary("새 곡 만들기", this::createDialog),
                  Theme.button("초대 코드로 참여", () -> inviteDialog(null))),
              BorderLayout.SOUTH);
          show("S04", p);
        });
  }

  private JPanel songList(List<Song> songs, boolean shared) {
    JPanel p = new JPanel(new BorderLayout(12, 12));
    p.setBorder(new EmptyBorder(18, 18, 18, 18));
    JTextField search = new JTextField(20);
    JComboBox<String> sort = new JComboBox<>(new String[] {"이름 오름차순", "이름 내림차순"});
    DefaultListModel<Song> model = new DefaultListModel<>();
    JList<Song> list = new JList<>(model);
    list.setFixedCellHeight(122);
    list.setCellRenderer(new SongCell(libraryRecords));
    list.setFont(list.getFont().deriveFont(20f));
    Runnable filter =
        () -> {
          model.clear();
          songs.stream()
              .filter(s -> s.shared() == shared && s.name().contains(search.getText()))
              .sorted(
                  sort.getSelectedIndex() == 0
                      ? Comparator.comparing(Song::name)
                      : Comparator.comparing(Song::name).reversed())
              .forEach(model::addElement);
          if (!model.isEmpty()) list.setSelectedIndex(0);
        };
    filter.run();
    search
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              public void insertUpdate(DocumentEvent e) {
                filter.run();
              }

              public void removeUpdate(DocumentEvent e) {
                filter.run();
              }

              public void changedUpdate(DocumentEvent e) {
                filter.run();
              }
            });
    sort.addActionListener(e -> filter.run());
    p.add(Theme.row(new JLabel("검색"), search, sort), BorderLayout.NORTH);
    Runnable openSong =
        () -> {
          if (list.getSelectedValue() != null) {
            current = list.getSelectedValue();
            week = Model.monday(service.today());
            week();
          }
        };
    list.addMouseListener(
        new MouseAdapter() {
          public void mouseClicked(MouseEvent event) {
            if (event.getClickCount() == 2) openSong.run();
          }
        });
    list.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "open-song");
    list.getActionMap()
        .put(
            "open-song",
            new AbstractAction() {
              public void actionPerformed(ActionEvent e) {
                openSong.run();
              }
            });
    JScrollPane songsScroll = new JScrollPane(list);
    songsScroll.setBorder(null);
    p.add(songsScroll);
    p.add(
        Theme.row(
            Theme.button(
                "선택한 곡 열기",
                () -> {
                  if (list.getSelectedValue() != null) {
                    current = list.getSelectedValue();
                    week = Model.monday(service.today());
                    week();
                  }
                }),
            new JLabel(
                songs.stream().noneMatch(s -> s.shared() == shared)
                    ? "아직 곡이 없어요. 첫 곡을 만들어 보세요."
                    : "개인과 공동 기록은 곡별로 보관됩니다.")),
        BorderLayout.SOUTH);
    return p;
  }

  public void week() {
    if (editor != null && editor.dirty) {
      guard(this::week);
      return;
    }
    if (current == null) {
      library();
      return;
    }
    work(
        () -> {
          current = service.access(current.id());
          currentMembers = service.participants(current.id());
          return service.phrases(current.id());
        },
        records ->
            show(
                current.shared() ? "S08" : "S05",
                current.shared()
                    ? new SharedWeekPanel(this, current, records, currentMembers, week)
                    : new PersonalWeekPanel(this, current, records, week)));
  }

  void showJournal(List<Phrase> records) {
    show("S14", new JournalPanel(this, current, records, currentMembers, week, this::week));
  }

  void showScore() {
    score();
  }

  public void playFrom(
      List<MidiEngine.Part> parts,
      int length,
      int bpm,
      Map<Integer, Integer> levels,
      long startTick) {
    try {
      midi.playFrom(parts, length, bpm, levels, startTick);
    } catch (Exception e) {
      error(e);
    }
  }

  private Phrase find(List<Phrase> records, String user, LocalDate date) {
    return records.stream()
        .filter(f -> f.author().equals(user) && f.date().equals(date))
        .findFirst()
        .orElse(null);
  }

  void edit(Phrase value, LocalDate date) {
    Phrase draft =
        value == null
            ? new Phrase(
                current.id(),
                service.user(),
                date,
                0,
                current.bpm(),
                current.instrument(),
                "",
                List.of())
            : current.shared()
                ? new Phrase(
                    value.songId(),
                    value.author(),
                    value.date(),
                    value.version(),
                    current.bpm(),
                    value.instrument(),
                    value.memo(),
                    value.notes())
                : value;
    EditorPanel next = new EditorPanel(this, current, draft);
    show("S06", next);
    editor = next;
  }

  public void saveEditor(Runnable after) {
    if (editor == null) return;
    editor.stop();
    final Phrase draft;
    try {
      draft = editor.snapshot();
    } catch (Exception e) {
      error(e);
      return;
    }
    JPanel confirm = Theme.column();
    confirm.add(
        new JLabel(
            current.name()
                + " · "
                + draft.date()
                + " · "
                + INSTRUMENT_NAMES[instrumentIndex(draft.instrument())]));
    confirm.add(
        new JLabel(
            "음 "
                + draft.notes().size()
                + "개 · "
                + draft.bpm()
                + " BPM · 기존 버전 "
                + draft.version()));
    JTextArea memo = new JTextArea(draft.memo(), 2, 28);
    memo.setEditable(false);
    confirm.add(memo);
    confirm.add(new JLabel("이 기기의 로컬 저장소에 저장합니다."));
    if (JOptionPane.showConfirmDialog(this, confirm, "D03 · 저장 확인", JOptionPane.OK_CANCEL_OPTION)
        != JOptionPane.OK_OPTION) return;
    work(
        () -> service.save(draft),
        saved -> {
          editor.dirty = false;
          after.run();
        });
  }

  private void detail(Phrase value, LocalDate date, String author) {
    inspectRecord(value, date, author, this::week);
  }

  void inspectRecord(Phrase value, LocalDate date, String author, Runnable backAction) {
    detailBack = backAction;
    EnsembleData.Member member =
        currentMembers.stream()
            .filter(m -> m.id().equals(author))
            .findFirst()
            .orElse(new EnsembleData.Member(author, author, false));
    Phrase phrase =
        value == null
            ? new Phrase(
                current.id(),
                author,
                date,
                0,
                current.bpm(),
                current.instrument(),
                "기록 없음 · 쉼 한 마디",
                List.of())
            : value;
    JPanel p =
        Theme.page(
            "S07",
            member.nickname() + "의 한 마디",
            date
                + " · "
                + author
                + " · "
                + ArtAssets.instrumentName(phrase.instrument())
                + " · 읽기 전용");
    RecordInspector inspector = new RecordInspector(this);
    inspector.setRecord(value, date, member, current.bpm(), current.instrument());
    p.add(new JScrollPane(inspector));
    JPanel footer = Theme.column();
    footer.add(
        new Transport(
            this,
            "감상하기",
            () -> play(List.of(new MidiEngine.Part(phrase, 0, 0, 100)), Model.BAR, phrase.bpm())));
    footer.add(
        Theme.row(
            Theme.button("돌아가기", backAction),
            Theme.button(
                "MIDI 내보내기",
                () ->
                    exportMidi(
                        List.of(new MidiEngine.Part(phrase, 0, 0, 100)),
                        Model.BAR,
                        phrase.bpm()))));
    p.add(footer, BorderLayout.SOUTH);
    show("S07", p);
    viewerTimer =
        new javax.swing.Timer(
            40,
            e -> {
              inspector.updatePlayback(midi.position());
            });
    viewerTimer.start();
  }

  void moveWeek(int amount) {
    week = amount == 0 ? Model.monday(service.today()) : week.plusWeeks(amount);
    week();
  }

  void showInvite(Song song) {
    inviteDialog(song);
  }

  void showManagement() {
    manageSong();
  }

  void showEnsemble(List<Phrase> records, LocalDate date) {
    ensemble(records, date);
  }

  public void play(List<MidiEngine.Part> parts, int length, int bpm) {
    try {
      midi.play(parts, length, bpm);
    } catch (Exception e) {
      error(e);
    }
  }

  private void playWeek(List<Phrase> records) {
    List<MidiEngine.Part> parts = new ArrayList<>();
    for (int d = 0; d < 7; d++) {
      Phrase f = find(records, service.user(), week.plusDays(d));
      if (f != null) parts.add(new MidiEngine.Part(f, d * Model.BAR, 0, 100));
    }
    play(parts, 7 * Model.BAR, current.bpm());
  }

  private void ensemble(List<Phrase> records) {
    ensemble(records, service.today());
  }

  private void ensemble(List<Phrase> records, LocalDate date) {
    show("S09", new EnsemblePanel(this, current, records, currentMembers, week, date));
  }

  public void playMix(
      List<MidiEngine.Part> parts, int length, int bpm, Map<Integer, Integer> levels) {
    try {
      midi.play(parts, length, bpm, levels);
    } catch (Exception ex) {
      error(ex);
    }
  }

  private void placeholder(boolean management) {
    if (management) manageSong();
    else score();
  }

  private void manageSong() {
    Song expected = current;
    JPanel p =
        Theme.page(
            "S10", "곡·참여자 관리", "로컬 권한 확인 · 소유자: " + current.owner() + " · 저장하기 전 변경은 적용되지 않습니다.");
    JTextField name = new JTextField(current.name(), 26);
    JSpinner bpm = new JSpinner(new SpinnerNumberModel(current.bpm(), 40, 200, 1));
    JComboBox<String> instrument = new JComboBox<>(INSTRUMENT_NAMES);
    instrument.setSelectedIndex(instrumentIndex(current.instrument()));
    boolean owner = current.owner().equals(service.user());
    name.setEnabled(owner);
    bpm.setEnabled(owner);
    instrument.setEnabled(owner);
    JPanel c = new JPanel(new BorderLayout(12, 12));
    c.add(
        Theme.row(new JLabel("곡 이름"), name, new JLabel("공통 BPM"), bpm, instrument),
        BorderLayout.NORTH);
    Object[][] rows =
        current.members().stream()
            .sorted()
            .map(
                u ->
                    new Object[] {
                      u,
                      u.equals(current.owner()) ? "소유자" : "참여자",
                      u.equals(service.user()) ? "나" : "로컬 계정"
                    })
            .toArray(Object[][]::new);
    JTable table =
        new JTable(rows, new String[] {"계정", "권한", "구분"}) {
          public boolean isCellEditable(int r, int col) {
            return false;
          }
        };
    table.setRowHeight(36);
    c.add(new JScrollPane(table));
    c.add(tempoInfo(bpm), BorderLayout.SOUTH);
    p.add(c);
    JButton save =
        Theme.primary(
            "곡 설정 저장",
            () -> {
              String title = name.getText();
              int tempo = (int) bpm.getValue(), program = PROGRAMS[instrument.getSelectedIndex()];
              work(
                  () -> service.updateSong(expected, title, tempo, program),
                  song -> {
                    current = song;
                    week();
                  });
            });
    save.setEnabled(owner);
    JButton invite = Theme.button("초대 코드 만들기", () -> inviteDialog(current));
    invite.setEnabled(owner && current.shared());
    JButton leave =
        Theme.button(
            "공동곡 탈퇴",
            () -> {
              Object[] options = {"취소", "탈퇴"};
              if (JOptionPane.showOptionDialog(
                      this,
                      "이 곡의 접근 권한을 내려놓습니다. 기존 소리 기록은 곡에 남습니다.",
                      "D05 · 공동곡 탈퇴",
                      JOptionPane.DEFAULT_OPTION,
                      JOptionPane.WARNING_MESSAGE,
                      null,
                      options,
                      options[0])
                  == 1) {
                String id = current.id();
                work(
                    () -> {
                      service.leave(id);
                      return null;
                    },
                    v -> {
                      current = null;
                      library();
                    });
              }
            });
    leave.setEnabled(!owner && current.shared());
    p.add(
        Theme.row(save, invite, leave, Theme.button("취소 · 주간으로", this::week)), BorderLayout.SOUTH);
    show("S10", p);
  }

  private JLabel tempoInfo(JSpinner bpm) {
    JLabel info = Theme.muted("");
    Runnable update =
        () ->
            info.setText(
                "한 마디 약 "
                    + String.format(Locale.ROOT, "%.1f", 240.0 / (int) bpm.getValue())
                    + "초 · 저장하면 공통 BPM에 적용 · 기존 음·메모는 보존");
    bpm.addChangeListener(e -> update.run());
    update.run();
    return info;
  }

  private void score() {
    work(
        () -> service.phrases(current.id()),
        records -> {
          List<Phrase> bars = new ArrayList<>();
          for (int d = 0; d < 7; d++) {
            LocalDate day = week.plusDays(d);
            Phrase f = find(records, service.user(), day);
            bars.add(
                f != null
                    ? f
                    : new Phrase(
                        current.id(),
                        service.user(),
                        day,
                        0,
                        current.bpm(),
                        current.instrument(),
                        "쉼 한 마디",
                        List.of()));
          }
          ScorePanel sheet = new ScorePanel(current.name(), bars);
          JPanel p = Theme.page("S11", "악보·출력", "내 주간 기록 · 시간 비례 오선보 · 주황선과 숫자는 실제 음 길이, 빈 시간은 쉼");
          p.add(new JScrollPane(sheet));
          JComboBox<String> zoom = new JComboBox<>(new String[] {"75%", "100%", "125%", "150%"});
          zoom.setSelectedIndex(1);
          zoom.addActionListener(
              e -> sheet.zoom(new double[] {.75, 1, 1.25, 1.5}[zoom.getSelectedIndex()]));
          p.add(
              Theme.row(
                  new JLabel("확대"),
                  zoom,
                  Theme.button(
                      "인쇄 / PDF",
                      () -> {
                        PrinterJob printer = PrinterJob.getPrinterJob();
                        printer.setJobName("MMM - " + current.name());
                        printer.setPrintable(new ScorePanel(current.name(), bars));
                        if (printer.printDialog())
                          work(
                              () -> {
                                printer.print();
                                return null;
                              },
                              v -> {});
                      }),
                  Theme.button(
                      "MIDI 내보내기",
                      () -> exportMidi(weekParts(records), 7 * Model.BAR, current.bpm())),
                  Theme.button("주간으로", this::week)),
              BorderLayout.SOUTH);
          show("S11", p);
        });
  }

  private List<MidiEngine.Part> weekParts(List<Phrase> records) {
    List<MidiEngine.Part> parts = new ArrayList<>();
    for (int d = 0; d < 7; d++) {
      Phrase f = find(records, service.user(), week.plusDays(d));
      if (f != null) parts.add(new MidiEngine.Part(f, d * Model.BAR, 0, 100));
    }
    return parts;
  }

  public void exportMidi(List<MidiEngine.Part> parts, int length, int bpm) {
    stopSound();
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("MIDI 내보내기");
    chooser.setSelectedFile(new java.io.File("MMM.mid"));
    chooser.setFileFilter(
        new javax.swing.filechooser.FileNameExtensionFilter("MIDI 파일 (*.mid)", "mid"));
    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
    Path chosen = chooser.getSelectedFile().toPath();
    if (!chosen.toString().toLowerCase(Locale.ROOT).endsWith(".mid"))
      chosen = chosen.resolveSibling(chosen.getFileName() + ".mid");
    final Path path = chosen;
    if (Files.exists(path)
        && JOptionPane.showConfirmDialog(
                this, "같은 이름의 MIDI 파일을 덮어쓸까요?", "D05 · 파일 확인", JOptionPane.OK_CANCEL_OPTION)
            != JOptionPane.OK_OPTION) return;
    work(
        () -> {
          MidiEngine.export(path, parts, length, bpm);
          return null;
        },
        v ->
            JOptionPane.showMessageDialog(
                this,
                "MIDI 파일을 저장했습니다.\n" + path,
                "D05 · 내보내기 완료",
                JOptionPane.INFORMATION_MESSAGE));
  }

  private void createDialog() {
    JTextField name = new JTextField(24);
    JRadioButton personal = new JRadioButton("개인", true), shared = new JRadioButton("공동 · 로컬 모의");
    ButtonGroup group = new ButtonGroup();
    group.add(personal);
    group.add(shared);
    JSpinner bpm = new JSpinner(new SpinnerNumberModel(90, 40, 200, 1));
    JComboBox<String> inst = new JComboBox<>(INSTRUMENT_NAMES);
    inst.setSelectedIndex(instrumentIndex(settings.instrument()));
    JPanel p = Theme.column();
    p.add(Theme.row(new JLabel("곡 이름"), name));
    p.add(Theme.row(personal, shared));
    p.add(Theme.row(new JLabel("BPM"), bpm, new JLabel("악기"), inst));
    JLabel validation = Theme.muted("곡 이름을 입력해 주세요.");
    JLabel illustration = new JLabel(ArtAssets.instrument(PROGRAMS[inst.getSelectedIndex()], 70));
    p.add(Theme.row(illustration, validation));
    Runnable validate =
        () ->
            validation.setText(
                name.getText().isBlank()
                    ? "곡 이름을 입력해 주세요."
                    : "한 마디 약 "
                        + String.format(java.util.Locale.ROOT, "%.1f", 240.0 / (int) bpm.getValue())
                        + "초 · "
                        + (shared.isSelected() ? "공동곡" : "개인곡"));
    name.getDocument()
        .addDocumentListener(
            new DocumentListener() {
              public void insertUpdate(DocumentEvent e) {
                validate.run();
              }

              public void removeUpdate(DocumentEvent e) {
                validate.run();
              }

              public void changedUpdate(DocumentEvent e) {
                validate.run();
              }
            });
    bpm.addChangeListener(e -> validate.run());
    shared.addItemListener(e -> validate.run());
    inst.addItemListener(
        e -> {
          if (e.getStateChange() == ItemEvent.SELECTED)
            illustration.setIcon(ArtAssets.instrument(PROGRAMS[inst.getSelectedIndex()], 70));
        });

    if (JOptionPane.showConfirmDialog(this, p, "D01 · 새 곡 만들기", JOptionPane.OK_CANCEL_OPTION)
        == JOptionPane.OK_OPTION) {
      String title = name.getText();
      boolean joint = shared.isSelected();
      int tempo = (int) bpm.getValue(), program = PROGRAMS[inst.getSelectedIndex()];
      work(
          () -> service.create(title, joint, tempo, program),
          s -> {
            current = s;
            week = Model.monday(service.today());
            week();
          });
    }
  }

  private void inviteDialog(Song song) {
    if (song != null) {
      String id = song.id();
      work(
          () -> service.invite(id),
          value -> {
            JTextField code = new JTextField(value, 28);
            code.setEditable(false);
            JPanel p = Theme.column();
            p.add(new JLabel("같은 기기의 로컬 초대 · 발급일부터 7일간 유효"));
            p.add(code);
            p.add(
                Theme.button(
                    "코드 복사",
                    () -> {
                      Toolkit.getDefaultToolkit()
                          .getSystemClipboard()
                          .setContents(new java.awt.datatransfer.StringSelection(value), null);
                      code.selectAll();
                    }));
            JOptionPane.showMessageDialog(this, p, "D02 · 초대 코드", JOptionPane.INFORMATION_MESSAGE);
          });
      return;
    }
    JTextField code = new JTextField(28);
    JPanel p = Theme.column();
    p.add(new JLabel("소유자가 발급한 로컬 초대 코드를 입력하세요."));
    p.add(code);
    if (JOptionPane.showConfirmDialog(this, p, "D02 · 초대 참여", JOptionPane.OK_CANCEL_OPTION)
        == JOptionPane.OK_OPTION) {
      String value = code.getText().strip();
      work(
          () -> {
            service.join(value);
            return null;
          },
          v -> library());
    }
  }

  private void settings() {
    if (service.user() == null) {
      login(service.demo ? "demo" : "");
      return;
    }
    JPanel p = Theme.page("S12", "나에게 맞는 소리", "설정은 계정별로 이 기기에 저장됩니다.");
    JSlider volume = new JSlider(0, 100, settings.volume());
    JComboBox<String> inst = new JComboBox<>(INSTRUMENT_NAMES);
    inst.setSelectedIndex(instrumentIndex(settings.instrument()));
    JCheckBox count = new JCheckBox("실시간 녹음 전 카운트인", settings.countIn());
    JCheckBox metronome = new JCheckBox("카운트인·녹음 중 메트로놈 소리", settings.metronome());
    JSpinner countBeats = new JSpinner(new SpinnerNumberModel(settings.countInBeats(), 1, 8, 1));
    JTabbedPane tabs = new JTabbedPane();
    JPanel sound = Theme.column();
    JLabel level = Theme.muted(volume.getValue() + "%");
    volume.addChangeListener(e -> level.setText(volume.getValue() + "%"));
    sound.add(Theme.row(new JLabel("전체 음량"), volume, level));
    sound.add(Theme.row(new JLabel("기본 악기"), inst));
    sound.add(Theme.row(count, new JLabel("준비 박 수"), countBeats));
    sound.add(Theme.row(metronome));
    tabs.addTab("소리", sound);
    JTextArea account =
        new JTextArea(
            "로컬 계정: "
                + service.user()
                + "\n\n저장 위치: "
                + dataPath
                + "\n\n"
                + (service.demo
                    ? "명시적 시연 기준일: " + service.today() + "\n실제 날짜와 분리된 데모 데이터입니다."
                    : "날짜: 기기의 Asia/Seoul 오늘. 서버 연결 시 서버 날짜로 교체 예정.")
                + "\n\nSpring Boot / PostgreSQL 연동은 미구현입니다.");
    account.setEditable(false);
    account.setLineWrap(true);
    tabs.addTab("계정·데이터", account);
    p.add(tabs);
    p.add(
        Theme.row(
            Theme.primary(
                "설정 저장",
                () -> {
                  Settings next =
                      new Settings(
                          volume.getValue(),
                          PROGRAMS[inst.getSelectedIndex()],
                          count.isSelected(),
                          metronome.isSelected(),
                          (int) countBeats.getValue());
                  String u = service.user();
                  work(
                      () -> {
                        service.repository.settings(u, next);
                        return next;
                      },
                      s -> {
                        settings = s;
                        midi.volume(s.volume());
                        library();
                      });
                }),
            Theme.button("취소", this::library),
            Theme.button(
                "로그아웃",
                () -> {
                  if (JOptionPane.showConfirmDialog(
                          this, "로컬 계정에서 로그아웃할까요?", "D05 · 로그아웃", JOptionPane.OK_CANCEL_OPTION)
                      == JOptionPane.OK_OPTION) {
                    service.logout();
                    start();
                  }
                })),
        BorderLayout.SOUTH);
    show("S12", p);
  }

  private void help() {
    JPanel p = Theme.page("S13", "하루 한 마디, 어렵지 않아요.", "짧게 연주하고, 눈으로 다듬고, 다시 들어 보세요.");
    JTabbedPane topics = new JTabbedPane();
    topics.addTab(
        "1분 시연",
        helpText(
            "1. 로그인: demo / demo1234\n"
                + "2. 곡 보관함 → 나의 작은 일주일 → 오늘 편집\n"
                + "3. 단계 입력에서 A S D G 네 번\n"
                + "4. 주황 음을 드래그해 위치·높이를 변경\n"
                + "5. 음 선택 → 길이·높낮이·세기 조절\n"
                + "6. MIDI 미리 듣기 → 저장 → D03 확인\n"
                + "7. 주간의 ‘저장본 감상’ → 감상하기\n\n"
                + "건반 클릭 후 A S D F G H J K로 연주할 수 있어요."));
    topics.addTab(
        "입력·편집",
        helpText(
            "단계 입력: 누를 때마다 1박. 시간 제한 없이 4박을 채워요.\n"
                + "실시간 입력: ‘녹음 시작’ 후 4박 카운트인, 이어서 4박 녹음.\n"
                + "모든 입력은 1/4박 단위로 맞추고 마디 끝에서 자릅니다.\n"
                + "펜타토닉: 초보자도 어울리는 음을 쉽게 고릅니다.\n"
                + "음 선택 후 드래그는 시간·높이, 슬라이더는 길이·높낮이·세기.\n"
                + "‘쉼 1박’은 다음 입력 위치를 이동합니다. 빈 부분은 무음입니다.\n"
                + "포커스를 잃거나 화면을 옮기면 모든 소리를 멈춥니다.\n"
                + "메모는 20자, 공백 마디도 쉼으로 저장할 수 있어요.\n"
                + "Cmd/Ctrl+Z: 실행 취소, Shift+Cmd/Ctrl+Z: 다시 실행, Cmd/Ctrl+S: 저장.\n"
                + "건반 포커스에서 Space: 미리 듣기/정지. Escape: 모든 소리 정지.\n"
                + "재생 막대에서 위치를 이동하거나 일시정지·반복을 선택할 수 있습니다."));
    topics.addTab(
        "날짜·저장",
        helpText(
            "오늘: 새 기록 또는 기존 기록 편집.\n"
                + "지난 날짜: 읽기 전용 감상. 미래: 선택 불가.\n"
                + "저장본 감상은 오늘도 읽기 전용입니다.\n"
                + "일반 모드는 기기의 서울 날짜를 사용합니다 (서버 미연결).\n"
                + "시연 모드는 --demo-date=YYYY-MM-DD로 명시하며 저장소가 분리됩니다.\n"
                + "자정이 지나면 이전 날짜 초안 저장은 거부하고 내용을 유지합니다.\n"
                + "저장 실패 시 편집 초안 유지 → 문제 해결 후 저장 재시도.\n"
                + "버전 충돌 시 다른 저장본을 덮어쓰지 않습니다.\n"
                + "공동 합주는 같은 날짜·같은 BPM에서 동시에 시작합니다."));
    topics.addTab(
        "소리 덩어리",
        helpText(
            "덩어리는 음 하나의 의미를 담습니다.\n\n"
                + "꼬리가 길수록: 오래 소리남 (NoteEvent.duration).\n"
                + "몸통이 두꺼울수록: 세게 소리남 (NoteEvent.velocity).\n"
                + "위에 있을수록: 높은 음 (NoteEvent.pitch).\n"
                + "가로 위치: 소리가 시작되는 박 (NoteEvent.start).\n\n"
                + "기본은 먹색, 선택하면 주황 윤곽, 재생 중이면 주황으로 빛납니다.\n"
                + "감상 화면은 회색으로 보여 주고 편집 입력을 받지 않습니다.\n"
                + "선택한 덩어리 전체를 끌면 시간·높낮이가 바뀌고, 꼬리 끝을 끌면 길이가 바뀝니다.\n"
                + "키보드 방향키·오른쪽 슬라이더로도 정밀하게 다듬을 수 있습니다."));
    topics.addTab(
        "구현 범위",
        helpText(
            "Java Swing 2D GUI / Java Sound MIDI / 로컬 파일 저장\n"
                + "계정·공동곡·초대는 로컬 모의 구현이며 실제 서버 인증이 아닙니다.\n"
                + "S10 로컬 곡 설정·초대·탈퇴, S11 시간 비례 악보·인쇄를 제공합니다.\n"
                + "서버 날짜·HTTP·Spring Boot·PostgreSQL·원격 협업은 미구현입니다.\n"
                + "카운트인·소리 메트로놈·편집 실행 취소/다시 실행을 제공합니다.\n"
                + "주간 재생은 곡 BPM 하나를 기준으로 일곱 마디를 재생합니다.\n"
                + "서로 다른 날짜의 개별 BPM은 감상 화면에서 적용됩니다."));
    topics.addTab("수업 요소 안내", new ComponentGuide());
    p.add(topics);
    p.add(
        Theme.row(Theme.button("시작으로", this::start), Theme.button("곡 보관함", this::library)),
        BorderLayout.SOUTH);
    show("S13", p);
  }

  private JScrollPane helpText(String text) {
    JTextArea a = new JTextArea(text);
    a.setEditable(false);
    a.setFont(a.getFont().deriveFont(18f));
    a.setMargin(new Insets(25, 25, 25, 25));
    a.setLineWrap(true);
    a.setWrapStyleWord(true);
    a.setBackground(Theme.PAPER);
    return new JScrollPane(a);
  }

  public static void main(String[] args) throws Exception {
    Path root = Path.of(System.getProperty("user.home"), ".mmm");
    LocalDate demoDate = null;
    for (String a : args) {
      if (a.startsWith("--data=")) root = Path.of(a.substring(7));
      else if (a.startsWith("--demo-date=")) demoDate = LocalDate.parse(a.substring(12));
      else throw new IllegalArgumentException("알 수 없는 실행 옵션: " + a);
    }
    boolean demo = demoDate != null;
    Clock clock =
        demo
            ? Clock.fixed(
                demoDate.atTime(12, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant(),
                ZoneId.of("Asia/Seoul"))
            : Clock.system(ZoneId.of("Asia/Seoul"));
    Path directory = root.resolve(demo ? "demo-" + demoDate : "local");
    MusicService service = new MusicService(new LocalRepository(directory), clock, demo);
    SwingUtilities.invokeLater(
        () -> {
          Theme.install();
          new MMM(service, directory);
        });
  }
}
