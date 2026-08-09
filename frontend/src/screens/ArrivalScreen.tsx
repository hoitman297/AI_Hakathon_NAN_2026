import './ArrivalScreen.css'

const ARRIVAL_IMAGE = '/assets/ui/story-cuts/09-opening-arrival.png'

interface ArrivalScreenProps {
  nickname: string
  onContinue: () => void
}

/**
 * 새 게임을 시작하면 캐릭터 생성 직후, 1일차 진입 전에 딱 한 번 보여주는 오프닝 컷씬.
 * 캡션은 이미지 안에 래스터로 그려져 있었으나(저해상도 확대 시 글씨가 뭉개지는 문제가 있어)
 * 이미지에서는 지우고, 같은 레터박스 자리에 실제 DOM 텍스트로 얹어 항상 선명하게 표시한다.
 */
export function ArrivalScreen({ nickname, onContinue }: ArrivalScreenProps) {
  return (
    <div className="arrival-screen">
      <div className="arrival-image-wrap">
        <img className="arrival-image" src={ARRIVAL_IMAGE} alt={`${nickname}이(가) 마을에 도착했다`} />
        <div className="arrival-caption">
          <p className="arrival-caption-line1">나는 얼마 전, 이 마을로 이사를 왔다.</p>
          <p className="arrival-caption-line2">마을 사람들과 친해지면서, 이곳 생활에 빨리 적응해야지.</p>
        </div>
      </div>
      <div className="arrival-content">
        <button className="pixel-button pixel-button--accent arrival-continue" onClick={onContinue}>
          마을로 들어가기
        </button>
      </div>
    </div>
  )
}
