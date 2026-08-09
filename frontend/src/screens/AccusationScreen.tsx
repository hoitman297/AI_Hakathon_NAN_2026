import { useEffect, useState, type DragEvent } from 'react'
import { listNpcsToday, type NpcSummary } from '../api/npcApi'
import { accuseNpc, type AccuseResult } from '../api/accusationApi'
import { findRosterEntry } from '../types/npc'
import './AccusationScreen.css'

interface AccusationScreenProps {
  sessionId: number
  day: number
  onResolved: (result: AccuseResult) => void
  onCancel: () => void
}

export function AccusationScreen({ sessionId, day, onResolved, onCancel }: AccusationScreenProps) {
  const [npcs, setNpcs] = useState<NpcSummary[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [selected, setSelected] = useState<NpcSummary | null>(null)
  const [dragOverNpcId, setDragOverNpcId] = useState<number | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [result, setResult] = useState<AccuseResult | null>(null)
  // 결과 확인 버튼을 빠르게 두 번 누르면 onResolved(부모의 다음날 전환 로직)가 중복 호출될 수
  // 있어서, 한 번 누르면 곧바로 비활성화한다.
  const [resolving, setResolving] = useState(false)

  useEffect(() => {
    listNpcsToday(sessionId)
      .then(setNpcs)
      .catch((err: unknown) => setLoadError(err instanceof Error ? err.message : 'NPC 목록을 불러오지 못했습니다.'))
  }, [sessionId])

  function handleCardDragStart(event: DragEvent<HTMLDivElement>) {
    event.dataTransfer.setData('text/plain', 'accusation-card')
    event.dataTransfer.effectAllowed = 'move'
  }

  function handleNpcDragOver(event: DragEvent<HTMLDivElement>, npcId: number) {
    event.preventDefault()
    event.dataTransfer.dropEffect = 'move'
    setDragOverNpcId(npcId)
  }

  function handleNpcDrop(event: DragEvent<HTMLDivElement>, npc: NpcSummary) {
    event.preventDefault()
    setDragOverNpcId(null)
    setSelected(npc)
  }

  async function handleConfirm() {
    if (!selected) return
    setSubmitting(true)
    setSubmitError(null)
    try {
      setResult(await accuseNpc(sessionId, selected.npcId))
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : '고발 처리에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  if (result) {
    return (
      <div className="accusation-screen">
        <div className="accusation-result pixel-panel">
          <div className={`accusation-result-badge ${result.correct ? 'is-correct' : 'is-wrong'}`}>
            {result.correct ? '정답' : '오답'}
          </div>
          <p>{result.message}</p>
          <button
            className="pixel-button pixel-button--accent"
            disabled={resolving}
            onClick={() => {
              setResolving(true)
              onResolved(result)
            }}
          >
            확인
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="accusation-screen">
      <div className="accusation-header">
        <h2>누구에게 고발 카드를 건네줄까요?</h2>
        <p>{day}일차 · 고발 카드를 지목하려는 마을 사람 위로 드래그해서 놓으세요.</p>
      </div>

      {loadError && <p className="pixel-error">{loadError}</p>}

      {!selected && npcs && (
        <>
          <div className="accusation-list">
            {npcs.map((npc) => {
              const roster = findRosterEntry(npc.name)
              return (
                <div
                  key={npc.npcId}
                  className={`accusation-npc-card pixel-panel ${dragOverNpcId === npc.npcId ? 'is-drag-over' : ''}`}
                  onDragOver={(event) => handleNpcDragOver(event, npc.npcId)}
                  onDragLeave={() => setDragOverNpcId((prev) => (prev === npc.npcId ? null : prev))}
                  onDrop={(event) => handleNpcDrop(event, npc)}
                >
                  {roster && <img className="accusation-npc-portrait pixel-art" src={roster.portrait} alt="" />}
                  <div className="accusation-npc-name">{npc.name}</div>
                  <div className="accusation-npc-role">{npc.role}</div>
                  <div className="accusation-npc-location">현재 위치: {npc.currentLocation}</div>
                </div>
              )
            })}
          </div>

          <div
            className="accusation-card"
            draggable
            onDragStart={handleCardDragStart}
            onDragEnd={() => setDragOverNpcId(null)}
          >
            <span className="accusation-card-icon" aria-hidden="true">
              📜
            </span>
            <span className="accusation-card-label">고발 카드</span>
          </div>
        </>
      )}

      {selected && (
        <div className="accusation-confirm pixel-panel">
          {findRosterEntry(selected.name) && (
            <img className="accusation-confirm-portrait pixel-art" src={findRosterEntry(selected.name)?.portrait} alt="" />
          )}
          <p className="accusation-confirm-caption">
            {selected.name} ({selected.role})을(를)
            <br />
            범인으로 지목해 고발하시겠습니까?
          </p>

          {submitError && <p className="pixel-error">{submitError}</p>}

          <div className="accusation-confirm-actions">
            <button className="pixel-button pixel-button--danger" onClick={handleConfirm} disabled={submitting}>
              {submitting ? '고발하는 중...' : '고발하기'}
            </button>
            <button className="pixel-button" onClick={() => setSelected(null)} disabled={submitting}>
              다시 선택
            </button>
          </div>
        </div>
      )}

      {!selected && (
        <button className="pixel-button accusation-cancel" onClick={onCancel}>
          마을로 돌아가기
        </button>
      )}
    </div>
  )
}
