import { clearPlayerProfile, getPlayerProfile, setPlayerProfile } from './playerProfile'

describe('playerProfile', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('returns null when nothing has been saved', () => {
    expect(getPlayerProfile()).toBeNull()
  })

  it('round-trips a saved profile', () => {
    setPlayerProfile({ nickname: '철수', gender: 'male' })

    expect(getPlayerProfile()).toEqual({ nickname: '철수', gender: 'male' })
  })

  it('clearPlayerProfile removes the saved profile', () => {
    setPlayerProfile({ nickname: '영희', gender: 'female' })

    clearPlayerProfile()

    expect(getPlayerProfile()).toBeNull()
  })

  it('returns null instead of throwing when the stored value is corrupted JSON', () => {
    localStorage.setItem('playerProfile', '{not valid json')

    expect(getPlayerProfile()).toBeNull()
  })
})
