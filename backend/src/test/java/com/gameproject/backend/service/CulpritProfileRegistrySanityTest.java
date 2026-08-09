package com.gameproject.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.gameproject.backend.domain.SabotageType;
import com.gameproject.backend.service.CulpritProfileRegistry.CulpritProfile;
import com.gameproject.backend.service.CulpritProfileRegistry.Target;

/**
 * "한 판(5일) 안에서는 완전히 같은 사보타주(장소+세부 대상)가 두 번 나오면 안 된다"는 요구사항의
 * 회귀 테스트. 7명 범인 전원에 대해 매일 밤 보조 유형 20% 확률까지 반영해 여러 판을
 * 시뮬레이션하고, 5일치가 항상 서로 다른 대상으로 채워지는지 확인한다.
 */
class CulpritProfileRegistrySanityTest {

    @Test
    void allNpcsFillFiveNightsWithoutExactDuplicate() {
        CulpritProfileRegistry registry = new CulpritProfileRegistry();
        Random random = new Random(42);
        String[] npcs = {"현수동", "나주부", "전주인", "박영계", "명자유", "김치준", "나박수"};

        for (String npcName : npcs) {
            CulpritProfile profile = registry.get(npcName);
            SabotageType secondary = registry.pickSecondaryType(random, profile.primaryType());

            for (int trial = 0; trial < 200; trial++) {
                List<Target> history = new ArrayList<>();
                for (int night = 1; night <= 5; night++) {
                    boolean useSecondary = random.nextInt(100) < 20;
                    SabotageType tonightType = useSecondary ? secondary : profile.primaryType();
                    Target t = profile.pickTonight(random, tonightType, history);
                    history.add(t);
                }
                long distinct = history.stream().distinct().count();
                assertThat(distinct)
                        .as("%s trial %d history=%s", npcName, trial, history)
                        .isEqualTo(5);
            }
        }
    }
}
