import { useEffect, useRef, useState } from 'react'
import { TitleScreen } from './screens/TitleScreen'
import { LoginModal } from './screens/LoginModal'
import { CharacterCreateScreen } from './screens/CharacterCreateScreen'
import { ArrivalScreen } from './screens/ArrivalScreen'
import { SaveSlotPicker } from './screens/SaveSlotPicker'
import { DayScreen } from './screens/DayScreen'
import { NightTransitionScreen } from './screens/NightTransitionScreen'
import { AccusationScreen } from './screens/AccusationScreen'
import { EndingScreen } from './screens/EndingScreen'
import { fetchMe, logout as logoutRequest, type AccountInfo, type AuthResult } from './api/authApi'
import { createSession, advanceDay, type SessionResponse } from './api/sessionApi'
import type { AccuseResult } from './api/accusationApi'
import { getAuthToken, clearAuthToken } from './api/authToken'
import { createDayChangeAutoSave } from './game/autoSave'
import { getPlayerProfile, setPlayerProfile, type CharacterGender } from './state/playerProfile'

type Screen = 'title' | 'character-create' | 'arrival' | 'save-select' | 'day' | 'night' | 'accusation' | 'ending'

function App() {
  const [screen, setScreen] = useState<Screen>('title')
  const [account, setAccount] = useState<AccountInfo | null>(null)
  const [session, setSession] = useState<SessionResponse | null>(null)
  const [nightInfo, setNightInfo] = useState<{ day: number; nextDay: number } | null>(null)

  const [showLoginModal, setShowLoginModal] = useState(false)

  const [creatingSession, setCreatingSession] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)

  const [advancing, setAdvancing] = useState(false)
  const [advanceError, setAdvanceError] = useState<string | null>(null)
  // handleAdvanceDay 참고 — 같은 렌더 틱 안의 중복 호출을 막기 위한 동기 플래그.
  const advanceInFlightRef = useRef(false)

  // 오프닝 컷씬(ArrivalScreen) 바로 다음에만 1일차 가이드 창을 띄운다 — "이어서하기"로
  // 기존 세이브를 불러올 때는 이 플래그가 세워지지 않으므로 뜨지 않는다.
  const [showDay1Guide, setShowDay1Guide] = useState(false)

  const sessionRef = useRef<SessionResponse | null>(null)
  useEffect(() => {
    sessionRef.current = session
  }, [session])

  const autoSaveFnRef = useRef<((day: number) => Promise<void>) | null>(null)
  useEffect(() => {
    autoSaveFnRef.current = createDayChangeAutoSave(() => {
      const current = sessionRef.current
      return {
        day: current?.currentDay ?? 1,
        phase: 'day',
        playerHp: Math.round(current?.staminaCurrent ?? 100),
        inventory: {},
        affection: {},
        cluesCollected: [],
        culpritId: null,
        sabotageSchedule: [],
        accusationAttempts: 0,
        chatHistory: {},
      }
    })
  }, [])

  useEffect(() => {
    const token = getAuthToken()
    if (!token) return
    fetchMe(token)
      .then(setAccount)
      .catch(() => clearAuthToken())
  }, [])

  function handleAuthSuccess(result: AuthResult) {
    setAccount({ accountId: result.accountId, username: result.username, nickname: result.nickname })
    setShowLoginModal(false)
  }

  async function handleLogout() {
    const token = getAuthToken()
    if (token) await logoutRequest(token)
    clearAuthToken()
    setAccount(null)
    setSession(null)
    setScreen('title')
  }

  function handleStartNewGame() {
    setCreateError(null)
    setScreen('character-create')
  }

  async function handleCharacterConfirm(nickname: string, gender: CharacterGender) {
    setCreatingSession(true)
    setCreateError(null)
    try {
      setPlayerProfile({ nickname, gender })
      const newSession = await createSession(nickname)
      setSession(newSession)
      autoSaveFnRef.current?.(newSession.currentDay)
      // 새 게임을 막 시작했을 때만 오프닝 컷씬(마을 도착 장면)을 한 번 보여준다 — "이어서하기"로
      // 기존 세이브를 불러올 때는 이미 마을에 있는 상태이므로 여기를 거치지 않고 바로 day로 간다.
      setScreen('arrival')
    } catch (err) {
      setCreateError(err instanceof Error ? err.message : '게임 시작에 실패했습니다.')
    } finally {
      setCreatingSession(false)
    }
  }

  function handleSelectSave(selected: SessionResponse) {
    setSession(selected)
    setScreen(selected.status === 'IN_PROGRESS' ? 'day' : 'ending')
  }

  async function handleAdvanceDay() {
    // "집으로 돌아가기" 버튼은 advancing으로 막지만, 고발 결과 화면의 확인 버튼처럼 별도
    // 경로로 호출되는 곳은 그 state를 못 보고 지나칠 수 있다 — 특히 오답 확인 버튼을 빠르게
    // 두 번 누르면 이 함수가 같은 렌더 틱에 두 번 호출돼(advancing state가 아직 반영되기
    // 전) 서버에 advance-day가 중복 호출됐다. 7·8일차라면 하루를 건너뛰는 정도로 끝나지만,
    // 9일차에 중복 호출되면 두 번째 호출이 이미 9일차로 넘어간 세션을 다시 읽어 "9일차에도
    // 고발 안 하고 넘김"으로 오판, 고발할 틈도 없이 배드엔딩 처리하는 버그로 이어졌다.
    // ref는 useState와 달리 동기적으로 즉시 반영되므로, 같은 틱의 재호출도 확실히 막는다.
    if (!session || advanceInFlightRef.current) return
    advanceInFlightRef.current = true
    setAdvancing(true)
    setAdvanceError(null)
    try {
      const prevDay = session.currentDay
      const updated = await advanceDay(session.sessionId)
      setSession(updated)

      if (updated.status !== 'IN_PROGRESS') {
        setScreen('ending')
        return
      }

      autoSaveFnRef.current?.(updated.currentDay)
      setNightInfo({ day: prevDay, nextDay: updated.currentDay })
      setScreen('night')
    } catch (err) {
      setAdvanceError(err instanceof Error ? err.message : '다음 날로 넘어가지 못했습니다.')
    } finally {
      advanceInFlightRef.current = false
      setAdvancing(false)
    }
  }

  function handleAccusationResolved(result: AccuseResult) {
    setSession((prev) => (prev ? { ...prev, status: result.sessionStatus } : prev))

    // 정답(SUCCESS)이거나 9일차 오답(BAD_ENDING)이면 이미 세션이 종료된 상태 — 곧바로
    // 엔딩 화면으로 간다. 이 분기가 갈리는 기준은 오직 서버가 내려준 sessionStatus뿐이라,
    // 정답인데 배드엔딩 화면이 먼저 뜨는 경우는 구조적으로 발생할 수 없다.
    if (result.sessionStatus !== 'IN_PROGRESS') {
      setScreen('ending')
      return
    }

    // 7·8일차에 지목했지만 오답인 경우 — 결과 확인 후 자동으로 다음 날(8·9일차)로 넘어간다.
    void handleAdvanceDay()
  }

  function handleQuitToTitle() {
    setSession(null)
    setScreen('title')
  }

  return (
    <>
      {screen === 'title' && (
        <TitleScreen
          isLoggedIn={!!account}
          nickname={account?.nickname ?? null}
          onLoginClick={() => setShowLoginModal(true)}
          onLogoutClick={handleLogout}
          onStartNewGame={handleStartNewGame}
          onContinue={() => setScreen('save-select')}
        />
      )}

      {screen === 'character-create' && (
        <CharacterCreateScreen
          onConfirm={handleCharacterConfirm}
          onBack={() => setScreen('title')}
          submitting={creatingSession}
          error={createError}
        />
      )}

      {screen === 'arrival' && session && (
        <ArrivalScreen
          nickname={getPlayerProfile()?.nickname ?? '당신'}
          onContinue={() => {
            setShowDay1Guide(true)
            setScreen('day')
          }}
        />
      )}

      {screen === 'save-select' && (
        <SaveSlotPicker
          onSelectSave={handleSelectSave}
          onStartNewGame={handleStartNewGame}
          onBack={() => setScreen('title')}
        />
      )}

      {screen === 'day' && session && (
        <DayScreen
          key={session.currentDay}
          session={session}
          advancing={advancing}
          advanceError={advanceError}
          onAdvanceDay={handleAdvanceDay}
          onOpenAccusation={() => setScreen('accusation')}
          onQuitToTitle={handleQuitToTitle}
          showOpeningGuide={showDay1Guide}
          onOpeningGuideShown={() => setShowDay1Guide(false)}
        />
      )}

      {screen === 'night' && nightInfo && session && (
        <NightTransitionScreen
          sessionId={session.sessionId}
          day={nightInfo.day}
          nextDay={nightInfo.nextDay}
          onContinue={() => setScreen('day')}
        />
      )}

      {screen === 'accusation' && session && (
        <AccusationScreen
          sessionId={session.sessionId}
          day={session.currentDay}
          onResolved={handleAccusationResolved}
          onCancel={() => setScreen('day')}
        />
      )}

      {screen === 'ending' && session && (
        <EndingScreen sessionId={session.sessionId} onBackToTitle={handleQuitToTitle} />
      )}

      {showLoginModal && <LoginModal onSuccess={handleAuthSuccess} onClose={() => setShowLoginModal(false)} />}
    </>
  )
}

export default App
