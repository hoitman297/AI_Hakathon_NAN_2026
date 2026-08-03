const STORAGE_KEY = 'gameSessionId'

/**
 * 백엔드에 "계정으로 진행 중인 세션 조회" API가 없어서(POST /api/sessions는 항상 새
 * 세션·새 범인을 생성함) "이어서하기"가 같은 세션으로 복귀하려면 sessionId를
 * 클라이언트에 직접 들고 있어야 한다. 브라우저 저장소를 지우면 이 값도 사라지고,
 * 그 경우 이어서하기는 세이브 스탯만 복원한 새 세션으로 대체된다.
 */
export function getStoredSessionId(): number | null {
  const raw = localStorage.getItem(STORAGE_KEY)
  return raw ? Number(raw) : null
}

export function setStoredSessionId(sessionId: number): void {
  localStorage.setItem(STORAGE_KEY, String(sessionId))
}

export function clearStoredSessionId(): void {
  localStorage.removeItem(STORAGE_KEY)
}
