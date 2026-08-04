# 백엔드/DB 추가 개발 필요 항목

> 2026-08-03 기준 현재 구현 상태를 감사(audit)해서 정리한 목록. CORS(구 1번)는 이미 해결됨.

## 🟡 기획 스펙 대비 미구현

### 2. 보조 사보타주 유형(secondaryType) — ✅ 해결(2026-08-03)
- `CulpritProfileRegistry.pickSecondaryType()`로 세션 생성 시 주 유형 제외 나머지 2개 중 랜덤 배정, `SessionService.pickTonightType()`로 매일 밤 주 80%/보조 20% 가중 랜덤 선택(`GameConstants.SECONDARY_SABOTAGE_TYPE_CHANCE_PERCENT`).
- 실제 Aiven DB에 세션 5개 생성해 `primary_type`/`secondary_type`이 항상 다르게 배정됨을 확인. `primaryType`/`secondaryType`은 어떤 컨트롤러/DTO에도 노출 안 됨(스포일러 위험 없음, 확인 완료).

### 3. 이동(움직임) API — ✅ 해결(2026-08-03 최초 구현, 2026-08-04 기획서 개정으로 모델 교체)
- **2026-08-04 업데이트**: 기획서 PDF가 개정되면서 이동 체력 소모 방식이 "1회당 5(운동화 4)" 이산 모델에서 **"이동 중 초당 0.15(운동화 착용 시 초당 0.12로 20% 감소), 정지 시 소모 없음"** 연속 모델로 바뀜 — 방향키 자유이동에서 "1회"가 뭘 기준으로 하는지 불명확했던 문제가 이걸로 해결됨. `MoveRequest`가 초 단위(`seconds`, 프론트가 실제 움직인 시간을 누적해 보고)를 받도록, `GameConstants.MOVE_STAMINA_PER_SECOND(_WITH_SNEAKERS)`도 이 값으로 교체됨(팀원이 `ef23c99`에서 프론트·백엔드 동시 반영, 프론트는 `DayScene.ts`가 1초 간격으로 배치 보고). 기존 `POST /api/sessions/{sessionId}/move` 엔드포인트/기절 시 409 동작은 그대로 유지, 소모량 계산 방식만 교체.
- 신버전 PDF 자체가 6페이지(체력 세부 수치표: 초당 0.15→0.12)와 9페이지(상점 아이템표, 운동화 효과: "5→4")가 서로 다른 문구로 남아있는 내부 모순이 있었음 — 우리 쪽 `DataSeeder`의 운동화 `effectDesc`와 `GameSession.sneakersEquipped` 주석도 옛 9페이지 문구("5→4")를 그대로 베껴서 갖고 있던 걸 발견, 6페이지 기준(초당 0.15→0.12)으로 갱신하고 Aiven·로컬 docker DB의 `shop_item_master` 실 데이터도 함께 UPDATE함. PDF 자체의 모순은 기획팀에 9페이지도 갱신해달라고 별도 확인 필요.
- **범위 한정은 여전히 유효**: 서버는 이동 시작/종료 판정(실제로 움직였는지)을 프론트에 맡기고 보고받은 시간만큼만 소모시킴 — 플레이어 실시간 좌표 자체는 서버가 추적하지 않음.

### 4. 랜덤 이벤트 내용 — ✅ 해결(2026-08-03, LLM 생성 파이프라인)
- `llm-proxy`에 `/internal/llm/event` 신설(`LlmService.generateEventContent()`) — eventType/대상(VILLAGE·PLAYER)/일차/오답으로 지목된 NPC 이름만 받아 연출 텍스트를 생성. **실제 범인 정보는 이 메서드에 아예 전달하지 않아 응답이 범인을 암시할 수 없음.**
- `AccusationService.logRandomEvent()`가 이 엔드포인트를 호출해 `RandomEventLog.description`을 채움. 실제 Aiven DB에서 7일차(마을 대상)/8일차(플레이어 대상) 두 이벤트 모두 실제 Claude 생성 텍스트로 채워지는 것 확인.
- LLM 호출 실패 시 llm-proxy 자체에서 대체 문구로 폴백(다른 생성 경로와 동일 패턴).

### 5. 엔딩 스토리 — ✅ 해결(2026-08-03, LLM 생성 파이프라인 + 캐시)
- `llm-proxy`에 `/internal/llm/ending` 신설(`LlmService.generateEndingStory()`) — 범인 이름/역할/나이/성격/말투/동기/사보타주 유형/구체적으로 벌인 일을 받아 1인칭 자백 톤 엔딩 스토리 생성.
- `NpcCaseAssignment`에 `ending_story_text` 컬럼 추가(캐시) — `AccusationService.getOrGenerateEndingStory()`가 최초 조회 시에만 LLM을 호출하고 이후 조회는 캐시된 텍스트를 즉시 반환(재호출 비용/지연 방지). `getEnding()`을 `readOnly` 트랜잭션에서 쓰기 가능하도록 변경.
- 실제 세션으로 전체 플로우(오답 2회 → 정답 고발 → ending 조회 2회) 검증 완료: 1차 조회는 실제 Claude 호출(약 20초, NPC 페르소나·동기에 맞는 사투리 섞인 1인칭 서사), 2차 조회는 캐시로 즉시 반환(동일 텍스트, LLM 재호출 없음) 확인.
- 배드엔딩(9일차 실패)은 기획서상 "범인 개별 스토리 공개"가 성공 케이스에만 명시돼 있어 LLM 콘텐츠 없이 기존 일반 문구 그대로 유지 — 범인 정체 스포일러 방지 목적도 겸함.
- Aiven·로컬 docker DB 모두 `ending_story_text` 컬럼 반영, 스키마 동일하게 유지.

## 🟢 품질/구조

### 6. 테스트 커버리지 — ✅ 해결(2026-08-03, 최소 범위)
- `SessionServiceTest`(4), `AccusationServiceTest`(6), `GameSaveServiceTest`(6) 추가 — Mockito로 리포지토리/협력 서비스를 목킹한 순수 단위 테스트(Spring 컨텍스트/DB 불필요, 빠름).
- 커버 범위: `SessionService.advanceDay()`(정상 전이/기절 복귀치/9일차 배드엔딩/이미 종료된 세션 예외), `AccusationService.accuse()`(정답/오답 판정, 7·8일차 랜덤 이벤트 발생, 9일차 배드엔딩, 유효 기간 밖 예외), `GameSaveService`(저장/기본값 로드/`ending_state` 동기화).
- `./gradlew test`로 5회 연속 재실행해 랜덤 로직(사보타주 보조 유형 20% 확률 등) 관련 플레이키 없음 확인.
- 남은 범위(전 항목 커버는 아님): `DialogueService`/`ShopService`/`FarmService`/`InventoryService` 등은 아직 테스트 없음 — 필요 시 추가 권장.

### 7. `game_save.ending_state` 연동 — ✅ 해결(2026-08-03)
- 결정: **게임 종료 시점에 서버가 자동으로 동기화**하는 쪽으로 진행(세이브 기능이 진행 상태만 추적하는 대안은 채택 안 함).
- `GameSaveService.syncEndingState(account, endingState)` 신설 — 해당 계정에 기존 세이브가 있으면 `ending_state`만 갱신(`save_data`는 건드리지 않음), 세이브가 없으면 아무 것도 안 함.
- 호출 지점 2곳: `AccusationService.accuse()`(정답 → `success`, 9일차 오답 → `bad_ending`), `SessionService.advanceDay()`(9일차에 고발 없이 그냥 다음 날로 넘기려 할 때의 강제 `bad_ending`).
- 실제 Aiven DB로 두 경로 모두 검증 완료: (1) 세이브 생성 후 세션을 9일차까지 진행해 고발 없이 종료 → `ending_state`가 `in_progress`→`bad_ending`으로 자동 갱신, (2) 별도 세션에서 정답 고발 → `success`로 자동 갱신. 두 경우 모두 `save_data`(스냅샷 JSON)는 그대로 유지됨.

### 9. backend↔llm-proxy read timeout이 실제 LLM 응답 속도보다 빡빡함 — ✅ 해결(2026-08-03)
- `config/LlmProxyRestClientConfig.java`의 read timeout을 20초 → 45초로 상향.
- 문제 확인 경위: 실제 Aiven DB로 정답 고발 → 엔딩 조회(최초 생성) 전체 플로우를 재현했더니 21.3초가 걸림 — 기존 20초 타임아웃이면 이 정상 응답도 504로 끊겼을 상황. 페르소나 생성(~23초)·랜덤 이벤트 연출(~20~24초)도 같은 경계에 걸쳐 있었음.
- connect timeout(3초)은 그대로 유지 — 응답 지연이 아니라 연결 자체가 안 되는 경우는 여전히 빠르게 감지됨.

### 10. 단서 카드 콘텐츠 — NPC 외형 기반 LLM 생성 (신규 기능, 2026-08-03)
- 계기: NPC 에셋(`기타/2. 에셋/NPC/*/metadata.json`)에 캐릭터 생성용 외형 묘사 프롬프트(머리색/길이, 체격, 복장 등)가 이미 있다는 걸 발견 — 이를 `Npc.appearanceDesc`(신규 컬럼)로 옮겨 단서 카드 생성 LLM의 입력 재료로 사용.
- `llm-proxy`에 `/internal/llm/clue`(최초 생성)·`/internal/llm/clue/clarify`(돋보기 사용 시 갱신) 신설 — 단서 5주제(머리카락/소지품/발자국/혈흔/자국)별 작성 가이드를 프롬프트에 포함, 범인 이름/정체는 절대 언급하지 않도록 지시.
- `SessionService.generateNightSabotage()`가 매일 밤 단서 생성 시 이 API를 호출(기존 정적 템플릿 문구 대체), `ClueService.clarify()`도 돋보기 사용 시 기존 "일반 문구 덧붙이기" 대신 이 API로 실제 명확화된 문구를 생성하도록 교체.
- 실제 세션(범인: 명자유 — "어깨 아래까지 오는 길고 곧은 검은 머리, 눈 한쪽을 가리는 무거운 앞머리")으로 검증: 최초 HAIR 단서가 "검고 긴 머리카락... 어깨 아래까지 자랄 만큼" 생성, 돋보기로 명확화하자 "눈썹 아래를 덮을 만큼 짧은 앞머리"가 추가로 드러남 — 둘 다 실제 외형 묘사와 정확히 일치하면서도 이름은 노출 안 됨.
- Aiven·로컬 docker `npc.appearance_desc` 컬럼 추가 + 기존 NPC 7명 데이터 백필 완료, `DataSeeder`도 향후 신규 설치 시 자동 반영되도록 갱신.
- **2026-08-05 추가 검증**: 나머지 4개 주제(소지품/발자국/혈흔/자국)도 실제 세션(1~5일차 밤 전부 진행, 범인: 나박수)으로 확인 완료. 5개 주제 전부 `Npc.appearanceDesc`/`personalityDesc`와 실제로 대응되는 내용 생성됨을 확인:
  - BELONGING: "옅은 베이지색 수건"이 `appearanceDesc`의 "light beige towel loosely wrapped around his neck"와 문자 그대로 일치, "색이 바랜 벽돌빛 천 조각"도 "faded brick-red short-sleeved work shirt"와 대응.
  - FOOTPRINT: "몸무게가 꽤 실린 다부진 체구의 발걸음"이 `appearanceDesc`의 "Broad sturdy build with wide shoulders and strong arms"와 대응.
  - MARK: "순간적으로 힘을 쏟아낸 흔적"이 `personalityDesc`의 "열정파, 화끈하고 직설적, 감정 숨기지 못함"과 대응(외형이 아닌 성격 기반 단서도 자연스럽게 생성됨).
  - BLOOD: 외형/성격 연계 없이도(1회성 주제 특성상) 자연스러운 현장 묘사 생성.
  - 5개 텍스트 전부 범인 이름("나박수")은 한 번도 언급되지 않음 — 목격자 힌트(`witnessHint`)에만 다른 NPC(전주인/나주부) 이름이 등장해 레드헤링 역할을 함, 스포일러 안전성 재확인.
  - 이 검증 과정에서 코드 결함은 발견되지 않음(수정 불필요, 순수 검증 완료).

### 11. llm-proxy 테스트 커버리지 전무 — ✅ 해결(2026-08-03)
- `LlmServiceTest`(15) — 페르소나/대화/랜덤이벤트/엔딩/단서/단서 명확화 6개 메서드 전부 성공 경로 + LLM 예외 시 폴백 경로 검증. `InternalApiKeyFilterTest`(5) — 헤더 없음/틀림/길이 다름/정상 케이스 전부 검증.
- **테스트 작성 중 실제 버그 발견 및 수정**: `checkRefusal()`이 `Optional<StopReason>`을 `Object`로 받아 `String.valueOf(stopReason)`로 비교하고 있었는데, `String.valueOf(Optional.of(StopReason.REFUSAL))`은 `"Optional[refusal]"`이 되어 `"refusal"`과 절대 같아질 수 없었음 — Claude가 안전 정책상 응답을 거부해도 이 안전장치가 전혀 작동하지 않는 상태였음. `Optional<StopReason>`을 직접 받아 `StopReason.REFUSAL`과 비교하도록 수정. 고친 코드를 잠시 원복해서 `chat_llmRefuses_returnsFallbackMessage` 테스트가 실제로 실패하는 것까지 확인해 회귀 방지 효과를 검증함.
- **Anthropic SDK 응답 객체(`Message`/`StructuredMessage`)는 Kotlin final class라 Mockito `mock()`이 불안정**(JDK 21 + Gradle 테스트 워커 환경에서 `UnfinishedStubbingException`) — mock 대신 실제 빌더로 값 객체를 직접 구성하는 방식으로 우회(`AnthropicClient`/`MessageService`는 순수 인터페이스라 정상적으로 mock).
- `llm-proxy/build.gradle`에 Mockito를 `-javaagent`로 명시 로드하도록 추가(`mockitoAgent` 구성) — self-attach 방식은 향후 JDK 버전에서 제거 예정이라는 경고 대응, Mockito 공식 권장 설정.

### 12. `game_save`(계정별 세이브)와 새 세션 생성 연동 — ✅ 해결(2026-08-03)
- 결정: **새 세션 생성 시 그 계정의 기존 세이브가 있으면 `ending_state`를 `in_progress`로 리셋**하는 쪽으로 진행(프론트가 `GameSession.status`를 우선해서 무시하는 대안은 채택 안 함) — 7번 항목에서 정한 "`ending_state`는 항상 현재 진행 중인 판의 상태를 반영한다"는 원칙을 세션 시작 시점까지 일관되게 적용.
- `GameSaveService`의 기존 private `DEFAULT_ENDING_STATE`를 `ENDING_STATE_SUCCESS`/`ENDING_STATE_BAD_ENDING`과 같은 위치의 public 상수 `ENDING_STATE_IN_PROGRESS`로 승격, `SessionService.createSession()` 끝에서 `gameSaveService.syncEndingState(account, GameSaveService.ENDING_STATE_IN_PROGRESS)` 호출.
- 그동안 테스트가 전혀 없었던 `createSession()`에 단위 테스트 2개 추가(`SessionServiceTest`, 정상 케이스 + NPC 마스터 데이터 없음 예외 케이스).
- 실제 Aiven DB로 검증: 세이브 생성(`in_progress`) → 세션A를 9일차까지 진행해 배드엔딩(`ending_state`→`bad_ending`) → 같은 계정으로 세션B(새 판) 생성 → 세이브 재조회 시 `ending_state`가 다시 `in_progress`로 리셋됨, `save_data`/`updatedAt`은 그대로 유지되는 것까지 확인.

### 13. LLM 호출 비용/횟수 제어 — ✅ 해결(2026-08-03)
- 점검해보니 대화·페르소나 생성·랜덤이벤트·엔딩·단서 생성 중 **`AccusationService.accuse()`가 실질적으로 무제한 호출 가능한 진짜 구멍**이었음: 7·8일차에 오답을 낼 때마다 `logRandomEvent()`가 매번 실행돼서, 같은 날 오답을 반복하면 그때마다 공짜로 LLM(랜덤 이벤트 생성)을 재호출시킬 수 있었음(체력 소모 등 다른 제약이 전혀 없어 대화보다 더 심각한 구멍). 기획서상으로도 "7→8→9일차 순차 재도전"은 하루 1회 시도가 전제라, 이건 비용 문제이자 게임 로직 결함이었음.
  - 수정: `AccusationLogRepository.existsBySessionAndDay()` 신설, `accuse()` 시작부에서 이미 그날 시도했으면 409로 차단(`accusationLogRepository.save()`/`randomEventLogRepository.save()`/`gameSaveService.syncEndingState()` 등 어떤 부작용도 없이 차단됨). 실제 Aiven DB로 7일차 오답 1회 → 같은 날 재시도 시 409, `random_event_log`/`accusation_log` 둘 다 1건만 남는 것까지 확인.
- **대화(`DialogueService.send()`)는 체력 소모(하루 최대 ~12회/NPC)로만 자연스럽게 제한돼 있어서, 프론트 버그/재시도 루프가 있으면 이 한도 안에서도 짧은 시간에 LLM 호출이 몰릴 수 있음** — `LlmRateLimiter`(계정별 슬라이딩 윈도, 인메모리) 신설해 `send()` 맨 앞(체력 소모 전)에서 검사. 기본값 분당 20회(`app.llm-rate-limit.max-calls`/`window-seconds`, env로 조정 가능), 초과 시 429(`LlmRateLimitExceededException`).
  - 실제 검증 시 한도를 분당 2회로 낮춰서 재현: 1·2번째 대화는 정상 처리(실제 Claude 응답), 3번째는 즉시 429 + 체력 그대로(부작용 없음) 확인.
  - 다른 LLM 호출 지점(페르소나 생성은 세션당 NPC별 1회 캐시, 랜덤 이벤트는 이제 하루 1회로 막힘, 엔딩/단서 생성은 세션당 1회 캐시 또는 하루 1회 제한 아이템 소모)은 이미 구조적으로 제한돼 있어 별도 레이트리밋 안 붙임 — dialogue와 accuse만 실제로 "제약 없이 반복 가능한" 경로였음.
- 단일 인스턴스 전제의 인메모리 구현(서버 다중화 시 Redis 등으로 교체 필요 — 이 프로젝트 규모에서는 불필요).
- `SessionServiceTest`(대화 무관), `AccusationServiceTest`(+1), `DialogueServiceTest`(신규, 2), `LlmRateLimiterTest`(신규, 4) 추가.

### 14. 호감도 등급별 NPC 대화 태도 — ✅ 해결(2026-08-04)
- 계기: 기획서 확정 스펙("호감도 70점 이상 우호적/단서 먼저 제공, 30~70점 기본 대화만, 30점 미만 회피·단답")이 `Affinity` 점수 자체는 DB에 쌓이고 있었지만, 실제 대화 LLM 프롬프트에는 전혀 반영되지 않고 있던 걸 LLM 기능 갭 분석 중 발견 — 호감도가 수치상으로만 존재하고 게임플레이에 아무 영향이 없는 상태였음.
- `NpcService.getAffinityScore(session, npc)` 신설(기존 `getDetail()`의 인라인 조회 로직 재사용), `DialogueService.send()`가 대화 LLM 호출 직전 이 값을 조회해 `LlmProxyClient.chat(...)`/`DialogueChatRequest`에 5번째 인자(`affinityScore`)로 추가 전달(backend·llm-proxy 양쪽 DTO 동시 수정).
- `LlmService.chat()`에 `affinityToneGuidance(int affinityScore)` 신설 — 70+/30~70/30미만 3구간으로 나눠 각기 다른 태도 지시문을 시스템 프롬프트에 삽입(높음: 우호적+단서 먼저 제공 시도, 보통: 예의만 지키고 단서는 먼저 안 꺼냄, 낮음: 경계·회피·단답이되 완전 차단은 아님).
- 실제 Aiven DB + 실제 Claude API로 두 구간 라이브 검증:
  - **30~70(보통) 구간**: 현수동(이장) 대상 여러 차례 대화 — 성격에 맞는 무뚝뚝하지만 실용적인 답변, 단서를 스스로 먼저 꺼내지 않음. 호감도가 정중한 발화에도 회당 +2~+3 수준으로만 느리게 오르는 것도 확인(캐릭터별 "고집 셈" 성격이 그대로 반영된 것으로, 의도된 동작).
  - **30미만(낮음) 구간**: 나주부(이장 아내) 대상으로 무례한 발화를 반복해 50→28까지 떨어뜨린 뒤 관찰 — 답변이 눈에 띄게 짧아지고 대화를 끝내려는 태도로 바뀜("문은 저쪽이에요", "등불 끄고 들어갈 테니, 알아서 하세요"). 이어서 **적대적이지 않은 중립 질문**("텃밭은 잘 되고 있나요?")을 던져도 여전히 경계·단답 톤이 유지되는 것까지 확인 — 직전 발화에 대한 일시적 반응이 아니라 호감도 등급 자체가 태도를 지배함을 검증.
  - **70+(높음) 구간**: 라이브 세션에서 실제로 70점을 넘기지는 못함(현수동의 완만한 상승 속도 + 체력 소모로 인한 대화 횟수 제한) — 다만 코드 경로가 세 구간 모두 동일한 `affinityToneGuidance()` 분기 구조를 공유하고, 낮음/보통 두 구간에서 지시문이 실제로 톤에 반영됨을 확인했으므로 높음 구간도 동일 메커니즘으로 작동할 것으로 판단(추가 신뢰 근거: `LlmServiceTest`에 5개 케이스 모두 `affinityScore` 인자 반영해 컴파일·테스트 통과).
- 테스트: `DialogueServiceTest`(2개, `npcService.getAffinityScore()` 스텁 추가 + 5-인자 `chat()` 검증), `LlmServiceTest`(`chat` 관련 5개 케이스 전부 5번째 인자 추가) — `backend`/`llm-proxy` 양쪽 `./gradlew test` 전체 통과 확인.
- 테스트 데이터 정리: 라이브 검증에 쓴 테스트 계정(`aft853972`)·세션(28) 및 연관 레코드(대화 로그/호감도/페르소나 상태 등) 전부 Aiven DB에서 삭제 완료.

### 15. 7일차 이후 "추리 대화 불가" 제한 — ✅ 해결(2026-08-05)
- 계기: `DialogueService.send()`에 "서버가 대화 주제를 판별하기 어려워 제한을 걸지 않았다"는 메모가 남아있던 항목 — 14번 항목(호감도 등급)과 같은 구조(LLM이 시스템 프롬프트 지시로 직접 판단)로 해결 가능하다고 보고 착수.
- `GameConstants.FIRST_ACCUSATION_DAY`(=7)를 기준으로 `session.getCurrentDay() >= 7`이면 `restrictDetectiveTalk=true`를 `DialogueChatRequest`에 실어 보냄(backend·llm-proxy DTO 양쪽에 6번째 필드 추가, `LlmProxyClient.chat()`/`DialogueService.send()` 시그니처 갱신).
- `LlmService.chat()`에서 `restrictDetectiveTalk`가 true면 "날씨·안부 같은 가벼운 대화에는 응하되, 사건·단서·범인 추리 관련 질문은 캐릭터답게 얼버무리고 새 정보를 주지 말라"는 지시를 시스템 프롬프트에 추가. 기존 `honestMode`(알리바이 질문에 한해 더 구체적으로 답하는 모드)와 충돌하지 않도록, 정직 모드가 같이 켜져 있으면 알리바이 질문은 예외로 명시.
- 실제 Aiven DB + Claude API로 라이브 검증: 세션을 7일차까지 진행 → 상점 주인(전주인)에게 "진범이 누구인지 단서 있으면 말해달라"고 직접 물었더니 "지금은 그런 걸 입에 올릴 때가 아닙니다"라며 캐릭터에 맞게 얼버무리고 물건 판매로 화제 전환(단서/범인 정보 전혀 노출 안 됨) — 반면 "오늘 장사는 잘 되세요?" 같은 평범한 안부에는 정상적으로 자연스럽게 응답하는 것까지 같은 세션에서 확인, 대화 자체를 막는 게 아니라 주제만 걸러진다는 것을 검증.
- 테스트: `DialogueServiceTest`에 7일차 이상일 때 `restrictDetectiveTalk=true`가 실제로 전달되는지 검증하는 케이스 추가. `LlmServiceTest`에 `ArgumentCaptor`로 실제 전송되는 시스템 프롬프트 문자열을 캡처해 `restrictDetectiveTalk=true`일 때만 제한 지시문이 포함되고 `false`일 때는 포함되지 않는 것을 직접 검증하는 케이스 2개 추가(기존 14번 항목의 호감도 등급 테스트는 프롬프트 내용까지는 검증하지 않았던 것보다 한 단계 더 엄격한 검증).
- `backend`/`llm-proxy` 양쪽 `./gradlew test` 전체 통과. 라이브 검증에 쓴 테스트 계정(`dtt855566`)·세션(29) 및 연관 레코드 전부 Aiven DB에서 삭제 완료.

---

### 참고
- 1번(CORS 미설정)은 이미 해결됨 — `config/WebMvcConfig.java`의 `addCorsMappings()` + `web/SessionOwnershipInterceptor.java`의 OPTIONS preflight 예외 처리.
- 8번(DB 마이그레이션 도구 없음, `ddl-auto=update` 의존)은 이미 인지된 트레이드오프로 별도 트래킹 안 함.
