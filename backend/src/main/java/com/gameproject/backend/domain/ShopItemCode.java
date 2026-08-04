package com.gameproject.backend.domain;

/**
 * 상점 아이템 4종을 코드로 식별하기 위한 값. 이름(name)은 화면 표시용 텍스트라
 * 표기 변경에 취약하므로, 게임 로직(효과 적용 분기 등)에서는 이 코드로 식별한다.
 */
public enum ShopItemCode {
    SNEAKERS,       // 운동화
    LIE_DETECTOR,   // 거짓말탐지기
    MAGNIFIER,      // 돋보기
    GIFT_SET        // 선물세트
}
