package com.gameproject.backend.service;

/**
 * 기획서에 명시된 고정 수치. 최대 체력치처럼 기획서에 없는 값은 잠정 기본값으로
 * 표기해뒀으니(기획 확정 스프린트 후) 갱신 필요.
 */
public final class GameConstants {

    private GameConstants() {
    }

    /** 기획서 미명시 — 잠정값. 기절 리스폰값(70)만 기획서에 확정되어 있음 */
    public static final int DEFAULT_STAMINA_MAX = 100;

    public static final int FAINT_RESTART_STAMINA = 70;

    public static final int MOVE_STAMINA = 5;
    public static final int MOVE_STAMINA_WITH_SNEAKERS = 4;
    public static final int DIALOGUE_STAMINA = 8;

    public static final int INVENTORY_SLOT_COUNT = 7;

    public static final int FIRST_ACCUSATION_DAY = 7;
    public static final int LAST_ACCUSATION_DAY = 9;
    public static final int SABOTAGE_NIGHTS = 5; // 1~5일차 밤

    public static final int AFFINITY_START = 50;
    public static final int AFFINITY_WRONG_ACCUSED_PENALTY = -30;
    /** 가까운 관계 오답 페널티 크기(절댓값) 범위: 10~15 */
    public static final int AFFINITY_WRONG_CLOSE_PENALTY_MAGNITUDE_MIN = 10;
    public static final int AFFINITY_WRONG_CLOSE_PENALTY_MAGNITUDE_MAX = 15;
    public static final int AFFINITY_WRONG_UNRELATED_PENALTY = -5;
    public static final int AFFINITY_DIALOGUE_GAIN_MIN = 2;
    public static final int AFFINITY_DIALOGUE_GAIN_MAX = 5;
    public static final int AFFINITY_GIFT_MIN = 10;
    public static final int AFFINITY_GIFT_MAX = 15;

    /** 사보타주 유형만으로 범인이 특정되는 것을 막기 위한 보조 유형 등장 확률(기획 방향성: 주 80% + 보조 20%) */
    public static final int SECONDARY_SABOTAGE_TYPE_CHANCE_PERCENT = 20;
}
