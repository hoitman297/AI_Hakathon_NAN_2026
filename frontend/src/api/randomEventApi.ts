import { getAuthToken } from './authToken'
import { API_BASE_URL } from './config'

export interface RandomEvent {
  eventId: number
  day: number
  target: 'VILLAGE' | 'PLAYER'
  eventType: string
  description: string
  viewed: boolean
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

export async function listUnviewedEvents(sessionId: number): Promise<RandomEvent[]> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/events/unviewed`, {
    headers: authHeaders(),
  })
  return parseOrThrow<RandomEvent[]>(response, '랜덤 이벤트 목록을 불러오지 못했습니다.')
}

export async function markEventViewed(sessionId: number, eventId: number): Promise<RandomEvent> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/events/${eventId}/view`, {
    method: 'POST',
    headers: authHeaders(),
  })
  return parseOrThrow<RandomEvent>(response, '이벤트를 확인 처리하지 못했습니다.')
}
