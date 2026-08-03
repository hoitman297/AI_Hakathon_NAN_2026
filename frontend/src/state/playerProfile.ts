export type CharacterGender = 'male' | 'female'

export interface PlayerProfile {
  nickname: string
  gender: CharacterGender
}

const STORAGE_KEY = 'playerProfile'

export function getPlayerProfile(): PlayerProfile | null {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as PlayerProfile
  } catch {
    return null
  }
}

export function setPlayerProfile(profile: PlayerProfile): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(profile))
}

export function clearPlayerProfile(): void {
  localStorage.removeItem(STORAGE_KEY)
}
