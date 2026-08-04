import { getAuthToken } from './authToken'

const API_BASE_URL = 'http://localhost:8080'

export interface SabotageScheduleEntry {
  day: number
  location: string
  variant: string
}

export interface SaveData {
  day: number
  phase: 'day' | 'night' | 'accusation'
  playerHp: number
  inventory: Record<string, number>
  affection: Record<string, number>
  cluesCollected: string[]
  culpritId: string | null
  sabotageSchedule: SabotageScheduleEntry[]
  accusationAttempts: number
  chatHistory: Record<string, string[]>
}

export interface SaveResponse {
  saveData: SaveData
  endingState: string
  updatedAt: string | null
}

function authHeaders(): HeadersInit {
  const token = getAuthToken()
  if (!token) {
    throw new Error('로그인 토큰이 없습니다. 먼저 로그인해주세요.')
  }
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
  }
}

/** 일차 전환 시점에만 호출할 것 — 그 외(사보타주 판정, 고발 시도 등)에는 호출하지 않는다. */
export async function saveGame(data: SaveData): Promise<SaveResponse> {
  const response = await fetch(`${API_BASE_URL}/api/save`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })
  if (!response.ok) {
    throw new Error(`저장 실패 (${response.status})`)
  }
  return (await response.json()) as SaveResponse
}

/** 저장된 세이브가 없으면 서버가 신규 게임 기본값을 200으로 반환한다 (404 아님). */
export async function loadGame(): Promise<SaveResponse> {
  const response = await fetch(`${API_BASE_URL}/api/save`, {
    method: 'GET',
    headers: authHeaders(),
  })
  if (!response.ok) {
    throw new Error(`불러오기 실패 (${response.status})`)
  }
  return (await response.json()) as SaveResponse
}
