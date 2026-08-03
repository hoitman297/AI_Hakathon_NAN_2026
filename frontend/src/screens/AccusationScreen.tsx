import { useEffect, useState } from 'react'
import { listNpcsToday, type NpcSummary } from '../api/npcApi'
import { accuseNpc, type AccuseResult } from '../api/accusationApi'
import { findRosterEntry } from '../types/npc'
import { villageAssets } from '../assets/asset-manifest'
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
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [result, setResult] = useState<AccuseResult | null>(null)

  useEffect(() => {
    listNpcsToday(sessionId)
      .then(setNpcs)
      .catch((err: unknown) => setLoadError(err instanceof Error ? err.message : 'NPC 목록을 불러오지 못했습니다.'))
  }, [sessionId])

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
          <button className="pixel-button pixel-button--accent" onClick={() => onResolved(result)}>
            확인
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="accusation-screen">
      <div className="accusation-header">
        <h2>범인 지목</h2>
        <p>{day}일차 · 진범이라 생각되는 마을 사람을 골라 고발 카드를 제출하세요.</p>
      </div>

      {loadError && <p className="pixel-error">{loadError}</p>}

      {!selected && npcs && (
        <div className="accusation-list">
          {npcs.map((npc) => {
            const roster = findRosterEntry(npc.name)
            return (
              <button key={npc.npcId} className="accusation-npc-card pixel-panel" onClick={() => setSelected(npc)}>
                {roster && <img className="accusation-npc-portrait pixel-art" src={roster.portrait} alt="" />}
                <div className="accusation-npc-name">{npc.name}</div>
                <div className="accusation-npc-role">{npc.role}</div>
                <div className="accusation-npc-location">현재 위치: {npc.currentLocation}</div>
              </button>
            )
          })}
        </div>
      )}

      {selected && (
        <div className="accusation-card-wrap">
          <div className="mission-card">
            <img className="mission-card-frame pixel-art" src={villageAssets.ui.missionCards.active} alt="" />
            {findRosterEntry(selected.name) && (
              <img
                className="mission-card-portrait pixel-art"
                src={findRosterEntry(selected.name)?.portrait}
                alt=""
              />
            )}
            <p className="mission-card-caption">
              {selected.name} ({selected.role})을(를)
              <br />
              범인으로 고발하시겠습니까?
            </p>
          </div>

          {submitError && <p className="pixel-error">{submitError}</p>}

          <div className="accusation-card-actions">
            <button className="pixel-button pixel-button--danger" onClick={handleConfirm} disabled={submitting}>
              {submitting ? '제출 중...' : '고발하기'}
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
