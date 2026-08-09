import { useEffect, useState } from 'react'
import { listShopItems, purchaseShopItem, type ShopItem } from '../api/shopApi'
import './ShopPanel.css'

interface ItemShopPanelProps {
  sessionId: number
  gold: number
  onGoldChange: (gold: number) => void
  onClose: () => void
}

export function ItemShopPanel({ sessionId, gold, onGoldChange, onClose }: ItemShopPanelProps) {
  const [items, setItems] = useState<ShopItem[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [busyKey, setBusyKey] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  useEffect(() => {
    listShopItems(sessionId)
      .then(setItems)
      .catch((err: unknown) => setLoadError(err instanceof Error ? err.message : '상점 목록을 불러오지 못했습니다.'))
  }, [sessionId])

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

  return (
    <div className="shop-overlay">
      <div className="shop-panel pixel-panel">
        <div className="shop-header">
          <h2>아이템 상점</h2>
          <div className="shop-gold">보유 골드: {gold}</div>
        </div>

        {loadError && <p className="pixel-error">{loadError}</p>}
        {message && <p className="shop-message">{message}</p>}

        <div className="shop-list">
          {items === null ? (
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
          )}
        </div>

        <button className="pixel-button shop-close" onClick={onClose}>
          닫기
        </button>
      </div>
    </div>
  )
}
