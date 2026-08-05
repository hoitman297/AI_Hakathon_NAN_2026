import { useEffect, useState } from 'react'
import { listInventory, applyInventoryItem, type InventorySlot } from '../api/inventoryApi'
import { listNpcsToday, type NpcSummary } from '../api/npcApi'
import './InventoryPanel.css'

// 이 두 아이템은 사용 시 대상 NPC를 지정해야 한다(백엔드 UseItemRequest.targetNpcId).
// ShopItemResponse에 itemCode가 안 내려오므로 표시 이름으로 판별한다.
const NEEDS_TARGET_NPC = new Set(['거짓말탐지기', '선물세트'])

interface InventoryPanelProps {
  sessionId: number
  onStaminaChange: (value: number) => void
  onClose: () => void
}

export function InventoryPanel({ sessionId, onStaminaChange, onClose }: InventoryPanelProps) {
  const [slots, setSlots] = useState<InventorySlot[] | null>(null)
  const [npcs, setNpcs] = useState<NpcSummary[] | null>(null)
  const [pickingTargetFor, setPickingTargetFor] = useState<number | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [busySlot, setBusySlot] = useState<number | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  function reload() {
    listInventory(sessionId)
      .then(setSlots)
      .catch((err: unknown) => setLoadError(err instanceof Error ? err.message : '인벤토리를 불러오지 못했습니다.'))
  }

  useEffect(reload, [sessionId])

  async function handleUse(slot: InventorySlot, targetNpcId?: number) {
    setMessage(null)
    setLoadError(null)
    setBusySlot(slot.slotIndex)
    setPickingTargetFor(null)
    try {
      const result = await applyInventoryItem(sessionId, slot.slotIndex, targetNpcId)
      setMessage(result)
      if (slot.itemType === 'CROP' || slot.itemType === 'FRUIT') {
        // 서버가 회복량을 텍스트로만 알려줘서, 별도 조회 없이 결과 문구에서 숫자만 뽑아 반영한다.
        const restored = Number(result.match(/체력 (\d+) 회복/)?.[1])
        if (!Number.isNaN(restored)) onStaminaChange(restored)
      }
      reload()
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : '아이템을 사용하지 못했습니다.')
    } finally {
      setBusySlot(null)
    }
  }

  function handleUseClick(slot: InventorySlot) {
    if (NEEDS_TARGET_NPC.has(slot.itemName)) {
      setMessage(null)
      setLoadError(null)
      setPickingTargetFor(slot.slotIndex)
      if (npcs === null) {
        listNpcsToday(sessionId)
          .then(setNpcs)
          .catch((err: unknown) => setLoadError(err instanceof Error ? err.message : 'NPC 목록을 불러오지 못했습니다.'))
      }
      return
    }
    handleUse(slot)
  }

  return (
    <div className="inventory-overlay">
      <div className="inventory-panel pixel-panel">
        <h2>인벤토리</h2>

        {loadError && <p className="pixel-error">{loadError}</p>}
        {message && <p className="inventory-message">{message}</p>}

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

        <button className="pixel-button inventory-close" onClick={onClose}>
          닫기
        </button>
      </div>
    </div>
  )
}
