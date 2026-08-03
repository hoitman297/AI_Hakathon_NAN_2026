export type NpcSlug = 'npc1' | 'npc2' | 'npc3' | 'npc4' | 'npc5' | 'npc6' | 'npc7'

export interface NpcRosterEntry {
  slug: NpcSlug
  name: string
  role: string
  portrait: string
}

export const NPC_ROSTER: NpcRosterEntry[] = [
  { slug: 'npc1', name: '현수동', role: '이장', portrait: '/assets/npc/npc1/idle/south.png' },
  { slug: 'npc2', name: '나주부', role: '농산물 상점 아내', portrait: '/assets/npc/npc2/idle/south.png' },
  { slug: 'npc3', name: '전주인', role: '아이템 상점 남편', portrait: '/assets/npc/npc3/idle/south.png' },
  { slug: 'npc4', name: '박영계', role: '양계장 주인', portrait: '/assets/npc/npc4/idle/south.png' },
  { slug: 'npc5', name: '명자유', role: '프리랜서', portrait: '/assets/npc/npc5/idle/south.png' },
  { slug: 'npc6', name: '김치준', role: '취준생 · 상점 직원', portrait: '/assets/npc/npc6/idle/south.png' },
  { slug: 'npc7', name: '나박수', role: '수박밭 주인', portrait: '/assets/npc/npc7/idle/south.png' },
]

/** 백엔드가 주는 NpcSummaryResponse.name(예: "현수동")으로 로컬 스프라이트를 찾는다. */
export function findRosterEntry(name: string): NpcRosterEntry | undefined {
  return NPC_ROSTER.find((entry) => name.includes(entry.name) || entry.name.includes(name))
}
