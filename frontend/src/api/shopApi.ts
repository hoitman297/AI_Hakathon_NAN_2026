import { getAuthToken } from './authToken'
import { API_BASE_URL } from './config'
import type { SessionResponse } from './sessionApi'

export interface ShopItem {
  itemId: number
  name: string
  category: 'PERMANENT_EQUIPMENT' | 'CONSUMABLE'
  price: number
  effectDesc: string
  usageLimit: string
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

export async function listShopItems(sessionId: number): Promise<ShopItem[]> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/shop/items`, {
    headers: authHeaders(),
  })
  return parseOrThrow<ShopItem[]>(response, '상점 목록을 불러오지 못했습니다.')
}

export async function purchaseShopItem(sessionId: number, itemId: number): Promise<SessionResponse> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/shop/purchase`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ itemId }),
  })
  return parseOrThrow<SessionResponse>(response, '구매에 실패했습니다.')
}

export async function sellItem(
  sessionId: number,
  itemType: 'CROP' | 'FRUIT',
  itemRefId: number,
  quantity: number,
): Promise<SessionResponse> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/shop/sell`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ itemType, itemRefId, quantity }),
  })
  return parseOrThrow<SessionResponse>(response, '판매에 실패했습니다.')
}
