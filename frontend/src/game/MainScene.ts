import Phaser from 'phaser'

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

const PLAYER_SPEED = 145
const WORLD_OBJECT_SCALE = 1

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
  private player!: Phaser.GameObjects.Image
  private cursors!: Phaser.Types.Input.Keyboard.CursorKeys
  private movementKeys!: Record<'up' | 'down' | 'left' | 'right', Phaser.Input.Keyboard.Key>
  private blockedTiles = new Set<string>()
  private mapData!: TerrainMapData
  private chickenCoop!: Phaser.GameObjects.Image
  private chickens: Phaser.GameObjects.Image[] = []
  private coopBroken = false
  private objectBlockers: Phaser.Geom.Rectangle[] = []

  constructor() {
    super('MainScene')
  }

  preload() {
    this.load.image('terrainChunk', '/assets/world/maps/korean-countryside-chunk-01.png?v=no-road-expanded-field-v18')
    this.load.json('terrainMapData', '/assets/world/maps/korean-countryside-chunk-01.json?v=no-road-expanded-field-v18')
    this.load.image('player-south', '/assets/characters/player-male/Idle/rotations/south.png')
    this.load.image('player-south-east', '/assets/characters/player-male/Idle/rotations/south-east.png')
    this.load.image('player-east', '/assets/characters/player-male/Idle/rotations/east.png')
    this.load.image('player-north-east', '/assets/characters/player-male/Idle/rotations/north-east.png')
    this.load.image('player-north', '/assets/characters/player-male/Idle/rotations/north.png')
    this.load.image('player-north-west', '/assets/characters/player-male/Idle/rotations/north-west.png')
    this.load.image('player-west', '/assets/characters/player-male/Idle/rotations/west.png')
    this.load.image('player-south-west', '/assets/characters/player-male/Idle/rotations/south-west.png')
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
    this.load.image('brokenFence', '/assets/world/objects/wood-fence-broken.png')
    this.load.image('zelkovaTree', '/assets/world/objects/zelkova-tree-v1.png')
    this.load.image('wildDeciduousTree', '/assets/world/objects/wild-deciduous-tree-v1.png')
    this.load.image('wildShrubCluster', '/assets/world/objects/wild-shrub-cluster-v1.png')
    this.load.image('wildGrassCluster', '/assets/world/objects/wild-grass-cluster-v1.png')
    this.load.image('communalWaterStation', '/assets/world/objects/communal-water-station-v1.png')
    this.load.image('villageBicycle', '/assets/world/objects/village-bicycle-v1.png')
    this.load.image('villageNoticeBoard', '/assets/world/objects/village-notice-board-v1.png')
    this.load.image('stoneFlowerBed', '/assets/world/objects/stone-flower-bed-v1.png')
    this.load.image('largeGraniteBoulder', '/assets/world/objects/large-granite-boulder-v1.png')
    this.load.image('broadcastSpeakerPole', '/assets/world/objects/broadcast-speaker-pole-v1.png')
    this.load.image('villageDog', '/assets/world/objects/village-dog-v1.png')
    this.load.image('najubuHouse', '/assets/world/buildings/houses/house-2.png')
    this.load.image('produceShop', '/assets/world/buildings/public/produce-shop.png')
    this.load.image('najubu', '/assets/characters/npcs/npc-02/Idle/rotations/south.png')
    this.load.image('gardenCarrot', '/assets/world/growth/crops/carrot-stage-3.png')
    this.load.image('gardenPotato', '/assets/world/growth/crops/potato-stage-3.png')
    this.load.image('gardenAppleTree', '/assets/world/growth/fruit/apple-tree-stage-3.png')
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
    this.load.image('jeonJuin', '/assets/characters/npcs/npc-03/Idle/rotations/south.png')
    this.load.image('parkYounggye', '/assets/characters/npcs/npc-04/Idle/rotations/south.png')
    this.load.image('myeongJayu', '/assets/characters/npcs/npc-05/Idle/rotations/south.png')
    this.load.image('kimChijun', '/assets/characters/npcs/npc-06/Idle/rotations/south.png')
    this.load.image('naBaksu', '/assets/characters/npcs/npc-07/Idle/rotations/south.png')
  }

  create() {
    this.mapData = this.cache.json.get('terrainMapData') as TerrainMapData
    this.blockedTiles = new Set(
      this.mapData.collision.blockedTiles.map(({ x, y }) => `${x},${y}`),
    )

    this.add.image(0, 0, 'terrainChunk').setOrigin(0, 0)
    this.createBridge()

    const spawnX = (this.mapData.spawn.x + 0.5) * this.mapData.tileWidth
    const spawnY = (this.mapData.spawn.y + 0.5) * this.mapData.tileHeight
    this.createFarmAreaObjects()
    this.createZoneOne()
    this.createZoneTwo()
    this.createEnvironmentalDensity()
    this.createRemainingZones()

    this.player = this.add.image(spawnX, spawnY, 'player-south').setDepth(spawnY).setScale(1.25)

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
  }

  update(_time: number, delta: number) {
    const left = this.cursors.left.isDown || this.movementKeys.left.isDown
    const right = this.cursors.right.isDown || this.movementKeys.right.isDown
    const up = this.cursors.up.isDown || this.movementKeys.up.isDown
    const down = this.cursors.down.isDown || this.movementKeys.down.isDown

    let dx = Number(right) - Number(left)
    let dy = Number(down) - Number(up)
    if (dx === 0 && dy === 0) return

    const length = Math.hypot(dx, dy)
    dx /= length
    dy /= length
    this.setPlayerDirection(dx, dy)
    const distance = PLAYER_SPEED * (delta / 1000)

    this.tryMove(this.player.x + dx * distance, this.player.y)
    this.tryMove(this.player.x, this.player.y + dy * distance)
    this.player.setDepth(this.player.y)
  }

  private setPlayerDirection(dx: number, dy: number) {
    const horizontal = dx < -0.35 ? 'west' : dx > 0.35 ? 'east' : ''
    const vertical = dy < -0.35 ? 'north' : dy > 0.35 ? 'south' : ''
    const direction = vertical && horizontal ? `${vertical}-${horizontal}` : vertical || horizontal
    if (direction) this.player.setTexture(`player-${direction}`)
  }

  private createFarmAreaObjects() {
    const tile = this.mapData.tileWidth

    const fencePositions = [88, 94, 100]
    fencePositions.forEach((tileX) => {
      const x = (tileX + 0.5) * tile
      const y = 29 * tile
      this.add.image(x, y, 'woodFence').setOrigin(0.5, 1).setScale(0.22).setDepth(y)
      this.objectBlockers.push(new Phaser.Geom.Rectangle(x - 66, y - 18, 132, 20))
    })

    const coopX = 104.5 * tile
    const coopY = 34 * tile
    this.chickenCoop = this.add
      .image(coopX, coopY, 'chickenCoopNormal')
      .setOrigin(0.5, 1)
      .setScale(0.26)
      .setDepth(coopY)
      .setInteractive({ useHandCursor: true })
    this.chickenCoop.on('pointerover', () => this.chickenCoop.setTint(0xfff1b5))
    this.chickenCoop.on('pointerout', () => this.chickenCoop.clearTint())
    this.chickenCoop.on('pointerdown', () => this.toggleChickenCoop())
    this.objectBlockers.push(new Phaser.Geom.Rectangle(coopX - 90, coopY - 72, 180, 72))

    const chickenSpecs: Array<[number, number, string]> = [
      [100.5, 34, 'chickenFront'],
      [102.5, 36, 'chickenLeft'],
      [105, 37, 'chickenRight'],
    ]
    this.chickens = chickenSpecs.map(([tileX, tileY, texture]) => {
      const x = tileX * tile
      const y = tileY * tile
      return this.add.image(x, y, texture).setOrigin(0.5, 1).setScale(0.14).setDepth(y)
    })

    const scarecrowX = 96 * tile
    const scarecrowY = 36 * tile
    this.add
      .image(scarecrowX, scarecrowY, 'scarecrow')
      .setOrigin(0.5, 1)
      .setScale(0.18)
      .setDepth(scarecrowY)

    const jarsX = 108 * tile
    const jarsY = 35 * tile
    this.add.image(jarsX, jarsY, 'onggiJars').setOrigin(0.5, 1).setScale(0.16).setDepth(jarsY)
  }

  private createZoneOne() {
    const tile = this.mapData.tileWidth
    const at = (tileX: number, tileY: number) => ({ x: tileX * tile, y: tileY * tile })

    // 1구역(좌상단): 회관을 중심으로 어귀와 정자가 연결되는 작은 생활권입니다.
    const hall = at(18, 18)
    this.add.image(hall.x, hall.y, 'villageHall').setOrigin(0.5, 1).setScale(0.22).setDepth(hall.y)
    this.objectBlockers.push(new Phaser.Geom.Rectangle(hall.x - 164, hall.y - 82, 328, 78))

    const pavilion = at(38, 40)
    this.add
      .image(pavilion.x, pavilion.y, 'villagePavilion')
      .setOrigin(0.5, 1)
      .setScale(0.2)
      .setDepth(pavilion.y)
    this.objectBlockers.push(new Phaser.Geom.Rectangle(pavilion.x - 92, pavilion.y - 48, 184, 45))

    const treePositions: Array<[number, number, number]> = [
      [5, 9, 0.105], [27, 8, 0.1], [39, 7, 0.11], [10, 29, 0.1],
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
      [8.2, 17.8, false], [29.3, 17.4, false],
    ].forEach(([x, y, flipped]) => place('woodFence', x as number, y as number, 0.14, { width: 82, height: 16 }, flipped as boolean))
    place('woodenPyeongsang', 30.7, 20.2, 0.14, { width: 60, height: 25 }, true)
    place('wildDeciduousTree', 3.4, 13.5, 0.13, { width: 25, height: 22 }, true)
    place('zelkovaTree', 33.2, 7.8, 0.085, { width: 28, height: 23 })

    // Signature 1990s village props. The center line from the road to the hall
    // doors stays open while the objects form two uneven civic-yard clusters.
    place('villageNoticeBoard', 10.2, 15.2, 0.075, { width: 62, height: 20 })
    place('villageBicycle', 23.2, 18.7, 0.068, { width: 58, height: 20 }, true)
    place('stoneFlowerBed', 13.5, 17.1, 0.065, { width: 72, height: 20 })
    place('broadcastSpeakerPole', 4.7, 11.8, 0.14, { width: 24, height: 20 })
    place('villageDog', 22.1, 20.4, 0.058)

    ;[
      [10.5, 18.6], [32.8, 17.7],
    ].forEach(([x, y], index) => place('wildShrubCluster', x, y, 0.052 + (index % 2) * 0.006, undefined, index % 2 === 0))
    ;[
      [14.1, 18.8], [36.7, 17.4],
    ].forEach(([x, y], index) => place('wildGrassCluster', x, y, 0.038 + (index % 3) * 0.004, undefined, index % 2 === 0))

    // Pavilion: fills the open civic space below Hyeon Sudong and Najubu,
    // while preserving a clear grass buffer before the northern riverbank.
    place('woodenPyeongsang', 42.2, 39.7, 0.12, { width: 52, height: 22 })
    place('wildDeciduousTree', 33.5, 36.2, 0.135, { width: 25, height: 22 })
    place('ordinaryTree', 43.4, 36.8, 0.095, { width: 35, height: 24 })
    place('stoneFlowerBed', 40.9, 42.5, 0.05, { width: 54, height: 16 })
    ;[[34.8, 41.3], [43, 41.8]].forEach(([x, y]) =>
      place('wildShrubCluster', x, y, 0.05),
    )

    // Chicken farm: working clutter and shelter at the plot edges, leaving the
    // middle clear for NPC/chicken movement and the sabotage interaction.
    place('stoneWell', 112.5, 35, 0.12, { width: 44, height: 30 })
    place('onggiJars', 109.6, 32.7, 0.095)
    place('woodenPyeongsang', 86.5, 34.5, 0.13, { width: 56, height: 23 }, true)
    ;[[84, 30.5], [116, 36.5]].forEach(([x, y], index) =>
      place(index % 2 === 0 ? 'wildDeciduousTree' : 'zelkovaTree', x, y, index % 2 === 0 ? 0.125 : 0.078, { width: 25, height: 21 }, index % 3 === 0),
    )
    ;[[87, 31], [110.8, 38.5], [116, 39]].forEach(([x, y]) =>
      place('wildShrubCluster', x, y, 0.052),
    )
    ;[[86.5, 39], [108.5, 39.4], [114, 40]].forEach(([x, y], index) =>
      place(index === 1 ? 'brokenFence' : 'woodFence', x, y, 0.13, { width: 76, height: 15 }, index % 2 === 0),
    )

    // Small, irregular groves break up the remaining grass without forming a
    // wall. They sit well away from the main road, river banks and bridge route.
    const grove: Array<[string, number, number, number]> = [
      // Southwest grove: three related silhouettes with breathing room.
      ['ordinaryTree', 8, 69, 0.095], ['wildDeciduousTree', 14, 73, 0.125],
      ['wildShrubCluster', 11, 76, 0.052],
      // Lower-east grove, separated from the pavilion walking yard.
      ['zelkovaTree', 86, 70, 0.08], ['ordinaryTree', 92, 75, 0.1],
      ['wildGrassCluster', 88, 78, 0.042],
      // Far southeast boundary cluster.
      ['ordinaryTree', 113, 70, 0.1], ['wildDeciduousTree', 120, 75, 0.125],
      ['wildShrubCluster', 116, 79, 0.052],
    ]
    grove.forEach(([texture, x, y, scale]) => place(texture, x, y, scale, { width: 24, height: 21 }))
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
    const home = at(55, 18)
    this.add.image(home.x, home.y, 'najubuHouse').setOrigin(0.5, 1).setScale(0.4).setDepth(home.y)
    this.objectBlockers.push(new Phaser.Geom.Rectangle(home.x - 100, home.y - 64, 200, 62))

    const shop = at(76, 19)
    this.add.image(shop.x, shop.y, 'produceShop').setOrigin(0.5, 1).setScale(0.38).setDepth(shop.y)
    this.objectBlockers.push(new Phaser.Geom.Rectangle(shop.x - 104, shop.y - 64, 208, 62))

    // Small cared-for vegetable plot left of the house.
    ;[[45.5, 18], [49.1, 17.8]].forEach(([x, y], index) =>
      add(index === 0 ? 'woodFence' : 'brokenFence', x, y, 0.125, { width: 72, height: 14 }, index === 1),
    )
    ;[[45.2, 16.4], [47.2, 16.8], [49, 15.8], [50.5, 17]].forEach(([x, y], index) =>
      add(index % 2 === 0 ? 'gardenCarrot' : 'gardenPotato', x, y, 0.062 + (index % 2) * 0.005),
    )
    add('gardenAppleTree', 46, 11.5, 0.12, { width: 24, height: 21 })
    add('stoneFlowerBed', 51.2, 17.4, 0.05, { width: 52, height: 16 })

    // Utility and resting cluster to the right; the front-door axis at x=55 is empty.
    add('onggiJars', 60.5, 16.7, 0.09, { width: 36, height: 23 })
    add('communalWaterStation', 63.5, 16.6, 0.072, { width: 58, height: 31 })
    add('woodenPyeongsang', 64.5, 19.5, 0.115, { width: 50, height: 21 }, true)
    add('villageBicycle', 72, 19.7, 0.055, { width: 47, height: 17 })
    add('stoneFlowerBed', 58.5, 17.7, 0.043, { width: 46, height: 14 }, true)
    add('wildShrubCluster', 61.8, 18.7, 0.046)
    add('wildGrassCluster', 66, 18.6, 0.037, undefined, true)
    add('villageDog', 58.8, 20.4, 0.052)

    // Uneven garden boundary vegetation avoids an artificial row.
    add('wildDeciduousTree', 43.8, 9.5, 0.125, { width: 24, height: 21 }, true)
    add('ordinaryTree', 66.5, 10.7, 0.09, { width: 34, height: 23 })
    ;[[44, 19.4], [47.8, 19], [62, 20.1], [68, 17.8], [80.5, 17.4]].forEach(([x, y], index) =>
      add(index % 2 === 0 ? 'wildShrubCluster' : 'wildGrassCluster', x, y, index % 2 === 0 ? 0.047 : 0.036, undefined, index % 3 === 0),
    )

    const npcLocation = at(55, 20.2)
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
    ) => {
      const { x, y } = at(tileX, tileY)
      this.add.image(x, y, texture).setOrigin(0.5, 1).setScale(scale).setDepth(y)
      this.objectBlockers.push(
        new Phaser.Geom.Rectangle(x - blockerWidth / 2, y - blockerHeight, blockerWidth, blockerHeight),
      )
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
    }

    // Zone 3: the commercial corner. The item shop faces the northern road;
    // its small storage building sits farther east, leaving a service yard between.
    building('itemShop', 96, 16, 0.34, 180, 55)
    building('storageBuilding', 116, 29, 0.25, 130, 40)
    prop('villageBicycle', 87.5, 19, 0.055, true)
    prop('woodenPyeongsang', 108.5, 18.8, 0.1)
    prop('onggiJars', 115, 27.5, 0.075)
    npc('jeonJuin', '전주인', 93.5, 19.5, NPC_DAY_SCHEDULES.jeonJuin)
    npc('kimChijun', '김치준', 99.5, 19.7, NPC_DAY_SCHEDULES.kimChijun)

    // Zone 6: chicken-farm owner remains beside the interactive coop and chickens.
    // The lane toward the river remains open for the sabotage investigation route.
    prop('stoneFlowerBed', 97, 37.6, 0.045)
    prop('wildGrassCluster', 106.8, 39, 0.035)
    npc('parkYounggye', '박영계', 101, 38.6, NPC_DAY_SCHEDULES.parkYounggye)

    // Zone 7: Myeong Jayu's quiet home and small, maintained side garden.
    building('myeongHouse', 27, 84, 0.34, 160, 50)
    prop('woodFence', 17.5, 84.3, 0.12)
    prop('stoneFlowerBed', 20.2, 82.5, 0.052)
    prop('onggiJars', 34.5, 82.8, 0.08)
    prop('wildShrubCluster', 18.8, 86.2, 0.045)
    npc('myeongJayu', '명자유', 27, 87, NPC_DAY_SCHEDULES.myeongJayu)

    // Zone 8: Kim's modest residence is deliberately quieter than his work area.
    // He is currently rendered at the item shop according to his 70% daytime route.
    building('kimHouse', 77, 90, 0.29, 145, 44)
    prop('villageBicycle', 68.5, 89.6, 0.05)
    prop('wildShrubCluster', 84.5, 89, 0.046)

    // Zone 9: a dedicated watermelon plot with the farmer's home on its east edge.
    building('watermelonFieldNormal', 104, 82, 0.28, 215, 65)
    building('baksuHouse', 119, 91, 0.27, 145, 44)
    prop('scarecrow', 99, 79.5, 0.15)
    prop('woodFence', 94, 84.5, 0.13)
    prop('woodFence', 113.5, 84.4, 0.13, true)
    prop('wildGrassCluster', 116, 88.3, 0.036)
    npc('naBaksu', '나박수', 104, 85.2, NPC_DAY_SCHEDULES.naBaksu)
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

  private toggleChickenCoop() {
    this.coopBroken = !this.coopBroken
    this.chickenCoop.setTexture(this.coopBroken ? 'chickenCoopBroken' : 'chickenCoopNormal')
    this.chickens.forEach((chicken) => chicken.setVisible(!this.coopBroken))
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
