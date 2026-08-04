import { setAuthToken } from './authToken'
import { API_BASE_URL } from './config'

export interface AuthResult {
  accountId: number
  username: string
  nickname: string
  token: string
}

export interface AccountInfo {
  accountId: number
  username: string
  nickname: string
}

async function parseOrThrow<T>(response: Response, fallbackMessage: string): Promise<T> {
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { error?: string } | null
    throw new Error(body?.error ?? fallbackMessage)
  }
  return (await response.json()) as T
}

export async function signup(username: string, password: string, nickname: string): Promise<AuthResult> {
  const response = await fetch(`${API_BASE_URL}/api/auth/signup`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password, nickname }),
  })
  const result = await parseOrThrow<AuthResult>(response, '회원가입에 실패했습니다.')
  setAuthToken(result.token)
  return result
}

export async function login(username: string, password: string): Promise<AuthResult> {
  const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  const result = await parseOrThrow<AuthResult>(response, '아이디 또는 비밀번호가 올바르지 않습니다.')
  setAuthToken(result.token)
  return result
}

export async function logout(token: string): Promise<void> {
  await fetch(`${API_BASE_URL}/api/auth/logout`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  }).catch(() => undefined)
}

export async function fetchMe(token: string): Promise<AccountInfo> {
  const response = await fetch(`${API_BASE_URL}/api/auth/me`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return parseOrThrow<AccountInfo>(response, '계정 정보를 불러오지 못했습니다.')
}
