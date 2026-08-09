import { useEffect, useState } from 'react'
import { getEnding, type EndingResult } from '../api/accusationApi'
import './EndingScreen.css'

// npc_id(1~7, DataSeeder 시딩 순서: 현수동/나주부/전주인/박영계/명자유/김치준/나박수)와
// 1:1로 매칭되는 동기 공개 일러스트. CulpritProfileRegistry.PROFILES의 motiveText와
// 파일명 접미사(development-resentment 등)가 각 NPC별로 대응된다.
const MOTIVE_REVEAL_IMAGE_BY_NPC_ID: Record<number, string> = {
  1: '/assets/ui/motives/npc-01-development-resentment-v2.png',
  2: '/assets/ui/motives/npc-02-jealousy-exclusion-v2.png',
  3: '/assets/ui/motives/npc-03-market-competition-v2.png',
  4: '/assets/ui/motives/npc-04-rumor-curiosity-v2.png',
  5: '/assets/ui/motives/npc-05-unstable-income-v2.png',
  6: '/assets/ui/motives/npc-06-rivalry-job-stress-v2.png',
  7: '/assets/ui/motives/npc-07-temper-resentment-v2.png',
}

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
            {isSuccess && ending.culpritNpcId && MOTIVE_REVEAL_IMAGE_BY_NPC_ID[ending.culpritNpcId] && (
              <img
                className="ending-motive-image pixel-art"
                src={MOTIVE_REVEAL_IMAGE_BY_NPC_ID[ending.culpritNpcId]}
                alt={`${ending.culpritName ?? '범인'}의 동기`}
              />
            )}
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
