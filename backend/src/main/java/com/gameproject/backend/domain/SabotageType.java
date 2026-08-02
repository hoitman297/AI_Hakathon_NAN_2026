package com.gameproject.backend.domain;

/** 사보타주 유형 3분류 (기획서 고정, 카테고리 추가 불가) */
public enum SabotageType {
    THEFT,      // 절도형: 작물/과일 서리, 상점 물건
    DAMAGE,     // 파손형: 울타리, 벤치, 조각상, 화분
    DISRUPTION  // 방해공작형: 동물 풀어놓기, 작물에 나쁜 것 뿌리기
}
