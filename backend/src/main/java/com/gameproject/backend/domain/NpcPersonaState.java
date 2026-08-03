package com.gameproject.backend.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

/**
 * 페르소나 생성 LLM의 결과 저장소.
 * 아키텍처 결정: 페르소나 생성 LLM과 대화용 LLM을 분리하고, 생성된 상세 성격을
 * DB에 저장했다가 대화 시 불러오는 구조 — llm-proxy는 상태를 갖지 않는(stateless)
 * 프록시로 두고, 영속성은 backend가 전담한다.
 */
@Entity
@Table(name = "npc_persona_state", uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "npc_id"}))
@Comment("페르소나 생성 LLM 결과 저장소 (판+NPC별 1건, 대화용 LLM이 매 대화마다 불러다 씀)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NpcPersonaState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "npc_id", nullable = false)
    private Npc npc;

    @Comment("페르소나 생성 LLM이 만든 상세 성격/배경 (JSON 문자열로 저장)")
    @Lob
    @Column(name = "generated_persona_json", nullable = false, columnDefinition = "TEXT")
    private String generatedPersonaJson;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;
}
