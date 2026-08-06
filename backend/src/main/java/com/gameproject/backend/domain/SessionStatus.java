package com.gameproject.backend.domain;

public enum SessionStatus {
    IN_PROGRESS,
    SUCCESS,
    BAD_ENDING,
    /** 플레이어가 세이브 슬롯에서 삭제한 세션 — 물리적으로 지우지 않고(연관 기록 보존) 목록/슬롯 수 계산에서만 제외한다. */
    DELETED
}
