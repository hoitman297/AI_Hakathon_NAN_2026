import { useEffect, useRef, useState } from 'react'
import { TitleScreen } from './screens/TitleScreen'
import { LoginModal } from './screens/LoginModal'
import { CharacterCreateScreen } from './screens/CharacterCreateScreen'
import { DayScreen } from './screens/DayScreen'
import { NightTransitionScreen } from './screens/NightTransitionScreen'
import { AccusationScreen } from './screens/AccusationScreen'
import { EndingScreen } from './screens/EndingScreen'
import { Modal } from './components/Modal'
import { fetchMe, logout as logoutRequest, type AccountInfo, type AuthResult } from './api/authApi'
import { createSession, getSession, advanceDay, type SessionResponse } from './api/sessionApi'
import type { AccuseResult } from './api/accusationApi'
import { getAuthToken, clearAuthToken } from './api/authToken'
import { loadGame } from './api/saveApi'
import { createDayChangeAutoSave } from './game/autoSave'
import { getPlayerProfile, setPlayerProfile, type CharacterGender } from './state/playerProfile'
import { getStoredSessionId, setStoredSessionId, clearStoredSessionId } from './state/gameSession'

type Screen = 'title' | 'character-create' | 'day' | 'night' | 'accusation' | 'ending'

function App() {
  const [screen, setScreen] = useState<Screen>('title')
  const [account, setAccount] = useState<AccountInfo | null>(null)
  const [session, setSession] = useState<SessionResponse | null>(null)
  const [nightInfo, setNightInfo] = useState<{ day: number; nextDay: number } | null>(null)

  const [showLoginModal, setShowLoginModal] = useState(false)
  const [showNoSaveModal, setShowNoSaveModal] = useState(false)
  const [continueLoading, setContinueLoading] = useState(false)
  const [continueError, setContinueError] = useState<string | null>(null)

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
        playerHp: current?.staminaCurrent ?? 100,
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
    clearStoredSessionId()
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
      setStoredSessionId(newSession.sessionId)
      setSession(newSession)
      autoSaveFnRef.current?.(newSession.currentDay)
      setScreen('day')
    } catch (err) {
      setCreateError(err instanceof Error ? err.message : '게임 시작에 실패했습니다.')
    } finally {
      setCreatingSession(false)
    }
  }

  async function handleContinue() {
    setContinueError(null)
    setContinueLoading(true)
    try {
      const saveRes = await loadGame()
      if (saveRes.updatedAt === null) {
        setShowNoSaveModal(true)
        return
      }

      let resumed: SessionResponse | null = null
      const storedId = getStoredSessionId()
      if (storedId) {
        try {
          const candidate = await getSession(storedId)
          if (candidate.status === 'IN_PROGRESS' && candidate.accountId === account?.accountId) {
            resumed = candidate
          }
        } catch {
          resumed = null
        }
      }

      if (!resumed) {
        const profile = getPlayerProfile()
        const playerId = profile?.nickname ?? account?.nickname ?? '플레이어'
        resumed = await createSession(playerId)
      }

      setStoredSessionId(resumed.sessionId)
      setSession(resumed)
      setScreen('day')
    } catch (err) {
      setContinueError(err instanceof Error ? err.message : '이어서하기에 실패했습니다.')
    } finally {
      setContinueLoading(false)
    }
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
    clearStoredSessionId()
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
          onContinue={handleContinue}
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

      {screen === 'night' && nightInfo && (
        <NightTransitionScreen
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

      {continueLoading && (
        <Modal title="불러오는 중">
          <p>저장된 게임을 불러오고 있습니다...</p>
        </Modal>
      )}

      {continueError && (
        <Modal
          title="오류"
          actions={
            <button className="pixel-button" onClick={() => setContinueError(null)}>
              확인
            </button>
          }
        >
          <p>{continueError}</p>
        </Modal>
      )}

      {showNoSaveModal && (
        <Modal
          title="알림"
          actions={
            <button className="pixel-button pixel-button--accent" onClick={() => setShowNoSaveModal(false)}>
              확인
            </button>
          }
        >
          <p>저장 내역이 없어요!</p>
        </Modal>
      )}
    </>
  )
}

export default App
