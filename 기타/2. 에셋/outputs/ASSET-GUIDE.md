# 1990년대 한국 농촌 미스터리 게임 자산 사용 안내

## React 프로젝트에 넣기

`outputs` 폴더의 내용물을 React 프로젝트의 `public/assets`에 복사합니다. 그다음 `asset-manifest.js`를 `src/assets/asset-manifest.js`로 옮겨 import하면 됩니다.

```jsx
import { villageAssets, renderGuide } from "./assets/asset-manifest";

export function Crop({ type, stage }) {
  const stageIndex = Math.max(1, Math.min(3, stage)) - 1;
  return (
    <img
      className="pixel-sprite"
      src={villageAssets.crops[type][stageIndex]}
      width={renderGuide.cropWidth}
      draggable={false}
      alt=""
    />
  );
}
```

```css
.pixel-sprite {
  display: block;
  image-rendering: pixelated;
  image-rendering: crisp-edges;
  user-select: none;
  pointer-events: none;
}
```

## 성장 상태 규칙

- 밭작물: `1 = 새싹`, `2 = 성장 중`, `3 = 수확 가능`
- 과일나무·산딸기: `1 = 채집 직후/열매 없음`, `2 = 재성장`, `3 = 채집 가능`
- 배열 인덱스는 0부터 시작하므로 게임의 1~3단계 값에서 `-1`하여 사용합니다.

## 시설 상태 규칙

- 양계장: `normal`에는 닭이 있고 `broken`에는 닭이 없습니다.
- 수박밭: `normal`은 수확 가능, `damaged`는 울타리·밭·수박이 훼손된 상태입니다.
- 상태 변경 시 동일 좌표에서 이미지 경로만 교체하면 됩니다.

## 맵 크기와 배치 기준

- 기준 타일: `32 × 32px`
- 캐릭터: 높이 `32px` 권장, 작은 버전은 `16px`
- 큰 맵은 한 장 이미지로 만들지 말고 32px 타일 좌표와 오브젝트 레이어로 구성합니다.
- 예시 월드: `160 × 120타일 = 5120 × 3840px`
- 화면에는 카메라 주변 청크만 렌더링합니다. 권장 청크는 `16 × 16타일 = 512 × 512px`입니다.
- 충돌 영역은 투명 PNG 전체가 아니라 건물 하단 기초, 나무 몸통, 울타리처럼 실제 지면을 막는 부분에만 둡니다.

권장 레이어 순서:

1. 잔디·흙·경작지·물 타일
2. 강둑과 길 가장자리
3. 다리와 밭
4. 건물·나무·울타리·가구
5. NPC·플레이어·닭
6. 지붕 또는 전경 가림 레이어
7. UI

## 성능 주의사항

- 제공 PNG는 편집용 고해상도 원본입니다. 화면에서는 `renderGuide` 값으로 축소하여 사용하세요.
- 실제 배포 전에는 필요한 표시 크기의 2배 정도로 일괄 축소한 WebP 또는 PNG 사본을 만드는 것이 좋습니다.
- 맵 전체를 하나의 5000px 이상 이미지로 렌더링하지 말고 타일과 오브젝트를 반복 배치해야 체력 기반 이동 거리도 쉽게 조정할 수 있습니다.

