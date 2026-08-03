import { getAuthToken } from './authToken'

const API_BASE_URL = 'http://localhost:8080'

export interface NpcSummary {
  npcId: number
  name: string
  role: string
  currentLocation: string
}

function authHeaders(): HeadersInit {
  const token = getAuthToken()
  if (!token) {
    throw new Error('로그인 토큰이 없습니다. 먼저 로그인해주세요.')
  }
  return { Authorization: `Bearer ${token}` }
}

export async function listNpcsToday(sessionId: number): Promise<NpcSummary[]> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/npcs`, {
    headers: authHeaders(),
  })
  if (!response.ok) {
    throw new Error('NPC 목록을 불러오지 못했습니다.')
  }
  return (await response.json()) as NpcSummary[]
}
