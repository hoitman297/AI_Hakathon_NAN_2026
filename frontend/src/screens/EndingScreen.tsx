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

/**
 * 범인 지목 이후 마지막에 보여주는 화면 — 작은 카드 팝업이 아니라 화면 전체를 쓰는 컷씬으로
 * 구성한다. 주의: story-cuts 이미지들은 전부 하단에 자체 캡션이 이미 그림으로 박혀 있어서
 * (오프닝 ArrivalScreen 때와 같은 문제), 이미지를 배경으로 깔고 그 위에 텍스트를 얹으면
 * 자체 캡션과 겹쳐서 읽을 수 없게 된다 — 그래서 이미지는 위쪽 영역에 전체(object-fit:
 * contain, 잘리지 않게)로 보여주고, 우리가 표시할 자막(범인 이름/사건 전문)은 그 아래
 * 별도의 자막 바 영역에 둬서 절대 겹치지 않게 분리한다. 성공 엔딩은 2단계(범인 동기 공개 →
 * 마을 평화 에필로그)로 이어지고, "다음" 버튼을 눌러야 마지막 화면(에필로그)으로 넘어간다.
 */
export function EndingScreen({ sessionId, onBackToTitle }: EndingScreenProps) {
  const [ending, setEnding] = useState<EndingResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [showEpilogue, setShowEpilogue] = useState(false)

  useEffect(() => {
    getEnding(sessionId)
      .then(setEnding)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : '엔딩 정보를 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }, [sessionId])

  if (loading) {
    return (
      <div className="ending-screen ending-screen--loading">
        <p className="ending-note">엔딩을 준비하는 중...</p>
      </div>
    )
  }

  if (error || !ending) {
    return (
      <div className="ending-screen ending-screen--loading">
        {error && <p className="pixel-error">{error}</p>}
        <button className="pixel-button pixel-button--accent" onClick={onBackToTitle}>
          타이틀로 돌아가기
        </button>
      </div>
    )
  }

  const isSuccess = ending.status === 'SUCCESS'
  const isEpilogue = isSuccess && showEpilogue

  const cutImage = !isSuccess
    ? BAD_ENDING_IMAGE
    : isEpilogue
      ? HAPPY_ENDING_IMAGE
      : (ending.culpritNpcId && MOTIVE_REVEAL_IMAGE_BY_NPC_ID[ending.culpritNpcId]) || HAPPY_ENDING_IMAGE

  return (
    <div className="ending-cinematic">
      <div className="ending-cinematic-badge">{isSuccess ? '성공' : '배드엔딩'}</div>

      <div className="ending-cinematic-stage">
        <img className="ending-cinematic-image" src={cutImage} alt={isSuccess ? (ending.culpritName ?? '엔딩') : '마을에서 쫓겨났습니다'} />
      </div>

      <div className="ending-cinematic-subtitle">
        {!isEpilogue && isSuccess && ending.culpritName && (
          <p className="ending-cinematic-heading">범인은 {withWasCopula(ending.culpritName)}</p>
        )}
        {!isEpilogue && <p className="ending-cinematic-body">{ending.endingStory}</p>}
        <div className="ending-cinematic-actions">
          {isSuccess && !isEpilogue ? (
            <button className="pixel-button pixel-button--accent" onClick={() => setShowEpilogue(true)}>
              다음
            </button>
          ) : (
            <button className="pixel-button pixel-button--accent" onClick={onBackToTitle}>
              타이틀로 돌아가기
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
