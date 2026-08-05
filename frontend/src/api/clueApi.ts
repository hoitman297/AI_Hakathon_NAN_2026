import { getAuthToken } from './authToken'
import { API_BASE_URL } from './config'

export interface ClueCard {
  clueId: number
  topic: string
  text: string
  clarified: boolean
}

export interface UnacquiredClue {
  clueId: number
  location: string
  day: number
  topic: string
}

const TOPIC_LABEL: Record<string, string> = {
  HAIR: '머리카락',
  BELONGING: '소지품',
  FOOTPRINT: '발자국',
  BLOOD: '혈흔',
  MARK: '자국',
}

export function topicLabel(topic: string): string {
  return TOPIC_LABEL[topic] ?? topic
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

async function parseOrThrow<T>(response: Response, fallbackMessage: string): Promise<T> {
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { error?: string } | null
    throw new Error(body?.error ?? fallbackMessage)
  }
  return (await response.json()) as T
}

export async function listAcquiredClues(sessionId: number): Promise<ClueCard[]> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/clues`, {
    headers: authHeaders(),
  })
  return parseOrThrow<ClueCard[]>(response, '습득한 단서 목록을 불러오지 못했습니다.')
}

export async function listUnacquiredClues(sessionId: number): Promise<UnacquiredClue[]> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/clues/unacquired`, {
    headers: authHeaders(),
  })
  return parseOrThrow<UnacquiredClue[]>(response, '미습득 단서 목록을 불러오지 못했습니다.')
}

export async function acquireClue(sessionId: number, clueId: number): Promise<ClueCard> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/clues/${clueId}/acquire`, {
    method: 'POST',
    headers: authHeaders(),
  })
  return parseOrThrow<ClueCard>(response, '단서를 습득하지 못했습니다.')
}

export async function clarifyClue(sessionId: number, clueId: number): Promise<ClueCard> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/clues/${clueId}/clarify`, {
    method: 'POST',
    headers: authHeaders(),
  })
  return parseOrThrow<ClueCard>(response, '단서를 명확화하지 못했습니다.')
}
