import { villageAssets } from '../assets/asset-manifest'
import './StaminaBar.css'

interface StaminaBarProps {
  value: number
  max: number
  day: number
}

export function StaminaBar({ value, max, day }: StaminaBarProps) {
  const ratio = Math.max(0, Math.min(1, max > 0 ? value / max : 0))

  return (
    <div className="stamina-hud">
      <div className="stamina-day">{day}일차</div>
      <img className="stamina-icon pixel-art" src={villageAssets.ui.stamina.sneakerIcon} alt="" />
      <div className="stamina-bar">
        <div className="stamina-fill-clip" style={{ width: `${ratio * 100}%` }}>
          <img className="stamina-fill pixel-art" src={villageAssets.ui.stamina.fill} alt="" />
        </div>
        <img className="stamina-frame pixel-art" src={villageAssets.ui.stamina.frame} alt="" />
      </div>
      <div className="stamina-value">
        {Math.round(value)} / {max}
      </div>
    </div>
  )
}
