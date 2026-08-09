import { useEffect, useState } from 'react'
import { getEnding, type EndingResult } from '../api/accusationApi'
import { withWasCopula } from '../utils/korean'
import './EndingScreen.css'

// npc_id(1~7, DataSeeder 시딩 순서: 현수동/나주부/전주인/박영계/명자유/김치준/나박수)와
// 1:1로 매칭되는 동기 공개 일러스트. story-cuts 폴더로 통합되며 캐릭터 이름 기반 파일명으로
// 바뀌었지만 01~07/09는 이전 motives/openings 폴더의 동일 파일을 이름만 바꾼 것이다.
const MOTIVE_REVEAL_IMAGE_BY_NPC_ID: Record<number, string> = {
  1: '/assets/ui/story-cuts/01-hyeon-sudong.png',
  2: '/assets/ui/story-cuts/02-na-jubu.png',
  3: '/assets/ui/story-cuts/03-jeon-juin.png',
  4: '/assets/ui/story-cuts/04-park-yeonggye.png',
  5: '/assets/ui/story-cuts/05-myeong-jayu.png',
  6: '/assets/ui/story-cuts/06-kim-chijun.png',
  7: '/assets/ui/story-cuts/07-na-baksu.png',
}

// 배드엔딩/해피엔딩 전용 컷씬. 둘 다 이미지 안에 자체 캡션이 들어 있는 완결된 연출용 일러스트다.
const BAD_ENDING_IMAGE = '/assets/ui/story-cuts/08-bad-ending-exile.png'
const HAPPY_ENDING_IMAGE = '/assets/ui/story-cuts/10-happy-ending-peace.png'

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
            {!isSuccess && (
              <img className="ending-motive-image pixel-art" src={BAD_ENDING_IMAGE} alt="마을에서 쫓겨났습니다" />
            )}
            {isSuccess && ending.culpritName && <h2>범인은 {withWasCopula(ending.culpritName)}</h2>}
            <p className="ending-story">{ending.endingStory}</p>
            {isSuccess && (
              <img className="ending-peace-image pixel-art" src={HAPPY_ENDING_IMAGE} alt="마을은 다시 평화를 되찾았습니다" />
            )}
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
