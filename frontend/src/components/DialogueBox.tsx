import { useEffect, useRef, useState } from 'react'
import { findRosterEntry } from '../types/npc'
import { fetchDialogueHistory, sendDialogueMessage, type DialogueMessage } from '../api/dialogueApi'
import { getSession } from '../api/sessionApi'
import { getNpcDetail } from '../api/npcApi'
import { HeartMeter } from './HeartMeter'
import './DialogueBox.css'

// 한 번 대화창을 열 때마다 주고받을 수 있는 질의응답 횟수 제한 — 이 횟수를 채우면
// NPC가 대화를 마무리하고, 다시 말을 걸려면 대화창을 닫았다 새로 열어야 한다.
const MAX_EXCHANGES_PER_VISIT = 3

interface DialogueBoxProps {
  sessionId: number
  npcId: number
  npcName: string
  npcRole: string
  onStaminaChange: (value: number) => void
  onClose: () => void
}

export function DialogueBox({ sessionId, npcId, npcName, npcRole, onStaminaChange, onClose }: DialogueBoxProps) {
  const roster = findRosterEntry(npcName)
  const [messages, setMessages] = useState<DialogueMessage[]>([])
  const [loadingHistory, setLoadingHistory] = useState(true)
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [honestModeActive, setHonestModeActive] = useState(false)
  const [exchangeCount, setExchangeCount] = useState(0)
  const [affinity, setAffinity] = useState<number | null>(null)
  const listRef = useRef<HTMLDivElement>(null)
  const conversationEnded = exchangeCount >= MAX_EXCHANGES_PER_VISIT

  useEffect(() => {
    fetchDialogueHistory(sessionId, npcId)
      .then(setMessages)
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
    setMessages((prev) => [...prev, { sender: 'USER', message, createdAt: new Date().toISOString() }])
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
      setExchangeCount((prev) => prev + 1)
    } catch (err) {
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
                남은 질문 {MAX_EXCHANGES_PER_VISIT - exchangeCount}/{MAX_EXCHANGES_PER_VISIT}
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
            {conversationEnded && (
              <p className="dialogue-status">{npcName}이(가) 대화를 마무리했습니다. 다음에 다시 말을 걸어보세요.</p>
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
