package com.gameproject.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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
 * 과일별 채집 쿨다운(재생 주기) 상태.
 * 최초 설계 초안에는 없었으나, "같은 나무는 재생 주기 전까지 재채집 불가" 규칙을
 * 구현하려면 필요해서 추가한 테이블. 개별 나무 단위가 아니라 과일 종류 단위로 근사함.
 */
@Entity
@Table(name = "fruit_forage_state", uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "fruit_id"}))
@Comment("과일별 채집 쿨다운 상태 (\"같은 나무\" 대신 \"같은 과일 종류\" 단위로 근사). 최초 기획 초안에는 없던 구현 보완 테이블")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FruitForageState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fruit_id", nullable = false)
    private FruitMaster fruit;

    @Comment("가장 최근 채집한 일차 (null이면 아직 채집한 적 없음)")
    @Column(name = "last_foraged_day")
    private Integer lastForagedDay;
}
