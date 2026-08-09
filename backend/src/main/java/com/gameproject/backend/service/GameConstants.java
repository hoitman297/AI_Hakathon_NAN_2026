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

    /** 기획서(체력 세부 수치, ✅ 확정): 이동은 초당 소모되는 연속형 모델. 운동화 착용 시 20% 감소. */
    public static final double MOVE_STAMINA_PER_SECOND = 0.15;
    public static final double MOVE_STAMINA_PER_SECOND_WITH_SNEAKERS = 0.12;
    public static final int DIALOGUE_STAMINA = 8;
    /** 기획서: NPC 한 명당 하루 최대 대화(질의응답) 횟수 — 대화창을 닫았다 다시 열어도 초기화되지 않고
     *  게임상 하루(currentDay) 단위로 누적된다. */
    public static final int MAX_DIALOGUE_EXCHANGES_PER_NPC_PER_DAY = 3;

    public static final int INVENTORY_SLOT_COUNT = 7;

    /** 기획서 미명시 — 시작 골드 0이면 씨앗(최저 5G)조차 못 사는 초반 경제 병목이 있어 잠정값으로 지급. */
    public static final int STARTING_GOLD = 100;

    /** 계정당 동시에 보유 가능한 세이브(게임 세션) 슬롯 수. 삭제된(DELETED) 세션은 여기 안 낀다. */
    public static final int MAX_SAVES_PER_ACCOUNT = 3;

    public static final int FIRST_ACCUSATION_DAY = 7;
    public static final int LAST_ACCUSATION_DAY = 9;
    public static final int SABOTAGE_NIGHTS = 5; // 1~5일차 밤

    public static final int AFFINITY_START = 50;
    public static final int AFFINITY_WRONG_ACCUSED_PENALTY = -30;
    /** 가까운 관계 오답 페널티 크기(절댓값) 범위: 10~15 */
    public static final int AFFINITY_WRONG_CLOSE_PENALTY_MAGNITUDE_MIN = 10;
    public static final int AFFINITY_WRONG_CLOSE_PENALTY_MAGNITUDE_MAX = 15;
    public static final int AFFINITY_WRONG_UNRELATED_PENALTY = -5;
    /** 선물세트 1개당 호감도 +10~15는 시작(50)에서 만점(100)까지 최소 4개(160G)가 필요해
     *  체감상 너무 안 오른다는 플레이테스트 피드백으로 상승폭을 늘림(잠정값). */
    public static final int AFFINITY_GIFT_MIN = 18;
    public static final int AFFINITY_GIFT_MAX = 25;

    /** 사보타주 유형만으로 범인이 특정되는 것을 막기 위한 보조 유형 등장 확률(기획 방향성: 주 80% + 보조 20%) */
    public static final int SECONDARY_SABOTAGE_TYPE_CHANCE_PERCENT = 20;

    /**
     * 목격담이 관계망을 타고 밤마다 한 단계씩 2차 전파될 확률(%). 기획서에 없는 구현 보완
     * 수치라 밸런스 미확정 잠정값 — 새 정보를 만들어내지 않고 "누가 아는지"만 넓히므로
     * 값이 다소 커도 범인 특정에는 영향이 없다(WitnessGossipService 참고).
     */
    public static final int WITNESS_SPREAD_CLOSE_CHANCE_PERCENT = 55; // 부부 등 가까운 관계
    public static final int WITNESS_SPREAD_HUB_CHANCE_PERCENT = 20; // 마을 소식통(박영계) 경유
}
