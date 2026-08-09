import type { InventorySlot } from '../api/inventoryApi'
import './SeedPickerPopup.css'

interface SeedPickerPopupProps {
  seeds: InventorySlot[]
  busy: boolean
  errorMessage: string | null
  onPick: (cropId: number) => void
  onClose: () => void
}

export function SeedPickerPopup({ seeds, busy, errorMessage, onPick, onClose }: SeedPickerPopupProps) {
  return (
    <div className="seed-picker-overlay">
      <div className="seed-picker-panel pixel-panel">
        <h3>심을 씨앗 선택</h3>

        {errorMessage && <p className="pixel-error">{errorMessage}</p>}

        {seeds.length === 0 ? (
          <p className="seed-picker-empty">보유한 씨앗이 없습니다. 농산물 상점에서 먼저 구매하세요.</p>
        ) : (
          <div className="seed-picker-list">
            {seeds.map((seed) => (
              <div key={seed.slotIndex} className="seed-picker-row">
                <span className="seed-picker-name">
                  {seed.itemName} <span className="seed-picker-qty">보유 {seed.quantity}</span>
                </span>
                <button
                  className="pixel-button pixel-button--accent"
                  disabled={busy}
                  onClick={() => onPick(seed.itemRefId)}
                >
                  심기
                </button>
              </div>
            ))}
          </div>
        )}

        <button className="pixel-button seed-picker-close" onClick={onClose} disabled={busy}>
          취소
        </button>
      </div>
    </div>
  )
}
