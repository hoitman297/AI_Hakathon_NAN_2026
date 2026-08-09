import { useEffect, useState } from 'react'
import { listSessions, deleteSession, type SessionResponse } from '../api/sessionApi'
import { Modal } from '../components/Modal'
import './SaveSlotPicker.css'

// 타이틀 화면과 동일한 배경을 써서 "이어서하기" 진입 시 배경이 바뀌어 보이지 않게 한다.
const SAVE_PICKER_BACKGROUND = '/assets/ui/title/title-board-v2.png'

// 백엔드 GameConstants.MAX_SAVES_PER_ACCOUNT와 맞춰야 한다. 이 값을 조회하는 API가 없어
// 프론트에 그대로 하드코딩했다(DayScreen의 FIRST_ACCUSATION_DAY와 같은 패턴).
const MAX_SAVES_PER_ACCOUNT = 3

interface SaveSlotPickerProps {
  onSelectSave: (session: SessionResponse) => void
  onStartNewGame: () => void
  onBack: () => void
}

const STATUS_LABEL: Record<SessionResponse['status'], string> = {
  IN_PROGRESS: '진행중',
  SUCCESS: '성공',
  BAD_ENDING: '실패',
}

function formatDateTime(iso: string | null): string {
  if (!iso) return ''
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}.${pad(date.getMonth() + 1)}.${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function SaveSlotPicker({ onSelectSave, onStartNewGame, onBack }: SaveSlotPickerProps) {
  const [saves, setSaves] = useState<SessionResponse[] | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [deletingId, setDeletingId] = useState<number | null>(null)
  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null)

  function fetchSaves() {
    return listSessions()
      .then(setSaves)
      .catch((err) => setError(err instanceof Error ? err.message : '세이브 목록을 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    fetchSaves()
  }, [])

  /** 삭제 등 사용자 액션 이후 재조회 — 마운트 이펙트와 달리 이벤트 핸들러 안에서 호출되므로
   * setState를 동기적으로 먼저 해도(로딩/에러 초기화) 문제 없다. */
  function refresh() {
    setLoading(true)
    setError(null)
    fetchSaves()
  }

  async function handleConfirmDelete() {
    if (confirmDeleteId === null) return
    setDeletingId(confirmDeleteId)
    try {
      await deleteSession(confirmDeleteId)
      setConfirmDeleteId(null)
      refresh()
    } catch (err) {
      setError(err instanceof Error ? err.message : '세이브를 삭제하지 못했습니다.')
    } finally {
      setDeletingId(null)
    }
  }

  const slots: (SessionResponse | null)[] = Array.from({ length: MAX_SAVES_PER_ACCOUNT }, (_, i) => saves?.[i] ?? null)

  return (
    <div className="save-picker-screen" style={{ backgroundImage: `url(${SAVE_PICKER_BACKGROUND})` }}>
      <div className="save-picker-overlay" />
      <div className="save-picker-content">
        <h1 className="save-picker-title">이어서하기</h1>
        <p className="save-picker-subtitle">불러올 세이브를 선택하세요 (최대 {MAX_SAVES_PER_ACCOUNT}개)</p>

        {loading && <p className="save-picker-status">불러오는 중...</p>}
        {error && <p className="save-picker-status save-picker-status--error">{error}</p>}

        {!loading && saves && (
          <div className="save-slot-grid">
            {slots.map((save, index) =>
              save ? (
                <div key={save.sessionId} className="save-slot pixel-panel">
                  <div className={`save-slot-badge save-slot-badge--${save.status.toLowerCase()}`}>
                    {STATUS_LABEL[save.status]}
                  </div>
                  <div className="save-slot-day">{save.currentDay}일차</div>
                  <div className="save-slot-time">
                    {save.status === 'IN_PROGRESS'
                      ? `시작: ${formatDateTime(save.startedAt)}`
                      : `종료: ${formatDateTime(save.endedAt)}`}
                  </div>
                  <div className="save-slot-actions">
                    <button className="pixel-button pixel-button--accent" onClick={() => onSelectSave(save)}>
                      {save.status === 'IN_PROGRESS' ? '이어하기' : '결과 보기'}
                    </button>
                    <button
                      className="pixel-button pixel-button--danger save-slot-delete"
                      onClick={() => setConfirmDeleteId(save.sessionId)}
                      disabled={deletingId === save.sessionId}
                    >
                      삭제
                    </button>
                  </div>
                </div>
              ) : (
                <div key={`empty-${index}`} className="save-slot save-slot--empty pixel-panel">
                  <div className="save-slot-empty-label">빈 슬롯</div>
                  <button className="pixel-button pixel-button--accent" onClick={onStartNewGame}>
                    새 게임 시작
                  </button>
                </div>
              ),
            )}
          </div>
        )}

        <button className="pixel-button save-picker-back" onClick={onBack}>
          타이틀로
        </button>
      </div>

      {confirmDeleteId !== null && (
        <Modal
          title="세이브 삭제"
          actions={
            <>
              <button className="pixel-button" onClick={() => setConfirmDeleteId(null)} disabled={deletingId !== null}>
                취소
              </button>
              <button
                className="pixel-button pixel-button--danger"
                onClick={handleConfirmDelete}
                disabled={deletingId !== null}
              >
                {deletingId !== null ? '삭제 중...' : '삭제'}
              </button>
            </>
          }
        >
          <p>이 세이브를 삭제하시겠어요? 되돌릴 수 없습니다.</p>
        </Modal>
      )}
    </div>
  )
}
