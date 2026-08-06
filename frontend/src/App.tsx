import { useEffect, useRef, useState } from 'react'
import { TitleScreen } from './screens/TitleScreen'
import { LoginModal } from './screens/LoginModal'
import { CharacterCreateScreen } from './screens/CharacterCreateScreen'
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
import { setPlayerProfile, type CharacterGender } from './state/playerProfile'

type Screen = 'title' | 'character-create' | 'save-select' | 'day' | 'night' | 'accusation' | 'ending'

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
      setScreen('day')
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
    if (!session) return
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
      setAdvancing(false)
    }
  }

  function handleAccusationResolved(result: AccuseResult) {
    setSession((prev) => (prev ? { ...prev, status: result.sessionStatus } : prev))
    setScreen(result.sessionStatus === 'IN_PROGRESS' ? 'day' : 'ending')
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
