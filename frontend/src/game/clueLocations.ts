// 사보타주 발생 장소는 백엔드(CulpritProfileRegistry)에서 자유 텍스트로 내려온다
// (예: "마을회관", "정자", "상점(근무)", "자택 인근 텃밭" 등). MainScene의 지도 위 장소는
// 한정된 스팟(spot.key) 몇 개뿐이라, 텍스트에 포함된 키워드로 가장 가까운 스팟에 매칭한다.
// 상점/자택처럼 특정할 수 없는 경우엔 후보 스팟 중 아무 곳이나 방문해도 습득할 수 있게 한다.
const SPOT_KEYWORDS: Record<string, string[]> = {
  'village-hall': ['회관', '정자', '마을 어귀'],
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
