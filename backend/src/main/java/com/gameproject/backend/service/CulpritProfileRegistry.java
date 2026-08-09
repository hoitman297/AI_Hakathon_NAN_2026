package com.gameproject.backend.service;

import java.util.ArrayList;
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
         * 오늘 밤 실제 대상 하나를 랜덤 선택.
         *
         * <p>후보 풀 = (오늘 밤 유형이 이 범인의 주 유형과 같을 때만) 범인 전용 대상 풀 +
         * {@link #COMMON_TARGETS}의 오늘 밤 유형 공통 풀. 보조 유형으로 걸린 밤에는 범인 전용
         * 풀(다른 유형의 서사라 안 맞음)은 아예 빼고 공통 풀만 쓴다 — 전에는 유형이 안 맞는
         * 전용 대상이 그대로 나가는 버그가 있었다. 공통 풀을 섞는 이유는 두 가지: (1) 대상 풀이
         * 2개뿐인 범인(전주인·명자유)도 5일치 사보타주를 겹치지 않게 채울 수 있도록 풀을 키우고,
         * (2) 매 판 범인 전용 대상만 나오면 유형만으로 범인이 특정되기 쉬운 문제를 완화한다.
         *
         * <p>이번 판(세션)에서 이미 쓴 대상(location+subTarget)은 후보 풀에 아직 안 쓴 대상이
         * 하나라도 남아 있는 한 제외한다 — 완전히 같은 사보타주가 반복되는 걸 막기 위함. 그래도
         * 후보가 다 떨어지면(공통+전용 풀을 합쳐도 5일치보다 적으면) 어쩔 수 없이 전체 풀에서
         * 다시 뽑는다.
         *
         * <p>마지막으로, 남은 후보 중 직전 밤과 "장소"가 다른 후보가 하나라도 있으면 그중에서
         * 뽑는다 — location만 비교해야 한다(Target 전체로 비교하면 같은 장소의 다른 하위 대상이
         * 걸러지지 않고 이틀 연속 같은 장소가 뽑히는 문제가 있었다).
         */
        public Target pickTonight(Random random, SabotageType tonightType, List<Target> previousTargets) {
            List<Target> pool = new ArrayList<>();
            if (tonightType == primaryType) {
                pool.addAll(targetPool);
            }
            pool.addAll(COMMON_TARGETS.getOrDefault(tonightType, List.of()));

            List<Target> unused = pool.stream()
                    .filter(t -> !previousTargets.contains(t))
                    .toList();
            List<Target> candidates = unused.isEmpty() ? pool : unused;

            if (!previousTargets.isEmpty()) {
                Target lastNight = previousTargets.get(previousTargets.size() - 1);
                List<Target> differentLocation = candidates.stream()
                        .filter(t -> !t.location().equals(lastNight.location()))
                        .toList();
                if (!differentLocation.isEmpty()) {
                    candidates = differentLocation;
                }
            }
            return candidates.get(random.nextInt(candidates.size()));
        }
    }

    /**
     * 특정 범인 서사에 묶이지 않은 유형별 공통 사보타주 대상 풀. {@link CulpritProfile#pickTonight}
     * 에서 범인 전용 풀과 함께 섞여서 후보가 된다. 장소 문자열은 전부 {@link #PROFILES}에 이미
     * 쓰인 것과 동일한 값만 사용한다 — 새 장소를 쓰려면 프론트 배경 에셋 매핑과 DataSeeder의
     * NpcLocationSchedule에도 같이 추가해야 한다.
     */
    private static final Map<SabotageType, List<Target>> COMMON_TARGETS = Map.of(
            SabotageType.THEFT, List.of(
                    new Target("상점", "채소 바구니"),
                    new Target("양계장", "달걀 몇 개"),
                    new Target("수박밭", "덜 익은 수박"),
                    new Target("자택 인근 텃밭", "무")),
            SabotageType.DAMAGE, List.of(
                    new Target("마을회관", "게시판"),
                    new Target("정자", "울타리"),
                    new Target("마을 어귀 순찰", "이정표"),
                    new Target("상점", "차양막")),
            SabotageType.DISRUPTION, List.of(
                    new Target("양계장", "사료통 엎기"),
                    new Target("수박밭", "잡초 씨앗 뿌리기"),
                    new Target("자택 인근 텃밭", "고양이 풀어놓기"),
                    new Target("상점", "물건 뒤섞기")));

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
