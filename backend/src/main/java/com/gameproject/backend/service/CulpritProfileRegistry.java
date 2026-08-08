package com.gameproject.backend.service;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.springframework.stereotype.Component;

import com.gameproject.backend.domain.SabotageType;

/**
 * 기획서 "범인별 사보타주 대상 풀" 표(💡 최종 확정 아님)를 코드로 옮긴 정적 룩업.
 * NPC 이름으로 조회해서, 해당 NPC가 이번 판의 범인으로 뽑혔을 때 쓸 주 유형/동기/대상 풀을 가져온다.
 * secondaryType(보조 유형)은 NPC별 고정이 아니라 {@link #pickSecondaryType}으로 세션마다 랜덤 배정된다.
 *
 * Target.location()은 DataSeeder의 NpcLocationSchedule locationName과 문자열이 정확히
 * 일치해야 한다 — SessionService가 이 값으로 "그 시간 그 장소에 있던 목격 NPC"를 찾기 때문.
 */
@Component
public class CulpritProfileRegistry {

    /** 사보타주가 실제로 벌어지는 장소와 구체적 대상. */
    public record Target(String location, String subTarget) {
    }

    public record CulpritProfile(SabotageType primaryType, String motiveText, List<Target> targetPool) {

        /** DB 저장/디버깅용 — 사람이 읽는 대상 풀 요약. */
        public String describePool() {
            return targetPool.stream()
                    .map(t -> t.subTarget() + "(" + t.location() + ")")
                    .reduce((a, b) -> a + " / " + b)
                    .orElse("");
        }

        /**
         * 오늘 밤 실제 대상 하나를 랜덤 선택. 대상 풀에 직전 밤과 "장소"가 다른 후보가 하나라도
         * 있으면 그중에서 뽑는다 — location만 비교해야 한다(Target 전체(location+subTarget)로
         * 비교하면 같은 장소의 다른 하위 대상(예: 상점-화분 → 상점-진열대)이 걸러지지 않고 이틀
         * 연속 같은 장소가 뽑히는 문제가 있었다). 후보 풀이 전부 같은 장소뿐이면(전체 풀에 다른
         * 장소가 없음) 어쩔 수 없이 그대로 전체 풀에서 뽑는다.
         */
        public Target pickTonight(Random random, Target excludePrevious) {
            List<Target> candidates = targetPool;
            if (excludePrevious != null) {
                List<Target> differentLocation = targetPool.stream()
                        .filter(t -> !t.location().equals(excludePrevious.location()))
                        .toList();
                if (!differentLocation.isEmpty()) {
                    candidates = differentLocation;
                }
            }
            return candidates.get(random.nextInt(candidates.size()));
        }
    }

    private static final Map<String, CulpritProfile> PROFILES = Map.of(
            "현수동", new CulpritProfile(SabotageType.DAMAGE,
                    "마을 개발에 대한 반감",
                    List.of(
                            new Target("마을회관", "벤치"),
                            new Target("정자", "화분"),
                            new Target("마을 어귀 순찰", "조형물"))),
            "나주부", new CulpritProfile(SabotageType.THEFT,
                    "막연한 질투·소외감(어릴 적 상처)",
                    List.of(
                            new Target("상점", "진열 상품"),
                            new Target("양계장", "사료"),
                            new Target("수박밭", "수박"))),
            "전주인", new CulpritProfile(SabotageType.DISRUPTION,
                    "은근한 승부욕(장터 경쟁 집착)",
                    List.of(
                            new Target("양계장", "닭 풀어놓기"),
                            new Target("수박밭", "농약통 엎기"))),
            "박영계", new CulpritProfile(SabotageType.THEFT,
                    "오지랖(소문 진위 확인)",
                    List.of(
                            new Target("자택", "택배 상자"),
                            new Target("상점(근무)", "재고 물품"),
                            new Target("수박밭", "농기구"))),
            "명자유", new CulpritProfile(SabotageType.THEFT,
                    "불안정한 수입",
                    List.of(
                            new Target("자택 인근 텃밭", "상추"),
                            new Target("자택 인근 텃밭", "고추"))),
            "김치준", new CulpritProfile(SabotageType.DISRUPTION,
                    "나박수와의 앙숙 관계 + 취준 스트레스",
                    List.of(
                            new Target("수박밭", "판매대"),
                            new Target("상점(납품)", "손수레"),
                            new Target("상점(납품)", "진열대"))),
            "나박수", new CulpritProfile(SabotageType.DAMAGE,
                    "다혈질 + 김치준에 대한 반감",
                    List.of(
                            new Target("상점", "화분"),
                            new Target("상점", "진열대"),
                            new Target("상점", "간판"),
                            new Target("상점(근무)", "개인 물건")))
    );

    public CulpritProfile get(String npcName) {
        CulpritProfile profile = PROFILES.get(npcName);
        if (profile == null) {
            throw new IllegalStateException("정의되지 않은 NPC입니다: " + npcName);
        }
        return profile;
    }

    /**
     * 판마다 보조 유형을 주 유형이 아닌 나머지 2개 중 랜덤으로 배정한다.
     * NPC별 주 유형이 고정이라 반복 플레이 시 유형만으로 범인이 특정되는 문제를 완화하기 위함.
     */
    public SabotageType pickSecondaryType(Random random, SabotageType primaryType) {
        List<SabotageType> candidates = java.util.Arrays.stream(SabotageType.values())
                .filter(type -> type != primaryType)
                .toList();
        return candidates.get(random.nextInt(candidates.size()));
    }
}
