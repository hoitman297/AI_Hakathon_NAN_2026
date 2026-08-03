package com.gameproject.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

/** NPC 마스터 데이터 (7명 고정). */
@Entity
@Table(name = "npc")
@Comment("NPC 마스터 데이터 (기획서상 7명 고정)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Npc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "npc_id")
    private Long npcId;

    @Comment("NPC 이름")
    @Column(nullable = false, length = 50)
    private String name;

    @Comment("마을에서의 역할/직업")
    @Column(nullable = false, length = 50)
    private String role;

    @Column
    private Integer age;

    @Comment("성격 요약 (페르소나 생성 LLM 입력값)")
    @Lob
    @Column(name = "personality_desc", columnDefinition = "TEXT")
    private String personalityDesc;

    @Comment("말투 특징 (페르소나 생성 LLM 입력값)")
    @Lob
    @Column(name = "speech_style", columnDefinition = "TEXT")
    private String speechStyle;

    @Comment("예시 대사")
    @Lob
    @Column(name = "sample_line", columnDefinition = "TEXT")
    private String sampleLine;
}
