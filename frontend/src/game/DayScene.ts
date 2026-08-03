import Phaser from 'phaser'
import { NPC_ROSTER } from '../types/npc'

export interface DaySceneInitData {
  day: number
  staminaCurrent: number
  staminaMax: number
  onStaminaChange: (value: number) => void
  onNpcClick: (npcName: string, npcRole: string) => void
}

const TILE = 32
const WORLD_TILES_W = 30
const WORLD_TILES_H = 20
const WORLD_W = WORLD_TILES_W * TILE
const WORLD_H = WORLD_TILES_H * TILE

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
  { key: 'house1', tileX: 4, tileY: 13, label: '민가', tilesWide: 5 },
  { key: 'house2', tileX: 10, tileY: 13, label: '민가', tilesWide: 5 },
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
  private lastStepAt = 0

  constructor() {
    super('DayScene')
  }

  init(data: DaySceneInitData) {
    this.initData = data
    this.stamina = data.staminaCurrent
  }

  preload() {
    this.load.image('grass', '/assets/background-assets/terrain-grass-source.png')
    this.load.image('house1', '/assets/background-assets/buildings/houses/house-1.png')
    this.load.image('house2', '/assets/background-assets/buildings/houses/house-2.png')
    this.load.image('produce-shop', '/assets/background-assets/buildings/public/produce-shop.png')
    this.load.image('item-shop', '/assets/background-assets/buildings/public/item-shop.png')
    this.load.image('village-hall', '/assets/background-assets/buildings/public/village-hall.png')
    this.load.image('tree', '/assets/background-assets/objects/ordinary-tree.png')
    NPC_ROSTER.forEach((npc) => this.load.image(`npc-${npc.slug}`, npc.portrait))
  }

  create() {
    for (let x = 0; x < WORLD_TILES_W; x++) {
      for (let y = 0; y < WORLD_TILES_H; y++) {
        this.add.image(x * TILE, y * TILE, 'grass').setOrigin(0).setDisplaySize(TILE, TILE)
      }
    }

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
      sprite.setDisplaySize(TILE, TILE)
      sprite.setInteractive({ useHandCursor: true })
      sprite.on('pointerdown', () => this.initData.onNpcClick(npc.name, npc.role))
      this.add
        .text(pos.x * TILE, pos.y * TILE + TILE / 2 + 2, npc.name, {
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

    if (this.input.keyboard) {
      this.cursors = this.input.keyboard.createCursorKeys()
      this.keyW = this.input.keyboard.addKey('W')
      this.keyA = this.input.keyboard.addKey('A')
      this.keyS = this.input.keyboard.addKey('S')
      this.keyD = this.input.keyboard.addKey('D')
    }
  }

  update(time: number) {
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

    if (time - this.lastStepAt > 260) {
      this.lastStepAt = time
      this.stamina = Math.max(0, this.stamina - 1)
      this.initData.onStaminaChange(this.stamina)
    }
  }
}
