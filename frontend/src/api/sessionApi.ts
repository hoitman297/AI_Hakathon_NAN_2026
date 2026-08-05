import { getAuthToken } from './authToken'
import { API_BASE_URL } from './config'

export interface SessionResponse {
  sessionId: number
  playerId: string | null
  accountId: number | null
  currentDay: number
  status: 'IN_PROGRESS' | 'SUCCESS' | 'BAD_ENDING'
  staminaCurrent: number
  staminaMax: number
  gold: number
  sneakersEquipped: boolean | null
  honestModeNpcId: number | null
  startedAt: string | null
  endedAt: string | null
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

/** playerId로 캐릭터 생성 화면에서 정한 닉네임을 넘긴다. 호출할 때마다 새 세션(+ 새 범인 배정)이 생성된다. */
export async function createSession(playerId: string): Promise<SessionResponse> {
  const response = await fetch(`${API_BASE_URL}/api/sessions`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ playerId }),
  })
  return parseOrThrow<SessionResponse>(response, '세션 생성에 실패했습니다.')
}

export async function getSession(sessionId: number): Promise<SessionResponse> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}`, {
    headers: authHeaders(),
  })
  return parseOrThrow<SessionResponse>(response, '세션 정보를 불러오지 못했습니다.')
}

/**
 * 이 계정에 진행 중인(IN_PROGRESS) 세션이 있으면 반환한다. localStorage에 저장된 세션 ID에
 * 의존하지 않고 서버가 직접 판단하므로, "게임종료"로 로컬 상태가 지워졌거나 다른 기기/브라우저로
 * 접속해도 "이어서하기"가 정확히 동작한다. 진행 중인 세션이 없으면 null.
 */
export async function getCurrentSession(): Promise<SessionResponse | null> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/current`, {
    headers: authHeaders(),
  })
  if (response.status === 204) return null
  return parseOrThrow<SessionResponse>(response, '진행 중인 게임을 확인하지 못했습니다.')
}

/** 낮 -> 밤(사보타주 발생) -> 다음날. 9일차에 고발 없이 호출하면 서버가 배드엔딩으로 종료한다. */
export async function advanceDay(sessionId: number): Promise<SessionResponse> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/day/advance`, {
    method: 'POST',
    headers: authHeaders(),
  })
  return parseOrThrow<SessionResponse>(response, '다음 날로 넘어가지 못했습니다.')
}

/**
 * 캐릭터가 실제로 움직인 경과 시간(초)을 보고해 체력을 소모시킨다.
 * 초당 0.15 소모(운동화 착용 시 0.12, 20% 감소)는 서버가 계산한다 — 여기서는 경과 시간만 넘긴다.
 */
export async function moveSession(sessionId: number, seconds: number): Promise<SessionResponse> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/move`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ seconds }),
  })
  return parseOrThrow<SessionResponse>(response, '이동 체력 반영에 실패했습니다.')
}
