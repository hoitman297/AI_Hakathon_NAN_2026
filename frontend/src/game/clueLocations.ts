// 사보타주 발생 장소는 백엔드(CulpritProfileRegistry)에서 자유 텍스트로 내려온다
// (예: "마을회관", "정자", "상점(근무)", "자택 인근 텃밭" 등). MainScene의 지도 위 장소는
// 한정된 스팟(spot.key) 몇 개뿐이라, 텍스트에 포함된 키워드로 가장 가까운 스팟에 매칭한다.
// 상점/자택처럼 특정할 수 없는 경우엔 후보 스팟 중 아무 곳이나 방문해도 습득할 수 있게 한다.
const SPOT_KEYWORDS: Record<string, string[]> = {
  'village-hall': ['회관', '정자'],
  // "마을 어귀 순찰"은 지도 위 다리(마을 어귀에 위치)에 매칭한다 — 예전엔 village-hall로
  // 흘러들어가서 단서 ❗가 다리 근처가 아니라 멀리 떨어진 회관에 떠서 헷갈렸다.
  bridge: ['마을 어귀'],
  'produce-shop': ['상점'],
  'item-shop': ['상점'],
  'chicken-coop': ['양계장'],
  'watermelon-field': ['수박밭'],
  house1: ['자택', '텃밭'],
  house2: ['자택', '텃밭'],
}

export function matchesLocationSpot(location: string, spotKey: string): boolean {
  const keywords = SPOT_KEYWORDS[spotKey]
  if (!keywords) return false
  return keywords.some((keyword) => location.includes(keyword))
}

/** 지도 위에 실제로 존재하는 모든 장소 스팟 키. */
export const ALL_SPOT_KEYS = Object.keys(SPOT_KEYWORDS)

/** 미습득 단서들의 장소 문자열 중 하나라도 이 스팟과 매칭되면 포함한다. */
export function spotsWithPendingClue(locations: string[]): string[] {
  return ALL_SPOT_KEYS.filter((spotKey) => locations.some((location) => matchesLocationSpot(location, spotKey)))
}
