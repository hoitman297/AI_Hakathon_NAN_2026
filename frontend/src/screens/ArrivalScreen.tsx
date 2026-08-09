import './ArrivalScreen.css'

const ARRIVAL_IMAGE = '/assets/ui/story-cuts/09-opening-arrival.png'

interface ArrivalScreenProps {
  nickname: string
  onContinue: () => void
}

/**
 * 새 게임을 시작하면 캐릭터 생성 직후, 1일차 진입 전에 딱 한 번 보여주는 오프닝 컷씬.
 * 09-opening-arrival.png는 배드/해피엔딩 컷씬과 같은 방식으로 캡션 텍스트가 이미지 안에
 * 레터박스와 함께 그려져 있는 완결된 연출 이미지라, 위에 별도 문구를 얹지 않고 원본 비율
 * 그대로(object-fit: contain) 보여준 뒤 버튼만 아래에 둔다.
 */
export function ArrivalScreen({ nickname, onContinue }: ArrivalScreenProps) {
  return (
    <div className="arrival-screen">
      <img className="arrival-image" src={ARRIVAL_IMAGE} alt={`${nickname}이(가) 마을에 도착했다`} />
      <div className="arrival-content">
        <button className="pixel-button pixel-button--accent arrival-continue" onClick={onContinue}>
          마을로 들어가기
        </button>
      </div>
    </div>
  )
}
