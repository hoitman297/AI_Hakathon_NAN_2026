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
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    getEnding(sessionId)
      .then(setEnding)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : '엔딩 정보를 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }, [sessionId])

  // ending이 아직 없을 때(로딩 중/에러) isSuccess를 false로 취급하면 정답 고발 직후에도
  // 스토리가 오기 전까지 잠깐 "배드엔딩" 배지/배경이 먼저 뜨는 것처럼 보인다 — 로딩 중에는
  // 성공/실패 어느 쪽 스타일도 아닌 중립 상태로 보여준다.
  const isSuccess = ending?.status === 'SUCCESS'
  const screenVariant = loading ? 'ending-screen--loading' : isSuccess ? 'ending-screen--success' : 'ending-screen--bad'

  return (
    <div className={`ending-screen ${screenVariant}`}>
      <div className="ending-panel pixel-panel">
        {loading && <p className="ending-note">엔딩을 준비하는 중...</p>}
        {!loading && <div className="ending-badge">{isSuccess ? '성공' : '배드엔딩'}</div>}
        {error && <p className="pixel-error">{error}</p>}
        {!loading && ending && (
          <>
            {isSuccess && ending.culpritName && <h2>범인은 {ending.culpritName}였다</h2>}
            {!isSuccess && <h2>마을에서 쫓겨나다</h2>}
            <p className="ending-story">{ending.endingStory}</p>
          </>
        )}
        {!loading && (
          <button className="pixel-button pixel-button--accent" onClick={onBackToTitle}>
            타이틀로 돌아가기
          </button>
        )}
      </div>
    </div>
  )
}
