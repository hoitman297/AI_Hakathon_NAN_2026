package com.gameproject.backend.service;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.gameproject.backend.domain.SabotageType;

/**
 * 기획서 "범인별 사보타주 대상 풀" 표(💡 최종 확정 아님)를 코드로 옮긴 정적 룩업.
 * NPC 이름으로 조회해서, 해당 NPC가 이번 판의 범인으로 뽑혔을 때 쓸 주 유형/동기/대상 풀을 가져온다.
 * secondaryType(보조 유형)은 기획서에서 방향성만 논의되고 미확정이라 아직 없음.
 */
@Component
public class CulpritProfileRegistry {

    public record CulpritProfile(SabotageType primaryType, String motiveText, String targetPoolDesc) {
    }

    private static final Map<String, CulpritProfile> PROFILES = Map.of(
            "현수동", new CulpritProfile(SabotageType.DAMAGE,
                    "마을 개발에 대한 반감",
                    "벤치/화분/조형물/다리 난간 중 랜덤"),
            "나주부", new CulpritProfile(SabotageType.THEFT,
                    "막연한 질투·소외감(어릴 적 상처)",
                    "그날 형편 좋아 보이는 임의 NPC의 밭/판매대"),
            "전주인", new CulpritProfile(SabotageType.DISRUPTION,
                    "은근한 승부욕(장터 경쟁 집착)",
                    "대상(양계장 or 수박밭) 랜덤 선정 → 액션 자동 매칭(양계장=동물 풀어놓기, 수박밭=작물 훼손)"),
            "박영계", new CulpritProfile(SabotageType.THEFT,
                    "오지랖(소문 진위 확인)",
                    "그날 소문의 주인공인 임의 NPC의 집/가게 물건"),
            "명자유", new CulpritProfile(SabotageType.THEFT,
                    "불안정한 수입",
                    "자택과 인접한 고정 이웃 1~2명의 텃밭"),
            "김치준", new CulpritProfile(SabotageType.DISRUPTION,
                    "나박수와의 앙숙 관계 + 취준 스트레스",
                    "나박수의 판매대/손수레/납품 진열대 중 랜덤"),
            "나박수", new CulpritProfile(SabotageType.DAMAGE,
                    "다혈질 + 김치준에 대한 반감",
                    "전주인 상점의 화분·진열대·간판 / 김치준 개인 물건 중 랜덤")
    );

    public CulpritProfile get(String npcName) {
        CulpritProfile profile = PROFILES.get(npcName);
        if (profile == null) {
            throw new IllegalStateException("정의되지 않은 NPC입니다: " + npcName);
        }
        return profile;
    }
}
