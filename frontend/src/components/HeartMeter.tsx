import './HeartMeter.css'

const TOTAL_HEARTS = 10
const POINTS_PER_HEART = 10

interface HeartMeterProps {
  score: number
}

export function HeartMeter({ score }: HeartMeterProps) {
  const clampedScore = Math.max(0, Math.min(100, score))

  return (
    <div className="heart-meter" title={`호감도 ${score}/100`}>
      {Array.from({ length: TOTAL_HEARTS }, (_, index) => {
        // 하트 하나가 담당하는 구간은 [index*10, index*10+10) 점 — 그 구간 안에서
        // 정확히 몇 %가 채워졌는지(0~100%)를 계산해서 반올림 없이 그대로 반영한다.
        const fillRatio = Math.max(0, Math.min(1, (clampedScore - index * POINTS_PER_HEART) / POINTS_PER_HEART))
        return (
          <span key={index} className="heart">
            <span className="heart-icon heart-icon--empty">♡</span>
            <span className="heart-icon heart-icon--filled" style={{ width: `${fillRatio * 100}%` }}>
              ♥
            </span>
          </span>
        )
      })}
    </div>
  )
}
