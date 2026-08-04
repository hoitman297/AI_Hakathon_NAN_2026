package com.gameproject.llmproxy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Usage;
import com.anthropic.services.blocking.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameproject.llmproxy.dto.DialogueChatRequest;
import com.gameproject.llmproxy.dto.DialogueChatResponse;
import com.gameproject.llmproxy.dto.DialogueTurn;
import com.gameproject.llmproxy.dto.GeneratedPersona;

/**
 * Anthropic SDK의 Message/StructuredMessage는 Kotlin final class라 Mockito로 mock()하면
 * (inline mock maker를 agent로 정식 로드해도) 일부 환경에서 UnfinishedStubbingException이
 * 나는 걸 확인했다 — 그래서 mock 대신 실제 빌더로 값 객체를 직접 구성해서 사용한다.
 * (AnthropicClient/MessageService는 순수 인터페이스라 평범하게 mock 가능, 이쪽만 mock한다.)
 *
 * <p>checkRefusal의 refusal 감지 경로는 generatePersona(구조화 출력)와 chat(일반 응답)
 * 두 응답 타입에서 한 번씩만 검증한다 — 나머지 메서드는 같은 private 로직을 재사용하므로
 * 중복 검증하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class LlmServiceTest {

    @Mock
    private AnthropicClient anthropicClient;
    @Mock
    private MessageService messageService;

    private LlmService llmService;

    @BeforeEach
    void setUp() throws Exception {
        llmService = new LlmService(anthropicClient, new ObjectMapper());
        setModel(llmService, "claude-test-model");
        when(anthropicClient.messages()).thenReturn(messageService);
    }

    private static void setModel(LlmService service, String model) throws Exception {
        Field field = LlmService.class.getDeclaredField("model");
        field.setAccessible(true);
        field.set(service, model);
    }

    // ------------------------------------------------------------------
    // generatePersona
    // ------------------------------------------------------------------

    @Test
    void generatePersona_success_returnsSerializedPersonaFromLlm() throws Exception {
        GeneratedPersona persona = new GeneratedPersona(1L, "나박수", "수박밭 주인", 32,
                "다혈질", "사투리", "3년 키운 수박이여", null, false, List.of("아이고"));
        when(messageService.create(any(StructuredMessageCreateParams.class)))
                .thenReturn(structuredMessage(StopReason.END_TURN, new ObjectMapper().writeValueAsString(persona), GeneratedPersona.class));

        String json = llmService.generatePersona(1L, "나박수", "수박밭 주인", 32,
                "다혈질", "사투리", "아이고", null);

        GeneratedPersona parsed = new ObjectMapper().readValue(json, GeneratedPersona.class);
        assertThat(parsed).isEqualTo(persona);
    }

    @Test
    void generatePersona_llmRefuses_fallsBackToInputFields() throws Exception {
        when(messageService.create(any(StructuredMessageCreateParams.class)))
                .thenReturn(structuredMessage(StopReason.REFUSAL, null, GeneratedPersona.class));

        String json = llmService.generatePersona(1L, "나박수", "수박밭 주인", 32,
                "다혈질", "사투리", "아이고", "동기");

        GeneratedPersona fallback = new ObjectMapper().readValue(json, GeneratedPersona.class);
        assertThat(fallback.name()).isEqualTo("나박수");
        assertThat(fallback.motive()).isEqualTo("동기");
        assertThat(fallback.isCulprit()).isTrue();
    }

    @Test
    void generatePersona_llmThrows_fallsBackToInputFields() throws Exception {
        when(messageService.create(any(StructuredMessageCreateParams.class)))
                .thenThrow(new RuntimeException("network error"));

        String json = llmService.generatePersona(1L, "현수동", "이장", 70,
                "고집 셈", "반말", "쯧", null);

        GeneratedPersona fallback = new ObjectMapper().readValue(json, GeneratedPersona.class);
        assertThat(fallback.name()).isEqualTo("현수동");
        assertThat(fallback.isCulprit()).isFalse();
    }

    // ------------------------------------------------------------------
    // chat
    // ------------------------------------------------------------------

    @Test
    void chat_success_returnsLlmReplyAndAffinityDelta() throws Exception {
        DialogueChatResponse llmOutput = new DialogueChatResponse("마을 일은 제가 챙깁니다.", 3);
        when(messageService.create(any(StructuredMessageCreateParams.class)))
                .thenReturn(structuredMessage(StopReason.END_TURN,
                        new ObjectMapper().writeValueAsString(llmOutput), DialogueChatResponse.class));

        DialogueChatResponse result = llmService.chat(new DialogueChatRequest(personaJson(),
                List.of(new DialogueTurn("USER", "안녕하세요")), "요즘 별일 없나요?", false));

        assertThat(result.reply()).isEqualTo("마을 일은 제가 챙깁니다.");
        assertThat(result.affinityDelta()).isEqualTo(3);
    }

    @Test
    void chat_affinityDeltaOutOfRange_isClamped() throws Exception {
        DialogueChatResponse llmOutput = new DialogueChatResponse("무례하시네요.", 999);
        when(messageService.create(any(StructuredMessageCreateParams.class)))
                .thenReturn(structuredMessage(StopReason.END_TURN,
                        new ObjectMapper().writeValueAsString(llmOutput), DialogueChatResponse.class));

        DialogueChatResponse result = llmService.chat(new DialogueChatRequest(personaJson(), List.of(), "욕설", false));

        assertThat(result.affinityDelta()).isEqualTo(5);
    }

    @Test
    void chat_llmRefuses_returnsFallbackMessageWithZeroDelta() {
        when(messageService.create(any(StructuredMessageCreateParams.class)))
                .thenReturn(structuredMessage(StopReason.REFUSAL, null, DialogueChatResponse.class));

        DialogueChatResponse result = llmService.chat(new DialogueChatRequest(personaJson(), List.of(), "질문", false));

        assertThat(result.reply()).contains("잠시 후 다시 말을 걸어주세요");
        assertThat(result.affinityDelta()).isZero();
    }

    @Test
    void chat_emptyContent_returnsFallbackMessage() {
        when(messageService.create(any(StructuredMessageCreateParams.class)))
                .thenReturn(structuredMessage(StopReason.END_TURN, null, DialogueChatResponse.class));

        DialogueChatResponse result = llmService.chat(new DialogueChatRequest(personaJson(), List.of(), "질문", false));

        assertThat(result.reply()).contains("잠시 후 다시 말을 걸어주세요");
        assertThat(result.affinityDelta()).isZero();
    }

    @Test
    void chat_llmThrows_returnsFallbackMessage() {
        when(messageService.create(any(StructuredMessageCreateParams.class)))
                .thenThrow(new RuntimeException("timeout"));

        DialogueChatResponse result = llmService.chat(new DialogueChatRequest(personaJson(), List.of(), "질문", false));

        assertThat(result.reply()).contains("잠시 후 다시 말을 걸어주세요");
        assertThat(result.affinityDelta()).isZero();
    }

    // ------------------------------------------------------------------
    // generateEventContent
    // ------------------------------------------------------------------

    @Test
    void generateEventContent_success_returnsLlmText() {
        when(messageService.create(any(MessageCreateParams.class)))
                .thenReturn(message(StopReason.END_TURN, "게시판에 도발적인 쪽지가 붙었다."));

        String text = llmService.generateEventContent("마을 게시판 도발 쪽지", "VILLAGE", 7, "현수동");

        assertThat(text).isEqualTo("게시판에 도발적인 쪽지가 붙었다.");
    }

    @Test
    void generateEventContent_llmThrows_returnsFallbackWithEventType() {
        when(messageService.create(any(MessageCreateParams.class)))
                .thenThrow(new RuntimeException("network error"));

        String text = llmService.generateEventContent("협박 편지", "PLAYER", 8, "나주부");

        assertThat(text).contains("협박 편지");
    }

    // ------------------------------------------------------------------
    // generateEndingStory
    // ------------------------------------------------------------------

    @Test
    void generateEndingStory_success_returnsLlmText() {
        when(messageService.create(any(MessageCreateParams.class)))
                .thenReturn(message(StopReason.END_TURN, "그래, 내가 했다."));

        String story = llmService.generateEndingStory("나박수", "수박밭 주인", 32, "다혈질", "사투리",
                "김치준과의 갈등", "DAMAGE", "화분/진열대/간판");

        assertThat(story).isEqualTo("그래, 내가 했다.");
    }

    @Test
    void generateEndingStory_llmThrows_returnsFallbackWithNameAndMotive() {
        when(messageService.create(any(MessageCreateParams.class)))
                .thenThrow(new RuntimeException("network error"));

        String story = llmService.generateEndingStory("나박수", "수박밭 주인", 32, "다혈질", "사투리",
                "김치준과의 갈등", "DAMAGE", "화분/진열대/간판");

        assertThat(story).contains("나박수").contains("김치준과의 갈등");
    }

    // ------------------------------------------------------------------
    // generateClueContent
    // ------------------------------------------------------------------

    @Test
    void generateClueContent_success_returnsLlmText() {
        when(messageService.create(any(MessageCreateParams.class)))
                .thenReturn(message(StopReason.END_TURN, "검고 긴 머리카락 한 올이 발견됐다."));

        String text = llmService.generateClueContent("HAIR", "long black hair", "내향적", "자택", "텃밭");

        assertThat(text).isEqualTo("검고 긴 머리카락 한 올이 발견됐다.");
    }

    @Test
    void generateClueContent_llmThrows_returnsFallbackWithLocationAndTarget() {
        when(messageService.create(any(MessageCreateParams.class)))
                .thenThrow(new RuntimeException("network error"));

        String text = llmService.generateClueContent("HAIR", "long black hair", "내향적", "자택", "텃밭");

        assertThat(text).contains("자택").contains("텃밭");
    }

    // ------------------------------------------------------------------
    // clarifyClueContent
    // ------------------------------------------------------------------

    @Test
    void clarifyClueContent_success_returnsLlmText() {
        when(messageService.create(any(MessageCreateParams.class)))
                .thenReturn(message(StopReason.END_TURN, "앞머리가 한쪽 눈을 가릴 만큼 길다."));

        String text = llmService.clarifyClueContent("HAIR", "long black hair with heavy bangs", "기존 문구");

        assertThat(text).isEqualTo("앞머리가 한쪽 눈을 가릴 만큼 길다.");
    }

    @Test
    void clarifyClueContent_llmThrows_returnsFallbackAppendedToPreviousText() {
        when(messageService.create(any(MessageCreateParams.class)))
                .thenThrow(new RuntimeException("network error"));

        String text = llmService.clarifyClueContent("HAIR", "long black hair", "기존 문구");

        assertThat(text).startsWith("기존 문구").contains("돋보기로 확인");
    }

    // ------------------------------------------------------------------
    // helpers — 실제 Message/StructuredMessage 값 객체를 빌더로 직접 구성한다 (mock 대신).
    // ------------------------------------------------------------------

    private String personaJson() {
        return """
                {"npcId":1,"name":"현수동","role":"이장","age":70,"personality":"고집 셈",
                "speechStyle":"반말","backstory":"오랜 세월 마을을 지켜온 인물","motive":null,
                "isCulprit":false,"sampleLines":["쯧"]}
                """;
    }

    /** text가 null이면 content가 빈 응답(거부/무텍스트 상황 재현)을 만든다. */
    private Message message(StopReason stopReason, String text) {
        List<ContentBlock> content = text == null
                ? List.of()
                : List.of(ContentBlock.ofText(TextBlock.builder().text(text).citations(List.of()).build()));

        return Message.builder()
                .id("msg_test")
                .content(content)
                .model("claude-test-model")
                .container(Optional.empty())
                .stopDetails(Optional.empty())
                .stopSequence(Optional.empty())
                .stopReason(stopReason)
                .usage(testUsage())
                .build();
    }

    private <T> StructuredMessage<T> structuredMessage(StopReason stopReason, String rawJsonText, Class<T> outputType) {
        return new StructuredMessage<>(outputType, message(stopReason, rawJsonText));
    }

    private Usage testUsage() {
        return Usage.builder()
                .inputTokens(1)
                .outputTokens(1)
                .cacheCreationInputTokens(0)
                .cacheReadInputTokens(0)
                .cacheCreation(Optional.empty())
                .serverToolUse(Optional.empty())
                .inferenceGeo(Optional.empty())
                .serviceTier(Optional.empty())
                .build();
    }
}
