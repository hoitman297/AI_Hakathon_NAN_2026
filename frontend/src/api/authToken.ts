/** 로그인 시 발급받은 토큰이 저장되는 localStorage 키. 로그인 플로우와 이 키를 맞춰야 한다. */
const AUTH_TOKEN_STORAGE_KEY = 'authToken'

export function getAuthToken(): string | null {
  return localStorage.getItem(AUTH_TOKEN_STORAGE_KEY)
}

export function setAuthToken(token: string): void {
  localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, token)
}

export function clearAuthToken(): void {
  localStorage.removeItem(AUTH_TOKEN_STORAGE_KEY)
}
