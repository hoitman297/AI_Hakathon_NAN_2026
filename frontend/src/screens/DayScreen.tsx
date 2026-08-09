import { useEffect, useRef, useState } from 'react'
import Phaser from 'phaser'
import { gameConfig } from '../game/config'
import { MainScene, type MainSceneInitData, type FarmPlotVisual } from '../game/MainScene'
import { StaminaBar } from '../components/StaminaBar'
import { DialogueBox } from '../components/DialogueBox'
import { EscMenu } from '../components/EscMenu'
import { InventoryHubPanel } from '../components/InventoryHubPanel'
import { ProduceShopPanel } from '../components/ProduceShopPanel'
import { ItemShopPanel } from '../components/ItemShopPanel'
import { SeedPickerPopup } from '../components/SeedPickerPopup'
import { NightLoadingOverlay } from '../components/NightLoadingOverlay'
import { moveSession, getSabotageLocations, type SessionResponse } from '../api/sessionApi'
import { listNpcsToday } from '../api/npcApi'
import { listUnacquiredClues, acquireClue, topicLabel, type UnacquiredClue } from '../api/clueApi'
import { matchesLocationSpot, spotsWithPendingClue } from '../game/clueLocations'
import {
  listFarmPlots,
  plantCrop,
  harvestPlot,
  listAvailableFruits,
  listFruitSpecies,
  forageFruit,
  type FarmPlot,
  type AvailableFruit,
} from '../api/farmApi'
import { listInventory, type InventorySlot } from '../api/inventoryApi'
import { cropTextureKey, computeCropStage, FRUIT_NAME_TO_KEY } from '../game/farmVisuals'
import { villageAssets } from '../assets/asset-manifest'
import './DayScreen.css'

// MainScene.ts의 FARM_PLOT_POSITIONS 길이와 맞춰야 한다 — 맵 위 실제 밭 타일 개수.
const FARM_PLOT_SLOT_COUNT = 8

/** 수확 안 한 밭들을 farmPlotId 순서로 맵 위 고정 슬롯(0..N-1)에 배정한다.
 *  수확된 밭은 더 이상 슬롯을 차지하지 않아 그 타일이 다시 빈 밭으로 보인다. */
function assignPlotsToSlots(plots: FarmPlot[]): Array<FarmPlot | null> {
  const unharvested = [...plots].filter((p) => !p.harvested).sort((a, b) => a.farmPlotId - b.farmPlotId)
  const slots: Array<FarmPlot | null> = new Array(FARM_PLOT_SLOT_COUNT).fill(null)
  unharvested.slice(0, FARM_PLOT_SLOT_COUNT).forEach((plot, index) => {
    slots[index] = plot
  })
  return slots
}

// 백엔드 GameConstants.FIRST_ACCUSATION_DAY / LAST_ACCUSATION_DAY 와 맞춰야 한다.
// 이 값을 조회하는 API가 없어 프론트에 그대로 하드코딩했다.
const FIRST_ACCUSATION_DAY = 7
const LAST_ACCUSATION_DAY = 9

// 고발 카드는 마을회관(이장 사무실)에 가서 그 자리에서 건네야 한다.
const ACCUSATION_LOCATION_SPOT = 'village-hall'

interface DayScreenProps {
  session: SessionResponse
  advancing: boolean
  advanceError: string | null
  onAdvanceDay: () => void
  onOpenAccusation: () => void
  onQuitToTitle: () => void
}

export function DayScreen({
  session,
  advancing,
  advanceError,
  onAdvanceDay,
  onOpenAccusation,
  onQuitToTitle,
}: DayScreenProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const gameRef = useRef<Phaser.Game | null>(null)
  const [stamina, setStamina] = useState(() => session.staminaCurrent)
  const [gold, setGold] = useState(() => session.gold)
  const [dialogueNpc, setDialogueNpc] = useState<{ npcId: number; name: string; role: string } | null>(null)
  const [escOpen, setEscOpen] = useState(false)
  const [inventoryOpen, setInventoryOpen] = useState(false)
  const [produceShopOpen, setProduceShopOpen] = useState(false)
  const [itemShopOpen, setItemShopOpen] = useState(false)
  const sneakersEquipped = session.sneakersEquipped ?? false

  // 농장(맵 위 실제 밭 타일)/채집(맵 위 나무·덤불) 상태. Phaser 씬 콜백(onFarmTileClick/
  // onForageTreeClick)은 마운트 시점에 한 번 캡처되므로, 최신값을 봐야 하는 부분은 ref로도
  // 같이 들고 있는다(위 unacquiredCluesRef와 같은 이유).
  const [farmPlots, setFarmPlots] = useState<FarmPlot[] | null>(null)
  const plotSlotsRef = useRef<Array<FarmPlot | null>>(new Array(FARM_PLOT_SLOT_COUNT).fill(null))
  const [seedInventory, setSeedInventory] = useState<InventorySlot[]>([])
  const [fruitSpecies, setFruitSpecies] = useState<AvailableFruit[]>([])
  const fruitSpeciesRef = useRef<AvailableFruit[]>([])
  const [availableFruitsToday, setAvailableFruitsToday] = useState<AvailableFruit[]>([])
  const availableFruitKeysRef = useRef<string[]>([])
  const [seedPickerSlotIndex, setSeedPickerSlotIndex] = useState<number | null>(null)
  const [seedPickerBusy, setSeedPickerBusy] = useState(false)
  const [seedPickerError, setSeedPickerError] = useState<string | null>(null)

  // 지금까지 사보타주가 발생한 장소 — 양계장/수박밭 등 실제 피해 여부를 맵 위 자산에 반영한다.
  const [sabotageLocations, setSabotageLocations] = useState<string[]>([])
  useEffect(() => {
    getSabotageLocations(session.sessionId)
      .then(setSabotageLocations)
      .catch((err: unknown) => console.error('사보타주 발생 장소를 불러오지 못했습니다.', err))
  }, [session.sessionId])
  const sabotageLocationsRef = useRef<string[]>([])
  useEffect(() => {
    sabotageLocationsRef.current = sabotageLocations
    const scene = gameRef.current?.scene.getScene('MainScene') as MainScene | undefined
    scene?.syncSabotageDamage(sabotageLocations)
  }, [sabotageLocations, session.currentDay])

  const canAccuse =
    session.status === 'IN_PROGRESS' &&
    session.currentDay >= FIRST_ACCUSATION_DAY &&
    session.currentDay <= LAST_ACCUSATION_DAY

  // 사보타주 단서는 지도 위 해당 장소를 직접 클릭해야 습득된다 — 항상 최신값을 봐야 해서
  // Phaser 씬 콜백(아래 handleLocationClick)에서는 state 대신 이 ref를 읽는다.
  const [unacquiredClues, setUnacquiredClues] = useState<UnacquiredClue[] | null>(null)
  const unacquiredCluesRef = useRef<UnacquiredClue[] | null>(null)
  useEffect(() => {
    unacquiredCluesRef.current = unacquiredClues
  }, [unacquiredClues])

  const [mapMessage, setMapMessage] = useState<string | null>(null)
  useEffect(() => {
    if (!mapMessage) return
    const timer = setTimeout(() => setMapMessage(null), 4500)
    return () => clearTimeout(timer)
  }, [mapMessage])

  // 7일차부터 [범인 고발하기] 버튼은 이 상태를 "무장"만 시키고, 실제 UI는 마을회관에서
  // 다시 한번 클릭해야 열린다 — 마찬가지 이유로 ref로도 들고 있는다.
  const [accusationArmed, setAccusationArmed] = useState(false)
  const accusationArmedRef = useRef(false)
  useEffect(() => {
    accusationArmedRef.current = accusationArmed
  }, [accusationArmed])
  const locationBusyRef = useRef(false)

  function reloadUnacquiredClues() {
    listUnacquiredClues(session.sessionId)
      .then(setUnacquiredClues)
      .catch((err: unknown) => console.error('미습득 단서 목록을 불러오지 못했습니다.', err))
  }
  useEffect(reloadUnacquiredClues, [session.sessionId])

  function reloadFarmPlots() {
    listFarmPlots(session.sessionId)
      .then(setFarmPlots)
      .catch((err: unknown) => console.error('밭 상태를 불러오지 못했습니다.', err))
  }
  function reloadSeedInventory() {
    listInventory(session.sessionId)
      .then((slots) => setSeedInventory(slots.filter((s) => s.itemType === 'SEED')))
      .catch((err: unknown) => console.error('씨앗 인벤토리를 불러오지 못했습니다.', err))
  }
  function reloadAvailableFruitsToday() {
    return listAvailableFruits(session.sessionId)
      .then(setAvailableFruitsToday)
      .catch((err: unknown) => console.error('오늘 채집 가능한 과일 목록을 불러오지 못했습니다.', err))
  }
  // 맵 진입 직후 나무를 바로 클릭하면 fruitSpeciesRef/availableFruitKeysRef가 아직 비어있는
  // 레이스가 있었다(채집이 조용히 아무 반응 없거나 "오늘은 딸 게 없다"는 잘못된 메시지가 뜸) —
  // NPC 목록 조회 때 겪었던 것과 같은 종류의 레이스라 같은 패턴(요청을 들고 있다가 클릭 시점에
  // 기다렸다 재확인)으로 고친다.
  const forageReadyPromiseRef = useRef<Promise<unknown> | null>(null)
  useEffect(() => {
    reloadFarmPlots()
    reloadSeedInventory()
    const availablePromise = reloadAvailableFruitsToday()
    const speciesPromise = listFruitSpecies(session.sessionId)
      .then(setFruitSpecies)
      .catch((err: unknown) => console.error('과일 종류 목록을 불러오지 못했습니다.', err))
    forageReadyPromiseRef.current = Promise.all([availablePromise, speciesPromise])
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session.sessionId])

  useEffect(() => {
    plotSlotsRef.current = assignPlotsToSlots(farmPlots ?? [])
    const scene = gameRef.current?.scene.getScene('MainScene') as MainScene | undefined
    const view: Array<FarmPlotVisual | null> = plotSlotsRef.current.map((plot) =>
      plot
        ? { textureKey: cropTextureKey(plot.cropName, computeCropStage(plot, session.currentDay)), readyToHarvest: plot.readyToHarvest }
        : null,
    )
    scene?.syncFarmPlots(view)
  }, [farmPlots, session.currentDay])

  useEffect(() => {
    fruitSpeciesRef.current = fruitSpecies
  }, [fruitSpecies])

  useEffect(() => {
    const keys = availableFruitsToday
      .map((fruit) => FRUIT_NAME_TO_KEY[fruit.name])
      .filter((key): key is string => !!key)
    availableFruitKeysRef.current = keys
    const scene = gameRef.current?.scene.getScene('MainScene') as MainScene | undefined
    scene?.syncForageTrees(keys)
  }, [availableFruitsToday])

  function handleFarmTileClick(slotIndex: number) {
    const plot = plotSlotsRef.current[slotIndex] ?? null
    if (!plot) {
      setSeedPickerError(null)
      setSeedPickerSlotIndex(slotIndex)
      return
    }
    if (plot.readyToHarvest) {
      harvestPlot(session.sessionId, plot.farmPlotId)
        .then((updated) => {
          setStamina(updated.staminaCurrent)
          setMapMessage(`${plot.cropName}을(를) 수확했습니다.`)
          reloadFarmPlots()
        })
        .catch((err: unknown) => setMapMessage(err instanceof Error ? err.message : '수확에 실패했습니다.'))
      return
    }
    setMapMessage(`${plot.cropName}이(가) 아직 자라는 중입니다 (${plot.readyDay}일차부터 수확 가능).`)
  }

  async function handleForageTreeClick(fruitKey: string) {
    // 초기 로딩(과일 종류/오늘의 채집 목록)이 아직 안 끝났을 수 있다 — 그 요청이 끝나길
    // 기다렸다가 한 번 더 확인한다(이미 끝났으면 즉시 통과).
    await forageReadyPromiseRef.current?.catch(() => undefined)
    const species = fruitSpeciesRef.current.find((fruit) => FRUIT_NAME_TO_KEY[fruit.name] === fruitKey)
    if (!species) {
      setMapMessage('채집 정보를 불러오는 중입니다. 잠시 후 다시 시도해주세요.')
      return
    }
    if (!availableFruitKeysRef.current.includes(fruitKey)) {
      setMapMessage('오늘은 여기서 딸 수 있는 게 없습니다.')
      return
    }
    forageFruit(session.sessionId, species.fruitId)
      .then((result) => {
        setStamina(result.staminaCurrent)
        setMapMessage(`${result.fruitName}을(를) 채집했습니다.`)
      })
      .catch((err: unknown) => setMapMessage(err instanceof Error ? err.message : '채집에 실패했습니다.'))
  }

  function handlePickSeed(cropId: number) {
    setSeedPickerBusy(true)
    setSeedPickerError(null)
    plantCrop(session.sessionId, cropId)
      .then((updated) => {
        setStamina(updated.staminaCurrent)
        setSeedPickerSlotIndex(null)
        setMapMessage('씨앗을 심었습니다.')
        reloadFarmPlots()
        reloadSeedInventory()
      })
      .catch((err: unknown) => setSeedPickerError(err instanceof Error ? err.message : '파종에 실패했습니다.'))
      .finally(() => setSeedPickerBusy(false))
  }

  async function handleLocationClick(spotKey: string) {
    if (spotKey === ACCUSATION_LOCATION_SPOT && accusationArmedRef.current && canAccuse) {
      setAccusationArmed(false)
      onOpenAccusation()
      return
    }

    if (locationBusyRef.current) return
    const target = (unacquiredCluesRef.current ?? []).find((clue) => matchesLocationSpot(clue.location, spotKey))
    if (target) {
      locationBusyRef.current = true
      try {
        await acquireClue(session.sessionId, target.clueId)
        setMapMessage(`${target.location}에서 단서(${topicLabel(target.topic)})를 습득했습니다.`)
        reloadUnacquiredClues()
      } catch (err) {
        setMapMessage(err instanceof Error ? err.message : '단서를 습득하지 못했습니다.')
      } finally {
        locationBusyRef.current = false
      }
      return
    }

    // 습득할 단서가 없으면 건물 용도대로 상점을 연다.
    if (spotKey === 'produce-shop') {
      setProduceShopOpen(true)
      return
    }
    if (spotKey === 'item-shop') {
      setItemShopOpen(true)
      return
    }
  }

  // NPC 이름 -> 실제 백엔드 npcId. Phaser 씬은 로컬 스프라이트 로스터(이름 기준)만 알고 있어서,
  // 대화 API를 호출하려면 이 매핑을 통해 클릭된 NPC 이름을 npcId로 바꿔줘야 한다.
  const npcIdByNameRef = useRef<Map<string, number>>(new Map())
  // Phaser 씬은 이 목록 로딩을 기다리지 않고 바로 시작돼서, 로딩이 끝나기 전에 NPC를 클릭하면
  // npcIdByNameRef가 아직 비어 있어 매핑을 못 찾는 경우가 있었다(새로고침 직후 등) — 그 요청
  // 자체를 들고 있다가, 클릭 시점에 아직 비어 있으면 이 요청이 끝나길 한 번 더 기다려서 재조회한다.
  const npcListPromiseRef = useRef<Promise<Map<string, number>> | null>(null)

  useEffect(() => {
    let cancelled = false
    const promise = listNpcsToday(session.sessionId).then((npcs) => {
      const map = new Map(npcs.map((npc) => [npc.name, npc.npcId]))
      if (!cancelled) npcIdByNameRef.current = map
      return map
    })
    npcListPromiseRef.current = promise
    promise.catch((err) => console.error('NPC 목록을 불러오지 못했습니다.', err))
    return () => {
      cancelled = true
    }
  }, [session.sessionId])

  useEffect(() => {
    if (!containerRef.current) return

    // 이동 중엔 대략 1초마다 handleMove가 호출되는데, 요청을 기다리지 않고 매번 새로 쏘면
    // 응답이 도착하는 순서가 요청 순서와 어긋날 수 있다(네트워크 지연 편차) — 그러면 나중에
    // 도착한 "더 과거" 요청의 응답이 최신 응답을 덮어써서 체력바가 95→35→95처럼 튀어 보였다.
    // 한 번에 하나의 요청만 보내고, 그사이 쌓인 이동 시간은 다음 요청에 합쳐서 보고한다 —
    // 응답을 항상 보낸 순서대로만 받게 되므로 이 역전이 원천적으로 불가능해진다.
    const pendingSecondsRef = { current: 0 }
    let moveInFlight = false

    async function handleMove(seconds: number) {
      pendingSecondsRef.current += seconds
      if (moveInFlight) return
      moveInFlight = true
      try {
        while (pendingSecondsRef.current > 0) {
          const toReport = pendingSecondsRef.current
          pendingSecondsRef.current = 0
          const updated = await moveSession(session.sessionId, toReport)
          setStamina(updated.staminaCurrent)
        }
      } catch (err) {
        console.error('이동 체력 반영에 실패했습니다.', err)
      } finally {
        moveInFlight = false
      }
    }

    const game = new Phaser.Game({ ...gameConfig, parent: containerRef.current })
    gameRef.current = game
    game.scene.add('MainScene', MainScene, false)
    const initData: MainSceneInitData = {
      day: session.currentDay,
      staminaCurrent: session.staminaCurrent,
      staminaMax: session.staminaMax,
      sneakersEquipped: session.sneakersEquipped ?? false,
      onStaminaChange: setStamina,
      onMove: handleMove,
      onNpcClick: (name, role) => {
        const resolveNpcId = (npcId: number | undefined) => {
          if (npcId === undefined) {
            console.error(`"${name}"에 대응하는 npcId를 찾지 못했습니다.`)
            return
          }
          setDialogueNpc({ npcId, name, role })
        }

        const cachedNpcId = npcIdByNameRef.current.get(name)
        if (cachedNpcId !== undefined) {
          resolveNpcId(cachedNpcId)
          return
        }
        // 아직 NPC 목록 로딩이 안 끝났을 수 있다 — 그 요청이 끝나길 기다렸다가 한 번 더 조회.
        npcListPromiseRef.current
          ?.then((map) => resolveNpcId(map.get(name)))
          .catch(() => {})
      },
      onLocationClick: handleLocationClick,
      onFarmTileClick: handleFarmTileClick,
      onForageTreeClick: handleForageTreeClick,
      clueSpots: spotsWithPendingClue((unacquiredCluesRef.current ?? []).map((c) => c.location)),
    }
    game.scene.start('MainScene', initData)

    // 밭/채집/사보타주 파손 데이터는 API 응답이 도착하는 대로 아래 다른 effect들이 밀어주는데,
    // 그 응답이 Phaser의 create()(이미지 60여 개 로딩)보다 먼저 끝나면 그 시점엔 씬이 아직
    // 준비 안 돼 있어 조용히 무시된다(각 sync 메서드가 스스로 방어, MainScene.isReady() 참고).
    // Scene의 CREATE 라이프사이클 이벤트를 직접 구독하는 방식은 scene.add()/start() 직후엔
    // Phaser 내부적으로 아직 씬 인스턴스가 매니저에 완전히 등록되지 않아(다음 게임 스텝에서야
    // 처리됨) getScene()이 null을 반환하는 경우가 있어 포기했다 — 대신 매 프레임 폴링으로
    // create() 완료를 기다렸다가, 그때까지 모아둔 최신값을 한 번 더 밀어넣는다.
    let cancelled = false
    let rafId: number
    const pushOnceReady = () => {
      if (cancelled) return
      const scene = gameRef.current?.scene.getScene('MainScene') as MainScene | undefined
      if (!scene || !scene.isReady()) {
        rafId = requestAnimationFrame(pushOnceReady)
        return
      }
      const plotView: Array<FarmPlotVisual | null> = plotSlotsRef.current.map((plot) =>
        plot
          ? {
              textureKey: cropTextureKey(plot.cropName, computeCropStage(plot, session.currentDay)),
              readyToHarvest: plot.readyToHarvest,
            }
          : null,
      )
      scene.syncFarmPlots(plotView)
      scene.syncForageTrees(availableFruitKeysRef.current)
      scene.syncSabotageDamage(sabotageLocationsRef.current)
      scene.setClueSpots(spotsWithPendingClue((unacquiredCluesRef.current ?? []).map((c) => c.location)))
    }
    rafId = requestAnimationFrame(pushOnceReady)

    return () => {
      cancelled = true
      cancelAnimationFrame(rafId)
      game.destroy(true)
      gameRef.current = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session.currentDay])

  useEffect(() => {
    const scene = gameRef.current?.scene.getScene('MainScene') as MainScene | undefined
    scene?.setInputLocked(
      !!dialogueNpc ||
        escOpen ||
        inventoryOpen ||
        produceShopOpen ||
        itemShopOpen ||
        seedPickerSlotIndex !== null ||
        advancing,
    )
  }, [dialogueNpc, escOpen, inventoryOpen, produceShopOpen, itemShopOpen, seedPickerSlotIndex, advancing])

  // 대화/아이템 사용/농사 등으로 stamina가 바뀔 때마다 Phaser 씬의 내부 체력값도 맞춰준다 —
  // 이동 중 체력 계산과 다른 이유로 값이 바뀐 뒤 다시 이동하면, 씬이 그 변화를 모른 채
  // 자기가 들고 있던 오래된 값으로 되돌려버리는 문제(대화창을 닫고 걸으면 체력이 도로 올라가는
  // 현상)가 있었다.
  useEffect(() => {
    const scene = gameRef.current?.scene.getScene('MainScene') as MainScene | undefined
    scene?.syncStamina(stamina)
  }, [stamina])

  // 미습득 단서 목록이 바뀔 때마다(최초 로딩, 습득 후 재조회) 지도 위 "❗" 표시와 양계장/수박밭
  // 파손 텍스처를 실제 사보타주 상태와 맞춘다.
  useEffect(() => {
    const scene = gameRef.current?.scene.getScene('MainScene') as MainScene | undefined
    scene?.setClueSpots(spotsWithPendingClue((unacquiredClues ?? []).map((clue) => clue.location)))
  }, [unacquiredClues])

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key !== 'Escape') return
      if (dialogueNpc) {
        setDialogueNpc(null)
        return
      }
      if (inventoryOpen) {
        setInventoryOpen(false)
        return
      }
      if (seedPickerSlotIndex !== null) {
        setSeedPickerSlotIndex(null)
        return
      }
      if (produceShopOpen) {
        setProduceShopOpen(false)
        return
      }
      if (itemShopOpen) {
        setItemShopOpen(false)
        return
      }
      setEscOpen((open) => !open)
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [dialogueNpc, inventoryOpen, produceShopOpen, itemShopOpen, seedPickerSlotIndex])

  // 체력(낮 시간)이 다 떨어지면 버튼 없이 자동으로 밤으로 넘어간다. 대화창이 열려 있는 동안엔
  // 넘어가지 않고 기다린다 — 안 그러면 대화 도중에 화면이 통째로 밤으로 전환되며 대화창이
  // 갑자기 사라지는 문제가 있었다. 대화창은 체력이 바닥나면 스스로 마무리 인사를 보여주고
  // 닫히므로(DialogueBox 참고), dialogueNpc가 null이 되는 순간 이 effect가 다시 평가되어
  // 자연스럽게 밤으로 넘어간다.
  const nightTriggeredRef = useRef(false)
  useEffect(() => {
    if (stamina > 0 || advancing || nightTriggeredRef.current || dialogueNpc) return
    nightTriggeredRef.current = true
    onAdvanceDay()
  }, [stamina, advancing, onAdvanceDay, dialogueNpc])

  return (
    <div className="day-screen">
      <div className="day-hud">
        <div className="day-hud-left">
          <div className="day-day">{session.currentDay}일차</div>
          <div className="day-gold">골드: {gold}</div>
          {sneakersEquipped && (
            <img className="day-sneaker-icon pixel-art" src={villageAssets.items.sneakers} alt="운동화 착용 중" />
          )}
        </div>
        <div className="day-hud-actions">
          <button className="pixel-button" onClick={() => setInventoryOpen(true)}>
            인벤토리
          </button>
          {canAccuse && (
            <button
              className="pixel-button pixel-button--danger"
              onClick={() => setAccusationArmed((prev) => !prev)}
            >
              {accusationArmed ? '고발 준비 취소' : '범인 고발하기'}
            </button>
          )}
          <button
            className="pixel-button"
            onClick={onAdvanceDay}
            disabled={advancing || !!dialogueNpc}
          >
            집으로 돌아가기
          </button>
        </div>
      </div>

      {advanceError && <p className="day-advance-error pixel-error">{advanceError}</p>}
      {accusationArmed && (
        <p className="day-armed-hint">마을회관으로 이동해 그 자리에서 다시 한번 클릭하면 고발 카드를 건넬 수 있습니다.</p>
      )}

      <div className="day-canvas-wrap">
        <StaminaBar value={stamina} max={session.staminaMax} />
        {mapMessage && <p className="day-map-message day-map-message--overlay">{mapMessage}</p>}
        <div ref={containerRef} className="day-canvas-inner" />
      </div>

      <p className="day-hint">
        방향키 / WASD로 이동, NPC에게 가까이 가서 클릭하면 대화하세요. 농산물 상점/아이템 상점 건물을
        클릭하면 상점이 열립니다. 밭 타일에 가까이서 클릭하면 심거나 수확할 수 있고, 나무/덤불을
        클릭하면 채집합니다. ESC로 메뉴를 엽니다.
      </p>

      {dialogueNpc && (
        <DialogueBox
          key={dialogueNpc.npcId}
          sessionId={session.sessionId}
          npcId={dialogueNpc.npcId}
          npcName={dialogueNpc.name}
          npcRole={dialogueNpc.role}
          staminaCurrent={stamina}
          onStaminaChange={setStamina}
          onClose={() => setDialogueNpc(null)}
        />
      )}

      {inventoryOpen && (
        <InventoryHubPanel
          sessionId={session.sessionId}
          currentDay={session.currentDay}
          onStaminaChange={(restored) => setStamina((prev) => Math.min(session.staminaMax, prev + restored))}
          onClose={() => setInventoryOpen(false)}
        />
      )}

      {seedPickerSlotIndex !== null && (
        <SeedPickerPopup
          seeds={seedInventory}
          busy={seedPickerBusy}
          errorMessage={seedPickerError}
          onPick={handlePickSeed}
          onClose={() => setSeedPickerSlotIndex(null)}
        />
      )}

      {produceShopOpen && (
        <ProduceShopPanel
          sessionId={session.sessionId}
          gold={gold}
          onGoldChange={setGold}
          onSeedPurchased={reloadSeedInventory}
          onClose={() => setProduceShopOpen(false)}
        />
      )}

      {itemShopOpen && (
        <ItemShopPanel
          sessionId={session.sessionId}
          gold={gold}
          onGoldChange={setGold}
          onClose={() => setItemShopOpen(false)}
        />
      )}

      {escOpen && <EscMenu onResume={() => setEscOpen(false)} onQuit={onQuitToTitle} />}

      {advancing && <NightLoadingOverlay />}
    </div>
  )
}
