-- game_project 스키마 테이블/컬럼 코멘트
-- Hibernate가 엔티티(@Comment)로 스키마를 새로 만들 때는 자동 반영되지만,
-- 이미 존재하는 컬럼은 ddl-auto: update로 소급 반영되지 않아서 직접 실행이 필요.
-- (backend/src/main/java/.../domain/*.java 의 @Comment 값과 동기화되어 있어야 함)
-- ENUM 컬럼은 실제 DB에 저장된 값 목록 그대로 유지해야 하므로 DESCRIBE로 확인한 정의를 그대로 사용함.

ALTER TABLE npc COMMENT = 'NPC 마스터 데이터 (기획서상 7명 고정)';
ALTER TABLE npc
  MODIFY name             VARCHAR(50) NOT NULL COMMENT 'NPC 이름',
  MODIFY role              VARCHAR(50) NOT NULL COMMENT '마을에서의 역할/직업',
  MODIFY personality_desc TEXT                 COMMENT '성격 요약 (페르소나 생성 LLM 입력값)',
  MODIFY speech_style     TEXT                 COMMENT '말투 특징 (페르소나 생성 LLM 입력값)',
  MODIFY sample_line      TEXT                 COMMENT '예시 대사';

ALTER TABLE npc_location_schedule COMMENT = '낮 동선 배치표 - NPC별 위치 등장 확률 (기획 제안 단계, 확정 아님)';
ALTER TABLE npc_location_schedule
  MODIFY slot          ENUM('PRIMARY','SECONDARY') NOT NULL COMMENT '기본 위치(PRIMARY) / 보조 위치(SECONDARY)',
  MODIFY location_name VARCHAR(50)                 NOT NULL COMMENT '장소 이름 (예: 마을회관, 상점)',
  MODIFY probability   INT                         NOT NULL COMMENT '등장 확률 (0~100, %)';

ALTER TABLE crop_master COMMENT = '농사 마스터 데이터 - 작물별 성장일수/체력소모/수확량/가격 (기획 제안 단계, 확정 아님)';
ALTER TABLE crop_master
  MODIFY name                     VARCHAR(30) NOT NULL COMMENT '작물 이름',
  MODIFY grow_days                INT         NOT NULL COMMENT '파종부터 수확까지 걸리는 일수',
  MODIFY plant_or_harvest_stamina INT         NOT NULL COMMENT '파종 1회 또는 수확 1회에 드는 체력 소모량',
  MODIFY yield_qty                INT         NOT NULL COMMENT '수확 시 얻는 개수',
  MODIFY sell_price               INT         NOT NULL COMMENT '상점 판매가 (개당)',
  MODIFY restore_hp               INT         NOT NULL COMMENT '섭취 시 회복되는 체력',
  MODIFY seed_price               INT         NOT NULL COMMENT '씨앗 구매가';

ALTER TABLE fruit_master COMMENT = '채집 마스터 데이터 - 과일별 재생주기/체력소모/가격 (기획 제안 단계, 확정 아님)';
ALTER TABLE fruit_master
  MODIFY name           VARCHAR(30) NOT NULL COMMENT '과일 이름',
  MODIFY regen_days     INT         NOT NULL COMMENT '채집 후 다시 채집 가능해지기까지의 쿨다운 일수',
  MODIFY forage_stamina INT         NOT NULL COMMENT '채집 1회당 체력 소모량',
  MODIFY restore_hp     INT         NOT NULL COMMENT '섭취 시 회복되는 체력',
  MODIFY sell_price     INT         NOT NULL COMMENT '상점 판매가 (개당)';

ALTER TABLE shop_item_master COMMENT = '상점 아이템 마스터 데이터 (운동화/거짓말탐지기/돋보기/선물세트)';
ALTER TABLE shop_item_master
  MODIFY category    ENUM('CONSUMABLE','PERMANENT_EQUIPMENT') NOT NULL COMMENT '영구 장비(PERMANENT_EQUIPMENT) 또는 소모품(CONSUMABLE)',
  MODIFY effect_desc TEXT                                              COMMENT '효과 설명',
  MODIFY name        VARCHAR(30)                              NOT NULL COMMENT '아이템 이름',
  MODIFY price       INT                                      NOT NULL COMMENT '구매 가격',
  MODIFY usage_limit VARCHAR(50)                                        COMMENT '사용 제한 설명 (예: "1회 구매, 영구" / "1일 1회" / "1개당 1회")';

ALTER TABLE game_session COMMENT = '게임 세션 - 한 판(플레이스루) 단위 상태. 게임의 최상위 기준 테이블';
ALTER TABLE game_session
  MODIFY current_day        INT                                          NOT NULL COMMENT '현재 진행 일차 (1~9)',
  MODIFY honest_mode_day    INT                                                   COMMENT '정직 모드가 적용되는 일차 (honest_mode_npc_id와 함께 사용)',
  MODIFY honest_mode_npc_id BIGINT                                                COMMENT '거짓말탐지기 사용 시 "정직 모드"가 적용될 다음 대화 상대 NPC (1턴짜리 임시 효과)',
  MODIFY player_id          VARCHAR(100)                                          COMMENT '로그인 없이 플레이하는 게스트용 임시 식별자 (디바이스 ID 등). 로그인 세션은 account_id로 식별',
  MODIFY account_id         BIGINT                                                COMMENT '로그인해서 시작한 세션일 경우의 계정 (게스트 플레이는 NULL)',
  MODIFY sneakers_equipped  BIT(1)                                       NOT NULL COMMENT '운동화 구매 후 장착 여부 (이동 체력 소모 5→4). 이동 API는 별도 미확정',
  MODIFY status              ENUM('BAD_ENDING','IN_PROGRESS','SUCCESS') NOT NULL COMMENT '진행중(IN_PROGRESS) / 성공(SUCCESS) / 배드엔딩(BAD_ENDING)',
  MODIFY culprit_npc_id      BIGINT                                     NOT NULL COMMENT '이번 판의 범인으로 랜덤 배정된 NPC';

ALTER TABLE npc_case_assignment COMMENT = '판별 범인(culprit)의 사보타주 유형/동기 배정 (기획 제안 단계, 확정 아님)';
ALTER TABLE npc_case_assignment
  MODIFY primary_type     ENUM('DAMAGE','DISRUPTION','THEFT') NOT NULL COMMENT '주 사보타주 유형 (절도형/파손형/방해공작형)',
  MODIFY secondary_type   ENUM('DAMAGE','DISRUPTION','THEFT')          COMMENT '보조 사보타주 유형 — 기획서상 방향성만 있고 미확정이라 nullable',
  MODIFY motive_text      TEXT                                          COMMENT '범행 동기 (페르소나 생성 LLM 입력값)',
  MODIFY target_pool_desc TEXT                                          COMMENT '사보타주 대상 풀 설명';

ALTER TABLE sabotage_event COMMENT = '밤마다 발생하는 사보타주 사건 기록 (1~5일차 밤, 판당 총 5회)';
ALTER TABLE sabotage_event
  MODIFY day            INT                                  NOT NULL COMMENT '발생 일차 (1~5)',
  MODIFY is_self_decoy  BIT(1)                                NOT NULL COMMENT '범인이 본인 소유물을 스스로 터는 척하는 특수 케이스 여부',
  MODIFY location       VARCHAR(50)                           NOT NULL COMMENT '사건 발생 장소',
  MODIFY type            ENUM('DAMAGE','DISRUPTION','THEFT')  NOT NULL COMMENT '절도형/파손형/방해공작형',
  MODIFY witness_npc_id  BIGINT                                        COMMENT '낮 동선과 목격담으로 연결되는 목격 NPC (nullable)';

ALTER TABLE clue_card COMMENT = '단서 카드 - 판당 총 5장, 사보타주 발생 장소에서 습득 (자동 지급 아님)';
ALTER TABLE clue_card
  MODIFY topic          ENUM('BELONGING','BLOOD','FOOTPRINT','HAIR','MARK') NOT NULL COMMENT '단서 주제: 머리카락/소지품/발자국/혈흔/자국',
  MODIFY text_ambiguous TEXT                                                NOT NULL COMMENT '애매하게 설계된 원본 단서 문구',
  MODIFY text_clarified TEXT                                                         COMMENT '돋보기 사용 후 갱신되는 명확화된 문구',
  MODIFY is_acquired    BIT(1)                                              NOT NULL COMMENT '플레이어가 실제로 습득했는지 여부';

ALTER TABLE affinity COMMENT = '사용자-NPC 간 호감도 (NPC끼리의 관계와는 별개 개념, 0~100, 시작값 50)';
ALTER TABLE affinity MODIFY score INT NOT NULL COMMENT '호감도 점수 (0~100, 시작값 50)';

ALTER TABLE npc_persona_state COMMENT = '페르소나 생성 LLM 결과 저장소 (판+NPC별 1건, 대화용 LLM이 매 대화마다 불러다 씀)';
ALTER TABLE npc_persona_state MODIFY generated_persona_json TEXT NOT NULL COMMENT '페르소나 생성 LLM이 만든 상세 성격/배경 (JSON 문자열로 저장)';

ALTER TABLE dialogue_log COMMENT = '대화용 LLM과 주고받은 대화 이력 (사용자/NPC 메시지 각각 1행)';
ALTER TABLE dialogue_log MODIFY sender ENUM('NPC','USER') NOT NULL COMMENT '발화자: USER(플레이어) 또는 NPC';

ALTER TABLE player_stat COMMENT = '일자별 플레이어 상태 (체력/골드/기절여부). 체력 최대치는 기획 미확정, 잠정값 100';
ALTER TABLE player_stat
  MODIFY day             INT    NOT NULL COMMENT '일차 (1~9)',
  MODIFY stamina_current INT    NOT NULL COMMENT '현재 체력',
  MODIFY stamina_max     INT    NOT NULL COMMENT '최대 체력 (기획 미확정, 잠정값 100)',
  MODIFY gold            INT    NOT NULL COMMENT '보유 골드',
  MODIFY fainted         BIT(1) NOT NULL COMMENT '이날 기절했는지 여부 (체력 0 도달)';

ALTER TABLE inventory_item COMMENT = '인벤토리 슬롯 (판당 7칸 고정)';
ALTER TABLE inventory_item
  MODIFY item_ref_id BIGINT                              NOT NULL COMMENT 'item_type에 따라 crop_master/fruit_master/shop_item_master의 PK를 참조 (FK 아님, 애플리케이션에서 분기 처리)',
  MODIFY item_type   ENUM('CROP','FRUIT','SHOP_ITEM','SEED') NOT NULL COMMENT 'CROP/FRUIT/SHOP_ITEM/SEED 중 어떤 마스터를 참조하는지',
  MODIFY slot_index  INT                                 NOT NULL COMMENT '슬롯 번호 (1~7)';

ALTER TABLE accusation_log COMMENT = '고발(범인 지목) 시도 이력 - 7~9일차에만 발생 가능';
ALTER TABLE accusation_log
  MODIFY day             INT    NOT NULL COMMENT '고발한 일차 (7~9)',
  MODIFY is_correct      BIT(1) NOT NULL COMMENT '정답 여부',
  MODIFY accused_npc_id  BIGINT NOT NULL COMMENT '지목한 NPC';

ALTER TABLE random_event_log COMMENT = '오답 고발 시 발생하는 랜덤 이벤트 (7~8일차 마을/주민 대상, 8~9일차 플레이어 대상)';
ALTER TABLE random_event_log
  MODIFY day        INT                      NOT NULL COMMENT '이벤트 발생 일차',
  MODIFY event_type VARCHAR(50)              NOT NULL COMMENT '이벤트 종류 (예: 협박 편지, 밭 훼손)',
  MODIFY target      ENUM('PLAYER','VILLAGE') NOT NULL COMMENT '이벤트 대상: 마을/주민(VILLAGE) 또는 플레이어(PLAYER)';

ALTER TABLE npc_witness_awareness COMMENT = '목격담이 관계망을 타고 2차 전파된 기록 (최초 기획 초안에는 없던 구현 보완 테이블)';
ALTER TABLE npc_witness_awareness
  MODIFY npc_id      BIGINT NOT NULL COMMENT '이 목격담을 전해 들어서 알게 된 NPC (원래 목격자 본인은 포함하지 않음)',
  MODIFY learned_day INT    NOT NULL COMMENT '전해 들은 일차';

ALTER TABLE farm_plot COMMENT = '파종된 작물의 재배 상태 (파종→성장→수확). 최초 기획 초안에는 없던 구현 보완 테이블';
ALTER TABLE farm_plot
  MODIFY planted_day INT    NOT NULL COMMENT '파종한 일차',
  MODIFY ready_day   INT    NOT NULL COMMENT '수확 가능해지는 일차 (planted_day + crop.grow_days)',
  MODIFY harvested   BIT(1) NOT NULL COMMENT '수확 완료 여부';

ALTER TABLE fruit_forage_state COMMENT = '과일별 채집 쿨다운 상태 ("같은 나무" 대신 "같은 과일 종류" 단위로 근사). 최초 기획 초안에는 없던 구현 보완 테이블';
ALTER TABLE fruit_forage_state MODIFY last_foraged_day INT COMMENT '가장 최근 채집한 일차 (null이면 아직 채집한 적 없음)';

ALTER TABLE account COMMENT = '로그인 계정. 최초 기획 초안에는 없던 구현 보완 테이블(game_session.player_id 연동용)';
ALTER TABLE account
  MODIFY username         VARCHAR(50)  NOT NULL COMMENT '로그인 아이디 (유일)',
  MODIFY password_hash    VARCHAR(100) NOT NULL COMMENT 'BCrypt로 해시된 비밀번호',
  MODIFY nickname         VARCHAR(50)  NOT NULL COMMENT '게임 내 표시 닉네임',
  MODIFY auth_token       VARCHAR(36)           COMMENT '현재 유효한 로그인 토큰 (opaque, 로그아웃 시 null로 초기화)',
  MODIFY token_expires_at DATETIME              COMMENT 'auth_token 만료 시각';
