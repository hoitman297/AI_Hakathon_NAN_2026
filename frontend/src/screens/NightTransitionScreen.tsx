import { useEffect, useState } from 'react'
import { getNightSummary, type NightSummary } from '../api/sabotageApi'
import './NightTransitionScreen.css'

interface NightTransitionScreenProps {
  sessionId: number
  day: number
  nextDay: number
  onContinue: () => void
}

export function NightTransitionScreen({ sessionId, day, nextDay, onContinue }: NightTransitionScreenProps) {
  const [summary, setSummary] = useState<NightSummary | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    getNightSummary(sessionId, day)
      .then(setSummary)
      .catch(() => setSummary(null))
      .finally(() => setLoading(false))
  }, [sessionId, day])

  return (
    <div className="night-screen">
      <div className="night-panel pixel-panel">
        <div className="night-day">{day}일차 밤</div>
        <p className="night-text">
          마을이 잠든 사이, 어둠 속에서 누군가 움직였다...
          <br />
          다음 날 아침, 마을 어딘가에서 사보타주의 흔적이 발견되었다.
        </p>

        {loading && <p className="night-note">지난밤 소식을 불러오는 중...</p>}

        {!loading && summary && (
          <div className="night-summary">
            <div className="night-summary-location">발생 장소: {summary.location}</div>
            {summary.summaryText && <p className="night-summary-text">{summary.summaryText}</p>}
          </div>
        )}

        {!loading && !summary && (
          <p className="night-note">(사보타주 상세 내용은 추후 단서 카드로 확인할 수 있습니다.)</p>
        )}

        <button className="pixel-button pixel-button--accent" onClick={onContinue}>
          {nextDay}일차 아침으로
        </button>
      </div>
    </div>
  )
}
