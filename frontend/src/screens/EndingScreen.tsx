import { useEffect, useState } from 'react'
import { getEnding, type EndingResult } from '../api/accusationApi'
import './EndingScreen.css'

interface EndingScreenProps {
  sessionId: number
  onBackToTitle: () => void
}

export function EndingScreen({ sessionId, onBackToTitle }: EndingScreenProps) {
  const [ending, setEnding] = useState<EndingResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getEnding(sessionId)
      .then(setEnding)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : '엔딩 정보를 불러오지 못했습니다.'))
  }, [sessionId])

  const isSuccess = ending?.status === 'SUCCESS'

  return (
    <div className={`ending-screen ${isSuccess ? 'ending-screen--success' : 'ending-screen--bad'}`}>
      <div className="ending-panel pixel-panel">
        <div className="ending-badge">{isSuccess ? '성공' : '배드엔딩'}</div>
        {error && <p className="pixel-error">{error}</p>}
        {ending && (
          <>
            {isSuccess && ending.culpritName && <h2>범인은 {ending.culpritName}였다</h2>}
            {!isSuccess && <h2>마을에서 쫓겨나다</h2>}
            <p className="ending-story">{ending.endingStory}</p>
          </>
        )}
        <button className="pixel-button pixel-button--accent" onClick={onBackToTitle}>
          타이틀로 돌아가기
        </button>
      </div>
    </div>
  )
}
