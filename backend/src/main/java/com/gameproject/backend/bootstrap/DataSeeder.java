package com.gameproject.backend.bootstrap;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.gameproject.backend.domain.CropMaster;
import com.gameproject.backend.domain.FruitMaster;
import com.gameproject.backend.domain.ItemCategory;
import com.gameproject.backend.domain.LocationSlot;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.NpcLocationSchedule;
import com.gameproject.backend.domain.ShopItemCode;
import com.gameproject.backend.domain.ShopItemMaster;
import com.gameproject.backend.repository.CropMasterRepository;
import com.gameproject.backend.repository.FruitMasterRepository;
import com.gameproject.backend.repository.NpcLocationScheduleRepository;
import com.gameproject.backend.repository.NpcRepository;
import com.gameproject.backend.repository.ShopItemMasterRepository;

import lombok.RequiredArgsConstructor;

/**
 * 기획서 분석 문서의 마스터 데이터(NPC 7명, 낮 동선표, 농사/채집/상점 데이터)를
 * 최초 기동 시 1회 시딩한다. 이미 데이터가 있으면 아무 것도 하지 않는다.
 * 💡 표시된 값(낮 동선표, 농사/채집 데이터)은 기획서상 "제안 단계, 최종 확정 아님".
 */
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final NpcRepository npcRepository;
    private final NpcLocationScheduleRepository locationScheduleRepository;
    private final CropMasterRepository cropMasterRepository;
    private final FruitMasterRepository fruitMasterRepository;
    private final ShopItemMasterRepository shopItemMasterRepository;

    @Override
    public void run(String... args) {
        if (npcRepository.count() == 0) {
            seedNpcs();
        }
        if (cropMasterRepository.count() == 0) {
            seedCrops();
        }
        if (fruitMasterRepository.count() == 0) {
            seedFruits();
        }
        if (shopItemMasterRepository.count() == 0) {
            seedShopItems();
        }
    }

    private void seedNpcs() {
        record NpcSeed(String name, String role, int age, String personality, String speech, String sample,
                        String appearance, List<Object[]> schedule) {
        }

        // appearance는 `기타/2. 에셋/NPC/*/metadata.json`의 캐릭터 생성 프롬프트에서
        // 머리카락/체격/복장 등 단서 카드 생성 LLM이 참고할 부분만 추려온 것 (원문은 영어).
        List<NpcSeed> seeds = List.of(
                new NpcSeed("현수동", "이장", 70,
                        "마을 최고령. 마을 애정도 높지만 변화를 싫어함. 겉은 퉁명스럽고 잔소리 많지만 마을 일엔 발 벗고 나섬. 고집 셈",
                        "반말 섞인 명령조, 옛날식 표현, 짧고 직설적",
                        "요즘 것들은 말이야, 마을이 뭔지도 모르면서… 쯧.",
                        "An elderly Korean man in his seventies. He has neatly combed short white hair with a "
                                + "slightly receding hairline, thick white eyebrows, deep wrinkles, and a stern "
                                + "grumpy expression. His posture is slightly bent with age. He wears a faded white "
                                + "long-sleeved shirt, a dark forest-green buttoned vest, loose dark brown trousers, "
                                + "and worn black slip-on shoes. Short elderly proportions with slightly narrow "
                                + "shoulders and a round head.",
                        List.of(
                                new Object[]{LocationSlot.PRIMARY, "마을회관", 70},
                                new Object[]{LocationSlot.SECONDARY, "마을 어귀 순찰", 20},
                                new Object[]{LocationSlot.SECONDARY, "정자", 10}
                        )),
                new NpcSeed("나주부", "아내", 32,
                        "상냥·사교적으로 보이나 낯선 사람에겐 거리를 둠. 남편(전주인)에겐 다정하고 신뢰",
                        "존댓말 기본, 부드럽고 다정한 어조",
                        "어머, 오늘 날씨 좋죠? 산책하기 딱이에요~",
                        "A Korean woman in her early thirties. She has feminine facial features, warm eyes, and "
                                + "long neat black hair tied in a low bun, with softly curved side bangs. She wears "
                                + "a muted lavender blouse with a rounded collar, a dusty rose calf-length skirt, a "
                                + "simple warm-beige waist apron, and practical dark brown shoes. Feminine average "
                                + "build with narrow shoulders and a defined waist.",
                        // "정자"는 CulpritProfileRegistry에서 현수동의 사보타주 대상 장소인데,
                        // 예전엔 현수동 본인 스케줄에만 있어서 그가 범인일 때 정자를 노리면
                        // 범인 본인(목격자 후보에서 제외됨) 말고는 아무도 그 자리에 없어 목격자가
                        // 절대 나올 수 없었다(SessionPersistenceService.findWitness). 나주부가
                        // 가끔 산책 삼아 정자에 들르는 걸로 목격 가능성을 열어둔다.
                        List.of(
                                new Object[]{LocationSlot.PRIMARY, "자택 정원", 50},
                                new Object[]{LocationSlot.SECONDARY, "상점", 25},
                                new Object[]{LocationSlot.SECONDARY, "마을회관", 15},
                                new Object[]{LocationSlot.SECONDARY, "정자", 10}
                        )),
                new NpcSeed("전주인", "남편, 상점 주인", 35,
                        "친절, 손님 응대 능숙, 눈치 빠르고 계산적. 은근히 승부욕 있음. 소비 패턴 파악 가능. 아내(나주부)와 신혼부부 느낌",
                        "상냥한 존댓말 + 장사꾼 멘트",
                        "어서 오세요! 오늘은 뭐 필요하신 거 있으세요?",
                        "A Korean man in his mid-thirties. He has neatly side-parted short black hair, alert eyes, "
                                + "and a polished friendly shopkeeper smile with a slightly calculating expression. "
                                + "He wears a faded light-blue collared shirt, a muted rust-brown shop apron, dark "
                                + "charcoal trousers, and clean brown shoes. Average adult proportions with a "
                                + "slightly large head and tidy posture.",
                        List.of(
                                new Object[]{LocationSlot.PRIMARY, "상점", 80},
                                new Object[]{LocationSlot.SECONDARY, "창고 정리", 20}
                        )),
                new NpcSeed("박영계", "양계장 주인", 41,
                        "활발·사교적, 마을 소식통 자처. 오지랖 넓고 입이 가벼움. 정 많음",
                        "친근한 반말 섞인 존댓말, 빠른 말투",
                        "얘, 그거 들었어? 아유 말도 마, 내가 다 들었잖아~",
                        "A Korean woman in her early forties. She has feminine facial features, lively eyes, and "
                                + "short-to-medium curly black permed hair that reaches her neck and creates a "
                                + "round fluffy silhouette. She wears a faded mustard-yellow work blouse, a dark "
                                + "teal full apron, loose warm-brown work pants, and dark green rubber farm boots. "
                                + "Sturdy feminine build with a slightly large head.",
                        // "마을 어귀 순찰"(현수동 대상)과 "자택 인근 텃밭"(명자유 대상)도 위 정자와
                        // 같은 이유로 목격자가 원천 불가능했던 장소들이다. 박영계는 "마을 소식통"
                        // 설정(WitnessGossipService의 GOSSIP_HUB_NAME)이라 여기저기 돌아다니는
                        // 캐릭터성과도 맞아서, 두 장소를 가끔 들르는 동선을 추가해 목격 가능성을 연다.
                        List.of(
                                new Object[]{LocationSlot.PRIMARY, "양계장", 50},
                                new Object[]{LocationSlot.SECONDARY, "마을회관", 15},
                                new Object[]{LocationSlot.SECONDARY, "상점", 10},
                                new Object[]{LocationSlot.SECONDARY, "마을 어귀 순찰", 15},
                                new Object[]{LocationSlot.SECONDARY, "자택 인근 텃밭", 10}
                        )),
                new NpcSeed("명자유", "프리랜서", 25,
                        "내향적, 히키코모리에 가까움. 겁 많고 예민하나 악의는 없음",
                        "짧고 단답형, 형식적 존댓말",
                        "…네, 뭐.",
                        "A Korean woman in her mid-twenties. She has pale feminine facial features, tired eyes, "
                                + "and very long slightly unkempt straight black hair reaching below her shoulders, "
                                + "with heavy bangs partially covering one eye. She has a slightly hunched posture. "
                                + "She wears an oversized muted blue-gray cardigan over a plain cream blouse, a long "
                                + "dark charcoal skirt, and simple faded brown slip-on shoes. Slim feminine build "
                                + "with narrow shoulders.",
                        List.of(
                                new Object[]{LocationSlot.PRIMARY, "자택", 80},
                                new Object[]{LocationSlot.SECONDARY, "자택 인근 텃밭", 20}
                        )),
                new NpcSeed("김치준", "취준생/상점 직원", 28,
                        "차분·예의 바름. 감정 표현 적음. 뒤끝 있음. 나박수와 앙숙",
                        "정중한 존댓말, 담담한 어조",
                        "네, 손님. 필요한 거 있으시면 말씀하세요.",
                        "A Korean man in his late twenties. He has neatly trimmed straight black hair with a "
                                + "simple side part, calm eyes, and a polite but emotionally restrained expression. "
                                + "His posture is straight and formal. He wears a clean off-white collared shirt, a "
                                + "muted navy sleeveless sweater vest, dark gray trousers, and simple black shoes. "
                                + "Slim average build with slightly narrow shoulders.",
                        List.of(
                                new Object[]{LocationSlot.PRIMARY, "상점(근무)", 70},
                                new Object[]{LocationSlot.SECONDARY, "마을회관 구석(이력서 작성)", 30}
                        )),
                new NpcSeed("나박수", "수박밭 주인", 32,
                        "열정파, 화끈하고 직설적, 감정 숨기지 못함. 김치준과 앙숙일 때만 뒤끝",
                        "사투리 섞인 거친 말투, 큰 목소리",
                        "아이고, 이 수박 좀 봐! 올해 최고작이여!",
                        "A Korean man in his early thirties. He has short rough black hair, thick eyebrows, "
                                + "sun-tanned skin, and a passionate loud expression. He wears a faded brick-red "
                                + "short-sleeved work shirt, loose dark olive work trousers, a light beige towel "
                                + "loosely wrapped around his neck, and dark brown rubber farm boots. Broad sturdy "
                                + "build with wide shoulders and strong arms.",
                        List.of(
                                new Object[]{LocationSlot.PRIMARY, "수박밭", 75},
                                new Object[]{LocationSlot.SECONDARY, "상점(납품)", 25}
                        ))
        );

        for (NpcSeed seed : seeds) {
            Npc npc = npcRepository.save(Npc.builder()
                    .name(seed.name())
                    .role(seed.role())
                    .age(seed.age())
                    .personalityDesc(seed.personality())
                    .speechStyle(seed.speech())
                    .sampleLine(seed.sample())
                    .appearanceDesc(seed.appearance())
                    .build());

            for (Object[] row : seed.schedule()) {
                locationScheduleRepository.save(NpcLocationSchedule.builder()
                        .npc(npc)
                        .slot((LocationSlot) row[0])
                        .locationName((String) row[1])
                        .probability((Integer) row[2])
                        .build());
            }
        }
    }

    private void seedCrops() {
        cropMasterRepository.saveAll(List.of(
                CropMaster.builder().name("당근").growDays(1).plantOrHarvestStamina(3).yieldQty(4).sellPrice(12).restoreHp(8).seedPrice(5).build(),
                CropMaster.builder().name("감자").growDays(2).plantOrHarvestStamina(5).yieldQty(3).sellPrice(18).restoreHp(10).seedPrice(10).build(),
                CropMaster.builder().name("딸기").growDays(2).plantOrHarvestStamina(4).yieldQty(5).sellPrice(16).restoreHp(10).seedPrice(12).build(),
                CropMaster.builder().name("고구마").growDays(3).plantOrHarvestStamina(5).yieldQty(2).sellPrice(25).restoreHp(15).seedPrice(15).build()
        ));
    }

    private void seedFruits() {
        fruitMasterRepository.saveAll(List.of(
                FruitMaster.builder().name("사과").regenDays(1).forageStamina(3).restoreHp(8).sellPrice(10).build(),
                FruitMaster.builder().name("산딸기").regenDays(1).forageStamina(3).restoreHp(6).sellPrice(8).build(),
                FruitMaster.builder().name("체리").regenDays(2).forageStamina(3).restoreHp(8).sellPrice(15).build(),
                FruitMaster.builder().name("감").regenDays(2).forageStamina(3).restoreHp(10).sellPrice(14).build()
        ));
    }

    private void seedShopItems() {
        shopItemMasterRepository.saveAll(List.of(
                ShopItemMaster.builder().name("운동화").itemCode(ShopItemCode.SNEAKERS)
                        .category(ItemCategory.PERMANENT_EQUIPMENT).price(50)
                        .effectDesc("이동 중 체력 소모량 초당 0.15→0.12로 감소 (20% 감소, 정지 시 소모 없음)").usageLimit("1회 구매, 영구").build(),
                ShopItemMaster.builder().name("거짓말탐지기").itemCode(ShopItemCode.LIE_DETECTOR)
                        .category(ItemCategory.CONSUMABLE).price(60)
                        .effectDesc("대화 중 사용 시 \"정직 모드\" 1턴 (알리바이 질문에 더 구체적 답변, 범인 여부는 안 알려줌)").usageLimit("1일 1회").build(),
                ShopItemMaster.builder().name("돋보기").itemCode(ShopItemCode.MAGNIFIER)
                        .category(ItemCategory.CONSUMABLE).price(30)
                        .effectDesc("애매한 단서 카드 1개를 더 명확한 문구로 갱신").usageLimit("1일 1회").build(),
                ShopItemMaster.builder().name("선물세트").itemCode(ShopItemCode.GIFT_SET)
                        .category(ItemCategory.CONSUMABLE).price(40)
                        .effectDesc("지정 NPC 호감도 즉시 +25~35").usageLimit("1개당 1회").build()
        ));
    }
}
