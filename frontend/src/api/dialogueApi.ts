import { getAuthToken } from './authToken'
import { API_BASE_URL } from './config'

export interface DialogueMessage {
  sender: 'USER' | 'NPC'
  message: string
  createdAt: string
}

/** exchangesUsedToday는 대화창을 새로 열어도 초기화되지 않는, 서버가 세는 오늘 누적 대화 횟수. */
export interface DialogueHistoryResult {
  messages: DialogueMessage[]
  exchangesUsedToday: number
  maxExchangesPerDay: number
}

export interface DialogueReplyResult {
  npcReply: string
  affinityScore: number
  staminaCurrent: number
  exchangesUsedToday: number
  maxExchangesPerDay: number
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

export async function fetchDialogueHistory(sessionId: number, npcId: number): Promise<DialogueHistoryResult> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/npcs/${npcId}/dialogue`, {
    headers: authHeaders(),
  })
  return parseOrThrow<DialogueHistoryResult>(response, '대화 기록을 불러오지 못했습니다.')
}

export async function sendDialogueMessage(
  sessionId: number,
  npcId: number,
  message: string,
): Promise<DialogueReplyResult> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/npcs/${npcId}/dialogue`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ message }),
  })
  return parseOrThrow<DialogueReplyResult>(response, 'NPC가 응답하지 못했습니다.')
}
