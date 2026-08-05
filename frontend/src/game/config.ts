import Phaser from 'phaser'

export const gameConfig: Phaser.Types.Core.GameConfig = {
  type: Phaser.AUTO,
  width: 640,
  height: 480,
  backgroundColor: '#7fae5c',
  scale: {
    // 부모 컨테이너(.day-canvas-wrap) 크기에 캔버스 자체를 맞춘다 — 좌우 레터박스 없이 꽉 채운다.
    // (FIT은 4:3 비율을 유지해서 넓은 화면에서 양옆에 여백이 남는다.)
    mode: Phaser.Scale.RESIZE,
    autoCenter: Phaser.Scale.CENTER_BOTH,
  },
  scene: [],
}
