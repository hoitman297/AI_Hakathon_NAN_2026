import { useEffect, useState } from 'react'
import {
  listAcquiredClues,
  listUnacquiredClues,
  acquireClue,
  clarifyClue,
  topicLabel,
  type ClueCard,
  type UnacquiredClue,
} from '../api/clueApi'
import './CluePanel.css'

interface CluePanelProps {
  sessionId: number
  onClose: () => void
}

type Tab = 'unacquired' | 'acquired'

export function CluePanel({ sessionId, onClose }: CluePanelProps) {
  const [tab, setTab] = useState<Tab>('unacquired')
  const [unacquired, setUnacquired] = useState<UnacquiredClue[] | null>(null)
  const [acquired, setAcquired] = useState<ClueCard[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [busyKey, setBusyKey] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  function loadUnacquired() {
    listUnacquiredClues(sessionId)
      .then(setUnacquired)
      .catch((err: unknown) => setLoadError(err instanceof Error ? err.message : '미습득 단서를 불러오지 못했습니다.'))
  }

  function loadAcquired() {
    listAcquiredClues(sessionId)
      .then(setAcquired)
      .catch((err: unknown) => setLoadError(err instanceof Error ? err.message : '습득한 단서를 불러오지 못했습니다.'))
  }

  useEffect(loadUnacquired, [sessionId])

  useEffect(() => {
    if (tab === 'acquired' && acquired === null) loadAcquired()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab])

  async function handleAcquire(clue: UnacquiredClue) {
    setMessage(null)
    setLoadError(null)
    setBusyKey(`acquire-${clue.clueId}`)
    try {
      await acquireClue(sessionId, clue.clueId)
      setMessage(`${clue.location}에서 단서(${topicLabel(clue.topic)})를 습득했습니다.`)
      loadUnacquired()
      setAcquired(null)
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : '단서를 습득하지 못했습니다.')
    } finally {
      setBusyKey(null)
    }
  }

  async function handleClarify(clue: ClueCard) {
    setMessage(null)
    setLoadError(null)
    setBusyKey(`clarify-${clue.clueId}`)
    try {
      await clarifyClue(sessionId, clue.clueId)
      setMessage('돋보기로 단서를 더 명확하게 밝혀냈습니다.')
      loadAcquired()
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : '단서를 명확화하지 못했습니다.')
    } finally {
      setBusyKey(null)
    }
  }

  return (
    <div className="clue-overlay">
      <div className="clue-panel pixel-panel">
        <h2>단서 카드</h2>

        <div className="clue-tabs">
          <button
            className={`pixel-button ${tab === 'unacquired' ? 'pixel-button--accent' : ''}`}
            onClick={() => setTab('unacquired')}
          >
            미습득
          </button>
          <button
            className={`pixel-button ${tab === 'acquired' ? 'pixel-button--accent' : ''}`}
            onClick={() => setTab('acquired')}
          >
            습득한 단서
          </button>
        </div>

        {loadError && <p className="pixel-error">{loadError}</p>}
        {message && <p className="clue-message">{message}</p>}

        <div className="clue-list">
          {tab === 'unacquired' &&
            (unacquired === null ? (
              <p className="clue-status">불러오는 중...</p>
            ) : unacquired.length === 0 ? (
              <p className="clue-status">아직 발견 안 된 사건이 없거나, 남은 단서를 전부 습득했습니다.</p>
            ) : (
              unacquired.map((clue) => (
                <div key={clue.clueId} className="clue-item-row">
                  <div className="clue-item-info">
                    <div className="clue-item-name">
                      {clue.location} <span className="clue-item-topic">· {topicLabel(clue.topic)}</span>
                    </div>
                    <div className="clue-item-desc">{clue.day}일차 밤 발생</div>
                  </div>
                  <button
                    className="pixel-button pixel-button--accent"
                    disabled={busyKey === `acquire-${clue.clueId}`}
                    onClick={() => handleAcquire(clue)}
                  >
                    습득
                  </button>
                </div>
              ))
            ))}

          {tab === 'acquired' &&
            (acquired === null ? (
              <p className="clue-status">불러오는 중...</p>
            ) : acquired.length === 0 ? (
              <p className="clue-status">습득한 단서가 없습니다.</p>
            ) : (
              acquired.map((clue) => (
                <div key={clue.clueId} className="clue-card-item">
                  <div className="clue-item-name">{topicLabel(clue.topic)}</div>
                  <p className="clue-card-text">{clue.text}</p>
                  <button
                    className="pixel-button"
                    disabled={clue.clarified || busyKey === `clarify-${clue.clueId}`}
                    onClick={() => handleClarify(clue)}
                  >
                    {clue.clarified ? '명확화됨' : '돋보기로 명확화'}
                  </button>
                </div>
              ))
            ))}
        </div>

        <button className="pixel-button clue-close" onClick={onClose}>
          닫기
        </button>
      </div>
    </div>
  )
}
