import { useEffect, useRef } from 'react'
import Phaser from 'phaser'
import { MainScene } from '../game/MainScene'
import './TitleScreen.css'

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
  const containerRef = useRef<HTMLDivElement>(null)
  const gameRef = useRef<Phaser.Game | null>(null)

  // 로그인 없이도 걸어다닐 수 있는 마을 맵을 타이틀 화면 배경으로 그대로 띄운다
  // (initData 없이 시작하므로 MainScene은 자유 이동만 되는 미리보기 모드로 동작한다).
  useEffect(() => {
    if (!containerRef.current || gameRef.current) return

    gameRef.current = new Phaser.Game({
      type: Phaser.AUTO,
      parent: containerRef.current,
      width: 1280,
      height: 720,
      backgroundColor: '#17201a',
      pixelArt: true,
      roundPixels: true,
      scale: {
        mode: Phaser.Scale.RESIZE,
        autoCenter: Phaser.Scale.CENTER_BOTH,
      },
      render: {
        antialias: false,
        pixelArt: true,
        roundPixels: true,
      },
      scene: [MainScene],
    })

    return () => {
      gameRef.current?.destroy(true)
      gameRef.current = null
    }
  }, [])

  return (
    <div className="title-screen">
      <div ref={containerRef} className="title-canvas" />
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

      <p className="title-help">WASD / 방향키로 마을을 둘러볼 수 있어요</p>
    </div>
  )
}
