-- 유저 로그 및 유저 정보 전체 초기화
-- 대상 DB: Aiven 원격 MySQL (game_project) — 배포된 백엔드가 실제로 쓰는 DB
-- master(기준) 데이터(npc, npc_location_schedule, crop_master, fruit_master, shop_item_master)는 건드리지 않음.
-- TRUNCATE는 AUTO_INCREMENT도 초기화됨. 되돌릴 수 없으니 실행 전 필요하면 백업할 것.

SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE dialogue_log;
TRUNCATE TABLE accusation_log;
TRUNCATE TABLE random_event_log;
TRUNCATE TABLE sabotage_event;
TRUNCATE TABLE clue_card;
TRUNCATE TABLE inventory_item;
TRUNCATE TABLE farm_plot;
TRUNCATE TABLE fruit_forage_state;
TRUNCATE TABLE npc_persona_state;
TRUNCATE TABLE npc_case_assignment;
TRUNCATE TABLE affinity;
TRUNCATE TABLE player_stat;
TRUNCATE TABLE game_save;
TRUNCATE TABLE game_session;
TRUNCATE TABLE account;

SET FOREIGN_KEY_CHECKS = 1;
