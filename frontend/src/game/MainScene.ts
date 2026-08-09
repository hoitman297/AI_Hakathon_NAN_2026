import Phaser from 'phaser'

import { NPC_ROSTER } from '../types/npc'
import { getPlayerProfile } from '../state/playerProfile'

type TilePoint = { x: number; y: number }

type TerrainMapData = {
  tileWidth: number
  tileHeight: number
  width: number
  height: number
  pixelWidth: number
  pixelHeight: number
  spawn: TilePoint
  bridge: { tileX: number; tileY: number; crossingTiles: number }
  zones: Array<{ id: number; x: number; y: number; width: number; height: number; name: string }>
  collision: { blockedTiles: TilePoint[] }
}

export interface MainSceneInitData {
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
  /** 지금 미습득 단서가 있는 장소 스팟 키 목록(clueLocations.spotsWithPendingClue 결과). */
  clueSpots?: string[]
}

const PLAYER_SPEED = 145
const WORLD_OBJECT_SCALE = 1
const WALK_DIRECTIONS = ['south', 'south-east', 'east', 'north-east', 'north', 'north-west', 'west', 'south-west'] as const
// player-boy-v2/player-girl-v2 Idle 이미지는 48x48로 만들어져 있고, 새로 추가된 player-male
// 걷기 애니메이션 프레임은 92x92라 그대로 섞어 쓰면 걷기 시작/정지할 때마다 캐릭터 크기가
// 튀어 보인다 — 항상 48px로 보이도록 걷기 텍스처 쪽만 이 비율로 축소한다.
const PLAYER_IDLE_DISPLAY_SIZE = 48
const PLAYER_WALK_FRAME_SIZE = 92
// 기획서 체력 세부 수치(✅ 확정): 이동 초당 0.15 소모, 운동화 착용 시 초당 0.12(20% 감소).
// 백엔드 GameConstants.MOVE_STAMINA_PER_SECOND(_WITH_SNEAKERS)와 값이 일치해야 한다.
const MOVE_STAMINA_PER_SECOND = 0.15
const MOVE_STAMINA_PER_SECOND_WITH_SNEAKERS = 0.12
// 이동 체력 소모를 이 정도 간격(초)으로 모아서 서버에 보고한다 — 매 프레임 호출하지 않는다.
const MOVE_REPORT_INTERVAL_SECONDS = 1

const NPC_DAY_SCHEDULES = {
  hyeonSudong: { primary: 'village-hall', primaryChance: 0.7, secondary: ['village-entrance', 'pavilion'], secondaryChance: [0.2, 0.1] },
  najubu: { primary: 'home-garden', primaryChance: 0.6, secondary: ['produce-shop', 'village-hall'], secondaryChance: [0.25, 0.15] },
  jeonJuin: { primary: 'item-shop', primaryChance: 0.8, secondary: 'warehouse', secondaryChance: 0.2 },
  parkYounggye: { primary: 'chicken-coop', primaryChance: 0.6, secondary: ['village-hall', 'shop'], secondaryChance: [0.25, 0.15] },
  myeongJayu: { primary: 'home', primaryChance: 0.8, secondary: 'home-garden', secondaryChance: 0.2 },
  kimChijun: { primary: 'item-shop', primaryChance: 0.7, secondary: 'village-hall-corner', secondaryChance: 0.3 },
  naBaksu: { primary: 'watermelon-field', primaryChance: 0.75, secondary: 'shop-delivery', secondaryChance: 0.25 },
} as const

export class MainScene extends Phaser.Scene {
  private player!: Phaser.GameObjects.Sprite
  private cursors!: Phaser.Types.Input.Keyboard.CursorKeys
  private movementKeys!: Record<'up' | 'down' | 'left' | 'right', Phaser.Input.Keyboard.Key>
  private blockedTiles = new Set<string>()
  private mapData!: TerrainMapData
  private chickenCoop!: Phaser.GameObjects.Image
  private watermelonField!: Phaser.GameObjects.Image
  private chickens: Phaser.GameObjects.Image[] = []
  private clueMarkersBySpot = new Map<string, Phaser.GameObjects.Text>()
  private activeClueSpots = new Set<string>()
  private villageAnimals: Phaser.GameObjects.Image[] = []
  private shadowPairs: Array<{ object: Phaser.GameObjects.Image; shadow: Phaser.GameObjects.Image }> = []
  private riverShimmers: Array<{
    object: Phaser.GameObjects.Ellipse
    velocityX: number
    phase: number
    lane: number
  }> = []
  private opaqueBottomPadding = new Map<string, number>()
  private objectBlockers: Phaser.Geom.Rectangle[] = []

  // initData가 있으면(실제 게임플레이) 체력 소모/이동 보고/NPC·장소 클릭 콜백이 활성화된다.
  // 없으면(타이틀 화면의 로그인 없는 마을 미리보기) 지금처럼 자유 이동만 가능하다.
  private initData?: MainSceneInitData
  private stamina = 0
  private inputLocked = false
  private pendingMovedSeconds = 0
  private playerTexturePrefix: 'player' | 'player-girl' = 'player'
  private npcInteractRange = 0
  private locationInteractRange = 0
  // player-male 걷기 애니메이션이 있는 캐릭터(현재는 남성 전용)인지 — 없으면 예전처럼
  // 방향별 정지 이미지를 그대로 교체하는 식으로 표현한다.
  private hasWalkCycle = false
  private lastDirection: (typeof WALK_DIRECTIONS)[number] = 'south'

  constructor() {
    super('MainScene')
  }

  init(data?: MainSceneInitData) {
    // Phaser는 scene.start(key)에 데이터 없이 호출해도(=미리보기 모드) init()에 빈 객체를
    // 넘길 수 있어서, 존재 여부가 아니라 실제 콜백 필드로 "진짜 게임플레이 데이터인지"를 판단한다.
    this.initData = data && typeof data.onNpcClick === 'function' ? data : undefined
    if (this.initData) {
      this.stamina = this.initData.staminaCurrent
    }
    this.pendingMovedSeconds = 0
  }

  preload() {
    this.load.image('terrainChunk', '/assets/world/maps/korean-countryside-chunk-01.png?v=no-road-expanded-field-v18')
    this.load.json('terrainMapData', '/assets/world/maps/korean-countryside-chunk-01.json?v=no-road-expanded-field-v18')
    WALK_DIRECTIONS.forEach((direction) => {
      this.load.image(`player-girl-${direction}`, `/assets/characters/player-girl-v2/Idle/rotations/${direction}.png`)
      // 남성 캐릭터 전용 4프레임 걷기 애니메이션(여성용은 아직 원본 에셋이 없어 정지 이미지만 씀).
      // 정지 포즈도 이 스프라이트시트의 0번 프레임을 그대로 쓴다(따로 정지 이미지 에셋을 안 씀) —
      // 예전엔 정지 상태만 다른 화풍의 player-boy-v2 이미지를 써서 걷기 시작/정지할 때마다
      // 캐릭터 생김새가 바뀌어 보이는 문제가 있었다.
      this.load.spritesheet(
        `player-walk-${direction}`,
        `/assets/characters/player-male/walk_cycle_all_directions/${direction}/${direction}_walk_sheet.png`,
        { frameWidth: PLAYER_WALK_FRAME_SIZE, frameHeight: PLAYER_WALK_FRAME_SIZE },
      )
    })
    this.load.image('ruralBridge', '/assets/world/bridge-rural-small-v2.png')
    this.load.image('chickenCoopNormal', '/assets/world/facilities/chicken-coop-normal.png')
    this.load.image('chickenCoopBroken', '/assets/world/facilities/chicken-coop-broken.png')
    this.load.image('chickenFront', '/assets/world/objects/chicken-front.png')
    this.load.image('chickenLeft', '/assets/world/objects/chicken-left.png')
    this.load.image('chickenRight', '/assets/world/objects/chicken-right.png')
    this.load.image('onggiJars', '/assets/world/objects/onggi-jars.png')
    this.load.image('woodFence', '/assets/world/objects/wood-fence-intact.png')
    this.load.image('scarecrow', '/assets/world/objects/scarecrow.png')
    this.load.image('villageHall', '/assets/world/buildings/public/village-hall-complete-v2.png')
    this.load.image('villagePavilion', '/assets/world/facilities/village-pavilion-v1.png')
    this.load.image('ordinaryTree', '/assets/world/objects/ordinary-tree.png')
    this.load.image('hyeonSudong', '/assets/characters/npcs/npc-01/Idle/rotations/south.png')
    this.load.image('stoneWell', '/assets/world/objects/stone-well.png')
    this.load.image('woodenPyeongsang', '/assets/world/objects/wooden-pyeongsang.png')
    this.load.image('zelkovaTree', '/assets/world/objects/zelkova-tree-v1.png')
    this.load.image('wildDeciduousTree', '/assets/world/objects/wild-deciduous-tree-v1.png')
    this.load.image('wildShrubCluster', '/assets/world/objects/wild-shrub-cluster-v1.png')
    this.load.image('wildGrassCluster', '/assets/world/objects/wild-grass-cluster-v1.png')
    this.load.image('villageBicycle', '/assets/world/objects/village-bicycle-v1.png')
    this.load.image('villageNoticeBoard', '/assets/world/objects/village-notice-board-v1.png')
    this.load.image('stoneFlowerBed', '/assets/world/objects/stone-flower-bed-v1.png')
    this.load.image('clothesline', '/assets/world/objects/clothesline-v1.png')
    this.load.image('largeGraniteBoulder', '/assets/world/objects/large-granite-boulder-v1.png')
    this.load.image('villageCultivator', '/assets/world/objects/generated-v1/cultivator-v1.png')
    this.load.image('newBroadcastSpeaker', '/assets/world/objects/generated-v1/broadcast-speaker-v1.png')
    this.load.image('lowStoneWall', '/assets/world/objects/generated-v1/low-stone-wall-v1.png')
    this.load.image('stonePile', '/assets/world/objects/generated-v1/stone-pile-v1.png')
    ;[1, 2, 3].forEach((variant) =>
      this.load.image(`ruralMailbox${variant}`, `/assets/world/objects/generated-v1/mailbox-${variant}-v1.png`),
    )
    ;[1, 2, 3, 4].forEach((variant) =>
      this.load.image(`newWildflower${variant}`, `/assets/world/objects/generated-v1/wildflower-${variant}-v1.png`),
    )
    this.load.image('broadcastSpeakerPole', '/assets/world/objects/broadcast-speaker-pole-v1.png')
    ;['south', 'south-east', 'east', 'north-east', 'north', 'north-west', 'west', 'south-west'].forEach((direction) =>
      this.load.image(`cat-01-${direction}`, `/assets/animals/cat-01/Idle/rotations/${direction}.png`),
    )
    ;['tuxedo-cat', 'white-dog'].forEach((animal) => {
      ;['south', 'south-east', 'east', 'north-east', 'north', 'north-west', 'west', 'south-west'].forEach((direction) =>
        this.load.image(`${animal}-${direction}`, `/assets/animals/${animal}/Idle/rotations/${direction}.png`),
      )
    })
    this.load.image('najubuHouse', '/assets/world/buildings/houses/house-2.png')
    this.load.image('produceShop', '/assets/world/buildings/public/produce-shop.png')
    this.load.image('najubu', '/assets/characters/npcs/npc-02/Idle/rotations/south.png')
    this.load.image('gardenAppleTree', '/assets/world/growth/fruit/apple-tree-stage-3.png')
    ;[1, 2, 3].forEach((stage) => {
      this.load.image(`farmCarrot${stage}`, `/assets/world/growth/crops/carrot-stage-${stage}.png`)
      this.load.image(`farmStrawberry${stage}`, `/assets/world/growth/crops/strawberry-stage-${stage}.png`)
      this.load.image(`farmCherry${stage}`, `/assets/world/growth/fruit/cherry-tree-stage-${stage}.png`)
    })
    this.load.spritesheet('itemShop', '/assets/world/buildings/public/item-shop.png', {
      frameWidth: 600,
      frameHeight: 517,
      endFrame: 0,
    })
    this.load.image('storageBuilding', '/assets/world/buildings/houses/house-7.png')
    this.load.image('myeongHouse', '/assets/world/buildings/houses/house-5.png')
    this.load.image('kimHouse', '/assets/world/buildings/houses/house-6.png')
    this.load.image('baksuHouse', '/assets/world/buildings/houses/house-3.png')
    this.load.image('watermelonFieldNormal', '/assets/world/facilities/watermelon-field-normal.png')
    this.load.image('watermelonFieldDamaged', '/assets/world/facilities/watermelon-field-damaged.png')
    this.load.image('jeonJuin', '/assets/characters/npcs/npc-03/Idle/rotations/south.png')
    this.load.image('parkYounggye', '/assets/characters/npcs/npc-04/Idle/rotations/south.png')
    this.load.image('myeongJayu', '/assets/characters/npcs/npc-05/Idle/rotations/south.png')
    this.load.image('kimChijun', '/assets/characters/npcs/npc-06/Idle/rotations/south.png')
    this.load.image('naBaksu', '/assets/characters/npcs/npc-07/Idle/rotations/south.png')
    ;[1, 2, 3].forEach((frame) =>
      this.load.image(`sparrowFlockFrame${frame}`, `/assets/world/objects/generated-v1/sparrow-flock-frame-${frame}.png?v=wave-v2`),
    )
    this.load.audio('morningFieldsBgm', '/assets/audio/bgm-samples/01-morning-fields.wav?v=3min-loop-v2')
  }

  create() {
    this.mapData = this.cache.json.get('terrainMapData') as TerrainMapData
    this.blockedTiles = new Set(
      this.mapData.collision.blockedTiles.map(({ x, y }) => `${x},${y}`),
    )
    this.npcInteractRange = this.mapData.tileWidth * 2
    this.locationInteractRange = this.mapData.tileWidth * 2.5
    this.playerTexturePrefix = getPlayerProfile()?.gender === 'female' ? 'player-girl' : 'player'
    this.hasWalkCycle = this.playerTexturePrefix === 'player'
    this.registerPlayerWalkAnimations()

    this.add.image(0, 0, 'terrainChunk').setOrigin(0, 0)
    this.createBridge()

    const spawnX = (this.mapData.spawn.x + 0.5) * this.mapData.tileWidth
    const spawnY = (this.mapData.spawn.y + 0.5) * this.mapData.tileHeight
    this.createFarmAreaObjects()
    this.createFarmCropRows()
    this.createZoneOne()
    this.createZoneTwo()
    this.createEnvironmentalDensity()
    this.createRemainingZones()
    this.createVillageAnimals()
    this.setClueSpots(this.initData?.clueSpots ?? [])

    // Keep a guaranteed walkable pocket around the initial spawn. Decorative
    // collision rectangles must never trap the player before the first input.
    const spawnSafetyArea = new Phaser.Geom.Rectangle(spawnX - 42, spawnY - 42, 84, 84)
    this.objectBlockers = this.objectBlockers.filter(
      (blocker) => !Phaser.Geom.Intersects.RectangleToRectangle(blocker, spawnSafetyArea),
    )

    const [initialTexture, initialFrame] = this.hasWalkCycle
      ? [`player-walk-south`, 0]
      : [`${this.playerTexturePrefix}-south`, undefined]
    this.player = this.add
      .sprite(spawnX, spawnY, initialTexture, initialFrame)
      .setDepth(spawnY)
      .setDisplaySize(PLAYER_IDLE_DISPLAY_SIZE, PLAYER_IDLE_DISPLAY_SIZE)
    this.createWorldLighting()

    this.cursors = this.input.keyboard!.createCursorKeys()
    this.movementKeys = this.input.keyboard!.addKeys({
      up: Phaser.Input.Keyboard.KeyCodes.W,
      down: Phaser.Input.Keyboard.KeyCodes.S,
      left: Phaser.Input.Keyboard.KeyCodes.A,
      right: Phaser.Input.Keyboard.KeyCodes.D,
    }) as Record<'up' | 'down' | 'left' | 'right', Phaser.Input.Keyboard.Key>

    const camera = this.cameras.main
    camera.setBounds(0, 0, this.mapData.pixelWidth, this.mapData.pixelHeight)
    camera.setZoom(1.35)
    camera.centerOn(this.player.x, this.player.y)
    camera.startFollow(this.player, true, 1, 1)
    this.startMorningFieldsBgm()
    this.startSparrowFlocks()
    this.startRiverSparkles()

    // 씬을 벗어날 때(밤으로 전환 등) 아직 서버에 보고 안 된 이동 시간이 남아있으면 마저 보고한다.
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => this.flushPendingMove())
  }

  /**
   * 대화창/ESC 메뉴가 열려 있는 동안 호출된다. Phaser는 기본적으로 WASD/방향키를
   * 캔버스에서 전역으로 캡처(preventDefault)해서, 잠그지 않으면 채팅 입력창에 같은
   * 글자를 입력할 수 없고(예: "a") 배경에서 캐릭터도 계속 움직인다.
   */
  setInputLocked(locked: boolean) {
    this.inputLocked = locked
    if (locked) {
      this.input.keyboard?.disableGlobalCapture()
    } else {
      this.input.keyboard?.enableGlobalCapture()
    }
  }

  /** 대화/아이템 사용/농사 등 이 씬 바깥에서 체력이 바뀌었을 때 내부 값도 맞춰준다. */
  syncStamina(value: number) {
    this.stamina = value
  }

  update(_time: number, delta: number) {
    this.updateWorldShadows()
    this.updateRiverShimmers(delta)
    if (this.inputLocked) return
    if (this.initData && this.stamina <= 0) {
      this.stopWalking()
      return
    }

    const left = this.cursors.left.isDown || this.movementKeys.left.isDown
    const right = this.cursors.right.isDown || this.movementKeys.right.isDown
    const up = this.cursors.up.isDown || this.movementKeys.up.isDown
    const down = this.cursors.down.isDown || this.movementKeys.down.isDown

    let dx = Number(right) - Number(left)
    let dy = Number(down) - Number(up)
    if (dx === 0 && dy === 0) {
      this.stopWalking()
      return
    }

    const length = Math.hypot(dx, dy)
    dx /= length
    dy /= length
    this.setPlayerDirection(dx, dy)
    const distance = PLAYER_SPEED * (delta / 1000)

    this.tryMove(this.player.x + dx * distance, this.player.y)
    this.tryMove(this.player.x, this.player.y + dy * distance)
    this.player.setDepth(this.player.y)

    // 정지 시 소모 없음 — 실제로 움직인 프레임에서만 경과 시간(초)만큼 체력을 깎는다.
    if (this.initData) {
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
  }

  private flushPendingMove() {
    if (!this.initData) return
    if (this.pendingMovedSeconds <= 0) return
    this.initData.onMove(this.pendingMovedSeconds)
    this.pendingMovedSeconds = 0
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
      .setDepth(1_000_001)

    this.tweens.add({
      targets: hint,
      alpha: 0,
      delay: 700,
      duration: 400,
      onComplete: () => hint.destroy(),
    })
  }

  /** 게임플레이 모드에서만 동작 — 거리 안이면 대화 콜백, 멀면 안내 힌트. */
  private handleNpcInteract(name: string, x: number, y: number) {
    if (!this.initData) return
    const distance = Phaser.Math.Distance.Between(this.player.x, this.player.y, x, y)
    if (distance <= this.npcInteractRange) {
      const role = NPC_ROSTER.find((entry) => entry.name === name)?.role ?? ''
      this.initData.onNpcClick(name, role)
    } else {
      this.showFloatingHint(x, y - this.mapData.tileHeight, '너무 멀어요! 가까이 가주세요')
    }
  }

  /** 게임플레이 모드에서만 동작 — 거리 안이면 장소 클릭 콜백(단서 습득/고발), 멀면 안내 힌트. */
  private handleLocationInteract(spotKey: string, x: number, y: number) {
    if (!this.initData) return
    const distance = Phaser.Math.Distance.Between(this.player.x, this.player.y, x, y)
    if (distance <= this.locationInteractRange) {
      this.initData.onLocationClick(spotKey)
    } else {
      this.showFloatingHint(x, y - this.mapData.tileHeight, '너무 멀어요! 가까이 가주세요')
    }
  }

  /** 8방향 걷기 애니메이션(player-walk-*)을 씬 시작 시 한 번만 등록한다. */
  private registerPlayerWalkAnimations() {
    WALK_DIRECTIONS.forEach((direction) => {
      const key = `walk-${direction}`
      if (this.anims.exists(key)) return
      this.anims.create({
        key,
        frames: this.anims.generateFrameNumbers(`player-walk-${direction}`, { start: 0, end: 3 }),
        frameRate: 10,
        repeat: -1,
      })
    })
  }

  private setPlayerDirection(dx: number, dy: number) {
    const horizontal = dx < -0.35 ? 'west' : dx > 0.35 ? 'east' : ''
    const vertical = dy < -0.35 ? 'north' : dy > 0.35 ? 'south' : ''
    const direction = vertical && horizontal ? `${vertical}-${horizontal}` : vertical || horizontal
    if (!direction) return
    this.lastDirection = direction as (typeof WALK_DIRECTIONS)[number]

    if (this.hasWalkCycle) {
      const animKey = `walk-${direction}`
      // isPlaying도 같이 봐야 한다 — stopWalking()이 애니메이션을 멈추고 정지 이미지로 텍스처만
      // 바꿔도 currentAnim.key 자체는 마지막 애니메이션 키로 남아있다. key만 비교하면, 걷다가
      // 멈췄다가 같은 방향으로 다시 걸을 때 "키가 그대로니 이미 재생 중"이라고 착각해서
      // play()를 다시 안 불러 정지 이미지에서 멈춰버리는 문제가 있었다.
      if (!this.player.anims.isPlaying || this.player.anims.currentAnim?.key !== animKey) {
        this.player.play(animKey)
        this.player.setDisplaySize(PLAYER_IDLE_DISPLAY_SIZE, PLAYER_IDLE_DISPLAY_SIZE)
      }
    } else {
      this.player.setTexture(`${this.playerTexturePrefix}-${direction}`)
    }
  }

  /**
   * 이동을 멈추면 걷기 애니메이션을 정지하고, 마지막으로 보던 방향의 정지 포즈로 되돌린다.
   * 정지 포즈도 같은 걷기 스프라이트시트의 0번 프레임을 그대로 쓴다 — 걷는 중/멈췄을 때
   * 캐릭터 생김새가 서로 다른 에셋이라 달라 보이지 않게 하기 위함.
   */
  private stopWalking() {
    if (!this.hasWalkCycle || !this.player.anims.isPlaying) return
    this.player.anims.stop()
    this.player.setTexture(`player-walk-${this.lastDirection}`, 0)
    this.player.setDisplaySize(PLAYER_IDLE_DISPLAY_SIZE, PLAYER_IDLE_DISPLAY_SIZE)
  }

  private createFarmAreaObjects() {
    const tile = this.mapData.tileWidth

    // Keep the entire farm boundary to the right of the produce shop footprint.
    const fencePositions = [90, 102, 114]
    fencePositions.forEach((tileX) => {
      const x = (tileX + 0.5) * tile
      const y = 18.5 * tile
      this.add.image(x, y, 'woodFence').setOrigin(0.5, 1).setScale(0.22).setDepth(y)
      this.objectBlockers.push(new Phaser.Geom.Rectangle(x - 66, y - 18, 132, 20))
    })

    const coopX = 115 * tile
    const coopY = 25 * tile
    this.chickenCoop = this.add
      .image(coopX, coopY, 'chickenCoopNormal')
      .setOrigin(0.5, 1)
      .setScale(0.26)
      .setDepth(coopY)
      .setInteractive({ useHandCursor: true })
    this.chickenCoop.on('pointerover', () => this.chickenCoop.setTint(0xfff1b5))
    this.chickenCoop.on('pointerout', () => this.chickenCoop.clearTint())
    this.chickenCoop.on('pointerdown', () => this.handleLocationInteract('chicken-coop', coopX, coopY))
    this.objectBlockers.push(new Phaser.Geom.Rectangle(coopX - 90, coopY - 72, 180, 72))
    this.registerClueMarker('chicken-coop', coopX, coopY)

    const chickenSpecs: Array<[number, number, string, number, number]> = [
      [110.5, 26, 'chickenFront', 0, 0],
      [114, 29, 'chickenLeft', 10, 3],
      [118, 26.5, 'chickenRight', 20, -2],
    ]
    this.chickens = chickenSpecs.map(([tileX, tileY, texture, offsetX, offsetY]) => {
      const x = tileX * tile
      const y = tileY * tile
      const chicken = this.add.image(x, y, texture).setOrigin(0.5, 1).setScale(0.14).setDepth(y)
      this.startRandomWander(chicken, new Phaser.Geom.Rectangle(x - 24 + offsetX, y - 15 + offsetY, 48, 30), ['chickenFront', 'chickenLeft', 'chickenRight'], 8, undefined, true)
      return chicken
    })

    const scarecrowX = 96 * tile
    const scarecrowY = 36 * tile
    this.add
      .image(scarecrowX, scarecrowY, 'scarecrow')
      .setOrigin(0.5, 1)
      .setScale(0.18)
      .setDepth(scarecrowY)

  }

  private createFarmCropRows() {
    const tile = this.mapData.tileWidth
    const rows: Array<{ y: number; prefix: string; scale: number }> = [
      { y: 27, prefix: 'farmCarrot', scale: 0.058 },
      { y: 32, prefix: 'farmCarrot', scale: 0.058 },
      { y: 37, prefix: 'farmStrawberry', scale: 0.06 },
      { y: 43, prefix: 'farmCherry', scale: 0.075 },
    ]
    rows.forEach(({ y, prefix, scale }, rowIndex) => {
      for (let column = 0; column < 7; column += 1) {
        const x = 78 + column * 5
        const stage = ((column + rowIndex) % 3) + 1
        this.add.image(x * tile, y * tile, `${prefix}${stage}`).setOrigin(0.5, 1).setScale(scale).setDepth(y * tile)
      }
    })
  }

  private createZoneOne() {
    const tile = this.mapData.tileWidth
    const at = (tileX: number, tileY: number) => ({ x: tileX * tile, y: tileY * tile })

    // 1구역(좌상단): 회관을 중심으로 어귀와 정자가 연결되는 작은 생활권입니다.
    const hall = at(18, 18)
    const hallImage = this.add.image(hall.x, hall.y, 'villageHall').setOrigin(0.5, 1).setScale(0.22).setDepth(hall.y)
    this.objectBlockers.push(new Phaser.Geom.Rectangle(hall.x - 164, hall.y - 82, 328, 78))
    hallImage.setInteractive({ useHandCursor: true })
    hallImage.on('pointerover', () => hallImage.setTint(0xfff1b5))
    hallImage.on('pointerout', () => hallImage.clearTint())
    hallImage.on('pointerdown', () => this.handleLocationInteract('village-hall', hall.x, hall.y))
    this.registerClueMarker('village-hall', hall.x, hall.y)

    const pavilion = at(40, 40)
    this.add
      .image(pavilion.x, pavilion.y, 'villagePavilion')
      .setOrigin(0.5, 1)
      .setScale(0.2)
      .setDepth(pavilion.y)
    this.objectBlockers.push(new Phaser.Geom.Rectangle(pavilion.x - 92, pavilion.y - 48, 184, 45))

    const treePositions: Array<[number, number, number]> = [
      [5, 11.5, 0.105], [27, 10.5, 0.1], [39, 9.5, 0.11], [10, 29, 0.1],
    ]
    treePositions.forEach(([tileX, tileY, scale]) => {
      const tree = at(tileX, tileY)
      this.add.image(tree.x, tree.y, 'ordinaryTree').setOrigin(0.5, 1).setScale(scale).setDepth(tree.y)
      this.objectBlockers.push(new Phaser.Geom.Rectangle(tree.x - 20, tree.y - 28, 40, 27))
    })

    // Until the in-game clock is connected, keep NPCs at their highest-probability
    // location so a browser refresh never teleports or hides them.
    const npcLocation = at(18, 20.5)
    const hyeon = this.add
      .image(npcLocation.x, npcLocation.y, 'hyeonSudong')
      .setOrigin(0.5, 1)
      .setScale(1.25)
      .setDepth(npcLocation.y)
      .setInteractive({ useHandCursor: true })
      .setData('daySchedule', NPC_DAY_SCHEDULES.hyeonSudong)

    this.add
      .text(npcLocation.x, npcLocation.y - 62, '현수동', {
        fontFamily: 'sans-serif', fontSize: '14px', color: '#fff5d6',
        backgroundColor: '#30271fdd', padding: { x: 7, y: 3 },
      })
      .setOrigin(0.5, 1)
      .setDepth(npcLocation.y + 1)
    hyeon.on('pointerover', () => hyeon.setTint(0xfff1b5))
    hyeon.on('pointerout', () => hyeon.clearTint())
    hyeon.on('pointerdown', () => this.handleNpcInteract('현수동', npcLocation.x, npcLocation.y))

  }

  private createEnvironmentalDensity() {
    const tile = this.mapData.tileWidth
    const place = (
      texture: string,
      tileX: number,
      tileY: number,
      scale: number,
      blocker?: { width: number; height: number },
      flipX = false,
    ) => {
      const x = tileX * tile
      const y = tileY * tile
      const object = this.add
        .image(x, y, texture)
        .setOrigin(0.5, 1)
        .setScale(scale * WORLD_OBJECT_SCALE)
        .setFlipX(flipX)
        .setDepth(y)
      if (blocker) {
        this.objectBlockers.push(
          new Phaser.Geom.Rectangle(
            x - (blocker.width * WORLD_OBJECT_SCALE) / 2,
            y - blocker.height * WORLD_OBJECT_SCALE,
            blocker.width * WORLD_OBJECT_SCALE,
            blocker.height * WORLD_OBJECT_SCALE,
          ),
        )
      }
      return object
    }

    // Village hall: a used civic yard, with an old well, resting platform,
    // irregular fence fragments, jars, shade trees and wild planting clusters.
    ;[
      [6.8, 22.8, false], [29.5, 22.6, false],
    ].forEach(([x, y, flipped]) => place('woodFence', x as number, y as number, 0.14, { width: 82, height: 16 }, flipped as boolean))
    place('wildDeciduousTree', 3.4, 15.5, 0.13, { width: 25, height: 22 }, true)

    // Signature 1990s village props. The center line from the road to the hall
    // doors stays open while the objects form two uneven civic-yard clusters.
    place('villageNoticeBoard', 28, 21.5, 0.075, { width: 62, height: 20 })
    place('stoneFlowerBed', 14.2, 26.2, 0.065, { width: 72, height: 20 })
    place('newWildflower1', 9.5, 30.2, 0.028)
    place('broadcastSpeakerPole', 4.7, 14, 0.14, { width: 24, height: 20 })

    ;[
      [5.8, 24.6], [34.2, 22.3],
    ].forEach(([x, y], index) => place('wildShrubCluster', x, y, 0.052 + (index % 2) * 0.006, undefined, index % 2 === 0))
    ;[
      [13.2, 24.8], [37, 22.8],
    ].forEach(([x, y], index) => place('wildGrassCluster', x, y, 0.038 + (index % 3) * 0.004, undefined, index % 2 === 0))

    // Pavilion: fills the open civic space below Hyeon Sudong and Najubu,
    // while preserving a clear grass buffer before the northern riverbank.
    place('wildDeciduousTree', 35.5, 36.2, 0.135, { width: 25, height: 22 })
    place('ordinaryTree', 45.4, 36.8, 0.095, { width: 35, height: 24 })
    // Purpose-built wildflowers form loose, asymmetric edge clusters while the
    // pavilion entrance and central walking lane stay open.
    place('newWildflower1', 33.7, 38.5, 0.045)
    place('newWildflower3', 34.8, 39.2, 0.026, undefined, true)
    place('newWildflower4', 47.1, 38.1, 0.042, undefined, true)
    place('newWildflower2', 48.2, 38.8, 0.025)

    // One substantial farming object anchors the initial view. Stone features
    // bridge the visual transition toward the field without reading as clutter.
    place('villageCultivator', 73, 44.2, 0.115, { width: 92, height: 45 }, true)

    // Chicken farm: working clutter and shelter at the plot edges, leaving the
    // middle clear for NPC/chicken movement and the sabotage interaction.
    place('stoneWell', 120, 37, 0.24, { width: 88, height: 60 })
    ;[[78.5, 47.5], [98, 48], [117, 47.5]].forEach(([x, y], index) =>
      place('woodFence', x, y, 0.13, { width: 76, height: 15 }, index % 2 === 0),
    )

    // Reeds and foxtail-like grass stay on the banks, never in the water channel.
    ;[[5, 53], [18, 52], [34, 53], [50, 52], [91, 52], [111, 53]].forEach(([x, y], index) =>
      place(index % 3 === 0 ? 'wildShrubCluster' : 'wildGrassCluster', x, y, index % 3 === 0 ? 0.04 : 0.046, undefined, index % 2 === 0),
    )
    ;[[8, 80], [24, 81], [42, 79], [70, 80], [96, 79], [121, 80]].forEach(([x, y], index) =>
      place('wildGrassCluster', x, y, 0.043 + (index % 2) * 0.004, undefined, index % 2 === 1),
    )
  }

  private createZoneTwo() {
    const tile = this.mapData.tileWidth
    const at = (tileX: number, tileY: number) => ({ x: tileX * tile, y: tileY * tile })
    const add = (
      texture: string,
      tileX: number,
      tileY: number,
      scale: number,
      blocker?: { width: number; height: number },
      flipX = false,
    ) => {
      const { x, y } = at(tileX, tileY)
      const object = this.add.image(x, y, texture).setOrigin(0.5, 1).setScale(scale * WORLD_OBJECT_SCALE).setFlipX(flipX).setDepth(y)
      if (blocker) {
        this.objectBlockers.push(
          new Phaser.Geom.Rectangle(
            x - (blocker.width * WORLD_OBJECT_SCALE) / 2,
            y - blocker.height * WORLD_OBJECT_SCALE,
            blocker.width * WORLD_OBJECT_SCALE,
            blocker.height * WORLD_OBJECT_SCALE,
          ),
        )
      }
      return object
    }

    // Zone 2: Najubu's home garden occupies the quieter western half while the
    // produce shop faces the road to the east. Both retain clear front paths.
    const home = at(48, 18)
    const homeImage = this.add.image(home.x, home.y, 'najubuHouse').setOrigin(0.5, 1).setScale(0.4).setDepth(home.y)
    this.objectBlockers.push(new Phaser.Geom.Rectangle(home.x - 100, home.y - 64, 200, 62))
    homeImage.setInteractive({ useHandCursor: true })
    homeImage.on('pointerover', () => homeImage.setTint(0xfff1b5))
    homeImage.on('pointerout', () => homeImage.clearTint())
    homeImage.on('pointerdown', () => this.handleLocationInteract('house1', home.x, home.y))
    this.registerClueMarker('house1', home.x, home.y)

    const shop = at(76, 19)
    const shopImage = this.add.image(shop.x, shop.y, 'produceShop').setOrigin(0.5, 1).setScale(0.38).setDepth(shop.y)
    this.objectBlockers.push(new Phaser.Geom.Rectangle(shop.x - 104, shop.y - 64, 208, 62))
    shopImage.setInteractive({ useHandCursor: true })
    shopImage.on('pointerover', () => shopImage.setTint(0xfff1b5))
    shopImage.on('pointerout', () => shopImage.clearTint())
    shopImage.on('pointerdown', () => this.handleLocationInteract('produce-shop', shop.x, shop.y))
    this.registerClueMarker('produce-shop', shop.x, shop.y)
    add('ruralMailbox1', 54.8, 19.2, 0.068, { width: 20, height: 14 })

    // Small cared-for vegetable plot left of the house.

    // Utility and resting cluster to the right; the front-door axis at x=55 is empty.
    add('onggiJars', 60.5, 16.7, 0.09, { width: 36, height: 23 })
    add('villageBicycle', 72, 19.7, 0.055, { width: 47, height: 17 })
    add('wildGrassCluster', 66, 18.6, 0.037, undefined, true)

    // Uneven garden boundary vegetation avoids an artificial row.
    add('ordinaryTree', 69, 12, 0.087, { width: 34, height: 23 })
    ;[[62, 20.1], [68, 17.8]].forEach(([x, y], index) =>
      add(index % 2 === 0 ? 'wildShrubCluster' : 'wildGrassCluster', x, y, index % 2 === 0 ? 0.047 : 0.036, undefined, index % 3 === 0),
    )
    add('newWildflower2', 39.5, 20.8, 0.026)

    const npcLocation = at(48, 20.2)
    const najubu = this.add
      .image(npcLocation.x, npcLocation.y, 'najubu')
      .setOrigin(0.5, 1)
      .setScale(1.25)
      .setDepth(npcLocation.y)
      .setInteractive({ useHandCursor: true })
      .setData('daySchedule', NPC_DAY_SCHEDULES.najubu)
    const nameTag = this.add
      .text(npcLocation.x, npcLocation.y - 62, '나주부', {
        fontFamily: 'sans-serif', fontSize: '14px', color: '#fff5d6',
        backgroundColor: '#30271fdd', padding: { x: 7, y: 3 },
      })
      .setOrigin(0.5, 1)
      .setDepth(npcLocation.y + 1)
    najubu.on('pointerover', () => najubu.setTint(0xfff1b5))
    najubu.on('pointerout', () => najubu.clearTint())
    najubu.on('pointerdown', () => this.handleNpcInteract('나주부', npcLocation.x, npcLocation.y))
    nameTag.setVisible(true)
  }

  private createRemainingZones() {
    const tile = this.mapData.tileWidth
    const at = (tileX: number, tileY: number) => ({ x: tileX * tile, y: tileY * tile })
    const building = (
      texture: string,
      tileX: number,
      tileY: number,
      scale: number,
      blockerWidth: number,
      blockerHeight: number,
      flipX = false,
      spotKey?: string,
    ) => {
      const { x, y } = at(tileX, tileY)
      const image = this.add.image(x, y, texture).setOrigin(0.5, 1).setScale(scale).setFlipX(flipX).setDepth(y)
      this.objectBlockers.push(
        new Phaser.Geom.Rectangle(x - blockerWidth / 2, y - blockerHeight, blockerWidth, blockerHeight),
      )
      if (spotKey) {
        image.setInteractive({ useHandCursor: true })
        image.on('pointerover', () => image.setTint(0xfff1b5))
        image.on('pointerout', () => image.clearTint())
        image.on('pointerdown', () => this.handleLocationInteract(spotKey, x, y))
        this.registerClueMarker(spotKey, x, y)
      }
      return image
    }
    const prop = (texture: string, tileX: number, tileY: number, scale: number, flipX = false) => {
      const { x, y } = at(tileX, tileY)
      return this.add.image(x, y, texture).setOrigin(0.5, 1).setScale(scale).setFlipX(flipX).setDepth(y)
    }
    const npc = (
      texture: string,
      name: string,
      tileX: number,
      tileY: number,
      schedule: unknown,
    ) => {
      if (texture === 'jeonJuin') {
        tileX = 14
        tileY = 57
      } else if (texture === 'kimChijun') {
        tileX = 76
        tileY = 22.5
      }
      const { x, y } = at(tileX, tileY)
      const character = this.add
        .image(x, y, texture)
        .setOrigin(0.5, 1)
        .setScale(1.25)
        .setDepth(y)
        .setInteractive({ useHandCursor: true })
        .setData('daySchedule', schedule)
      this.add
        .text(x, y - 62, name, {
          fontFamily: 'sans-serif', fontSize: '14px', color: '#fff5d6',
          backgroundColor: '#30271fdd', padding: { x: 7, y: 3 },
        })
        .setOrigin(0.5, 1)
        .setDepth(y + 1)
      character.on('pointerover', () => character.setTint(0xfff1b5))
      character.on('pointerout', () => character.clearTint())
      character.on('pointerdown', () => this.handleNpcInteract(name, x, y))
    }

    // Zone 3: the commercial corner. The item shop faces the northern road;
    // its small storage building sits farther east, leaving a service yard between.
    building('itemShop', 14, 53, 0.34, 180, 55, false, 'item-shop')
    prop('villageBicycle', 3.5, 55.5, 0.055, true)
    prop('newWildflower1', 24.2, 56.2, 0.027)
    prop('newWildflower4', 22.9, 57, 0.019, true)
    npc('jeonJuin', '전주인', 8, 57, NPC_DAY_SCHEDULES.jeonJuin)
    npc('kimChijun', '김치준', 14, 57, NPC_DAY_SCHEDULES.kimChijun)

    // Zone 6: chicken-farm owner remains beside the interactive coop and chickens.
    // The lane toward the river remains open for the sabotage investigation route.
    npc('parkYounggye', '박영계', 101, 38.6, NPC_DAY_SCHEDULES.parkYounggye)

    // Zone 7: Myeong Jayu's quiet home and small, maintained side garden.
    building('myeongHouse', 27, 84, 0.34, 160, 50, false, 'house2')
    prop('woodFence', 17.5, 84.3, 0.12)
    prop('stoneFlowerBed', 14.8, 87.5, 0.052)
    prop('onggiJars', 34.5, 82.8, 0.08)
    prop('clothesline', 40, 88, 0.105)
    prop('ruralMailbox2', 32.8, 84.2, 0.062)
    prop('wildShrubCluster', 18.8, 86.2, 0.045)
    prop('ordinaryTree', 6.5, 92, 0.09, true)
    prop('wildGrassCluster', 12, 93, 0.042)
    prop('newWildflower3', 11.5, 89.2, 0.026)
    npc('myeongJayu', '명자유', 27, 87, NPC_DAY_SCHEDULES.myeongJayu)

    // Zone 8: Kim's modest residence is deliberately quieter than his work area.
    // He is currently rendered at the item shop according to his 70% daytime route.
    building('kimHouse', 77, 82, 0.29, 145, 44, true)
    prop('ruralMailbox3', 82.2, 82.3, 0.064)
    prop('villageBicycle', 68.5, 82.6, 0.05)
    prop('wildDeciduousTree', 57, 94, 0.11)
    prop('wildGrassCluster', 64, 94, 0.04, true)
    prop('newWildflower4', 64, 85, 0.025)

    // Zone 9: a dedicated watermelon plot with the farmer's home on its east edge.
    this.watermelonField = building('watermelonFieldNormal', 104, 82, 0.28, 215, 65, false, 'watermelon-field')
    prop('scarecrow', 99, 79.5, 0.15)
    prop('woodFence', 94, 84.5, 0.13)
    prop('woodFence', 113.5, 84.4, 0.13, true)
    prop('wildGrassCluster', 116, 88.3, 0.036)
    npc('naBaksu', '나박수', 104, 85.2, NPC_DAY_SCHEDULES.naBaksu)
  }

  private createVillageAnimals() {
    const tile = this.mapData.tileWidth
    const animals = [
      {
        prefix: 'cat-01', id: 'f947bb98-6dc4-44eb-a116-077d7d52120b',
        x: 46, y: 46, scale: 1.35,
        bounds: new Phaser.Geom.Rectangle(42 * tile, 43 * tile, 10 * tile, 7 * tile),
      },
      {
        prefix: 'tuxedo-cat', id: '65e29cec-105c-4730-8c51-910a84faeaeb',
        x: 36, y: 91, scale: 0.675,
        bounds: new Phaser.Geom.Rectangle(31 * tile, 88 * tile, 15 * tile, 7 * tile),
      },
      {
        prefix: 'white-dog', id: 'd0334d96-89ff-4ee3-b830-ff742294508c',
        x: 23, y: 29, scale: 1.35,
        bounds: new Phaser.Geom.Rectangle(17 * tile, 25 * tile, 13 * tile, 8 * tile),
      },
    ]

    animals.forEach(({ prefix, id, x, y, scale, bounds }) => {
      const animal = this.add
        .image(x * tile, y * tile, `${prefix}-south`)
        .setOrigin(0.5, 1)
        .setScale(scale)
        .setDepth(y * tile)
        .setData('animalId', id)
      this.villageAnimals.push(animal)
      this.startRandomWander(animal, bounds, [], 30, prefix)
    })
  }

  private startRandomWander(
    animal: Phaser.GameObjects.Image,
    bounds: Phaser.Geom.Rectangle,
    idleTextures: string[],
    maxStep: number,
    directionalPrefix?: string,
    stepped = false,
  ) {
    const chooseNextStep = () => {
      if (!animal.active) return
      const angle = Phaser.Math.FloatBetween(0, Math.PI * 2)
      const distance = Phaser.Math.Between(Math.round(maxStep * 0.35), maxStep)
      const targetX = Phaser.Math.Clamp(animal.x + Math.cos(angle) * distance, bounds.left, bounds.right)
      const targetY = Phaser.Math.Clamp(animal.y + Math.sin(angle) * distance, bounds.top, bounds.bottom)
      const dx = targetX - animal.x
      const dy = targetY - animal.y
      if (directionalPrefix) {
        const horizontal = dx < -3 ? 'west' : dx > 3 ? 'east' : ''
        const vertical = dy < -3 ? 'north' : dy > 3 ? 'south' : ''
        animal.setTexture(`${directionalPrefix}-${vertical && horizontal ? `${vertical}-${horizontal}` : vertical || horizontal || 'south'}`)
      } else if (idleTextures.length) {
        animal.setTexture(idleTextures[Phaser.Math.Between(0, idleTextures.length - 1)])
      }
      this.tweens.add({
        targets: animal,
        x: targetX,
        y: targetY,
        duration: stepped ? Phaser.Math.Between(360, 560) : Phaser.Math.Between(900, 1700),
        ease: stepped ? 'Stepped' : 'Sine.easeInOut',
        easeParams: stepped ? [4] : undefined,
        onUpdate: (_tween, target) => {
          animal.setDepth(animal.y)
          if (stepped) target.y += Math.sin(Date.now() / 55) * 0.12
        },
        onComplete: () => this.time.delayedCall(stepped ? Phaser.Math.Between(700, 1600) : Phaser.Math.Between(900, 2600), chooseNextStep),
      })
    }
    this.time.delayedCall(Phaser.Math.Between(500, 1600), chooseNextStep)
  }

  private startMorningFieldsBgm() {
    this.sound.pauseOnBlur = false
    const bgm = this.sound.add('morningFieldsBgm', { loop: true, volume: 0.26 })
    const play = () => {
      if (!bgm.isPlaying) bgm.play()
    }
    play()
    this.input.once('pointerdown', play)
    this.input.keyboard?.once('keydown', play)
  }

  private startSparrowFlocks() {
    const launch = () => {
      const fromLeft = Phaser.Math.Between(0, 1) === 0
      const fromTop = Phaser.Math.Between(0, 1) === 0
      const scale = Phaser.Math.FloatBetween(0.12, 0.16)
      const startX = fromLeft ? -150 : this.scale.width + 150
      const endX = fromLeft ? this.scale.width + 150 : -150
      const startY = fromTop ? -120 : this.scale.height + 120
      const endY = fromTop ? this.scale.height + 120 : -120
      const flightAngle = Math.atan2(endY - startY, endX - startX)
      const flock = this.add
        .image(startX, startY, 'sparrowFlockFrame1')
        .setScrollFactor(0)
        .setScale(scale)
        .setRotation(flightAngle + Math.PI / 2)
        .setAlpha(0.82)
        .setDepth(999990)
      let frame = 1
      const flapTimer = this.time.addEvent({
        delay: 145,
        loop: true,
        callback: () => {
          frame = frame % 3 + 1
          if (flock.active) flock.setTexture(`sparrowFlockFrame${frame}`)
        },
      })
      this.tweens.add({
        targets: flock,
        x: endX,
        y: endY,
        duration: Phaser.Math.Between(5200, 6800),
        ease: 'Linear',
        onComplete: () => {
          flapTimer.remove()
          flock.destroy()
        },
      })
    }
    this.time.delayedCall(1800, launch)
    this.time.addEvent({ delay: 5000, loop: true, callback: launch })
  }

  private startRiverSparkles() {
    for (let index = 0; index < 72; index += 1) {
      const size = Phaser.Math.FloatBetween(1.1, 2.8)
      const object = this.add
        .ellipse(
          0,
          0,
          size,
          size * Phaser.Math.FloatBetween(0.65, 1),
          0xbdefff,
          Phaser.Math.FloatBetween(0.35, 0.85),
        )
        .setDepth(999998)
        .setBlendMode(Phaser.BlendModes.ADD)
      const shimmer = {
        object,
        velocityX: Phaser.Math.FloatBetween(7, 16),
        phase: Phaser.Math.FloatBetween(0, Math.PI * 2),
        lane: index % 12,
      }
      this.riverShimmers.push(shimmer)
      this.resetRiverShimmer(shimmer)
    }
  }

  private updateRiverShimmers(delta: number) {
    this.riverShimmers.forEach((shimmer) => {
      shimmer.object.x += shimmer.velocityX * (delta / 1000)
      shimmer.object.y += Math.sin(this.time.now / 180 + shimmer.phase) * 0.025
      const flicker = Math.pow(Math.abs(Math.sin(this.time.now / 135 + shimmer.phase)), 2.4)
      shimmer.object.setAlpha(0.08 + flicker * 0.62)
      shimmer.object.setScale(0.58 + flicker * 0.72)

      const tileX = Math.floor(shimmer.object.x / this.mapData.tileWidth)
      const tileY = Math.floor(shimmer.object.y / this.mapData.tileHeight)
      const stillOnRiver = this.blockedTiles.has(`${tileX},${tileY}`)
      const visible = Phaser.Geom.Rectangle.Overlaps(this.cameras.main.worldView, shimmer.object.getBounds())
      if (!stillOnRiver || !visible) this.resetRiverShimmer(shimmer)
    })
  }

  private resetRiverShimmer(shimmer: { object: Phaser.GameObjects.Ellipse; velocityX: number; phase: number; lane: number }) {
    const view = this.cameras.main.worldView
    const column = shimmer.lane % 4
    const row = Math.floor(shimmer.lane / 4)
    const allVisibleRiverTiles = this.mapData.collision.blockedTiles.filter(({ x, y }) => {
      const worldX = (x + 0.5) * this.mapData.tileWidth
      const worldY = (y + 0.5) * this.mapData.tileHeight
      return view.contains(worldX, worldY)
    })
    if (!allVisibleRiverTiles.length) {
      shimmer.object.setVisible(false)
      return
    }
    const riverLeft = Math.min(...allVisibleRiverTiles.map(({ x }) => (x + 0.5) * this.mapData.tileWidth))
    const riverRight = Math.max(...allVisibleRiverTiles.map(({ x }) => (x + 0.5) * this.mapData.tileWidth)) + this.mapData.tileWidth
    const riverTop = Math.min(...allVisibleRiverTiles.map(({ y }) => (y + 0.5) * this.mapData.tileHeight))
    const riverBottom = Math.max(...allVisibleRiverTiles.map(({ y }) => (y + 0.5) * this.mapData.tileHeight)) + this.mapData.tileHeight
    const laneLeft = Phaser.Math.Linear(riverLeft, riverRight, column / 4)
    const laneRight = Phaser.Math.Linear(riverLeft, riverRight, (column + 1) / 4)
    const laneTop = Phaser.Math.Linear(riverTop, riverBottom, row / 3)
    const laneBottom = Phaser.Math.Linear(riverTop, riverBottom, (row + 1) / 3)
    let visibleRiverTiles = allVisibleRiverTiles.filter(({ x, y }) => {
      const worldX = (x + 0.5) * this.mapData.tileWidth
      const worldY = (y + 0.5) * this.mapData.tileHeight
      return worldX >= laneLeft && worldX < laneRight && worldY >= laneTop && worldY < laneBottom
    })
    if (!visibleRiverTiles.length) {
      visibleRiverTiles = allVisibleRiverTiles.filter(({ x }) => {
        const worldX = (x + 0.5) * this.mapData.tileWidth
        return worldX >= laneLeft && worldX < laneRight
      })
    }
    if (!visibleRiverTiles.length) visibleRiverTiles = allVisibleRiverTiles
    if (!visibleRiverTiles.length) {
      shimmer.object.setVisible(false)
      return
    }
    const tile = Phaser.Utils.Array.GetRandom(visibleRiverTiles)
    shimmer.object
      .setPosition(
        (tile.x + Phaser.Math.FloatBetween(0.1, 0.7)) * this.mapData.tileWidth,
        (tile.y + Phaser.Math.FloatBetween(0.2, 0.8)) * this.mapData.tileHeight,
      )
      .setVisible(true)
    shimmer.velocityX = Phaser.Math.FloatBetween(7, 16)
    shimmer.phase = Phaser.Math.FloatBetween(0, Math.PI * 2)
  }

  private createWorldLighting() {
    const buildingTextures = new Set([
      'villageHall', 'najubuHouse', 'produceShop', 'itemShop', 'storageBuilding',
      'myeongHouse', 'kimHouse', 'baksuHouse', 'villagePavilion',
      'chickenCoopNormal', 'chickenCoopBroken', 'villageNoticeBoard',
    ])
    const images = this.children.list.filter(
      (child): child is Phaser.GameObjects.Image => child instanceof Phaser.GameObjects.Image,
    )
    images.forEach((object) => {
      if (object.texture.key === 'terrainChunk') return
      if (buildingTextures.has(object.texture.key)) {
        if (object.texture.key === 'villageNoticeBoard') {
          const bottomPadding = this.getOpaqueBottomPadding(object)
          const visibleBottom =
            object.y + (object.frame.cutHeight * (1 - object.originY) - bottomPadding) * Math.abs(object.scaleY)
          this.add
            .ellipse(object.x, visibleBottom - 1, object.displayWidth * 0.72, 5, 0x29342f, 0.15)
            .setDepth(object.depth - 0.5)
          return
        }
        this.add
          .image(object.x + 3, object.y + 4, object.texture.key, object.frame.name)
          .setOrigin(object.originX, object.originY)
          .setScale(object.scaleX, object.scaleY)
          .setFlipX(object.flipX)
          .setTint(0x26312c)
          .setAlpha(0.18)
          .setDepth(object.depth - 0.6)
        return
      }
      const shadow = this.add
        .image(object.x, object.y, object.texture.key, object.frame.name)
        .setOrigin(object.originX, 1)
        .setScale(object.scaleX * 0.86, object.scaleY * 0.15)
        .setFlipX(object.flipX)
        .setTint(0x34463d)
        .setAlpha(0.17)
        .setDepth(object.depth - 0.5)
      this.attachShadowToOpaqueBase(object, shadow)
      this.shadowPairs.push({ object, shadow })
    })

    this.add
      .rectangle(0, 0, this.scale.width, this.scale.height, 0xffd59a, 0.055)
      .setOrigin(0)
      .setScrollFactor(0)
      .setDepth(999999)
      .setBlendMode(Phaser.BlendModes.ADD)
  }

  private updateWorldShadows() {
    this.shadowPairs.forEach(({ object, shadow }) => {
      if (!object.active) return
      if (shadow.texture.key !== object.texture.key || shadow.frame.name !== object.frame.name) {
        shadow.setTexture(object.texture.key, object.frame.name)
      }
      shadow.setOrigin(object.originX, 1)
      shadow.setScale(object.scaleX * 0.86, object.scaleY * 0.15)
      this.attachShadowToOpaqueBase(object, shadow)
      shadow.setFlipX(object.flipX)
      shadow.setDepth(object.depth - 0.5)
      shadow.setVisible(object.visible)
    })
  }

  private attachShadowToOpaqueBase(object: Phaser.GameObjects.Image, shadow: Phaser.GameObjects.Image) {
    const bottomPadding = this.getOpaqueBottomPadding(object)
    const objectScaleY = Math.abs(object.scaleY)
    const shadowScaleY = Math.abs(shadow.scaleY)
    const visibleObjectBottom =
      object.y + (object.frame.cutHeight * (1 - object.originY) - bottomPadding) * objectScaleY

    // Align the last visible shadow pixel with the last visible object pixel.
    // This deliberately ignores transparent PNG padding, which differs per asset.
    shadow.setPosition(object.x, visibleObjectBottom + bottomPadding * shadowScaleY)
  }

  private getOpaqueBottomPadding(object: Phaser.GameObjects.Image) {
    const frame = object.frame
    const cacheKey = `${object.texture.key}:${String(frame.name)}`
    const cached = this.opaqueBottomPadding.get(cacheKey)
    if (cached !== undefined) return cached

    const canvas = document.createElement('canvas')
    canvas.width = frame.cutWidth
    canvas.height = frame.cutHeight
    const context = canvas.getContext('2d', { willReadFrequently: true })
    if (!context) return 0

    try {
      context.drawImage(
        frame.source.image as CanvasImageSource,
        frame.cutX,
        frame.cutY,
        frame.cutWidth,
        frame.cutHeight,
        0,
        0,
        frame.cutWidth,
        frame.cutHeight,
      )
      const pixels = context.getImageData(0, 0, frame.cutWidth, frame.cutHeight).data
      let lastOpaqueRow = frame.cutHeight - 1
      rowSearch: for (let y = frame.cutHeight - 1; y >= 0; y -= 1) {
        for (let x = 0; x < frame.cutWidth; x += 1) {
          if (pixels[(y * frame.cutWidth + x) * 4 + 3] > 12) {
            lastOpaqueRow = y
            break rowSearch
          }
        }
      }
      const padding = frame.cutHeight - 1 - lastOpaqueRow
      this.opaqueBottomPadding.set(cacheKey, padding)
      return padding
    } catch {
      this.opaqueBottomPadding.set(cacheKey, 0)
      return 0
    }
  }

  private createBridge() {
    const bridgeX = (this.mapData.bridge.tileX + 0.5) * this.mapData.tileWidth
    const bridgeY = this.mapData.bridge.tileY * this.mapData.tileHeight
    this.add
      .image(bridgeX, bridgeY, 'ruralBridge')
      .setOrigin(0.5, 0.5)
      .setScale(0.36)
      .setDepth(10)
  }

  /** 장소 스팟 위에 "미습득 단서 있음" 표시를 달아둔다 — 기본은 숨김, setClueSpots가 켠다. */
  private registerClueMarker(spotKey: string, x: number, y: number) {
    const marker = this.add
      .text(x, y - this.mapData.tileHeight * 1.6, '❗', {
        fontFamily: 'sans-serif', fontSize: '22px', color: '#fff08a',
      })
      .setOrigin(0.5, 1)
      .setDepth(y + 2)
      .setVisible(false)
    this.tweens.add({
      targets: marker,
      y: marker.y - 6,
      duration: 620,
      yoyo: true,
      repeat: -1,
      ease: 'Sine.easeInOut',
    })
    this.clueMarkersBySpot.set(spotKey, marker)
  }

  /**
   * 지금 미습득 단서가 있는 장소 스팟 목록으로 지도 상태를 동기화한다 — 장소마다 "❗" 표시를
   * 켜고, 전용 파손 텍스처가 있는 양계장/수박밭은 실제 파손 이미지로 바꾼다(예전엔 클릭할
   * 때마다 로컬 상태만 토글하는 장식용이라 실제 사보타주 여부와 무관했다).
   */
  setClueSpots(spotKeys: string[]) {
    this.activeClueSpots = new Set(spotKeys)
    this.clueMarkersBySpot.forEach((marker, spotKey) => marker.setVisible(this.activeClueSpots.has(spotKey)))

    const coopBroken = this.activeClueSpots.has('chicken-coop')
    this.chickenCoop?.setTexture(coopBroken ? 'chickenCoopBroken' : 'chickenCoopNormal')
    this.chickens.forEach((chicken) => chicken.setVisible(!coopBroken))

    const fieldDamaged = this.activeClueSpots.has('watermelon-field')
    this.watermelonField?.setTexture(fieldDamaged ? 'watermelonFieldDamaged' : 'watermelonFieldNormal')
  }

  private tryMove(nextX: number, nextY: number) {
    const halfWidth = 8
    const halfHeight = 7
    const minX = halfWidth
    const maxX = this.mapData.pixelWidth - halfWidth
    const minY = halfHeight
    const maxY = this.mapData.pixelHeight - halfHeight
    const x = Phaser.Math.Clamp(nextX, minX, maxX)
    const y = Phaser.Math.Clamp(nextY, minY, maxY)

    const samplePoints = [
      [x - halfWidth, y + halfHeight],
      [x + halfWidth, y + halfHeight],
    ]
    const blocked = samplePoints.some(([sampleX, sampleY]) => {
      const tileX = Math.floor(sampleX / this.mapData.tileWidth)
      const tileY = Math.floor(sampleY / this.mapData.tileHeight)
      const blockedByTerrain = this.blockedTiles.has(`${tileX},${tileY}`)
      const blockedByObject = this.objectBlockers.some((rect) => rect.contains(sampleX, sampleY))
      return blockedByTerrain || blockedByObject
    })
    if (!blocked) this.player.setPosition(x, y)
  }
}
