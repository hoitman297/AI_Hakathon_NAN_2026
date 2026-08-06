import { getAuthToken } from './authToken'
import { API_BASE_URL } from './config'

export interface NpcSummary {
  npcId: number
  name: string
  role: string
  currentLocation: string
}

export interface NpcDetail {
  npcId: number
  name: string
  role: string
  age: number
  personalityDesc: string
  speechStyle: string
  sampleLine: string
  affinityScore: number
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

export async function getNpcDetail(sessionId: number, npcId: number): Promise<NpcDetail> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/npcs/${npcId}`, {
    headers: authHeaders(),
  })
  if (!response.ok) {
    throw new Error('주민 정보를 불러오지 못했습니다.')
  }
  return (await response.json()) as NpcDetail
}
