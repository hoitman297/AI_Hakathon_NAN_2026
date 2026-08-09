import { useEffect, useRef, useState } from 'react'
import { findRosterEntry } from '../types/npc'
import { fetchDialogueHistory, sendDialogueMessage, type DialogueMessage } from '../api/dialogueApi'
import { getSession } from '../api/sessionApi'
import { getNpcDetail } from '../api/npcApi'
import { HeartMeter } from './HeartMeter'
import './DialogueBox.css'

// 대화창을 새로 열기 전(히스토리 로딩 전) 잠깐 보여줄 기본값 — 실제 한도는 서버가
// /dialogue GET 응답(maxExchangesPerDay)으로 내려준다. 백엔드
// GameConstants.MAX_DIALOGUE_EXCHANGES_PER_NPC_PER_DAY와 맞춰야 한다.
const DEFAULT_MAX_EXCHANGES_PER_DAY = 3

// 백엔드 GameConstants.DIALOGUE_STAMINA와 맞춰야 한다. 이 값을 조회하는 API가 없어
// 프론트에 그대로 하드코딩했다(FIRST_ACCUSATION_DAY와 같은 패턴).
const DIALOGUE_STAMINA_COST = 8

// 체력이 다 떨어져 대화가 자동으로 마무리될 때, 마지막 답변을 읽을 시간을 준 뒤 닫는다.
const STAMINA_DEPLETED_AUTO_CLOSE_MS = 1600

interface DialogueBoxProps {
  sessionId: number
  npcId: number
  npcName: string
  npcRole: string
  staminaCurrent: number
  onStaminaChange: (value: number) => void
  onClose: () => void
}

export function DialogueBox({
  sessionId,
  npcId,
  npcName,
  npcRole,
  staminaCurrent,
  onStaminaChange,
  onClose,
}: DialogueBoxProps) {
  const roster = findRosterEntry(npcName)
  const [messages, setMessages] = useState<DialogueMessage[]>([])
  const [loadingHistory, setLoadingHistory] = useState(true)
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [honestModeActive, setHonestModeActive] = useState(false)
  // 대화창을 새로 열어도 초기화되지 않는, 서버(DB의 오늘치 대화 로그) 기준 누적 횟수 —
  // 예전엔 이 컴포넌트가 마운트될 때마다 0으로 시작하는 로컬 state였어서, 대화창을 닫았다
  // 다시 열면 하루 제한이 사실상 무한정 풀리는 버그가 있었다.
  const [exchangesUsedToday, setExchangesUsedToday] = useState(0)
  const [maxExchangesPerDay, setMaxExchangesPerDay] = useState(DEFAULT_MAX_EXCHANGES_PER_DAY)
  const [affinity, setAffinity] = useState<number | null>(null)
  // 서버 응답으로 실제 체력이 0 이하가 된 경우(정상 케이스는 아래 staminaTooLowToContinue가
  // 미리 막지만, 만일을 대비한 안전망) — 대화를 강제로 마무리하고 잠시 후 자동으로 닫는다.
  const [staminaDepleted, setStaminaDepleted] = useState(false)
  const listRef = useRef<HTMLDivElement>(null)
  const turnLimitReached = exchangesUsedToday >= maxExchangesPerDay
  // 다음 메시지를 보내면 체력이 바닥나 밤으로 넘어갈 상황이면, 보내기 전에 미리 입력을 막는다.
  const staminaTooLowToContinue = staminaCurrent - DIALOGUE_STAMINA_COST <= 0
  const conversationEnded = turnLimitReached || staminaDepleted || staminaTooLowToContinue

  useEffect(() => {
    if (!staminaDepleted) return
    const timer = setTimeout(onClose, STAMINA_DEPLETED_AUTO_CLOSE_MS)
    return () => clearTimeout(timer)
  }, [staminaDepleted, onClose])

  useEffect(() => {
    fetchDialogueHistory(sessionId, npcId)
      .then((result) => {
        setMessages(result.messages)
        setExchangesUsedToday(result.exchangesUsedToday)
        setMaxExchangesPerDay(result.maxExchangesPerDay)
      })
      .catch((err) => setError(err instanceof Error ? err.message : '대화 기록을 불러오지 못했습니다.'))
      .finally(() => setLoadingHistory(false))

    getSession(sessionId)
      .then((session) => setHonestModeActive(session.honestModeNpcId === npcId))
      .catch(() => undefined)

    getNpcDetail(sessionId, npcId)
      .then((detail) => setAffinity(detail.affinityScore))
      .catch(() => undefined)
  }, [sessionId, npcId])

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight })
  }, [messages, sending])

  async function handleSend() {
    const message = input.trim()
    if (!message || sending || conversationEnded) return

    setError(null)
    setSending(true)
    const optimisticMessage: DialogueMessage = { sender: 'USER', message, createdAt: new Date().toISOString() }
    setMessages((prev) => [...prev, optimisticMessage])
    setInput('')

    try {
      const result = await sendDialogueMessage(sessionId, npcId, message)
      setMessages((prev) => [
        ...prev,
        { sender: 'NPC', message: result.npcReply, createdAt: new Date().toISOString() },
      ])
      onStaminaChange(result.staminaCurrent)
      setAffinity(result.affinityScore)
      // 서버는 정직 모드가 걸려있던 경우 이번 한 턴에 소모하고 해제한다.
      setHonestModeActive(false)
      setExchangesUsedToday(result.exchangesUsedToday)
      setMaxExchangesPerDay(result.maxExchangesPerDay)
      if (result.staminaCurrent <= 0) {
        setStaminaDepleted(true)
      }
    } catch (err) {
      // 요청이 실패하면 서버에는 이 메시지가 저장되지 않았다 — 방금 낙관적으로 추가한 내
      // 메시지를 화면에 그대로 남겨두면 실제로는 저장 안 된 대화가 나눈 것처럼 보이므로
      // 목록에서 지우고 입력창에 되돌려서 다시 보낼 수 있게 한다.
      setMessages((prev) => prev.filter((m) => m !== optimisticMessage))
      setInput(message)
      setError(err instanceof Error ? err.message : 'NPC가 응답하지 못했습니다.')
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="dialogue-overlay">
      <div className="dialogue-box pixel-panel">
        {roster && <img className="dialogue-portrait pixel-art" src={roster.portrait} alt="" />}
        <div className="dialogue-content">
          <div className="dialogue-name">
            <span>
              {npcName} <span className="dialogue-role">· {npcRole}</span>
              {honestModeActive && <span className="dialogue-honest-badge">정직 모드 (이번 대화만 적용)</span>}
            </span>
            {!conversationEnded && (
              <span className="dialogue-turns-left">
                오늘 남은 대화 {maxExchangesPerDay - exchangesUsedToday}/{maxExchangesPerDay}
              </span>
            )}
          </div>

          {affinity !== null && (
            <div className="dialogue-affinity">
              <HeartMeter score={affinity} />
              <span className="dialogue-affinity-score">호감도 {affinity}/100</span>
            </div>
          )}

          <div className="dialogue-history" ref={listRef}>
            {loadingHistory && <p className="dialogue-status">대화 기록을 불러오는 중...</p>}
            {!loadingHistory && messages.length === 0 && (
              <p className="dialogue-status">아직 나눈 대화가 없습니다. 말을 걸어보세요.</p>
            )}
            {messages.map((msg, index) => (
              <p
                key={index}
                className={`dialogue-line ${msg.sender === 'USER' ? 'dialogue-line--user' : 'dialogue-line--npc'}`}
              >
                {msg.message}
              </p>
            ))}
            {sending && <p className="dialogue-status dialogue-status--thinking">{npcName}이(가) 생각하는 중...</p>}
            {staminaDepleted && (
              <p className="dialogue-status dialogue-status--warning">체력이 다 떨어졌습니다. 대화를 마치고 곧 밤이 됩니다...</p>
            )}
            {!staminaDepleted && staminaTooLowToContinue && (
              <p className="dialogue-status dialogue-status--warning">
                체력이 부족해 오늘은 더 대화할 수 없습니다. 다음에 다시 말을 걸어주세요.
              </p>
            )}
            {!staminaDepleted && !staminaTooLowToContinue && turnLimitReached && (
              <p className="dialogue-status">{npcName}이(가) 대화를 마무리했습니다. 오늘은 더 대화할 수 없어요, 내일 다시 말을 걸어보세요.</p>
            )}
          </div>

          {error && <p className="dialogue-error pixel-error">{error}</p>}

          {!conversationEnded && (
            <form
              className="dialogue-input-row"
              onSubmit={(event) => {
                event.preventDefault()
                handleSend()
              }}
            >
              <input
                className="dialogue-input pixel-input"
                type="text"
                value={input}
                maxLength={1000}
                placeholder="할 말을 입력하세요..."
                disabled={sending || loadingHistory}
                onChange={(event) => setInput(event.target.value)}
                autoFocus
              />
              <button className="pixel-button pixel-button--accent" type="submit" disabled={sending || !input.trim()}>
                {sending ? '...' : '말하기'}
              </button>
            </form>
          )}

          <div className="dialogue-actions">
            <button className="pixel-button" onClick={onClose}>
              대화 종료
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
