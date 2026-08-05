import { useEffect, useState } from 'react'
import { listShopItems, purchaseShopItem, sellItem, type ShopItem } from '../api/shopApi'
import { listInventory, type InventorySlot } from '../api/inventoryApi'
import './ShopPanel.css'

interface ShopPanelProps {
  sessionId: number
  gold: number
  onGoldChange: (gold: number) => void
  onClose: () => void
}

type Tab = 'buy' | 'sell'

export function ShopPanel({ sessionId, gold, onGoldChange, onClose }: ShopPanelProps) {
  const [tab, setTab] = useState<Tab>('buy')
  const [items, setItems] = useState<ShopItem[] | null>(null)
  const [sellable, setSellable] = useState<InventorySlot[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [busyKey, setBusyKey] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  useEffect(() => {
    listShopItems(sessionId)
      .then(setItems)
      .catch((err: unknown) => setLoadError(err instanceof Error ? err.message : '상점 목록을 불러오지 못했습니다.'))
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

  async function handleBuy(item: ShopItem) {
    setMessage(null)
    setLoadError(null)
    setBusyKey(`buy-${item.itemId}`)
    try {
      const updated = await purchaseShopItem(sessionId, item.itemId)
      onGoldChange(updated.gold)
      setMessage(`${item.name}을(를) 구매했습니다.`)
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : '구매에 실패했습니다.')
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
          <h2>상점</h2>
          <div className="shop-gold">보유 골드: {gold}</div>
        </div>

        <div className="shop-tabs">
          <button
            className={`pixel-button ${tab === 'buy' ? 'pixel-button--accent' : ''}`}
            onClick={() => setTab('buy')}
          >
            구매
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
          {tab === 'buy' &&
            (items === null ? (
              <p className="shop-status">불러오는 중...</p>
            ) : (
              items.map((item) => (
                <div key={item.itemId} className="shop-item-row">
                  <div className="shop-item-info">
                    <div className="shop-item-name">
                      {item.name} <span className="shop-item-price">{item.price} G</span>
                    </div>
                    <div className="shop-item-desc">{item.effectDesc}</div>
                    <div className="shop-item-limit">{item.usageLimit}</div>
                  </div>
                  <button
                    className="pixel-button pixel-button--accent"
                    disabled={busyKey === `buy-${item.itemId}` || gold < item.price}
                    onClick={() => handleBuy(item)}
                  >
                    구매
                  </button>
                </div>
              ))
            ))}

          {tab === 'sell' &&
            (sellable === null ? (
              <p className="shop-status">불러오는 중...</p>
            ) : sellable.length === 0 ? (
              <p className="shop-status">팔 수 있는 작물/과일이 없습니다.</p>
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
