// 2일차(첫 사보타주 발생 다음날) 가이드 창을 세션당 한 번만 보여주기 위한 로컬 플래그.
// 서버에 저장할 정도로 중요한 상태는 아니라서(그냥 튜토리얼 문구) localStorage로 충분하다.
function sabotageGuideKey(sessionId: number): string {
  return `sabotage-guide-seen:${sessionId}`
}

export function hasSeenSabotageGuide(sessionId: number): boolean {
  return localStorage.getItem(sabotageGuideKey(sessionId)) === '1'
}

export function markSabotageGuideSeen(sessionId: number): void {
  localStorage.setItem(sabotageGuideKey(sessionId), '1')
}
