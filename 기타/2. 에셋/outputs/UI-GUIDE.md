# UI 자산 적용 안내

UI 이미지에는 글자를 넣지 않았습니다. 한글 제목·수치·미션 내용은 React에서 텍스트로 올려야 수정과 반응형 처리가 쉽고 선명합니다.

## 체력·이동 에너지 바

`stamina-frame.png`와 `stamina-fill.png`를 같은 위치에 겹칩니다. 채움 이미지를 감싼 요소의 너비만 현재 체력 비율로 줄입니다.

```jsx
export function StaminaBar({ value, max = 100 }) {
  const ratio = Math.max(0, Math.min(1, value / max));

  return (
    <div className="stamina-hud">
      <img className="stamina-icon" src={villageAssets.ui.stamina.sneakerIcon} alt="이동 체력" />
      <div className="stamina-bar">
        <div className="stamina-fill-clip" style={{ width: `${ratio * 100}%` }}>
          <img className="stamina-fill" src={villageAssets.ui.stamina.fill} alt="" />
        </div>
        <img className="stamina-frame" src={villageAssets.ui.stamina.frame} alt="" />
      </div>
    </div>
  );
}
```

```css
.stamina-hud { display: flex; align-items: center; gap: 4px; }
.stamina-icon { width: 40px; image-rendering: pixelated; }
.stamina-bar { position: relative; width: 240px; height: 61px; }
.stamina-frame { position: absolute; inset: 0; width: 100%; height: 100%; z-index: 2; image-rendering: pixelated; }
.stamina-fill-clip { position: absolute; left: 5%; top: 27%; width: 90%; height: 46%; overflow: hidden; z-index: 1; }
.stamina-fill { width: 216px; height: 100%; max-width: none; image-rendering: pixelated; }
```

## 인벤토리

- `inventory-window-trimmed.png`에는 5 × 4 슬롯이 있습니다.
- 배경 이미지를 패널에 깔고 실제 아이템 버튼은 CSS Grid로 같은 위치에 겹칩니다.
- 가방 레벨에 따라 활성 슬롯을 `8 → 14 → 20`처럼 늘릴 수 있습니다.
- 이미지 자체의 빈 제목·설명 영역에는 React 텍스트를 올립니다.

## 미션 카드

- `mission-card-active.png`: 진행 중
- `mission-card-completed.png`: 녹색 봉인
- `mission-card-failed.png`: 깨진 붉은 봉인
- 카드 중앙의 큰 창에는 미션 이미지를 배치합니다.
- 카드 아래의 빈 종이 영역에는 1~2줄짜리 짧은 안내 문구만 배치합니다.
- `missions/examples`에는 문서의 사보타주 흐름을 반영한 양계장 조사·수박밭 조사 완성 예시가 있습니다.

```jsx
export function MissionCard({ state = "active", image, children }) {
  return (
    <article className="mission-card">
      <img className="mission-card-frame pixel-ui" src={villageAssets.ui.missionCards[state]} alt="" />
      <img className="mission-card-image pixel-ui" src={image} alt="" />
      <p className="mission-card-caption">{children}</p>
    </article>
  );
}
```

```css
.mission-card { position: relative; width: 300px; aspect-ratio: 0.84; }
.mission-card-frame { position: absolute; inset: 0; width: 100%; height: 100%; }
.mission-card-image {
  position: absolute;
  left: 11.5%; top: 14.5%; width: 77%; height: 58%;
  object-fit: cover;
}
.mission-card-caption {
  position: absolute;
  left: 12%; right: 12%; top: 75%; margin: 0;
  color: #3b2b20; text-align: center; line-height: 1.35;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
  overflow: hidden;
}
```

모든 UI 이미지에는 다음 스타일을 적용하세요.

```css
.pixel-ui {
  image-rendering: pixelated;
  image-rendering: crisp-edges;
  user-select: none;
}
```
