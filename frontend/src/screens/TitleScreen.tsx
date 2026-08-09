import { useEffect, useRef } from 'react'
import { villageAssets } from '../assets/asset-manifest'
import './TitleScreen.css'

const BGM_SRC = '/assets/audio/bgm-samples/01-morning-fields.wav'

interface TitleScreenProps {
  isLoggedIn: boolean
  nickname: string | null
  onLoginClick: () => void
  onLogoutClick: () => void
  onStartNewGame: () => void
  onContinue: () => void
}

export function TitleScreen({
  isLoggedIn,
  nickname,
  onLoginClick,
  onLogoutClick,
  onStartNewGame,
  onContinue,
}: TitleScreenProps) {
  const audioRef = useRef<HTMLAudioElement>(null)

  // 브라우저 자동재생 정책 때문에 사용자 입력 없이는 재생이 막힐 수 있다 — 마운트 시
  // 한 번 시도하고, 막히면 첫 클릭/키 입력에 이어서 재생한다(MainScene의 BGM 처리와 동일한 패턴).
  useEffect(() => {
    const audio = audioRef.current
    if (!audio) return

    const play = () => {
      // jsdom(테스트 환경)의 play()는 Promise를 반환하지 않으므로 옵셔널 체이닝으로 방어한다.
      audio.play()?.catch(() => undefined)
    }
    play()
    window.addEventListener('pointerdown', play, { once: true })
    window.addEventListener('keydown', play, { once: true })

    return () => {
      window.removeEventListener('pointerdown', play)
      window.removeEventListener('keydown', play)
    }
  }, [])

  return (
    <div className="title-screen" style={{ backgroundImage: `url(${villageAssets.concept})` }}>
      <audio ref={audioRef} src={BGM_SRC} loop />
      <div className="title-overlay" />
      <div className="title-content">
        <h1 className="title-logo">마을 사보타주 추리 게임</h1>
        <p className="title-tagline">누군가 마을을 망치고 있다. 찾아내지 않으면 내가 마을에서 쫒겨난다!</p>

        <div className="title-buttons">
          {isLoggedIn ? (
            <>
              <button className="pixel-button pixel-button--accent" onClick={onStartNewGame}>
                게임시작
              </button>
              <button className="pixel-button" onClick={onContinue}>
                이어서하기
              </button>
            </>
          ) : (
            <>
              <button className="pixel-button pixel-button--accent" disabled title="로그인 후 이용할 수 있습니다.">
                게임시작
              </button>
              <button className="pixel-button" onClick={onLoginClick}>
                로그인
              </button>
            </>
          )}
        </div>

        {isLoggedIn && (
          <div className="title-account">
            {nickname}님 접속 중 · <button className="title-logout" onClick={onLogoutClick}>로그아웃</button>
          </div>
        )}
      </div>
    </div>
  )
}
