import { useEffect, useState } from 'react'
import { listInventory, applyInventoryItem, type InventorySlot } from '../api/inventoryApi'
import { listNpcsToday, getNpcDetail, type NpcSummary } from '../api/npcApi'
import {
  listAcquiredClues,
  listUnacquiredClues,
  clarifyClue,
  topicLabel,
  type ClueCard,
  type UnacquiredClue,
} from '../api/clueApi'
import { getNightSummary } from '../api/sabotageApi'
import { HeartMeter } from './HeartMeter'
import { Modal } from './Modal'
import './InventoryHubPanel.css'

// 이 두 아이템은 사용 시 대상 NPC를 지정해야 한다(백엔드 UseItemRequest.targetNpcId).
// ShopItemResponse에 itemCode가 안 내려오므로 표시 이름으로 판별한다.
const NEEDS_TARGET_NPC = new Set(['거짓말탐지기', '선물세트'])

// 백엔드상 한 게임당 사보타주/단서는 1~5일차 밤에 하루 1건씩, 총 5건 고정이다
// (SabotageEvent 주석 참고). 조회 API가 없어 프론트에 하드코딩했다.
const TOTAL_CLUES = 5

// 단서 주제(ClueTopic) 5종 전용으로 미리 만들어둔 완성형 카드(프레임+삽화+캡션이 이미
// 합쳐진 이미지, ui/missions/topics 폴더). 장소(location)는 값이 없거나 매칭 안 되는
// 경우가 있었지만 topic은 모든 단서에 항상 존재해서 이쪽이 훨씬 안정적이다 — 5종 전부
// 커버되므로 매칭 안 되는 경우를 위한 빈 프레임 폴백만 남겨둔다(사실상 안 쓰일 안전망).
const CLUE_TOPIC_ILLUSTRATION: Record<string, string> = {
  HAIR: '/assets/ui/missions/topics/topic-hair.png',
  BELONGING: '/assets/ui/missions/topics/topic-belonging.png',
  FOOTPRINT: '/assets/ui/missions/topics/topic-footprint.png',
  BLOOD: '/assets/ui/missions/topics/topic-blood.png',
  MARK: '/assets/ui/missions/topics/topic-mark.png',
}
const CLUE_CARD_BLANK_FRAME = '/assets/ui/missions/mission-card-active.png'

type MainTab = 'items' | 'clues' | 'affinity'

type ClueSlotStatus = 'locked' | 'pending' | 'acquired'

interface ClueSlot {
  day: number
  status: ClueSlotStatus
  location?: string
  clue?: ClueCard
}

/**
 * 단서 카드는 1~5일차 순서로 고정 5칸에 배치된다. 미습득 목록(day 포함)은 정확하지만,
 * 습득한 단서(ClueCardResponse)에는 day가 없다 — 세션당 하루 1건씩 클루ID가 생성 순서(=일차
 * 순서) 그대로 오름차순이라는 전제로, "습득된 것으로 추정되는 지난 날짜"와 "미습득 목록에
 * 없는 습득 단서"를 순서대로 짝지어 채운다.
 */
function buildClueSlots(
  currentDay: number,
  unacquired: UnacquiredClue[],
  acquired: ClueCard[],
  nightLocations: Map<number, string | null>,
): ClueSlot[] {
  const unacquiredByDay = new Map(unacquired.map((clue) => [clue.day, clue]))
  const pastDays = Array.from({ length: TOTAL_CLUES }, (_, i) => i + 1).filter((day) => day < currentDay)
  const acquiredDays = pastDays.filter((day) => !unacquiredByDay.has(day))
  const sortedAcquired = [...acquired].sort((a, b) => a.clueId - b.clueId)
  const dayToClue = new Map<number, ClueCard>()
  acquiredDays.forEach((day, index) => {
    const clue = sortedAcquired[index]
    if (clue) dayToClue.set(day, clue)
  })

  return Array.from({ length: TOTAL_CLUES }, (_, i) => {
    const day = i + 1
    if (day >= currentDay) return { day, status: 'locked' as const }
    const pending = unacquiredByDay.get(day)
    if (pending) return { day, status: 'pending' as const, location: pending.location }
    return { day, status: 'acquired' as const, location: nightLocations.get(day) ?? undefined, clue: dayToClue.get(day) }
  })
}

interface AffinityEntry {
  npcId: number
  name: string
  role: string
  age: number
  score: number
}

interface InventoryHubPanelProps {
  sessionId: number
  currentDay: number
  onStaminaChange: (value: number) => void
  onClose: () => void
}

export function InventoryHubPanel({ sessionId, currentDay, onStaminaChange, onClose }: InventoryHubPanelProps) {
  const [mainTab, setMainTab] = useState<MainTab>('items')

  const [slots, setSlots] = useState<InventorySlot[] | null>(null)
  const [npcs, setNpcs] = useState<NpcSummary[] | null>(null)
  const [pickingTargetFor, setPickingTargetFor] = useState<number | null>(null)
  const [invError, setInvError] = useState<string | null>(null)
  const [busySlot, setBusySlot] = useState<number | null>(null)
  const [invMessage, setInvMessage] = useState<string | null>(null)

  const [unacquired, setUnacquired] = useState<UnacquiredClue[] | null>(null)
  const [acquired, setAcquired] = useState<ClueCard[] | null>(null)
  const [nightLocations, setNightLocations] = useState<Map<number, string | null>>(new Map())
  const [clueError, setClueError] = useState<string | null>(null)
  const [busyClueKey, setBusyClueKey] = useState<string | null>(null)
  const [selectedSlot, setSelectedSlot] = useState<ClueSlot | null>(null)

  const [affinityList, setAffinityList] = useState<AffinityEntry[] | null>(null)
  const [affinityError, setAffinityError] = useState<string | null>(null)

  function reloadInventory() {
    listInventory(sessionId)
      .then(setSlots)
      .catch((err: unknown) => setInvError(err instanceof Error ? err.message : '인벤토리를 불러오지 못했습니다.'))
  }

  function loadUnacquired() {
    listUnacquiredClues(sessionId)
      .then(setUnacquired)
      .catch((err: unknown) => setClueError(err instanceof Error ? err.message : '미습득 단서를 불러오지 못했습니다.'))
  }

  function loadAcquired() {
    listAcquiredClues(sessionId)
      .then(setAcquired)
      .catch((err: unknown) => setClueError(err instanceof Error ? err.message : '습득한 단서를 불러오지 못했습니다.'))
  }

  function loadAffinity() {
    return listNpcsToday(sessionId)
      .then((npcs) => Promise.all(npcs.map((npc) => getNpcDetail(sessionId, npc.npcId))))
      .then((details) => {
        setAffinityList(
          details.map((d) => ({ npcId: d.npcId, name: d.name, role: d.role, age: d.age, score: d.affinityScore })),
        )
        setAffinityError(null)
      })
      .catch((err: unknown) => setAffinityError(err instanceof Error ? err.message : '호감도 정보를 불러오지 못했습니다.'))
  }

  useEffect(reloadInventory, [sessionId])
  useEffect(loadUnacquired, [sessionId])
  // 상단 탭에 습득 개수(n/5)를 보여줘야 해서, 단서 탭을 열기 전에도 미리 불러온다.
  useEffect(loadAcquired, [sessionId])

  // 습득한 단서 카드에는 발생 일차가 안 내려와서(day 없음), 지난 밤 소식 API로 일차별
  // 장소를 따로 모아 슬롯 표시(장소 이름 텍스트)에 사용한다 — 습득 여부와 무관하게 조회 가능.
  useEffect(() => {
    let cancelled = false
    const pastDays = Array.from({ length: TOTAL_CLUES }, (_, i) => i + 1).filter((day) => day < currentDay)
    Promise.all(
      pastDays.map((day) =>
        getNightSummary(sessionId, day)
          .then((summary) => [day, summary?.location ?? null] as const)
          .catch(() => [day, null] as const),
      ),
    ).then((entries) => {
      if (!cancelled) setNightLocations(new Map(entries))
    })
    return () => {
      cancelled = true
    }
  }, [sessionId, currentDay])

  useEffect(() => {
    if (mainTab === 'affinity' && affinityList === null) loadAffinity()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mainTab])

  async function handleUse(slot: InventorySlot, targetNpcId?: number) {
    setInvMessage(null)
    setInvError(null)
    setBusySlot(slot.slotIndex)
    setPickingTargetFor(null)
    try {
      const result = await applyInventoryItem(sessionId, slot.slotIndex, targetNpcId)
      setInvMessage(result)
      if (slot.itemType === 'CROP' || slot.itemType === 'FRUIT') {
        // 서버가 회복량을 텍스트로만 알려줘서, 별도 조회 없이 결과 문구에서 숫자만 뽑아 반영한다.
        const restored = Number(result.match(/체력 (\d+) 회복/)?.[1])
        if (!Number.isNaN(restored)) onStaminaChange(restored)
      }
      reloadInventory()
    } catch (err) {
      setInvError(err instanceof Error ? err.message : '아이템을 사용하지 못했습니다.')
    } finally {
      setBusySlot(null)
    }
  }

  function handleUseClick(slot: InventorySlot) {
    if (NEEDS_TARGET_NPC.has(slot.itemName)) {
      setInvMessage(null)
      setInvError(null)
      setPickingTargetFor(slot.slotIndex)
      if (npcs === null) {
        listNpcsToday(sessionId)
          .then(setNpcs)
          .catch((err: unknown) => setInvError(err instanceof Error ? err.message : 'NPC 목록을 불러오지 못했습니다.'))
      }
      return
    }
    handleUse(slot)
  }

  async function handleClarify(clue: ClueCard) {
    setClueError(null)
    setBusyClueKey(`clarify-${clue.clueId}`)
    try {
      const updated = await clarifyClue(sessionId, clue.clueId)
      setAcquired((prev) => prev?.map((c) => (c.clueId === updated.clueId ? updated : c)) ?? prev)
      setSelectedSlot((prev) => (prev?.clue?.clueId === updated.clueId ? { ...prev, clue: updated } : prev))
    } catch (err) {
      setClueError(err instanceof Error ? err.message : '단서를 명확화하지 못했습니다.')
    } finally {
      setBusyClueKey(null)
    }
  }

  const acquiredCount = acquired?.length ?? 0
  const clueSlots =
    unacquired !== null && acquired !== null ? buildClueSlots(currentDay, unacquired, acquired, nightLocations) : null

  return (
    <div className="inventory-hub-overlay">
      <div className="inventory-hub-panel pixel-panel">
        <div className="inventory-hub-tabs">
          <button
            className={`pixel-button ${mainTab === 'items' ? 'pixel-button--accent' : ''}`}
            onClick={() => setMainTab('items')}
          >
            아이템
          </button>
          <button
            className={`pixel-button ${mainTab === 'clues' ? 'pixel-button--accent' : ''}`}
            onClick={() => setMainTab('clues')}
          >
            단서 카드
          </button>
          <button
            className={`pixel-button ${mainTab === 'affinity' ? 'pixel-button--accent' : ''}`}
            onClick={() => setMainTab('affinity')}
          >
            호감도
          </button>
        </div>

        {mainTab === 'items' && (
          <div className="inventory-hub-section">
            {invError && <p className="pixel-error">{invError}</p>}
            {invMessage && <p className="inventory-message">{invMessage}</p>}

            <div className="inventory-grid">
              {slots === null ? (
                <p className="inventory-status">불러오는 중...</p>
              ) : slots.length === 0 ? (
                <p className="inventory-status">가진 아이템이 없습니다.</p>
              ) : (
                slots.map((slot) => (
                  <div key={slot.slotIndex} className="inventory-slot">
                    <div className="inventory-slot-name">{slot.itemName}</div>
                    <div className="inventory-slot-qty">x{slot.quantity}</div>

                    {pickingTargetFor === slot.slotIndex ? (
                      <div className="inventory-target-picker">
                        {npcs === null ? (
                          <p className="inventory-status">NPC 목록 불러오는 중...</p>
                        ) : (
                          npcs.map((npc) => (
                            <button
                              key={npc.npcId}
                              className="pixel-button inventory-target-btn"
                              disabled={busySlot === slot.slotIndex}
                              onClick={() => handleUse(slot, npc.npcId)}
                            >
                              {npc.name}
                            </button>
                          ))
                        )}
                        <button className="pixel-button" onClick={() => setPickingTargetFor(null)}>
                          취소
                        </button>
                      </div>
                    ) : (
                      <button
                        className="pixel-button pixel-button--accent"
                        disabled={busySlot === slot.slotIndex}
                        onClick={() => handleUseClick(slot)}
                      >
                        사용
                      </button>
                    )}
                  </div>
                ))
              )}
            </div>
          </div>
        )}

        {mainTab === 'clues' && (
          <div className="inventory-hub-section">
            <div className="clue-progress">
              단서 카드 {acquiredCount}/{TOTAL_CLUES}
            </div>

            {clueError && <p className="pixel-error">{clueError}</p>}
            <p className="clue-hint">사보타주가 발생한 장소를 직접 찾아가야 단서를 습득할 수 있습니다.</p>

            <div className="clue-slot-grid">
              {clueSlots === null ? (
                <p className="clue-status">불러오는 중...</p>
              ) : (
                clueSlots.map((slot) => (
                  <button
                    key={slot.day}
                    type="button"
                    className={`clue-slot clue-slot--${slot.status}`}
                    disabled={slot.status !== 'acquired'}
                    onClick={() => setSelectedSlot(slot)}
                  >
                    <div className="clue-slot-day">{slot.day}일차</div>
                    {slot.status === 'locked' && <div className="clue-slot-icon clue-slot-icon--locked">🔒</div>}
                    {slot.status === 'pending' && <div className="clue-slot-icon clue-slot-icon--pending">?</div>}
                    {slot.status === 'acquired' && (
                      <div className="clue-slot-label">{slot.location ?? '단서 확보'}</div>
                    )}
                  </button>
                ))
              )}
            </div>
          </div>
        )}

        {mainTab === 'affinity' && (
          <div className="inventory-hub-section">
            {affinityError && <p className="pixel-error">{affinityError}</p>}

            <div className="affinity-list">
              {affinityList === null ? (
                <p className="inventory-status">불러오는 중...</p>
              ) : affinityList.length === 0 ? (
                <p className="inventory-status">마을 주민이 없습니다.</p>
              ) : (
                affinityList.map((npc) => (
                  <div key={npc.npcId} className="affinity-row">
                    <div className="affinity-info">
                      <div className="affinity-name">{npc.name}</div>
                      <div className="affinity-role">
                        {npc.role} · {npc.age}세
                      </div>
                    </div>
                    <div className="affinity-meter">
                      <HeartMeter score={npc.score} />
                      <span className="affinity-score">{npc.score}/100</span>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        )}

        <button className="pixel-button inventory-hub-close" onClick={onClose}>
          닫기
        </button>
      </div>

      {selectedSlot && selectedSlot.clue && (
        <Modal
          title={`${selectedSlot.day}일차 단서${selectedSlot.location ? ` · ${selectedSlot.location}` : ''}`}
          actions={
            <>
              <button
                className="pixel-button"
                disabled={selectedSlot.clue.clarified || busyClueKey === `clarify-${selectedSlot.clue.clueId}`}
                onClick={() => selectedSlot.clue && handleClarify(selectedSlot.clue)}
              >
                {selectedSlot.clue.clarified ? '명확화됨' : '돋보기로 명확화'}
              </button>
              <button className="pixel-button pixel-button--accent" onClick={() => setSelectedSlot(null)}>
                닫기
              </button>
            </>
          }
        >
          <img
            className={`clue-detail-image${CLUE_TOPIC_ILLUSTRATION[selectedSlot.clue.topic] ? '' : ' clue-detail-image--blank'}`}
            src={CLUE_TOPIC_ILLUSTRATION[selectedSlot.clue.topic] ?? CLUE_CARD_BLANK_FRAME}
            alt={topicLabel(selectedSlot.clue.topic)}
          />
          <p className="clue-card-text">
            {selectedSlot.clue.clarified
              ? selectedSlot.clue.text
              : `${topicLabel(selectedSlot.clue.topic)}이 발견됐다.`}
          </p>
        </Modal>
      )}
    </div>
  )
}
