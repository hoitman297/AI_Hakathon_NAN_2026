import { fetchMe, login, logout, signup } from './authApi'
import { clearAuthToken, getAuthToken } from './authToken'

function mockFetchOnce(status: number, body: unknown) {
  const response = {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response))
  return response
}

describe('authApi', () => {
  beforeEach(() => {
    clearAuthToken()
    vi.unstubAllGlobals()
  })

  it('login success stores the returned token for later API calls', async () => {
    mockFetchOnce(200, { accountId: 1, username: 'u', nickname: 'n', token: 'tok-123' })

    const result = await login('u', 'pw')

    expect(result.token).toBe('tok-123')
    expect(getAuthToken()).toBe('tok-123')
  })

  it('login failure throws the server-provided error message and does not store a token', async () => {
    mockFetchOnce(401, { error: '아이디 또는 비밀번호가 틀렸습니다.' })

    await expect(login('u', 'wrong')).rejects.toThrow('아이디 또는 비밀번호가 틀렸습니다.')
    expect(getAuthToken()).toBeNull()
  })

  it('login failure without a parseable JSON body falls back to the default message', async () => {
    const response = {
      ok: false,
      status: 500,
      json: async () => {
        throw new Error('not json')
      },
    } as unknown as Response
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response))

    await expect(login('u', 'pw')).rejects.toThrow('아이디 또는 비밀번호가 올바르지 않습니다.')
  })

  it('signup success stores the returned token', async () => {
    mockFetchOnce(200, { accountId: 2, username: 'u2', nickname: 'n2', token: 'tok-456' })

    await signup('u2', 'pw', 'n2')

    expect(getAuthToken()).toBe('tok-456')
  })

  it('logout swallows network errors instead of throwing', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network down')))

    await expect(logout('tok')).resolves.toBeUndefined()
  })

  it('fetchMe returns account info on success', async () => {
    mockFetchOnce(200, { accountId: 3, username: 'u3', nickname: 'n3' })

    const account = await fetchMe('tok')

    expect(account.nickname).toBe('n3')
  })

  it('fetchMe failure throws the fallback message', async () => {
    mockFetchOnce(401, null)

    await expect(fetchMe('expired-tok')).rejects.toThrow('계정 정보를 불러오지 못했습니다.')
  })
})
