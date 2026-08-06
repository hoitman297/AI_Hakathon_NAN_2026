import { villageAssets } from '../assets/asset-manifest'
import './StaminaBar.css'

interface StaminaBarProps {
  value: number
  max: number
}

export function StaminaBar({ value, max }: StaminaBarProps) {
  const ratio = Math.max(0, Math.min(1, max > 0 ? value / max : 0))

  return (
    <div className="stamina-hud stamina-hud--side">
      <div className="stamina-bar-wrap">
        <div className="stamina-bar">
          <div className="stamina-fill-clip" style={{ width: `${ratio * 100}%` }}>
            <img className="stamina-fill pixel-art" src={villageAssets.ui.stamina.fill} alt="" />
          </div>
          <img className="stamina-frame pixel-art" src={villageAssets.ui.stamina.frame} alt="" />
        </div>
      </div>
      <div className="stamina-value">
        {Math.round(value)} / {max}
      </div>
    </div>
  )
}
