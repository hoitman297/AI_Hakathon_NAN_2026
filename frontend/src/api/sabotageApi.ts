import { getAuthToken } from './authToken'
import { API_BASE_URL } from './config'

export interface NightSummary {
  location: string
  summaryText: string | null
}

function authHeaders(): HeadersInit {
  const token = getAuthToken()
  if (!token) {
    throw new Error('로그인 토큰이 없습니다. 먼저 로그인해주세요.')
  }
  return { Authorization: `Bearer ${token}` }
}

/** 그날 밤 사보타주가 없었으면(6일차 이후 등) null. */
export async function getNightSummary(sessionId: number, day: number): Promise<NightSummary | null> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/sabotage/${day}`, {
    headers: authHeaders(),
  })
  if (response.status === 204) return null
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { error?: string } | null
    throw new Error(body?.error ?? '지난밤 소식을 불러오지 못했습니다.')
  }
  return (await response.json()) as NightSummary
}
