// 기타/2. 에셋/outputs/asset-manifest.js 를 TS로 옮긴 버전.
// 실제 파일은 frontend/public/assets/ 에 복사되어 있다 (기타/2. 에셋/outputs 미러, raw/ 원본 제외).
const ASSET_BASE = '/assets'
const asset = (path: string) => `${ASSET_BASE}/${path}`

const growthStages = (folder: string, name: string): [string, string, string] => [
  asset(`background-assets/growth/${folder}/${name}-stage-1.png`),
  asset(`background-assets/growth/${folder}/${name}-stage-2.png`),
  asset(`background-assets/growth/${folder}/${name}-stage-3.png`),
]

export const villageAssets = {
  concept: asset('concept-bg.png'),

  terrain: {
    grass: asset('background-assets/terrain-grass-source.png'),
    dirtPath: asset('background-assets/terrain-dirt-path-source.png'),
    tilledSoil: asset('background-assets/terrain-tilled-soil-source.png'),
    water: asset('background-assets/terrain-water-source.png'),
    grassDirtEdge: asset('background-assets/edge-grass-dirt.png'),
    riverbankEdge: asset('background-assets/edge-riverbank.png'),
    concreteBridge: asset('background-assets/bridge-concrete.png'),
  },

  crops: {
    potato: growthStages('crops', 'potato'),
    sweetPotato: growthStages('crops', 'sweet-potato'),
    carrot: growthStages('crops', 'carrot'),
    strawberry: growthStages('crops', 'strawberry'),
    watermelon: growthStages('crops', 'watermelon'),
  },

  fruitAndForage: {
    appleTree: growthStages('fruit', 'apple-tree'),
    cherryTree: growthStages('fruit', 'cherry-tree'),
    persimmonTree: growthStages('fruit', 'persimmon-tree'),
    raspberryBush: growthStages('fruit', 'raspberry-bush'),
  },

  houses: Array.from({ length: 7 }, (_, index) =>
    asset(`background-assets/buildings/houses/house-${index + 1}.png`),
  ),

  publicBuildings: {
    produceShop: asset('background-assets/buildings/public/produce-shop.png'),
    itemShop: asset('background-assets/buildings/public/item-shop.png'),
    villageHall: asset('background-assets/buildings/public/village-hall.png'),
  },

  facilities: {
    park: asset('background-assets/facilities/village-park.png'),
    chickenCoop: {
      normal: asset('background-assets/facilities/chicken-coop-normal.png'),
      broken: asset('background-assets/facilities/chicken-coop-broken.png'),
    },
    watermelonField: {
      normal: asset('background-assets/facilities/watermelon-field-normal.png'),
      damaged: asset('background-assets/facilities/watermelon-field-damaged.png'),
    },
  },

  objects: {
    ordinaryTree: asset('background-assets/objects/ordinary-tree.png'),
    chicken: {
      front: asset('background-assets/objects/chicken-front.png'),
      back: asset('background-assets/objects/chicken-back.png'),
      left: asset('background-assets/objects/chicken-left.png'),
      right: asset('background-assets/objects/chicken-right.png'),
    },
    fence: {
      intact: asset('background-assets/objects/wood-fence-intact.png'),
      broken: asset('background-assets/objects/wood-fence-broken.png'),
    },
    onggiJars: asset('background-assets/objects/onggi-jars.png'),
    stoneWell: asset('background-assets/objects/stone-well.png'),
    pyeongsang: asset('background-assets/objects/wooden-pyeongsang.png'),
    scarecrow: asset('background-assets/objects/scarecrow.png'),
  },

  furniture: {
    singleBed: asset('background-assets/furniture/single-bed.png'),
    wardrobe: asset('background-assets/furniture/wardrobe.png'),
    lowTable: asset('background-assets/furniture/low-table.png'),
    woodenChair: asset('background-assets/furniture/wooden-chair.png'),
    bookshelf: asset('background-assets/furniture/bookshelf.png'),
    storageCabinet: asset('background-assets/furniture/storage-cabinet.png'),
    floorLamp: asset('background-assets/furniture/floor-lamp.png'),
    storageChest: asset('background-assets/furniture/storage-chest.png'),
  },

  items: {
    sneakers: asset('items/sneakers.png'),
    lieDetector: asset('items/lie-detector.png'),
    bags: [1, 2, 3].map((level) => asset(`items/bag-level-${level}.png`)),
  },

  ui: {
    stamina: {
      frame: asset('ui/hud/stamina-frame.png'),
      fill: asset('ui/hud/stamina-fill.png'),
      sneakerIcon: asset('ui/hud/stamina-sneaker-icon.png'),
    },
    inventoryWindow: asset('ui/panels/inventory-window-trimmed.png'),
    missionCards: {
      active: asset('ui/missions/mission-card-active.png'),
      completed: asset('ui/missions/mission-card-completed.png'),
      failed: asset('ui/missions/mission-card-failed.png'),
    },
  },
} as const

export const renderGuide = {
  tile: 32,
  characterHeight: 32,
  cropWidth: 32,
  bushWidth: 56,
  fruitTreeWidth: 112,
  ordinaryTreeWidth: 128,
  chickenWidth: 24,
  fenceWidth: 96,
  houseWidth: 224,
  shopWidth: 256,
  villageHallWidth: 320,
} as const
