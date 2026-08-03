import { villageAssets } from '../assets/asset-manifest'
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
  return (
    <div className="title-screen" style={{ backgroundImage: `url(${villageAssets.concept})` }}>
      <div className="title-overlay" />
      <div className="title-content">
        <h1 className="title-logo">마을 사보타주 추리 게임</h1>
        <p className="title-tagline">누군가 마을을 망치고 있다. 낮엔 이야기를 듣고, 밤엔 사건이 벌어진다.</p>

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
