# 백엔드/DB 추가 개발 필요 항목

> 2026-08-03 기준 현재 구현 상태를 감사(audit)해서 정리한 목록. CORS(구 1번)는 이미 해결됨.

## 🟡 기획 스펙 대비 미구현

### 2. 보조 사보타주 유형(secondaryType) 미구현
- 주 유형 80% / 보조 유형 20% 가중 랜덤으로 구현하기로 방향은 정했으나, 실제 코드에는 아직 반영되지 않음.
- 관련 파일: `service/CulpritProfileRegistry.java`, `domain/NpcCaseAssignment.java`(`secondaryType` 필드는 있지만), `service/SessionService.java`의 `createSession()`에서 `secondaryType(null)`로 고정.
- TODO: NPC별 보조 유형(주 유형 제외 나머지 2개 중 랜덤)을 세션 생성 시 배정하는 로직 추가.

### 3. 이동(움직임) API 없음
- `service/GameConstants.java`의 `MOVE_STAMINA`/`MOVE_STAMINA_WITH_SNEAKERS` 상수는 정의돼 있으나, 이를 소비하는 API 자체가 백엔드에 없음.
- 운동화 아이템 효과(이동 체력 5→4 할인, `GameSession.sneakersEquipped`)를 붙일 곳이 없어서 구매해도 실질적 효과 없음.
- 선행 조건: 프론트 맵 이동 방식(자유이동 vs 타일 기반, 서버에 언제 어떻게 이동을 알릴지)이 먼저 정해져야 API 설계 가능.

### 4. 랜덤 이벤트 내용이 비어있음
- 관련 파일: `service/AccusationService.java`의 `logRandomEvent()`, `domain/RandomEventLog.java`.
- 7→8일차(마을 대상)/8→9일차(플레이어 대상) 오답 시 이벤트 타입(예: "협박 편지", "마을 게시판 도발 쪽지")만 기록되고 `description` 컬럼은 항상 `null`.
- TODO: 이벤트별 실제 연출 텍스트 콘텐츠 필요 (작가 작성 또는 LLM 생성 파이프라인 연결).

### 5. 엔딩 스토리가 얇음
- 관련 파일: `service/AccusationService.java`의 `getEnding()`, `dto/EndingResponse.java`.
- 현재 "범인 이름 + 동기 한 줄"만 반환. 기획서의 "성공 시 범인 개별 스토리 공개"에 필요한 캐릭터별 서사 콘텐츠가 없음.
- TODO: NPC 7명 × (정답/배드엔딩) 엔딩 콘텐츠 필요. llm-proxy를 활용해 동적 생성하는 것도 방법.

## 🟢 품질/구조

### 6. 테스트 커버리지 전무
- `BackendApplicationTests.contextLoads()` 하나뿐, 그 외 단위/통합 테스트 없음.
- 최근 수정된 핵심 로직(9일차 게임 종료 조건, 사보타주 대상 다양화, 세션 IDOR 방지, 낙관적 락(`@Version`), 상점 아이템 1일 1회 제한, `game_save` 저장/조회)이 전부 테스트 없이 코드 리뷰만으로 검증된 상태.
- TODO: 최소한 `SessionService.advanceDay()`(배드엔딩 전이), `AccusationService.accuse()`(정답/오답 판정), `GameSaveService`(저장/기본값 반환) 단위 테스트부터 추가 권장.

### 7. `game_save.ending_state`가 실제 게임 결과와 연동 안 됨
- 관련 파일: `service/GameSaveService.java`.
- `save()`가 호출될 때마다 항상 `'in_progress'`로 고정 저장 — 요구사항에 갱신 방법이 정의되어 있지 않아 미구현 상태로 남겨둠.
- `GameSession.status`(SUCCESS/BAD_ENDING, `AccusationService`가 갱신)와 완전히 별개로 동작해서, 실제로 정답을 맞히거나 배드엔딩이 나도 세이브 쪽 `ending_state`에는 반영되지 않음.
- TODO: 게임 종료 시점에 `GameSaveService`를 같이 호출해 `ending_state`를 갱신할지, 아니면 세이브 기능은 계속 진행 상태만 추적할지 결정 필요.

---

### 참고
- 1번(CORS 미설정)은 이미 해결됨 — `config/WebMvcConfig.java`의 `addCorsMappings()` + `web/SessionOwnershipInterceptor.java`의 OPTIONS preflight 예외 처리.
- 8번(DB 마이그레이션 도구 없음, `ddl-auto=update` 의존)은 이미 인지된 트레이드오프로 별도 트래킹 안 함.
