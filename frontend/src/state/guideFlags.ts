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

// 7~8/8~9일차 랜덤 이벤트 공용 알림("잡히지 않은 범인이 사보타주를 일으켰습니다.")도 진입 시
// 1회만 보여줘야 한다. 이벤트 자체의 확인 여부(viewed)는 서버가 들고 있지만, 그건 "내용을 다
// 봤는지"이고 이 알림은 "그 내용을 보러 가라는 안내를 봤는지"라 별개 상태라 로컬로 관리한다.
function randomEventNoticeKey(sessionId: number, day: number): string {
  return `random-event-notice-seen:${sessionId}:${day}`
}

export function hasSeenRandomEventNotice(sessionId: number, day: number): boolean {
  return localStorage.getItem(randomEventNoticeKey(sessionId, day)) === '1'
}

export function markRandomEventNoticeSeen(sessionId: number, day: number): void {
  localStorage.setItem(randomEventNoticeKey(sessionId, day), '1')
}
