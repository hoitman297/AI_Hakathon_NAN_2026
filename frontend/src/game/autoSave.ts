import { saveGame, type SaveData } from '../api/saveApi'

/**
 * 일차(day)가 바뀌는 시점에만 저장 API를 호출하는 트리거를 만든다.
 * 사보타주 판정/고발 시도 등 다른 시점에는 절대 호출하지 말 것 — 게임 루프에서
 * "일차가 바뀌었을 때"에만 반환된 함수를 호출해야 한다. 수동 저장 버튼은 없다.
 */
export function createDayChangeAutoSave(getSaveData: () => SaveData) {
  let lastSavedDay: number | null = null

  return async function onDayChanged(currentDay: number): Promise<void> {
    if (lastSavedDay === currentDay) {
      return // 같은 일차에서 중복 호출 방지
    }
    lastSavedDay = currentDay
    try {
      await saveGame(getSaveData())
    } catch (error) {
      console.error('자동저장 실패:', error)
    }
  }
}
