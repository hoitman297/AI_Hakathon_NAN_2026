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

        {loading && <p className="night-note">지난밤 소식을 불러오는 중...</p>}

        {!loading && summary && (
          <p className="night-text">
            마을 주민들이 잠든 사이, <strong>{summary.location}</strong>에서 이상한 일이 벌어진 것 같다.
          </p>
        )}

        {!loading && !summary && <p className="night-text">마을은 오늘 밤도 평온했던 것 같다.</p>}

        <button
          className="pixel-button pixel-button--accent"
          onClick={onContinue}
          disabled={loading}
          title={loading ? '지난밤 소식을 확인하는 중입니다.' : undefined}
        >
          {nextDay}일차 아침으로
        </button>
      </div>
    </div>
  )
}
