import Phaser from 'phaser'
import { NPC_ROSTER } from '../types/npc'

export interface DaySceneInitData {
  day: number
  staminaCurrent: number
  staminaMax: number
  sneakersEquipped: boolean
  onStaminaChange: (value: number) => void
  /** 이동으로 실제 경과한 시간(초)을 주기적으로 보고 — 서버가 초당 소모량을 반영해 체력을 깎는다. */
  onMove: (seconds: number) => void
  onNpcClick: (npcName: string, npcRole: string) => void
  /** 지도 위 장소(마을회관/상점/양계장 등)를 플레이어가 가까이서 클릭했을 때 spot.key를 보고한다. */
  onLocationClick: (spotKey: string) => void
}

// 기획서 체력 세부 수치(✅ 확정): 이동 초당 0.15 소모, 운동화 착용 시 초당 0.12(20% 감소).
// 백엔드 GameConstants.MOVE_STAMINA_PER_SECOND(_WITH_SNEAKERS)와 값이 일치해야 한다.
const MOVE_STAMINA_PER_SECOND = 0.15
const MOVE_STAMINA_PER_SECOND_WITH_SNEAKERS = 0.12
// 이동 체력 소모를 이 정도 간격(초)으로 모아서 서버에 보고한다 — 매 프레임 호출하지 않는다.
const MOVE_REPORT_INTERVAL_SECONDS = 1

const TILE = 32
// NPC 스프라이트는 타일 하나에 딱 맞추면 너무 작아 보여서, 타일 그리드(이동/거리 판정)는
// 그대로 두고 시각적 크기만 이만큼 키운다.
const NPC_DISPLAY_SIZE = TILE * 1.6
const WORLD_TILES_W = 30
const WORLD_TILES_H = 20
const WORLD_W = WORLD_TILES_W * TILE
const WORLD_H = WORLD_TILES_H * TILE

// 대화를 걸 수 있는 최대 거리(플레이어 중심 ~ NPC 중심). 타일 2칸 정도 — 바로 옆이 아니어도
// 조금 여유 있게 붙으면 클릭할 수 있는 정도로 잡았다.
const NPC_INTERACT_RANGE = TILE * 2

// 장소(건물/시설)는 NPC보다 판정 영역이 넓어서 상호작용 가능 거리도 조금 더 넉넉하게 잡았다.
const LOCATION_INTERACT_RANGE = TILE * 2.5

// 넓은 화면(RESIZE 스케일 모드)에서는 카메라 뷰포트가 마을(WORLD_W x WORLD_H)보다 커질 수 있다.
// 카메라는 월드 경계 밖으로 스크롤하지 않으므로, 잔디만 이 범위까지 더 깔아서 배경색 여백이
// 그대로 드러나지 않게 한다 — 이동 가능 범위(WORLD_W/H)나 건물/NPC 배치는 그대로 둔다.
const BG_TILES_W = 90
const BG_TILES_H = 60

interface BuildingSpot {
  key: string
  tileX: number
  tileY: number
  label: string
  tilesWide: number
}

const BUILDING_SPOTS: BuildingSpot[] = [
  { key: 'village-hall', tileX: 6, tileY: 3, label: '마을회관', tilesWide: 6 },
  { key: 'produce-shop', tileX: 13, tileY: 3, label: '농산물 상점', tilesWide: 5 },
  { key: 'item-shop', tileX: 19, tileY: 3, label: '아이템 상점', tilesWide: 5 },
  { key: 'chicken-coop', tileX: 25, tileY: 3, label: '양계장', tilesWide: 4 },
  { key: 'house1', tileX: 4, tileY: 13, label: '민가', tilesWide: 5 },
  { key: 'house2', tileX: 10, tileY: 13, label: '민가', tilesWide: 5 },
  { key: 'watermelon-field', tileX: 17, tileY: 13, label: '수박밭', tilesWide: 5 },
]

const NPC_TILE_POSITIONS: Array<{ x: number; y: number }> = [
  { x: 8, y: 8 },
  { x: 15, y: 8 },
  { x: 21, y: 8 },
  { x: 6, y: 15 },
  { x: 12, y: 15 },
  { x: 18, y: 15 },
  { x: 24, y: 11 },
]

export class DayScene extends Phaser.Scene {
  private initData!: DaySceneInitData
  private stamina = 0
  private player!: Phaser.GameObjects.Rectangle
  private cursors?: Phaser.Types.Input.Keyboard.CursorKeys
  private keyW?: Phaser.Input.Keyboard.Key
  private keyA?: Phaser.Input.Keyboard.Key
  private keyS?: Phaser.Input.Keyboard.Key
  private keyD?: Phaser.Input.Keyboard.Key
  private pendingMovedSeconds = 0
  private inputLocked = false

  constructor() {
    super('DayScene')
  }

  init(data: DaySceneInitData) {
    this.initData = data
    this.stamina = data.staminaCurrent
    this.pendingMovedSeconds = 0
  }

  preload() {
    this.load.image('grass', '/assets/background-assets/terrain-grass-source.png')
    this.load.image('house1', '/assets/background-assets/buildings/houses/house-1.png')
    this.load.image('house2', '/assets/background-assets/buildings/houses/house-2.png')
    this.load.image('produce-shop', '/assets/background-assets/buildings/public/produce-shop.png')
    this.load.image('item-shop', '/assets/background-assets/buildings/public/item-shop.png')
    this.load.image('village-hall', '/assets/background-assets/buildings/public/village-hall.png')
    this.load.image('chicken-coop', '/assets/background-assets/facilities/chicken-coop-normal.png')
    this.load.image('watermelon-field', '/assets/background-assets/facilities/watermelon-field-normal.png')
    this.load.image('tree', '/assets/background-assets/objects/ordinary-tree.png')
    NPC_ROSTER.forEach((npc) => this.load.image(`npc-${npc.slug}`, npc.portrait))
  }

  create() {
    this.add.tileSprite(0, 0, BG_TILES_W * TILE, BG_TILES_H * TILE, 'grass').setOrigin(0)

    BUILDING_SPOTS.forEach((spot) => {
      const displayWidth = spot.tilesWide * TILE
      const image = this.add.image(spot.tileX * TILE, spot.tileY * TILE, spot.key).setOrigin(0, 1)
      const scale = displayWidth / image.width
      image.setDisplaySize(displayWidth, image.height * scale)
      this.add
        .text(spot.tileX * TILE, spot.tileY * TILE - image.displayHeight - 4, spot.label, {
          fontSize: '11px',
          color: '#fff8ec',
          backgroundColor: '#3b2b20cc',
          padding: { x: 4, y: 2 },
        })
        .setDepth(5)

      const centerX = spot.tileX * TILE + image.displayWidth / 2
      const centerY = spot.tileY * TILE - image.displayHeight / 2
      image.setInteractive({ useHandCursor: true })
      image.on('pointerover', () => image.setTint(0xfff1b5))
      image.on('pointerout', () => image.clearTint())
      image.on('pointerdown', () => {
        const distance = Phaser.Math.Distance.Between(this.player.x, this.player.y, centerX, centerY)
        if (distance <= LOCATION_INTERACT_RANGE) {
          this.initData.onLocationClick(spot.key)
        } else {
          this.showFloatingHint(centerX, spot.tileY * TILE - image.displayHeight - 4, '너무 멀어요! 가까이 가주세요')
        }
      })
    })

    const treeSpots: Array<[number, number]> = [
      [3, 17],
      [9, 17],
      [26, 5],
      [27, 15],
    ]
    treeSpots.forEach(([tx, ty]) => {
      const tree = this.add.image(tx * TILE, ty * TILE, 'tree').setOrigin(0.5, 1)
      tree.setDisplaySize(TILE * 2, tree.height * ((TILE * 2) / tree.width))
    })

    NPC_ROSTER.forEach((npc, index) => {
      const pos = NPC_TILE_POSITIONS[index]
      const sprite = this.add.image(pos.x * TILE, pos.y * TILE, `npc-${npc.slug}`)
      sprite.setDisplaySize(NPC_DISPLAY_SIZE, NPC_DISPLAY_SIZE)
      sprite.setInteractive({ useHandCursor: true })
      sprite.on('pointerdown', () => {
        const distance = Phaser.Math.Distance.Between(this.player.x, this.player.y, sprite.x, sprite.y)
        if (distance <= NPC_INTERACT_RANGE) {
          this.initData.onNpcClick(npc.name, npc.role)
        } else {
          this.showFloatingHint(sprite.x, sprite.y - TILE, '너무 멀어요! 가까이 가주세요')
        }
      })
      this.add
        .text(pos.x * TILE, pos.y * TILE + NPC_DISPLAY_SIZE / 2 + 2, npc.name, {
          fontSize: '10px',
          color: '#fff8ec',
          backgroundColor: '#3b2b20cc',
          padding: { x: 3, y: 1 },
        })
        .setOrigin(0.5, 0)
    })

    this.player = this.add.rectangle(TILE * 15, TILE * 11, TILE * 0.6, TILE * 0.6, 0xff5b3d)
    this.player.setStrokeStyle(2, 0x3b2b20)
    this.player.setDepth(10)

    this.cameras.main.setBounds(0, 0, WORLD_W, WORLD_H)
    this.cameras.main.startFollow(this.player, true, 0.15, 0.15)
    this.cameras.main.setZoom(1.7)

    // RESIZE 스케일 모드에서는 캔버스가 브라우저 창 크기에 맞춰 계속 바뀌는데,
    // 카메라 뷰포트는 자동으로 따라오지 않으므로 직접 맞춰줘야 한다.
    this.cameras.main.setSize(this.scale.width, this.scale.height)
    this.scale.on(Phaser.Scale.Events.RESIZE, this.handleResize, this)
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => this.scale.off(Phaser.Scale.Events.RESIZE, this.handleResize, this))

    // Phaser는 포인터 좌표를 캔버스의 DOM 위치(getBoundingClientRect)로 변환할 때 그 결과를
    // 캐싱해두고, window resize 이벤트가 있을 때만 다시 계산한다. 그런데 이 캔버스는 위쪽 HUD의
    // 실제 렌더링 높이에 따라 flex 레이아웃으로 세로 위치가 정해지는데, HUD 안 이미지 로딩 등으로
    // "크기"는 안 바뀌고 "위치"만 나중에 바뀌면 resize 이벤트가 안 뜨고 캔버스 위치 캐시가 안
    // 갱신돼서, 클릭 좌표(특히 y)가 실제 스프라이트 위치와 어긋나 NPC를 클릭해도 대화가 전혀
    // 안 열리는 문제가 있었다 — 레이아웃이 안정된 다음 프레임에 강제로 다시 계산해서 맞춘다.
    requestAnimationFrame(() => this.scale.refresh())

    if (this.input.keyboard) {
      this.cursors = this.input.keyboard.createCursorKeys()
      this.keyW = this.input.keyboard.addKey('W')
      this.keyA = this.input.keyboard.addKey('A')
      this.keyS = this.input.keyboard.addKey('S')
      this.keyD = this.input.keyboard.addKey('D')
    }

    // 씬을 벗어날 때(밤으로 전환 등) 아직 서버에 보고 안 된 이동 시간이 남아있으면 마저 보고한다.
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => this.flushPendingMove())
  }

  /**
   * 대화창/ESC 메뉴가 열려 있는 동안 호출된다. Phaser는 기본적으로 WASD/방향키를
   * 캔버스에서 전역으로 캡처(preventDefault)해서, 잠그지 않으면 채팅 입력창에 같은
   * 글자를 입력할 수 없고(예: "a") 배경에서 캐릭터도 계속 움직인다.
   */
  private handleResize(gameSize: Phaser.Structs.Size) {
    this.cameras.main.setSize(gameSize.width, gameSize.height)
  }

  /** 대화/아이템 사용/농사 등 이 씬 바깥에서 체력이 바뀌었을 때 내부 값도 맞춰준다 — 안 그러면
   * 다음 이동 프레임에서 이 씬이 들고 있던(그 사이의 변화를 모르는) 오래된 체력값으로
   * update()가 다시 계산해서 방금 반영된 변화를 덮어써 버린다. */
  syncStamina(value: number) {
    this.stamina = value
  }

  setInputLocked(locked: boolean) {
    this.inputLocked = locked
    if (locked) {
      this.input.keyboard?.disableGlobalCapture()
    } else {
      this.input.keyboard?.enableGlobalCapture()
    }
  }

  update(_time: number, delta: number) {
    if (this.inputLocked) return
    if (this.stamina <= 0) return

    let dx = 0
    let dy = 0
    if (this.cursors?.left.isDown || this.keyA?.isDown) dx -= 1
    if (this.cursors?.right.isDown || this.keyD?.isDown) dx += 1
    if (this.cursors?.up.isDown || this.keyW?.isDown) dy -= 1
    if (this.cursors?.down.isDown || this.keyS?.isDown) dy += 1

    if (dx === 0 && dy === 0) return

    const length = Math.hypot(dx, dy) || 1
    const speed = 2.4
    this.player.x = Phaser.Math.Clamp(this.player.x + (dx / length) * speed, TILE / 2, WORLD_W - TILE / 2)
    this.player.y = Phaser.Math.Clamp(this.player.y + (dy / length) * speed, TILE / 2, WORLD_H - TILE / 2)

    // 정지 시 소모 없음 — 실제로 움직인 프레임에서만 경과 시간(초)만큼 체력을 깎는다.
    const deltaSeconds = delta / 1000
    const ratePerSecond = this.initData.sneakersEquipped
      ? MOVE_STAMINA_PER_SECOND_WITH_SNEAKERS
      : MOVE_STAMINA_PER_SECOND
    this.stamina = Math.max(0, this.stamina - ratePerSecond * deltaSeconds)
    this.initData.onStaminaChange(this.stamina)

    this.pendingMovedSeconds += deltaSeconds
    if (this.pendingMovedSeconds >= MOVE_REPORT_INTERVAL_SECONDS) {
      this.flushPendingMove()
    }
  }

  /** NPC/장소와 너무 멀리 떨어진 채로 클릭했을 때, 그 위치 위에 잠깐 안내 문구를 띄웠다가 지운다. */
  private showFloatingHint(x: number, y: number, text: string) {
    const hint = this.add
      .text(x, y, text, {
        fontSize: '11px',
        color: '#fff8ec',
        backgroundColor: '#b23a2fcc',
        padding: { x: 4, y: 2 },
      })
      .setOrigin(0.5, 1)
      .setDepth(20)

    this.tweens.add({
      targets: hint,
      alpha: 0,
      delay: 700,
      duration: 400,
      onComplete: () => hint.destroy(),
    })
  }

  private flushPendingMove() {
    if (this.pendingMovedSeconds <= 0) return
    this.initData.onMove(this.pendingMovedSeconds)
    this.pendingMovedSeconds = 0
  }
}
