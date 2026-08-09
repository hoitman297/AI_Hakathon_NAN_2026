import { useEffect, useState } from 'react'
import { listCrops, buySeed, type CropSummary } from '../api/farmApi'
import { sellItem } from '../api/shopApi'
import { listInventory, type InventorySlot } from '../api/inventoryApi'
import './ShopPanel.css'

interface ProduceShopPanelProps {
  sessionId: number
  gold: number
  onGoldChange: (gold: number) => void
  /** 씨앗 구매 성공 후 호출 — DayScreen이 맵 위 씨앗 심기 팝업에 쓰는 인벤토리를 새로고침한다. */
  onSeedPurchased?: () => void
  onClose: () => void
}

type Tab = 'seeds' | 'sell'

export function ProduceShopPanel({ sessionId, gold, onGoldChange, onSeedPurchased, onClose }: ProduceShopPanelProps) {
  const [tab, setTab] = useState<Tab>('seeds')
  const [crops, setCrops] = useState<CropSummary[] | null>(null)
  const [sellable, setSellable] = useState<InventorySlot[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [busyKey, setBusyKey] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  useEffect(() => {
    listCrops(sessionId)
      .then(setCrops)
      .catch((err: unknown) => setLoadError(err instanceof Error ? err.message : '작물 목록을 불러오지 못했습니다.'))
  }, [sessionId])

  function loadSellable() {
    listInventory(sessionId)
      .then((slots) => setSellable(slots.filter((s) => s.itemType === 'CROP' || s.itemType === 'FRUIT')))
      .catch((err: unknown) => setLoadError(err instanceof Error ? err.message : '보유 물품을 불러오지 못했습니다.'))
  }

  useEffect(() => {
    if (tab === 'sell' && sellable === null) loadSellable()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab])

  async function handleBuySeed(crop: CropSummary) {
    setMessage(null)
    setLoadError(null)
    setBusyKey(`seed-${crop.cropId}`)
    try {
      const updated = await buySeed(sessionId, crop.cropId)
      onGoldChange(updated.gold)
      onSeedPurchased?.()
      setMessage(`${crop.name} 씨앗을 구매했습니다. 농장 밭 타일로 가서 직접 심어보세요.`)
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : '씨앗 구매에 실패했습니다.')
    } finally {
      setBusyKey(null)
    }
  }

  async function handleSell(slot: InventorySlot) {
    setMessage(null)
    setLoadError(null)
    setBusyKey(`sell-${slot.slotIndex}`)
    try {
      const updated = await sellItem(sessionId, slot.itemType as 'CROP' | 'FRUIT', slot.itemRefId, 1)
      onGoldChange(updated.gold)
      setMessage(`${slot.itemName} 1개를 판매했습니다.`)
      loadSellable()
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : '판매에 실패했습니다.')
    } finally {
      setBusyKey(null)
    }
  }

  return (
    <div className="shop-overlay">
      <div className="shop-panel pixel-panel">
        <div className="shop-header">
          <h2>농산물 상점</h2>
          <div className="shop-gold">보유 골드: {gold}</div>
        </div>

        <div className="shop-tabs">
          <button
            className={`pixel-button ${tab === 'seeds' ? 'pixel-button--accent' : ''}`}
            onClick={() => setTab('seeds')}
          >
            씨앗 구매
          </button>
          <button
            className={`pixel-button ${tab === 'sell' ? 'pixel-button--accent' : ''}`}
            onClick={() => setTab('sell')}
          >
            판매
          </button>
        </div>

        {loadError && <p className="pixel-error">{loadError}</p>}
        {message && <p className="shop-message">{message}</p>}

        <div className="shop-list">
          {tab === 'seeds' &&
            (crops === null ? (
              <p className="shop-status">불러오는 중...</p>
            ) : (
              crops.map((crop) => (
                <div key={crop.cropId} className="shop-item-row">
                  <div className="shop-item-info">
                    <div className="shop-item-name">
                      {crop.name} <span className="shop-item-price">{crop.seedPrice} G</span>
                    </div>
                    <div className="shop-item-desc">
                      성장 {crop.growDays}일 · 파종 체력 {crop.plantOrHarvestStamina} · 수확 시 판매가 {crop.sellPrice}G
                    </div>
                  </div>
                  <button
                    className="pixel-button pixel-button--accent"
                    disabled={busyKey === `seed-${crop.cropId}` || gold < crop.seedPrice}
                    onClick={() => handleBuySeed(crop)}
                  >
                    씨앗 구매
                  </button>
                </div>
              ))
            ))}

          {tab === 'sell' &&
            (sellable === null ? (
              <p className="shop-status">불러오는 중...</p>
            ) : sellable.length === 0 ? (
              <p className="shop-status">팔 수 있는 농산물이 없습니다.</p>
            ) : (
              sellable.map((slot) => (
                <div key={slot.slotIndex} className="shop-item-row">
                  <div className="shop-item-info">
                    <div className="shop-item-name">{slot.itemName}</div>
                    <div className="shop-item-desc">보유 수량: {slot.quantity}</div>
                  </div>
                  <button
                    className="pixel-button pixel-button--accent"
                    disabled={busyKey === `sell-${slot.slotIndex}`}
                    onClick={() => handleSell(slot)}
                  >
                    1개 판매
                  </button>
                </div>
              ))
            ))}
        </div>

        <button className="pixel-button shop-close" onClick={onClose}>
          닫기
        </button>
      </div>
    </div>
  )
}
