# 연구 출처와 적용 범위

이 문서는 음음음 감정 화음 카탈로그의 **1차 연구 7편**을 정리합니다. 원문 결과의 간단한 요약과 제품 설계에 적용한 판단을 분리했습니다. DOI는 출처 추적용이며 논문 원문·음원·원시 응답을 이 저장소에 복제했다는 뜻이 아닙니다.

두 편의 2016년 Lahdelma–Eerola 논문은 제목과 DOI로 구분합니다. 카탈로그 내부 식별자에 A/B가 쓰이더라도, 다른 논문의 참고문헌에 쓰인 `2016a/2016b`와 일치한다고 가정하지 않습니다.

## 1. 단일 화음의 감정적 인상 — 직접 후보 관계의 출처

**Lahdelma, I., & Eerola, T. (2016). Single chords convey distinct emotional qualities to both naïve and expert listeners. _Psychology of Music, 44_(1), 37–54.**

- DOI: <https://doi.org/10.1177/0305735614552006>
- 저자 기관 자료: <https://durham-repository.worktribe.com/output/1443703/single-chords-convey-distinct-emotional-qualities-to-both-naive-and-expert-listeners>
- 출판 연도는 2016년이며 온라인 선공개는 2014년입니다.
- **실험:** 269명이 14개 화음·전위 자극을 피아노와 현악 음색으로 듣고 9개 차원에서 평가했습니다.
- **핵심 결과:** 화음 종류에 따라 감정 인상이 달랐고, 전위와 음색도 평가에 관여했습니다. 일부 화음은 그리움·향수 인상을 전달했습니다.
- **음음음 적용:** [CRITERIA.md](CRITERIA.md)의 9개 후보 연결 및 평균·SD의 직접 출처입니다. 해당 표는 두 음색과 화음별로 사용한 전위 조건을 합친 유형별 값입니다. 감·증화음은 기본위치만 사용했습니다.
- **적용 한계:** 현재 앱의 C4 기본위치·GM 피아노·4초 샘플을 이 연구가 직접 평가한 것은 아닙니다. 참가자 평균을 사용자 개인의 반응 확률로 바꾸지 않습니다.

## 2. 음역·불협화·선호도 — 샘플 조건을 기록할 근거

**Lahdelma, I., & Eerola, T. (2016). Mild dissonance preferred over consonance in single chord perception. _i-Perception, 7_(3).**

- DOI: <https://doi.org/10.1177/2041669516655812>
- 공개 원문: <https://pmc.ncbi.nlm.nih.gov/articles/PMC4934671/>
- **실험:** 최종 분석 410명. 15개 화음을 두 음역에서 피아노 음색으로 평가했으며 정서가·긴장·활력·협화·선호도의 1–7 척도를 사용했습니다. 자극은 4.8초였습니다.
- **핵심 결과:** 화음 종류·음역·전위에 따른 차이가 관찰됐고, 약한 불협화를 포함한 일부 화음의 선호도가 높았습니다.
- **음음음 적용:** 실제 샘플의 음역·전위·음색·길이를 남깁니다. 선호도, 정서가, 긴장감을 각각 다른 개념으로 취급합니다.
- **적용 한계:** 이 논문의 1–7 값과 앞 논문의 1–5 값을 같은 열에서 직접 비교하지 않습니다. ‘불협화=싫음’ 또는 ‘선호도 높음=행복’으로 바꾸지 않습니다.

### 이 연구에 연결된 공개 자극 자료

Harvard Dataverse 자료 DOI: <https://doi.org/10.7910/DVN/GE5PPL>

이 링크는 **공개 실험 자극의 위치를 안내하는 참고 항목**입니다. 이번 구현에서는 해당 데이터셋을 내려받거나 배포 파일에 포함하지 않았습니다. 앱의 미리 듣기는 자체 MIDI 구성입니다. 나중에 데이터셋을 사용할 때에는 실제 포함 파일, 버전, 이용 조건, 인용 형식을 확인하고 사용 기록을 별도로 남깁니다.

## 3. 자동 정서 점화 — 측정 방식이 다르면 결론도 구분

**Lahdelma, I., & Eerola, T. (2024). Valenced priming with acquired affective concepts in music: Automatic reactions to common tonal chords. _Music Perception, 41_(3), 161–175.**

- DOI: <https://doi.org/10.1525/mp.2024.41.3.161>
- 출판사 원문: <https://online.ucpress.edu/mp/article/41/3/161/199792/Valenced-Priming-with-Acquired-Affective-Concepts>
- **실험:** 각각 약 100명 규모의 4개 하위 실험에서 화음을 정서 점화 자극으로 사용했습니다. 긍정·부정 표적에 대한 자동 반응을 살폈습니다.
- **핵심 결과:** 감·증화음에서는 점화 효과가 지지됐지만, 단화음과 sus4에서는 해당 효과를 뒷받침하는 강한 증거가 확보되지 않았습니다.
- **음음음 적용:** 감정 인상의 자기보고, 자동 반응, 특정 감정의 실제 경험을 서로 다른 근거로 분류합니다.
- **적용 한계:** 단화음에서 점화 효과가 없었다는 결과를 ‘단화음은 슬프게 들릴 수 없다’로 해석하지 않습니다. 감·증화음의 부정적 정서 점화를 ‘공포’라는 구체 태그의 직접 검증으로 바꾸지도 않습니다.

## 4. 여러 음악 단서의 조작 — 화음 이외 조건도 필요

**Eerola, T., Friberg, A., & Bresin, R. (2013). Emotional expression in music: Contribution, linearity, and additivity of primary musical cues. _Frontiers in Psychology, 4_, 487.**

- DOI: <https://doi.org/10.3389/fpsyg.2013.00487>
- 공개 원문: <https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2013.00487/full>
- **실험:** 46명이 200개 음악 예시를 평가했습니다. 조성·템포·강약·아티큘레이션·음색·음역을 조작했습니다. 음악 경험이 풍부한 참가자가 많았습니다.
- **핵심 결과:** 여섯 단서 모두 감정 인상 평가에 기여했고 단서의 기여는 감정별로 달랐습니다.
- **음음음 적용:** 비교용 미리 듣기 조건을 고정하고, 편집기에서 악기·속도·음배치가 바뀌면 원문의 인상을 보장하지 않는다고 설명합니다.
- **적용 한계:** 단일 화음별 태그의 직접 출처는 아닙니다. 논문의 집단 평균 평정에 대한 설명력을 개인 감정 예측 정확도로 표시하지 않습니다. 200은 참가자 수가 아니라 음악 예시 수입니다.

## 5. 음악문화 노출 — 보편적 정답으로 표현하지 않을 근거

**Smit, E. A., Milne, A. J., Sarvasy, H. S., & Dean, R. T. (2022). Emotional responses in Papua New Guinea show negligible evidence for a universal effect of major versus minor music. _PLOS ONE, 17_(6), e0269597.**

- DOI: <https://doi.org/10.1371/journal.pone.0269597>
- 공개 원문: <https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0269597>
- **실험:** 파푸아뉴기니 170명과 시드니 비음악가 60명·음악가 19명에게 장단조 종지 및 선율을 비교하게 했습니다. 파푸아뉴기니 모형 분석에서는 면담 정보가 없는 1명을 제외했습니다.
- **핵심 결과:** 장단조와 행복 평가의 관계는 서양음악 노출 수준과 관련됐습니다. 노출이 가장 적은 공동체에서는 장조 종지의 더 높은 행복 평가에 대한 강한 증거가 없었습니다.
- **음음음 적용:** 문화·음악 경험과 개인차를 적용 한계에 포함하며 한국어 목표 사용자 평가를 계획합니다.
- **적용 한계:** 노출을 무작위 배정한 실험이 아닙니다. 저자들이 배제하지 않은 보편성 가능성을 ‘완전히 반증했다’고 표현하지 않습니다.

## 6. 지각된 감정과 느낀 감정 — 질문과 제품 명칭의 근거

**Merrill, J., Omigie, D., & Wald-Fuhrmann, M. (2020). Locus of emotion influences psychophysiological reactions to music. _PLOS ONE, 15_(8), e0237641.**

- DOI: <https://doi.org/10.1371/journal.pone.0237641>
- 공개 원문: <https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0237641>
- **실험:** 40명이 영화음악 32개를 들으며 음악이 표현하는 감정 또는 자신이 느낀 감정을 평가했습니다. 여러 생리적 반응을 연속 측정했습니다.
- **핵심 결과:** 같은 음악이라도 어느 감정에 주의를 두고 평가하는지에 따라 생리적 반응이 달랐습니다.
- **음음음 적용:** 태그는 ‘이 소리에서 지각되는 분위기’로 정의합니다. 자신의 현재 감정에 대한 질문이나 상태 추론과 합치지 않습니다.
- **적용 한계:** 특정 코드와 감정의 대응표를 제공하는 연구가 아닙니다. 음악의 인상과 실제 감정이 항상 무관하다는 주장도 뒷받침하지 않습니다.

## 7. 화성적 예상 위반 — 진행 문맥을 구분할 근거

**Steinbeis, N., Koelsch, S., & Sloboda, J. A. (2006). The role of harmonic expectancy violations in musical emotions: Evidence from subjective, physiological, and neural responses. _Journal of Cognitive Neuroscience, 18_(8), 1380–1393.**

- DOI: <https://doi.org/10.1162/jocn.2006.18.8.1380>
- 논문 정보: <https://pubmed.ncbi.nlm.nih.gov/16859422/>
- 저자 제공 원문: <https://stefan-koelsch.de/papers/Steinbeis%2B_JOCN18_2006_expectancy%2Bemotion.pdf>
- **실험:** 음악가 12명·비음악가 12명. 바흐 코랄 6개에서 화음 하나만 바꾼 세 버전을 비교했습니다.
- **핵심 결과:** 화성적으로 예상하기 어려운 사건일수록 긴장감·전반적 정서 강도·피부전기반응이 증가했습니다.
- **음음음 적용:** 단일 화음의 인상과 진행 속 역할을 구분합니다. 진행 단위 태그를 확장할 때는 조성과 앞뒤 화음을 함께 기록합니다.
- **적용 한계:** 긴장 증가가 곧 불안·공포·분노의 발생을 뜻하지 않습니다. 특정 서양 조성음악 문맥의 결과를 모든 음악에 그대로 적용하지 않습니다.

## 출처 자료를 관리하는 원칙

1. **서지와 사실을 요약해서 기록합니다.** 논문 PDF, 전문, 표 이미지, 저자 음원을 무단으로 저장소에 묶지 않습니다.
2. **수치의 소속을 분리합니다.** 원문 요약값은 `evidence.tsv`, 실제 재생 구성은 `chords.tsv`, 둘 사이의 제품 판단은 `links.tsv`와 선정 기준에 둡니다. `catalog.properties`는 스키마 버전·자료 종류·검토일·청취 검증 상태를 기록합니다.
3. **추가 해석을 명명합니다.** 한국어 태그 번역, 후보 선정, 화면 흐름은 음음음의 설계 결정입니다. 저자가 그 제품 설계를 검증했다는 뜻이 아닙니다.
4. **출처 정정이 가능해야 합니다.** 논문 제목·DOI·척도·음색 조건이 잘못되면 근거를 수정하고 연결 관계의 유지 여부를 다시 검토합니다.

자체 청취 결과가 생기면 이 일곱 편의 논문 수치에 섞지 않고 별도 조사 버전·재생 조건·응답 수를 가진 자료로 관리합니다. 구체적인 계획은 [VALIDATION.md](VALIDATION.md)에 있습니다.
