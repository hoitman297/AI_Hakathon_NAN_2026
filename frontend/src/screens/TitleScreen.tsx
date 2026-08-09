import { useEffect, useRef } from 'react'
import './TitleScreen.css'

const BGM_SRC = '/assets/audio/bgm-samples/01-morning-fields.wav'

interface TitleScreenProps {
  isLoggedIn: boolean
  nickname: string | null
  onLoginClick: () => void
  onLogoutClick: () => void
  onStartNewGame: () => void
  onContinue: () => void
  onVillagePreview: () => void
}

export function TitleScreen({
  isLoggedIn,
  nickname,
  onLoginClick,
  onLogoutClick,
  onStartNewGame,
  onContinue,
  onVillagePreview,
}: TitleScreenProps) {
  const audioRef = useRef<HTMLAudioElement>(null)

  useEffect(() => {
    const audio = audioRef.current
    if (!audio) return

    const play = () => {
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
    <main className="title-screen">
      <audio ref={audioRef} src={BGM_SRC} loop />
      <div className="title-overlay" />
      <section className="title-content" aria-label="게임 시작 메뉴">
        <img className="title-brand" src="/assets/ui/title/title-logo-v1.png" alt="그날, 마을에서" />
        <p className="title-tagline">평화롭던 마을에 사보타주가 시작됐다.<br />정해진 기회 안에 범인을 찾아내세요.</p>

        <div className="title-buttons">
          {isLoggedIn ? (
            <>
              <button className="pixel-button pixel-button--accent" onClick={onStartNewGame}>게임시작</button>
              <button className="pixel-button" onClick={onContinue}>이어서하기</button>
            </>
          ) : (
            <>
              <button className="pixel-button pixel-button--accent" disabled title="로그인 후 이용할 수 있습니다.">게임시작</button>
              <button className="pixel-button" onClick={onLoginClick}>로그인</button>
            </>
          )}
        </div>

        <button className="pixel-button title-preview-button" type="button" onClick={onVillagePreview}>
          로그인 없이 마을 화면 보기
        </button>

        {isLoggedIn && (
          <div className="title-account">
            {nickname}님 접속 중 · <button className="title-logout" onClick={onLogoutClick}>로그아웃</button>
          </div>
        )}
      </section>
    </main>
  )
}
