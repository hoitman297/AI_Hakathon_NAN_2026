import './ArrivalScreen.css'

const ARRIVAL_IMAGE = '/assets/ui/openings/opening-arrival-v3.png'

interface ArrivalScreenProps {
  nickname: string
  onContinue: () => void
}

/**
 * 새 게임을 시작하면 캐릭터 생성 직후, 1일차 진입 전에 딱 한 번 보여주는 오프닝 컷씬.
 * opening-arrival-v3.png 에셋은 이 화면 전용으로 추가됐지만 어느 커밋에서도 실제로
 * 연결된 적이 없어서(타이틀 로고/보드만 연결됨) 새로 만들었다.
 */
export function ArrivalScreen({ nickname, onContinue }: ArrivalScreenProps) {
  return (
    <div className="arrival-screen" style={{ backgroundImage: `url('${ARRIVAL_IMAGE}')` }}>
      <div className="arrival-vignette" />
      <div className="arrival-content">
        <p className="arrival-text">
          {nickname}은(는) 오랜만에 고향 마을 어귀에 들어섰다.
          <br />
          평화로워 보이는 풍경이지만, 어딘가 미묘하게 뒤숭숭한 공기가 감돈다.
        </p>
        <button className="pixel-button pixel-button--accent arrival-continue" onClick={onContinue}>
          마을로 들어가기
        </button>
      </div>
    </div>
  )
}
