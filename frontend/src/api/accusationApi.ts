import { getAuthToken } from './authToken'
import { API_BASE_URL } from './config'

export interface AccuseResult {
  correct: boolean
  message: string
  sessionStatus: 'IN_PROGRESS' | 'SUCCESS' | 'BAD_ENDING'
}

export interface EndingResult {
  status: 'IN_PROGRESS' | 'SUCCESS' | 'BAD_ENDING'
  culpritNpcId: number | null
  culpritName: string | null
  endingStory: string
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

export async function accuseNpc(sessionId: number, accusedNpcId: number): Promise<AccuseResult> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/accuse`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ accusedNpcId }),
  })
  return parseOrThrow<AccuseResult>(response, '고발 처리에 실패했습니다.')
}

export async function getEnding(sessionId: number): Promise<EndingResult> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/ending`, {
    headers: authHeaders(),
  })
  return parseOrThrow<EndingResult>(response, '엔딩 정보를 불러오지 못했습니다.')
}
