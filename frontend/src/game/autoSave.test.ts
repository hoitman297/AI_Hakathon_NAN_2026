import { createDayChangeAutoSave } from './autoSave'
import { saveGame, type SaveData, type SaveResponse } from '../api/saveApi'

vi.mock('../api/saveApi', () => ({
  saveGame: vi.fn(),
}))

const mockedSaveGame = vi.mocked(saveGame)

function sampleSaveData(day: number): SaveData {
  return {
    day,
    phase: 'day',
    playerHp: 100,
    inventory: {},
    affection: {},
    cluesCollected: [],
    culpritId: null,
    sabotageSchedule: [],
    accusationAttempts: 0,
    chatHistory: {},
  }
}

function sampleResponse(day: number): SaveResponse {
  return { saveData: sampleSaveData(day), endingState: 'in_progress', updatedAt: null }
}

describe('createDayChangeAutoSave', () => {
  beforeEach(() => {
    mockedSaveGame.mockReset()
  })

  it('saves once when the day actually changes', async () => {
    mockedSaveGame.mockResolvedValue(sampleResponse(2))
    const onDayChanged = createDayChangeAutoSave(() => sampleSaveData(2))

    await onDayChanged(2)

    expect(mockedSaveGame).toHaveBeenCalledTimes(1)
  })

  it('does not save again for a repeated call with the same day', async () => {
    mockedSaveGame.mockResolvedValue(sampleResponse(2))
    const onDayChanged = createDayChangeAutoSave(() => sampleSaveData(2))

    await onDayChanged(2)
    await onDayChanged(2)

    expect(mockedSaveGame).toHaveBeenCalledTimes(1)
  })

  it('saves again once the day actually changes', async () => {
    mockedSaveGame.mockResolvedValue(sampleResponse(2))
    const onDayChanged = createDayChangeAutoSave(() => sampleSaveData(2))

    await onDayChanged(2)
    await onDayChanged(3)

    expect(mockedSaveGame).toHaveBeenCalledTimes(2)
  })

  it('swallows save errors instead of throwing (auto-save must not break the game loop)', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    mockedSaveGame.mockRejectedValue(new Error('network down'))
    const onDayChanged = createDayChangeAutoSave(() => sampleSaveData(4))

    await expect(onDayChanged(4)).resolves.toBeUndefined()
  })
})
