-- random_event_log에 "플레이어가 확인했는지" 상태를 추가한다 (RandomEventLog.viewed/viewedAt 참고).
-- ddl-auto가 update가 아닌 환경(예: none)에서는 Hibernate가 기존 테이블에 새 컬럼을 자동으로
-- 추가해주지 않으므로, 배포 전 실제 DB에 이 스크립트를 직접 한 번 실행해야 한다.
-- (같은 이유로 존재하는 db/table-comments.sql과 동일한 성격의 마이그레이션 스크립트)

ALTER TABLE random_event_log
  ADD COLUMN is_viewed BIT(1) NOT NULL DEFAULT 0 COMMENT '플레이어가 실제로 알림/❗을 통해 확인했는지 여부 (ClueCard.acquired와 같은 패턴)',
  ADD COLUMN viewed_at DATETIME NULL;
