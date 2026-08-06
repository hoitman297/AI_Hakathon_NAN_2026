import './HeartMeter.css'

const TOTAL_HEARTS = 10
const POINTS_PER_HEART = 10

interface HeartMeterProps {
  score: number
}

export function HeartMeter({ score }: HeartMeterProps) {
  const filled = Math.max(0, Math.min(TOTAL_HEARTS, Math.round(score / POINTS_PER_HEART)))

  return (
    <div className="heart-meter" title={`호감도 ${score}/100`}>
      {Array.from({ length: TOTAL_HEARTS }, (_, index) => (
        <span key={index} className={`heart ${index < filled ? 'heart--filled' : 'heart--empty'}`}>
          {index < filled ? '♥' : '♡'}
        </span>
      ))}
    </div>
  )
}
