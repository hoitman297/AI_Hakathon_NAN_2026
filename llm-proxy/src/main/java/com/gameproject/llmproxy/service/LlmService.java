package com.gameproject.llmproxy.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.ThinkingConfigDisabled;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameproject.llmproxy.dto.DialogueChatRequest;
import com.gameproject.llmproxy.dto.DialogueChatResponse;
import com.gameproject.llmproxy.dto.DialogueTurn;
import com.gameproject.llmproxy.dto.GeneratedPersona;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 실제 LLM(Claude) 연동. 페르소나 생성 LLM과 대화용 LLM 호출을 각각 담당한다.
 * 페르소나 생성은 구조화 출력(JSON schema)으로 받아 대화 LLM이 그대로 재료로 쓸 수 있게 하고,
 * 대화 응답은 실시간성이 중요해 thinking을 꺼서 지연을 줄인다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LlmService {

    /** JSON Schema는 숫자 범위(minimum/maximum)를 강제할 수 없어("Not supported" — 구조화 출력 제약),
     * LLM이 이 범위를 벗어난 값을 낼 가능성에 대비해 애플리케이션 코드에서 방어적으로 clamp한다. */
    private static final int AFFINITY_DELTA_MIN = -5;
    private static final int AFFINITY_DELTA_MAX = 5;

    private final AnthropicClient anthropicClient;
    private final ObjectMapper objectMapper;

    @Value("${anthropic.model}")
    private String model;

    public String generatePersona(Long npcId, String name, String role, Integer age,
                                   String personalityDesc, String speechStyle, String sampleLine,
                                   String motiveText) {
        boolean isCulprit = motiveText != null;
        String systemPrompt = """
                당신은 마을 사보타주 추리 게임의 NPC 페르소나를 설계하는 작가입니다.
                주어진 NPC 기본 정보를 바탕으로, 대화용 LLM이 이 캐릭터를 실시간으로 연기할 때
                참고할 수 있는 구체적인 배경 서사와 예시 대사를 만들어주세요.
                범인으로 배정된 NPC라면(동기가 주어짐) 그 동기를 배경 서사와 자연스럽게 엮어
                구체화하되, 너무 노골적으로 죄를 암시하지는 마세요.
                """;
        String userPrompt = """
                NPC ID: %d
                이름: %s
                역할: %s
                나이: %s
                성격 요약: %s
                말투 특징: %s
                예시 대사: %s
                범인 여부: %s
                동기(초안): %s
                """.formatted(npcId, name, role, age, personalityDesc, speechStyle, sampleLine,
                isCulprit, motiveText == null ? "(범인 아님)" : motiveText);

        try {
            StructuredMessageCreateParams<GeneratedPersona> params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(2000L)
                    .system(systemPrompt)
                    .outputConfig(GeneratedPersona.class)
                    .addUserMessage(userPrompt)
                    .build();

            var response = anthropicClient.messages().create(params);
            checkRefusal(response.stopReason(), "페르소나 생성");

            GeneratedPersona persona = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .findFirst()
                    .map(typed -> typed.text())
                    .orElseThrow(() -> new IllegalStateException("LLM 응답에 persona 텍스트 블록이 없습니다"));

            return objectMapper.writeValueAsString(persona);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("생성된 페르소나 직렬화 실패", e);
        } catch (RuntimeException e) {
            log.error("페르소나 생성 LLM 호출 실패 (npcId={}), 대체 페르소나로 진행", npcId, e);
            return fallbackPersonaJson(npcId, name, role, age, personalityDesc, speechStyle, sampleLine,
                    motiveText, isCulprit);
        }
    }

    public DialogueChatResponse chat(DialogueChatRequest request) {
        GeneratedPersona persona = parsePersona(request.personaJson());

        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("당신은 '%s'(%s, %s세) 역할을 연기합니다.\n".formatted(
                persona.name(), persona.role(), persona.age()));
        systemPrompt.append("성격: %s\n".formatted(persona.personality()));
        systemPrompt.append("말투: %s\n".formatted(persona.speechStyle()));
        systemPrompt.append("배경: %s\n".formatted(persona.backstory()));
        systemPrompt.append("이 말투와 배경에 맞춰 1~3문장으로 짧게 대답하세요. 캐릭터에서 벗어나지 마세요.\n");
        if (request.honestMode()) {
            systemPrompt.append("지금은 '정직 모드'입니다: 알리바이 관련 질문에 평소보다 더 구체적으로 답하되, ")
                    .append("본인이 범인인지 여부는 절대 직접 밝히지 마세요.\n");
        }
        if (request.restrictDetectiveTalk()) {
            systemPrompt.append("지금은 이야기가 막바지에 접어든 시점입니다: 날씨·마을 소식·안부 같은 ")
                    .append("가벼운 일상 대화에는 평소처럼 응하되, 사건·단서·범인 추리와 직접 관련된 ")
                    .append("질문에는 '지금은 그 얘기를 할 때가 아니다'는 식으로 캐릭터답게 얼버무리며 ")
                    .append("구체적인 정보나 단서를 새로 주지 마세요")
                    .append(request.honestMode() ? " (단, 위 정직 모드가 적용되는 알리바이 질문은 예외입니다).\n" : ".\n");
        }
        // 목격담: 이 NPC가 그날 밤 근처에 있었다는 설정만 주어지고, 실제로 무엇을 봤는지는
        // 이 프롬프트에도 알려주지 않는다 — 캐릭터가 스스로 애매하게 지어내되 범인을 특정하지
        // 못하게 하기 위함. 7일차 이후(추리 대화 제한)에는 어차피 새 정보를 주면 안 되므로 생략.
        if (request.witnessContext() != null && !request.restrictDetectiveTalk()) {
            systemPrompt.append("이 캐릭터는 %s 무렵 근처에 있었습니다. 대화 주제가 그날 밤이나 그 장소, ".formatted(request.witnessContext()))
                    .append("혹은 마을에서 벌어진 사건과 관련되면, 그림자·발소리·인기척 정도의 애매한 목격담을 ")
                    .append("캐릭터의 성격과 말투에 맞게 자연스럽게 흘리세요 — 구체적으로 누구를 봤는지는 ")
                    .append("당신도 모른다는 태도를 유지하세요. 관련 없는 화제라면 이 이야기를 먼저 꺼내지 마세요.\n");
        }
        // 최근 마을 전체에 퍼진 사건(랜덤 이벤트) — 마을 사람이면 누구나 알 법한 공개 사건이라
        // 자연스러운 잡담 소재로 씀. 플레이어 개인 대상 이벤트는 여기 포함되지 않는다.
        if (request.recentVillageEventContext() != null) {
            systemPrompt.append("최근 마을에 이런 소문이 돌고 있습니다: %s ".formatted(request.recentVillageEventContext()))
                    .append("자연스러운 흐름이면 이 이야기를 언급하거나 반응해도 좋지만, 캐릭터가 관심 없어할 ")
                    .append("만한 주제라면 굳이 먼저 꺼내지 않아도 됩니다.\n");
        }
        systemPrompt.append(affinityToneGuidance(request.affinityScore(), request.honestMode()));
        systemPrompt.append("""
                또한 이 캐릭터의 성격·가치관·말투에 비추어, 방금 플레이어 발화가 이 캐릭터의
                호감도에 어떤 영향을 줬는지 판단하세요. 예의 바르거나 이 캐릭터가 흥미로워할
                만한 화제거나 다정한 태도면 호감도를 올리고, 무례하거나 캐묻거나 이 캐릭터가
                싫어할 만한 태도면 내리세요. 특별히 영향이 없는 평범한 대화면 0으로 두세요.
                실제 게임 밸런스상 한 턴의 변화 폭은 보통 작습니다 — 평범한 대화는 -2~+3 정도,
                뚜렷하게 무례하거나 인상적인 경우에만 그보다 큰 값을 쓰세요.
                """);

        List<MessageParam> messages = new ArrayList<>();
        for (DialogueTurn turn : request.history()) {
            MessageParam.Role role = "NPC".equalsIgnoreCase(turn.sender())
                    ? MessageParam.Role.ASSISTANT
                    : MessageParam.Role.USER;
            messages.add(MessageParam.builder().role(role).content(turn.message()).build());
        }
        messages.add(MessageParam.builder().role(MessageParam.Role.USER).content(request.userMessage()).build());

        try {
            StructuredMessageCreateParams<DialogueChatResponse> params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(1024L)
                    .system(systemPrompt.toString())
                    .thinking(ThinkingConfigDisabled.builder().build())
                    .outputConfig(DialogueChatResponse.class)
                    .messages(messages)
                    .build();

            var response = anthropicClient.messages().create(params);
            checkRefusal(response.stopReason(), "대화 응답");

            DialogueChatResponse result = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .findFirst()
                    .map(typed -> typed.text())
                    .orElseThrow(() -> new IllegalStateException("LLM 응답에 대화 텍스트 블록이 없습니다"));

            return new DialogueChatResponse(result.reply(), clampAffinityDelta(result.affinityDelta()));
        } catch (RuntimeException e) {
            log.error("대화 LLM 호출 실패", e);
            return new DialogueChatResponse("(연결이 불안정한지 대답을 잇지 못했습니다. 잠시 후 다시 말을 걸어주세요.)", 0);
        }
    }

    /**
     * 기획서 확정 스펙: 호감도 70점 이상은 우호적 태도로 단서·정보를 먼저 제공하려는 태도,
     * 30~70점은 기본적인 일상 대화만 가능(단서를 스스로 먼저 꺼내지 않음),
     * 30점 미만은 대화 자체를 회피하며 단답형으로만 답함(완전 차단은 아님).
     * 30점 미만 구간은 여기서 더 나아가 비협조적으로 얼버무리거나 살짝 말을 지어내는 등
     * 완전히 솔직하지 않은 태도까지 취하도록 지시한다(사용자 요청 확장) — 다만 정직 모드가
     * 적용되는 알리바이 질문에 대해서만큼은 예외로 둔다.
     */
    private String affinityToneGuidance(int affinityScore, boolean honestMode) {
        if (affinityScore >= 70) {
            return "현재 이 캐릭터의 플레이어에 대한 호감도는 %d점(높음)입니다. 플레이어에게 우호적이고 친근한 태도로 답하고, "
                    .formatted(affinityScore)
                    + "본인이 알고 있는 단서나 정보가 있다면 먼저 나서서 알려주려는 태도를 보이세요.\n";
        } else if (affinityScore >= 30) {
            return "현재 이 캐릭터의 플레이어에 대한 호감도는 %d점(보통)입니다. 예의는 지키되 아직 특별히 친하지는 않은 "
                    .formatted(affinityScore)
                    + "사이입니다. 일상적인 대화 정도만 하고, 단서나 민감한 정보를 스스로 먼저 꺼내지는 마세요.\n";
        } else {
            String guidance = "현재 이 캐릭터의 플레이어에 대한 호감도는 %d점(낮음)입니다. 플레이어를 경계하거나 불편해하며 "
                    .formatted(affinityScore)
                    + "대화를 짧고 무뚝뚝하게 회피하려는 태도를 보이세요. 단서나 정보는 절대 스스로 꺼내지 마세요. "
                    + "질문에 비협조적으로 얼버무리거나, 사실을 살짝 다르게 말하거나 둘러대는 등 완전히 솔직하지 "
                    + "않은 태도를 보여도 됩니다.";
            if (honestMode) {
                guidance += " 다만 위에서 정직 모드가 적용된다고 한 알리바이 관련 질문에 한해서는 그 지시를 우선하세요.";
            }
            return guidance + " 대화 자체를 완전히 거부하지는 말고 짧게라도 응답하세요.\n";
        }
    }

    private int clampAffinityDelta(Integer affinityDelta) {
        if (affinityDelta == null) {
            return 0;
        }
        return Math.max(AFFINITY_DELTA_MIN, Math.min(AFFINITY_DELTA_MAX, affinityDelta));
    }

    /**
     * 오답 고발 직후 벌어지는 랜덤 이벤트(마을 게시판 도발 쪽지 등)의 연출 텍스트.
     * 실제 범인이 누구인지는 이 메서드에 아예 전달되지 않으므로 응답이 범인을 암시할 수 없다.
     */
    public String generateEventContent(String eventType, String target, int day, String accusedNpcName) {
        String systemPrompt = """
                당신은 마을 사보타주 추리 게임의 이벤트 연출 작가입니다.
                플레이어가 오답 고발을 한 직후 벌어지는 짧은 사건을 묘사합니다.
                누가 진짜 범인인지는 알 수 없다는 전제로 글을 쓰세요 — 특정 인물을 범인으로
                암시하거나 지목하지 마세요. 2~3문장으로 긴장감 있게 서술하세요.
                """;
        String targetDesc = "PLAYER".equals(target) ? "플레이어 본인" : "마을 전체/주민들";
        String userPrompt = """
                일차: %d
                이벤트 종류: %s
                사건 대상: %s
                직전 상황: 플레이어가 %s을(를) 범인으로 지목했으나 틀렸다.
                """.formatted(day, eventType, targetDesc, accusedNpcName);

        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(300L)
                    .system(systemPrompt)
                    .thinking(ThinkingConfigDisabled.builder().build())
                    .addUserMessage(userPrompt)
                    .build();

            Message response = anthropicClient.messages().create(params);
            checkRefusal(response.stopReason(), "랜덤 이벤트 연출");

            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .findFirst()
                    .map(text -> text.text())
                    .orElseGet(() -> fallbackEventDescription(eventType));
        } catch (RuntimeException e) {
            log.error("랜덤 이벤트 연출 LLM 호출 실패 (eventType={}), 대체 문구로 진행", eventType, e);
            return fallbackEventDescription(eventType);
        }
    }

    /**
     * 밤 사보타주 다음날 아침, 마을에 퍼지는 소문 수준의 짧은 연출 텍스트.
     * 단서 카드(포렌식 톤, 애매하게 설계)와 달리 이건 "다음날 아침 알림" 화면에 쓰이는
     * 분위기용 텍스트라 범인을 특정할 정보(외형 등)는 아예 주지 않는다 — 장소/유형/대상만으로 서술.
     */
    public String generateSabotageSummary(String location, String type, String subTarget, int day) {
        String systemPrompt = """
                당신은 마을 사보타주 추리 게임의 아침 뉴스 연출 작가입니다.
                간밤에 벌어진 사보타주 사건이 다음날 아침 마을에 알려지는 순간을 1~2문장으로
                분위기 있게 묘사하세요. 범인이 누구인지 암시할 정보(외형, 이름 등)는 전혀 없이,
                오직 장소와 무슨 일이 있었는지만으로 서술하세요.
                """;
        String userPrompt = """
                일차: %d
                장소: %s
                사보타주 유형: %s
                구체적 피해 대상: %s
                """.formatted(day, location, type, subTarget);

        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(300L)
                    .system(systemPrompt)
                    .thinking(ThinkingConfigDisabled.builder().build())
                    .addUserMessage(userPrompt)
                    .build();

            Message response = anthropicClient.messages().create(params);
            checkRefusal(response.stopReason(), "밤 사보타주 아침 알림 생성");

            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .findFirst()
                    .map(text -> text.text())
                    .orElseGet(() -> fallbackSabotageSummary(location));
        } catch (RuntimeException e) {
            log.error("밤 사보타주 아침 알림 LLM 호출 실패 (location={}), 대체 문구로 진행", location, e);
            return fallbackSabotageSummary(location);
        }
    }

    /** 선물세트를 받은 NPC의 짧은 감사/반응 대사. */
    public String generateGiftReaction(String name, String role, Integer age, String personalityDesc,
                                        String speechStyle, String sampleLine) {
        String systemPrompt = """
                당신은 마을 사보타주 추리 게임의 이벤트 연출 작가입니다.
                플레이어에게 선물을 받은 NPC가 그 자리에서 보이는 짧은 반응을 씁니다.
                이 캐릭터의 성격·말투·말버릇에 맞춰 1인칭으로 1~2문장만 쓰세요.
                """;
        String userPrompt = """
                이름: %s
                역할: %s
                나이: %s
                성격: %s
                말투: %s
                평소 말버릇 예시: %s
                """.formatted(name, role, age, personalityDesc, speechStyle, sampleLine);

        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(200L)
                    .system(systemPrompt)
                    .thinking(ThinkingConfigDisabled.builder().build())
                    .addUserMessage(userPrompt)
                    .build();

            Message response = anthropicClient.messages().create(params);
            checkRefusal(response.stopReason(), "선물 반응 생성");

            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .findFirst()
                    .map(text -> text.text())
                    .orElseGet(() -> fallbackGiftReaction(name));
        } catch (RuntimeException e) {
            log.error("선물 반응 LLM 호출 실패 (name={}), 대체 문구로 진행", name, e);
            return fallbackGiftReaction(name);
        }
    }

    /**
     * 오답 고발로 지목된 NPC의 억울함 토로 연출. 이 메서드가 호출되는 시점엔 이미 오답으로
     * 판정됐다는 뜻이라(accused != 실제 범인) 이 NPC는 항상 무고하다 — 실제 범인이 누구인지는
     * 이 NPC도 알 길이 없으므로 그런 내용은 아예 전달하지 않는다.
     */
    public String generateWrongAccusationReaction(String name, String role, Integer age,
                                                    String personalityDesc, String speechStyle, String sampleLine) {
        String systemPrompt = """
                당신은 마을 사보타주 추리 게임의 이벤트 연출 작가입니다.
                플레이어에게 범인으로 잘못 지목된(실제로는 무고한) NPC가 그 자리에서 억울함을
                토로하는 장면을 씁니다. 이 캐릭터의 성격·말투·말버릇에 맞춰 1인칭으로,
                황당하거나 억울하거나 화나거나 하는 감정을 자연스럽게 드러내며 2~3문장으로 쓰세요.
                실제 범인이 누구인지는 이 캐릭터도 전혀 모르므로 다른 인물을 언급하거나
                추측하지 마세요.
                """;
        String userPrompt = """
                이름: %s
                역할: %s
                나이: %s
                성격: %s
                말투: %s
                평소 말버릇 예시: %s
                """.formatted(name, role, age, personalityDesc, speechStyle, sampleLine);

        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(300L)
                    .system(systemPrompt)
                    .thinking(ThinkingConfigDisabled.builder().build())
                    .addUserMessage(userPrompt)
                    .build();

            Message response = anthropicClient.messages().create(params);
            checkRefusal(response.stopReason(), "오답 고발 반응 생성");

            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .findFirst()
                    .map(text -> text.text())
                    .orElseGet(() -> fallbackWrongAccusationReaction(name));
        } catch (RuntimeException e) {
            log.error("오답 고발 반응 LLM 호출 실패 (name={}), 대체 문구로 진행", name, e);
            return fallbackWrongAccusationReaction(name);
        }
    }

    /** 정답 고발 성공 시 공개되는, 범인 캐릭터 개별 엔딩 스토리. */
    public String generateEndingStory(String name, String role, Integer age, String personalityDesc,
                                       String speechStyle, String motiveText, String primaryType,
                                       String targetPoolDesc) {
        String systemPrompt = """
                당신은 마을 사보타주 추리 게임의 엔딩 작가입니다. 플레이어가 범인을 정확히
                지목했을 때 공개되는, 이 캐릭터만의 개별 엔딩 스토리를 씁니다.
                자백하듯 감정을 담아 4~6문장으로, 왜 이런 일을 벌였는지와 지금 심경을
                그 인물의 시점에서 서술하세요.
                """;
        String userPrompt = """
                이름: %s
                역할: %s
                나이: %s
                성격: %s
                말투: %s
                동기: %s
                저지른 사보타주 유형: %s
                구체적으로 벌인 일: %s
                """.formatted(name, role, age, personalityDesc, speechStyle, motiveText, primaryType, targetPoolDesc);

        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(600L)
                    .system(systemPrompt)
                    .thinking(ThinkingConfigDisabled.builder().build())
                    .addUserMessage(userPrompt)
                    .build();

            Message response = anthropicClient.messages().create(params);
            checkRefusal(response.stopReason(), "엔딩 스토리 생성");

            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .findFirst()
                    .map(text -> text.text())
                    .orElseGet(() -> fallbackEndingStory(name, motiveText));
        } catch (RuntimeException e) {
            log.error("엔딩 스토리 생성 LLM 호출 실패 (name={}), 대체 문구로 진행", name, e);
            return fallbackEndingStory(name, motiveText);
        }
    }

    /**
     * 단서 카드 문구. NPC의 실제 외형/성격 묘사를 참고해 NPC마다 다른 단서가 나오게 하되,
     * 문구 자체는 애매하게 설계해 이 정보만으로 범인을 바로 특정할 수 없게 한다.
     */
    public String generateClueContent(String topic, String appearanceDesc, String personalityDesc,
                                       String location, String subTarget) {
        String systemPrompt = """
                당신은 마을 사보타주 추리 게임의 단서 카드 작가입니다. 이번 판의 실제 범인(용의자)의
                외형/성격 묘사를 참고해 아래 단서 주제에 맞는 문구를 1~2문장으로 씁니다.
                가짜 단서는 없고 항상 진짜 단서만 쓰되, 문구 자체를 애매하게 설계해 이 정보만으로
                바로 범인을 특정할 수 없게 하세요. 범인의 이름이나 정체는 절대 언급하지 마세요.

                주제별 작성 가이드:
                - HAIR(머리카락): 외형 묘사의 머리카락 색깔/길이/질감 중 일부만 언급하세요.
                - BELONGING(소지품): 외형 묘사의 복장/소지품에서 힌트를 얻어 개인 물건이나 범행
                  도구 흔적을 묘사하세요.
                - FOOTPRINT(발자국): 체격 묘사(체구·자세)에서 크기·보폭을 유추해 묘사하세요.
                - BLOOD(혈흔): 외형과 무관하게 그 자리에서 발견된 혈흔 흔적만 짧게 묘사하세요
                  (1회성 단서).
                - MARK(자국): 체격/힘 관련 묘사를 참고해 긁힌 자국·도구 자국·넘어진 흔적 등을
                  묘사하세요.
                """;
        String userPrompt = """
                단서 주제: %s
                사건 발생 장소: %s
                구체적 대상: %s
                용의자 외형 묘사: %s
                용의자 성격 요약: %s
                """.formatted(topic, location, subTarget, appearanceDesc, personalityDesc);

        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(300L)
                    .system(systemPrompt)
                    .thinking(ThinkingConfigDisabled.builder().build())
                    .addUserMessage(userPrompt)
                    .build();

            Message response = anthropicClient.messages().create(params);
            checkRefusal(response.stopReason(), "단서 카드 생성");

            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .findFirst()
                    .map(text -> text.text())
                    .orElseGet(() -> fallbackClueText(topic, location, subTarget));
        } catch (RuntimeException e) {
            log.error("단서 카드 생성 LLM 호출 실패 (topic={}), 대체 문구로 진행", topic, e);
            return fallbackClueText(topic, location, subTarget);
        }
    }

    /** 돋보기 사용 시 단서 문구에 외형 묘사 기반 디테일을 하나 더 추가해 갱신한다. */
    public String clarifyClueContent(String topic, String appearanceDesc, String previousText) {
        String systemPrompt = """
                당신은 단서 카드를 돋보기로 자세히 살펴봤을 때 드러나는 추가 정보를 씁니다.
                기존 애매한 문구에 용의자 외형 묘사에서 얻은 디테일 한 가지를 더 추가해 2~3문장으로
                갱신하세요. 여전히 범인의 이름이나 정체는 절대 언급하지 마세요.
                """;
        String userPrompt = """
                단서 주제: %s
                기존 문구: %s
                용의자 외형 묘사: %s
                """.formatted(topic, previousText, appearanceDesc);

        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(300L)
                    .system(systemPrompt)
                    .thinking(ThinkingConfigDisabled.builder().build())
                    .addUserMessage(userPrompt)
                    .build();

            Message response = anthropicClient.messages().create(params);
            checkRefusal(response.stopReason(), "단서 카드 명확화");

            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .findFirst()
                    .map(text -> text.text())
                    .orElseGet(() -> fallbackClarifiedText(previousText));
        } catch (RuntimeException e) {
            log.error("단서 카드 명확화 LLM 호출 실패 (topic={}), 대체 문구로 진행", topic, e);
            return fallbackClarifiedText(previousText);
        }
    }

    private String fallbackClueText(String topic, String location, String subTarget) {
        return topic + " 관련 단서 — 그날 밤 " + location + "의 " + subTarget + " 부근에서 발견됨.";
    }

    private String fallbackClarifiedText(String previousText) {
        return previousText + " (돋보기로 확인: 좀 더 구체적인 정황이 드러났다)";
    }

    private String fallbackSabotageSummary(String location) {
        return "간밤에 " + location + " 근처에서 무슨 일이 있었다는 소문이 마을에 퍼졌다.";
    }

    private String fallbackGiftReaction(String name) {
        return name + "이(가) 선물을 받고 반가운 표정을 짓습니다.";
    }

    private String fallbackWrongAccusationReaction(String name) {
        return name + "이(가) 억울하다는 표정으로 강하게 부인합니다. \"저는 절대 그런 적 없어요!\"";
    }

    private String fallbackEventDescription(String eventType) {
        return "그날 밤, " + eventType + "이(가) 있었다는 이야기가 조용히 마을에 퍼졌다.";
    }

    private String fallbackEndingStory(String name, String motiveText) {
        return name + "이(가) 범인이었습니다. 동기: " + motiveText;
    }

    /**
     * Claude가 안전 정책상 응답을 거부하면 stop_reason이 REFUSAL로 온다 (content는
     * 비어있거나 일부만 옴). 여기서 미리 걸러서 명확한 예외로 던지면, 호출부의 기존
     * catch(RuntimeException) 폴백 로직으로 자연스럽게 이어진다.
     *
     * <p>주의: {@code stopReason()}은 {@code Optional<StopReason>}을 반환한다. 과거에는
     * 이 파라미터를 {@code Object}로 받아 {@code String.valueOf(stopReason)}로 비교했는데,
     * {@code String.valueOf(Optional.of(StopReason.REFUSAL))}은 {@code "Optional[refusal]"}이 되어
     * {@code "refusal"}과 절대 같아질 수 없었다 — 즉 이 안전장치가 실질적으로 항상 무력화돼
     * 있었다(테스트 작성 중 발견). {@code Optional<StopReason>}을 직접 받아 비교하도록 수정.
     */
    private void checkRefusal(Optional<StopReason> stopReason, String context) {
        if (stopReason.filter(StopReason.REFUSAL::equals).isPresent()) {
            throw new IllegalStateException("LLM이 안전 정책상 응답을 거부했습니다 (" + context + ")");
        }
    }

    private GeneratedPersona parsePersona(String personaJson) {
        try {
            return objectMapper.readValue(personaJson, GeneratedPersona.class);
        } catch (JsonProcessingException e) {
            log.warn("personaJson 파싱 실패, 원본 문자열을 배경으로 대체", e);
            return new GeneratedPersona(null, "알 수 없는 NPC", "주민", null,
                    "알 수 없음", "평범한 존댓말", personaJson, null, false, List.of());
        }
    }

    private String fallbackPersonaJson(Long npcId, String name, String role, Integer age,
                                        String personalityDesc, String speechStyle, String sampleLine,
                                        String motiveText, boolean isCulprit) {
        GeneratedPersona fallback = new GeneratedPersona(
                npcId, name, role, age, personalityDesc, speechStyle,
                sampleLine, motiveText, isCulprit, List.of(sampleLine));
        try {
            return objectMapper.writeValueAsString(fallback);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("대체 페르소나 직렬화마저 실패", e);
        }
    }
}
