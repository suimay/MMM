# 기능별 재구성 이력 · 2026-11-25

과거 suimay 계정의 AI 협업 구현을 지금 기능별로 나눈 검토용 이력이다. 수인이 전부 손으로 작성했다는 의미가 아니다. 새 커밋은 실제 생성 시각을 사용하며 과거 날짜·작성자를 소급 지정하지 않았다.

- 기준 커밋: 93c9ac1. 기존 main·수인 기능 브랜치·팀원 이력과 사용자 미커밋 파일을 수정하지 않았다.
- 이 브랜치는 과거 작업 검토용이며 제품 main에 중복 병합하지 않는다.
- 이전 맥의 Java 소스·테스트 31개는 모두 기존 커밋과 바이트 단위로 일치했다. 새 구현으로 중복 귀속하지 않았다.
- Js105120의 f022ff6 서버·악기·실시간 입력과 후속 통합 변경은 제외했다.
- 개발 대화 ‘코드에 감정 붙이기’의 기준표·태그·앱 연결 요청을 6a9d99d와 대조했다. 초기 기능은 suimay Git 커밋을 1차 근거로 사용했다. ‘MMM 코드 클론 및 실행’은 실행 확인이며 새 구현 근거로 사용하지 않았다.
- 대화·계정명만으로 직접 작성자를 증명하지 않는다. 원본 커밋과 파일 일치를 추적한다.
- 같은 원본 단계의 여러 커밋은 서로 의존한다. 각 중간 커밋의 독립 실행을 보장하는 이력은 아니다.

## 새 구현·검증·자산 커밋 53개

| 새 커밋 | 범위 | 원본 | 파일 |
|---|---|---|---|
| [b8eb533](https://github.com/suimay/MMM/commit/b8eb5331b4a39c742713a9b57416031c2c118949) | 모델·계정·로컬 저장 | [babc799](https://github.com/suimay/MMM/commit/babc79949880e7d4691a7072b0288f89e913afb7) | app/src/main/java/mmm/LocalRepository.java<br>app/src/main/java/mmm/Model.java<br>app/src/main/java/mmm/MusicService.java<br>app/src/main/java/mmm/Repository.java |
| [0b77b67](https://github.com/suimay/MMM/commit/0b77b67ad85e7f47cf6797dbd05b401a0b484501) | MIDI·재생 제어 | [babc799](https://github.com/suimay/MMM/commit/babc79949880e7d4691a7072b0288f89e913afb7) | app/src/main/java/mmm/MidiEngine.java<br>app/src/main/java/mmm/Transport.java |
| [a09273f](https://github.com/suimay/MMM/commit/a09273f0f66eab714176d71e8be4b12c310e8cb4) | 연주 입력·시각 편집 | [babc799](https://github.com/suimay/MMM/commit/babc79949880e7d4691a7072b0288f89e913afb7) | app/src/main/java/mmm/EditorPanel.java<br>app/src/main/java/mmm/NoteCanvas.java<br>app/src/main/java/mmm/Piano.java |
| [0beb7aa](https://github.com/suimay/MMM/commit/0beb7aa2c12c2eb40476b169b4e2674931f5a03d) | 화면 연결·공통 UI | [babc799](https://github.com/suimay/MMM/commit/babc79949880e7d4691a7072b0288f89e913afb7) | app/src/main/java/mmm/MMM.java<br>app/src/main/java/mmm/ScorePanel.java<br>app/src/main/java/mmm/Theme.java |
| [252d9d6](https://github.com/suimay/MMM/commit/252d9d6c2abca2d7061922c814cf2c5775d3f194) | 회귀·GUI 검사 | [babc799](https://github.com/suimay/MMM/commit/babc79949880e7d4691a7072b0288f89e913afb7) | app/src/test/java/mmm/FeatureTest.java<br>app/src/test/java/mmm/GuiFlowTest.java<br>app/src/test/java/mmm/ModelRepositoryTest.java |
| [42a527e](https://github.com/suimay/MMM/commit/42a527efdb12cdcdf132b425a9250302ace3a0eb) | 실행·빌드 진입점 | [babc799](https://github.com/suimay/MMM/commit/babc79949880e7d4691a7072b0288f89e913afb7) | .gitignore<br>app/.gitignore<br>app/build.sh<br>app/demo.command<br>app/package-mac.sh<br>app/run.command<br>app/test-gui.sh |
| [4ec92dc](https://github.com/suimay/MMM/commit/4ec92dcb3ecf4b0257deaf3beaeb3fc0788849f9) | 기능 설명·디자인 지침 | [babc799](https://github.com/suimay/MMM/commit/babc79949880e7d4691a7072b0288f89e913afb7) | app/README.md<br>app/docs/VERIFICATION.md |
| [642b82b](https://github.com/suimay/MMM/commit/642b82b7c38d65ca606f0b373c97c79c2409f5b5) | 생성된 UI 자산·시연 캡처 | [babc799](https://github.com/suimay/MMM/commit/babc79949880e7d4691a7072b0288f89e913afb7) | app/docs/S01-start.png<br>app/docs/S04-library.png<br>app/docs/S05-saved.png<br>app/docs/S06-editor.png<br>app/docs/S07-listen.png<br>app/docs/S08-shared.png<br>app/docs/S09-ensemble.png<br>app/docs/S10-management.png<br>app/docs/S11-score.png<br>app/resources/MMM.icns |
| [6748190](https://github.com/suimay/MMM/commit/6748190c11de48591cd6c22b929a610b99e355f4) | MIDI·재생 제어 | [fff8121](https://github.com/suimay/MMM/commit/fff8121f12aaa9e7db7dbf3e388250bbced8d8e2) | app/src/main/java/mmm/Transport.java |
| [a91cd3a](https://github.com/suimay/MMM/commit/a91cd3acdebd7564de74e6a3e564e306a5e85d1e) | 연주 입력·시각 편집 | [fff8121](https://github.com/suimay/MMM/commit/fff8121f12aaa9e7db7dbf3e388250bbced8d8e2) | app/src/main/java/mmm/EditorPanel.java<br>app/src/main/java/mmm/NoteCanvas.java<br>app/src/main/java/mmm/Piano.java |
| [6d77ea6](https://github.com/suimay/MMM/commit/6d77ea6b4059e731e1274a17e99ec2e7da8fffcd) | 일기·주간 재생·프롤로그 | [fff8121](https://github.com/suimay/MMM/commit/fff8121f12aaa9e7db7dbf3e388250bbced8d8e2) | app/src/main/java/mmm/DayCard.java<br>app/src/main/java/mmm/SongCell.java |
| [b7ecb96](https://github.com/suimay/MMM/commit/b7ecb96af424fd3f590d3a0df02912ad6d4f2481) | 화면 연결·공통 UI | [fff8121](https://github.com/suimay/MMM/commit/fff8121f12aaa9e7db7dbf3e388250bbced8d8e2) | app/src/main/java/mmm/MMM.java<br>app/src/main/java/mmm/Theme.java |
| [ba87c30](https://github.com/suimay/MMM/commit/ba87c3074b71dd2c75b3c1fad0ecdfcd8fe1d2ef) | 회귀·GUI 검사 | [fff8121](https://github.com/suimay/MMM/commit/fff8121f12aaa9e7db7dbf3e388250bbced8d8e2) | app/src/test/java/mmm/GuiFlowTest.java<br>app/src/test/java/mmm/SoundShapeTest.java |
| [b9b1865](https://github.com/suimay/MMM/commit/b9b1865aa8fb464a57a5144bf8efe3b95625576d) | 실행·빌드 진입점 | [fff8121](https://github.com/suimay/MMM/commit/fff8121f12aaa9e7db7dbf3e388250bbced8d8e2) | app/build.sh<br>app/package-mac.sh<br>app/src/main/java/mmm/SoundShape.java |
| [68c3fa6](https://github.com/suimay/MMM/commit/68c3fa6b1261e3d030233e888e1080825d38d0b4) | 기능 설명·디자인 지침 | [fff8121](https://github.com/suimay/MMM/commit/fff8121f12aaa9e7db7dbf3e388250bbced8d8e2) | README.md<br>app/README.md<br>app/docs/DESIGN.md<br>app/docs/VERIFICATION.md |
| [ae5f6ea](https://github.com/suimay/MMM/commit/ae5f6ea1c896aa71d9b1fab58850cf18d91e2b58) | 생성된 UI 자산·시연 캡처 | [fff8121](https://github.com/suimay/MMM/commit/fff8121f12aaa9e7db7dbf3e388250bbced8d8e2) | app/docs/S01-start.png<br>app/docs/S04-library.png<br>app/docs/S05-saved.png<br>app/docs/S06-editor.png<br>app/docs/S06-minimum.png<br>app/docs/S07-listen.png<br>app/docs/S08-shared.png<br>app/docs/S09-ensemble.png<br>app/docs/S10-management.png<br>app/docs/S11-score.png<br>app/docs/sound-shape-semantics.png |
| [16cafc5](https://github.com/suimay/MMM/commit/16cafc5efc6be6a7c955796dba892e2a7115900c) | 모델·계정·로컬 저장 | [d578279](https://github.com/suimay/MMM/commit/d57827971e849164e452364d6ae37fe36bd32b4a) | app/src/main/java/mmm/MusicService.java |
| [14943bd](https://github.com/suimay/MMM/commit/14943bd3c8b9fbaf977bc2b6e323946114a12058) | MIDI·재생 제어 | [d578279](https://github.com/suimay/MMM/commit/d57827971e849164e452364d6ae37fe36bd32b4a) | app/src/main/java/mmm/MidiEngine.java |
| [5e72bd7](https://github.com/suimay/MMM/commit/5e72bd7424e40b84ce92469aa7f82dc2176ab3be) | 연주 입력·시각 편집 | [d578279](https://github.com/suimay/MMM/commit/d57827971e849164e452364d6ae37fe36bd32b4a) | app/src/main/java/mmm/EditorPanel.java<br>app/src/main/java/mmm/NoteCanvas.java |
| [747bdfb](https://github.com/suimay/MMM/commit/747bdfb0ef5aff179dca5bfb64f8d040500503fa) | 합주·작성자별 감상 | [d578279](https://github.com/suimay/MMM/commit/d57827971e849164e452364d6ae37fe36bd32b4a) | app/src/main/java/mmm/EnsembleData.java<br>app/src/main/java/mmm/EnsemblePanel.java<br>app/src/main/java/mmm/RecordInspector.java<br>app/src/main/java/mmm/SharedWeekPanel.java |
| [3ff6436](https://github.com/suimay/MMM/commit/3ff6436e7cea24c3d5d76a79ba0e00254ef3cd84) | 화면 연결·공통 UI | [d578279](https://github.com/suimay/MMM/commit/d57827971e849164e452364d6ae37fe36bd32b4a) | app/src/main/java/mmm/ArtAssets.java<br>app/src/main/java/mmm/ComponentGuide.java<br>app/src/main/java/mmm/MMM.java<br>app/src/main/java/mmm/Theme.java |
| [864ced8](https://github.com/suimay/MMM/commit/864ced83e3f58485cb29cf5adbaf88cdeb393822) | 회귀·GUI 검사 | [d578279](https://github.com/suimay/MMM/commit/d57827971e849164e452364d6ae37fe36bd32b4a) | app/src/test/java/mmm/ComponentEvidence.java<br>app/src/test/java/mmm/EnsembleDataTest.java<br>app/src/test/java/mmm/GuiFlowTest.java |
| [4ed30f0](https://github.com/suimay/MMM/commit/4ed30f0420334c167dce2c6da3966403e57b6327) | 실행·빌드 진입점 | [d578279](https://github.com/suimay/MMM/commit/d57827971e849164e452364d6ae37fe36bd32b4a) | app/build.sh<br>app/package-mac.sh |
| [ae9f5f8](https://github.com/suimay/MMM/commit/ae9f5f89b4e93c5d2a497eb8ae69ef9bb0c29d0c) | 기능 설명·디자인 지침 | [d578279](https://github.com/suimay/MMM/commit/d57827971e849164e452364d6ae37fe36bd32b4a) | README.md<br>app/README.md<br>app/docs/VERIFICATION.md<br>app/docs/components.md |
| [5b68d83](https://github.com/suimay/MMM/commit/5b68d8362930a2ab355cb5ec8ecf71c43725efe0) | 생성된 UI 자산·시연 캡처 | [d578279](https://github.com/suimay/MMM/commit/d57827971e849164e452364d6ae37fe36bd32b4a) | app/docs/D01-dialog.png<br>app/docs/D02-dialog.png<br>app/docs/D03-dialog.png<br>app/docs/D04-dialog.png<br>app/docs/D05-dialog.png<br>app/docs/S01-start.png<br>app/docs/S02-login.png<br>app/docs/S03-signup.png<br>app/docs/S04-library.png<br>app/docs/S06-editor.png<br>app/docs/S06-minimum.png<br>app/docs/S07-friend.png<br>app/docs/S07-listen.png<br>app/docs/S08-shared.png<br>app/docs/S09-ensemble.png<br>app/docs/S09-part-detail.png<br>app/docs/S09-playing.png<br>app/docs/S10-management.png<br>app/docs/S11-score.png<br>app/docs/S12-settings.png<br>app/docs/S13-components.png<br>app/docs/S13-help.png<br>app/resources/images/avatar-demo.png<br>app/resources/images/avatar-moon.png<br>app/resources/images/avatar-star.png<br>app/resources/images/instrument-0.png<br>app/resources/images/instrument-10.png<br>app/resources/images/instrument-12.png<br>app/resources/images/instrument-24.png |
| [842c380](https://github.com/suimay/MMM/commit/842c3807615a45bbd570be4b4a5322da2988b72c) | MIDI·재생 제어 | [183562b](https://github.com/suimay/MMM/commit/183562bb06e74ac3d4a48086dd9e6a0443e81545) | app/src/main/java/mmm/MidiEngine.java |
| [0bd352c](https://github.com/suimay/MMM/commit/0bd352c4118b4ae5ac6be5abfbf26886d3d3e46b) | 연주 입력·시각 편집 | [183562b](https://github.com/suimay/MMM/commit/183562bb06e74ac3d4a48086dd9e6a0443e81545) | app/src/main/java/mmm/EditorPanel.java<br>app/src/main/java/mmm/NoteCanvas.java<br>app/src/main/java/mmm/Piano.java |
| [29d11eb](https://github.com/suimay/MMM/commit/29d11eb9550f3780782ef3859930311a4fd92a0b) | 합주·작성자별 감상 | [183562b](https://github.com/suimay/MMM/commit/183562bb06e74ac3d4a48086dd9e6a0443e81545) | app/src/main/java/mmm/EnsemblePanel.java<br>app/src/main/java/mmm/RecordInspector.java<br>app/src/main/java/mmm/SharedWeekPanel.java |
| [79f91a4](https://github.com/suimay/MMM/commit/79f91a4ef47978af64d993f03350837459934bf1) | 일기·주간 재생·프롤로그 | [183562b](https://github.com/suimay/MMM/commit/183562bb06e74ac3d4a48086dd9e6a0443e81545) | app/src/main/java/mmm/DayCard.java<br>app/src/main/java/mmm/DayPlaybackBar.java<br>app/src/main/java/mmm/JournalPanel.java<br>app/src/main/java/mmm/PersonalWeekPanel.java<br>app/src/main/java/mmm/ProloguePanel.java |
| [739f7a5](https://github.com/suimay/MMM/commit/739f7a5ac5f80c0a0fe68a0250f4646434b66f0a) | 화면 연결·공통 UI | [183562b](https://github.com/suimay/MMM/commit/183562bb06e74ac3d4a48086dd9e6a0443e81545) | app/src/main/java/mmm/ComponentGuide.java<br>app/src/main/java/mmm/MMM.java |
| [cd12986](https://github.com/suimay/MMM/commit/cd12986c83667bb8d0341704396754488cf777b8) | 회귀·GUI 검사 | [183562b](https://github.com/suimay/MMM/commit/183562bb06e74ac3d4a48086dd9e6a0443e81545) | app/src/test/java/mmm/ComponentEvidence.java<br>app/src/test/java/mmm/GuiFlowTest.java |
| [007768f](https://github.com/suimay/MMM/commit/007768fd2d024107080eb7b380cc463044e3ae4c) | 실행·빌드 진입점 | [183562b](https://github.com/suimay/MMM/commit/183562bb06e74ac3d4a48086dd9e6a0443e81545) | app/package-mac.sh |
| [a27b4ca](https://github.com/suimay/MMM/commit/a27b4ca10037a15183d606dff251e9d64b86ea0d) | 기능 설명·디자인 지침 | [183562b](https://github.com/suimay/MMM/commit/183562bb06e74ac3d4a48086dd9e6a0443e81545) | README.md<br>app/README.md<br>app/docs/DESIGN.md<br>app/docs/UX-REVIEW.md<br>app/docs/VERIFICATION.md<br>app/docs/components.md |
| [b42e8f6](https://github.com/suimay/MMM/commit/b42e8f6d2d0b754d2cacbf078a5c2b28e161dca8) | 생성된 UI 자산·시연 캡처 | [183562b](https://github.com/suimay/MMM/commit/183562bb06e74ac3d4a48086dd9e6a0443e81545) | app/docs/D02-dialog.png<br>app/docs/D04-dialog.png<br>app/docs/S01-minimum.png<br>app/docs/S01-start.png<br>app/docs/S02-login.png<br>app/docs/S03-signup.png<br>app/docs/S04-library.png<br>app/docs/S05-personal.png<br>app/docs/S05-saved.png<br>app/docs/S06-editor.png<br>app/docs/S06-minimum.png<br>app/docs/S07-friend.png<br>app/docs/S07-listen.png<br>app/docs/S08-shared.png<br>app/docs/S09-ensemble.png<br>app/docs/S09-part-detail.png<br>app/docs/S09-playing.png<br>app/docs/S10-management.png<br>app/docs/S11-score.png<br>app/docs/S12-settings.png<br>app/docs/S13-components.png<br>app/docs/S13-help.png<br>app/docs/S14-journal.png<br>app/docs/S14-minimum.png |
| [ba444d3](https://github.com/suimay/MMM/commit/ba444d3639ce16374a0e52bafe1ed539fa07de74) | 일기·주간 재생·프롤로그 | [7e0702b](https://github.com/suimay/MMM/commit/7e0702b7e13635ccb6c5df918aa5f6373e87cc6b) | app/src/main/java/mmm/ProloguePanel.java |
| [5c0616f](https://github.com/suimay/MMM/commit/5c0616fe3d7b96932e563b91868478a191156f58) | 화면 연결·공통 UI | [7e0702b](https://github.com/suimay/MMM/commit/7e0702b7e13635ccb6c5df918aa5f6373e87cc6b) | app/src/main/java/mmm/ComponentGuide.java<br>app/src/main/java/mmm/MMM.java |
| [7b7a5aa](https://github.com/suimay/MMM/commit/7b7a5aa7467e93503bb1037c994a6adef92c3db2) | 회귀·GUI 검사 | [7e0702b](https://github.com/suimay/MMM/commit/7e0702b7e13635ccb6c5df918aa5f6373e87cc6b) | app/src/test/java/mmm/GuiFlowTest.java |
| [ffc24d6](https://github.com/suimay/MMM/commit/ffc24d6d2cdda23e9355be36e047361e75693c0d) | 실행·빌드 진입점 | [7e0702b](https://github.com/suimay/MMM/commit/7e0702b7e13635ccb6c5df918aa5f6373e87cc6b) | app/package-mac.sh |
| [f30f11a](https://github.com/suimay/MMM/commit/f30f11af3af7e0de259fab45b91e906576cd6224) | 기능 설명·디자인 지침 | [7e0702b](https://github.com/suimay/MMM/commit/7e0702b7e13635ccb6c5df918aa5f6373e87cc6b) | README.md<br>app/README.md<br>app/docs/DESIGN.md<br>app/docs/UX-REVIEW.md<br>app/docs/VERIFICATION.md<br>app/docs/components.md |
| [0a9e83a](https://github.com/suimay/MMM/commit/0a9e83a94743fe64fa85b8e6b4b21147fb8a185a) | 생성된 UI 자산·시연 캡처 | [7e0702b](https://github.com/suimay/MMM/commit/7e0702b7e13635ccb6c5df918aa5f6373e87cc6b) | app/docs/S01-minimum.png<br>app/docs/S01-start.png<br>app/docs/S13-components.png |
| [5b6c50a](https://github.com/suimay/MMM/commit/5b6c50a3cd7c62bd2fc463b55e0fd2b898c48e44) | 일기·주간 재생·프롤로그 | [11c7a9b](https://github.com/suimay/MMM/commit/11c7a9bab61632d47bc4756e0a3f335970fabe84) | app/src/main/java/mmm/ProloguePanel.java |
| [398098d](https://github.com/suimay/MMM/commit/398098d504ca78604dd97b813305227cf04ee91b) | 화면 연결·공통 UI | [11c7a9b](https://github.com/suimay/MMM/commit/11c7a9bab61632d47bc4756e0a3f335970fabe84) | app/src/main/java/mmm/Theme.java |
| [37f37af](https://github.com/suimay/MMM/commit/37f37af1f543e01f029be67ff128e651b8d0d0cc) | 회귀·GUI 검사 | [11c7a9b](https://github.com/suimay/MMM/commit/11c7a9bab61632d47bc4756e0a3f335970fabe84) | app/src/test/java/mmm/SoundShapeTest.java |
| [6393802](https://github.com/suimay/MMM/commit/6393802f72bdab24bb199a85d35552325a3d76c8) | 기능 설명·디자인 지침 | [11c7a9b](https://github.com/suimay/MMM/commit/11c7a9bab61632d47bc4756e0a3f335970fabe84) | README.md<br>app/README.md<br>app/docs/DESIGN.md<br>app/docs/UX-REVIEW.md |
| [97b88d2](https://github.com/suimay/MMM/commit/97b88d2a541f5c5438385d50cb5f279ae6cb093b) | MIDI·재생 제어 | [6a9d99d](https://github.com/suimay/MMM/commit/6a9d99dfd3f258d47c6eae62e6c69c6d6e2597c2) | app/src/main/java/mmm/MidiEngine.java |
| [2877e8d](https://github.com/suimay/MMM/commit/2877e8d3313fc173895d65305c5324811c0c480b) | 연주 입력·시각 편집 | [6a9d99d](https://github.com/suimay/MMM/commit/6a9d99dfd3f258d47c6eae62e6c69c6d6e2597c2) | app/src/main/java/mmm/EditorPanel.java |
| [8c691ea](https://github.com/suimay/MMM/commit/8c691ea9226a708b8aa952bf02c15fd93327b688) | 화면 연결·공통 UI | [6a9d99d](https://github.com/suimay/MMM/commit/6a9d99dfd3f258d47c6eae62e6c69c6d6e2597c2) | app/src/main/java/mmm/MMM.java |
| [a3fa361](https://github.com/suimay/MMM/commit/a3fa3615416a097163e8bcf5d7d3a3ea7375e1f0) | 근거·화음 데이터·로더 | [6a9d99d](https://github.com/suimay/MMM/commit/6a9d99dfd3f258d47c6eae62e6c69c6d6e2597c2) | app/resources/mmm/emotion/catalog.properties<br>app/resources/mmm/emotion/chords.tsv<br>app/resources/mmm/emotion/evidence.tsv<br>app/resources/mmm/emotion/links.tsv<br>app/resources/mmm/emotion/sources.tsv<br>app/resources/mmm/emotion/tags.tsv<br>app/src/main/java/mmm/EmotionCatalog.java |
| [4dfa7db](https://github.com/suimay/MMM/commit/4dfa7dbd5120a3804cf3059768e4f6f33636e744) | 분위기 선택·화음 미리듣기 | [6a9d99d](https://github.com/suimay/MMM/commit/6a9d99dfd3f258d47c6eae62e6c69c6d6e2597c2) | app/src/main/java/mmm/EmotionPalette.java |
| [1e0f226](https://github.com/suimay/MMM/commit/1e0f22665fe7583d55b8a1e97b1a1c32073e8b30) | 회귀·GUI 검사 | [6a9d99d](https://github.com/suimay/MMM/commit/6a9d99dfd3f258d47c6eae62e6c69c6d6e2597c2) | app/src/test/java/mmm/EmotionCatalogTest.java<br>app/src/test/java/mmm/EmotionGuiTest.java |
| [8538890](https://github.com/suimay/MMM/commit/8538890172b3cb0ae7c7e7a7985f35700774a572) | 실행·빌드 진입점 | [6a9d99d](https://github.com/suimay/MMM/commit/6a9d99dfd3f258d47c6eae62e6c69c6d6e2597c2) | app/.gitignore<br>app/build.sh<br>app/review-emotions.command<br>app/test-emotions-gui.sh |
| [0e94376](https://github.com/suimay/MMM/commit/0e9437642594a94cdc1baa85e914e655c5a1d0d1) | 기능 설명·디자인 지침 | [6a9d99d](https://github.com/suimay/MMM/commit/6a9d99dfd3f258d47c6eae62e6c69c6d6e2597c2) | README.md<br>app/README.md<br>app/docs/emotion/CRITERIA.md<br>app/docs/emotion/README.md<br>app/docs/emotion/SOURCES.md<br>app/docs/emotion/VALIDATION.md |
| [d8cc8cd](https://github.com/suimay/MMM/commit/d8cc8cd874abca18310235cce0065fedbf51060f) | 생성된 UI 자산·시연 캡처 | [6a9d99d](https://github.com/suimay/MMM/commit/6a9d99dfd3f258d47c6eae62e6c69c6d6e2597c2) | app/docs/emotion/editor-with-chord.png<br>app/docs/emotion/evidence.png<br>app/docs/emotion/palette-minimum.png<br>app/docs/emotion/palette.png |

## 제외·보존

생성된 이미지와 시연 캡처는 assets 커밋으로 분리했다. 실행에 쓰는 아이콘·이미지는 유지했다. JDK·빌드 캐시·계정·개인 기록·개발 대화·비공개 링크·발표용 미추적 묶음은 가져오지 않았다. 다음 컴파일·패키지 산출물은 재추가하지 않았다.

- babc799: downloads/MMM-2.0.jar
- fff8121: downloads/MMM-2.1.jar
- d578279: app/docs/presentation-captures.zip
- d578279: downloads/MMM-2.2.jar
- 183562b: app/docs/presentation-captures.zip
- 183562b: downloads/MMM-2.3.jar
- 7e0702b: app/docs/presentation-captures.zip
- 7e0702b: downloads/MMM-2.3.1.jar
- 6a9d99d: downloads/MMM-emotion-preview.jar

원본 문서의 과거 배포 설명은 역사적 기록이다. 이 브랜치는 app/build.sh로 새로 빌드하고 app/demo.command 또는 app/review-emotions.command로 실행한다. 최신 디자인 지침은 app/docs/DESIGN.md이며 과거 외형을 유지 조건으로 취급하지 않는다.

## 검증

- 원본 단계별 구현·테스트·자산의 SHA-256 일치를 확인했다. README 2개에는 재구성·디자인 안내만 추가했다.
- app/test-gui.sh: Java 21 빌드·기능 307개 및 실제 Swing 흐름 73개 통과.
- app/test-emotions-gui.sh: 실제 화음 GUI 검사 20개 통과.
- GUI 검사가 생성한 캡처·컴포넌트 설명표는 원본 상태로 되돌려 생성물 차이를 과거 구현 변경으로 기록하지 않았다.
- 공개 전 새 텍스트 파일을 비밀 키·자격 증명·서명 URL·비공개 대화 링크 패턴으로 검사했다.
- 실제 청음·다른 물리 기기·원격 서버·Mac 패키지는 이 과거 재구성 검증에 포함하지 않는다.
