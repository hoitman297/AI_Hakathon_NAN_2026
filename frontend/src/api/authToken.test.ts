import { clearAuthToken, getAuthToken, setAuthToken } from './authToken'

describe('authToken', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('returns null when no token has been set', () => {
    expect(getAuthToken()).toBeNull()
  })

  it('setAuthToken persists the token for later retrieval', () => {
    setAuthToken('abc-123')

    expect(getAuthToken()).toBe('abc-123')
  })

  it('clearAuthToken removes a previously stored token', () => {
    setAuthToken('abc-123')

    clearAuthToken()

    expect(getAuthToken()).toBeNull()
  })
})
