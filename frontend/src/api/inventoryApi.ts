import { getAuthToken } from './authToken'
import { API_BASE_URL } from './config'

export interface InventorySlot {
  slotIndex: number
  itemType: 'CROP' | 'FRUIT' | 'SHOP_ITEM'
  itemRefId: number
  itemName: string
  quantity: number
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

export async function listInventory(sessionId: number): Promise<InventorySlot[]> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/inventory`, {
    headers: authHeaders(),
  })
  return parseOrThrow<InventorySlot[]>(response, '인벤토리를 불러오지 못했습니다.')
}

/** 결과 안내 문구(예: "당근 섭취, 체력 8 회복")를 그대로 반환한다. */
export async function applyInventoryItem(
  sessionId: number,
  slotIndex: number,
  targetNpcId?: number,
): Promise<string> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/inventory/use`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ slotIndex, targetNpcId: targetNpcId ?? null }),
  })
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { error?: string } | null
    throw new Error(body?.error ?? '아이템을 사용하지 못했습니다.')
  }
  return response.text()
}
