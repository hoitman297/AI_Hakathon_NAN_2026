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

    public String chat(DialogueChatRequest request) {
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

        List<MessageParam> messages = new ArrayList<>();
        for (DialogueTurn turn : request.history()) {
            MessageParam.Role role = "NPC".equalsIgnoreCase(turn.sender())
                    ? MessageParam.Role.ASSISTANT
                    : MessageParam.Role.USER;
            messages.add(MessageParam.builder().role(role).content(turn.message()).build());
        }
        messages.add(MessageParam.builder().role(MessageParam.Role.USER).content(request.userMessage()).build());

        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(1024L)
                    .system(systemPrompt.toString())
                    .thinking(ThinkingConfigDisabled.builder().build())
                    .messages(messages)
                    .build();

            Message response = anthropicClient.messages().create(params);
            checkRefusal(response.stopReason(), "대화 응답");

            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .findFirst()
                    .map(text -> text.text())
                    .orElse("...");
        } catch (RuntimeException e) {
            log.error("대화 LLM 호출 실패", e);
            return "(연결이 불안정한지 대답을 잇지 못했습니다. 잠시 후 다시 말을 걸어주세요.)";
        }
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
