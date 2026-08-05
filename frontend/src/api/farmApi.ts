import { getAuthToken } from './authToken'
import { API_BASE_URL } from './config'
import type { SessionResponse } from './sessionApi'

export interface CropSummary {
  cropId: number
  name: string
  seedPrice: number
  growDays: number
  plantOrHarvestStamina: number
  sellPrice: number
  restoreHp: number
}

export interface FarmPlot {
  farmPlotId: number
  cropName: string
  plantedDay: number
  readyDay: number
  harvested: boolean
  readyToHarvest: boolean
}

export interface AvailableFruit {
  fruitId: number
  name: string
}

export interface ForageResult {
  fruitId: number
  fruitName: string
  staminaCurrent: number
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

export async function listCrops(sessionId: number): Promise<CropSummary[]> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/farm/crops`, {
    headers: authHeaders(),
  })
  return parseOrThrow<CropSummary[]>(response, '작물 목록을 불러오지 못했습니다.')
}

export async function listFarmPlots(sessionId: number): Promise<FarmPlot[]> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/farm/plots`, {
    headers: authHeaders(),
  })
  return parseOrThrow<FarmPlot[]>(response, '밭 상태를 불러오지 못했습니다.')
}

export async function plantCrop(sessionId: number, cropId: number): Promise<SessionResponse> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/farm/plant`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ cropId }),
  })
  return parseOrThrow<SessionResponse>(response, '파종에 실패했습니다.')
}

export async function harvestPlot(sessionId: number, farmPlotId: number): Promise<SessionResponse> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/farm/harvest`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ farmPlotId }),
  })
  return parseOrThrow<SessionResponse>(response, '수확에 실패했습니다.')
}

export async function listAvailableFruits(sessionId: number): Promise<AvailableFruit[]> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/forage/today`, {
    headers: authHeaders(),
  })
  return parseOrThrow<AvailableFruit[]>(response, '채집 가능한 과일 목록을 불러오지 못했습니다.')
}

export async function forageFruit(sessionId: number, fruitId: number): Promise<ForageResult> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/forage`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ fruitId }),
  })
  return parseOrThrow<ForageResult>(response, '채집에 실패했습니다.')
}
