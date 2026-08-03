import { useState } from 'react'
import type { FormEvent } from 'react'
import type { CharacterGender } from '../state/playerProfile'
import './CharacterCreateScreen.css'

interface CharacterCreateScreenProps {
  onConfirm: (nickname: string, gender: CharacterGender) => void
  onBack: () => void
  submitting: boolean
  error: string | null
}

export function CharacterCreateScreen({ onConfirm, onBack, submitting, error }: CharacterCreateScreenProps) {
  const [nickname, setNickname] = useState('')
  const [gender, setGender] = useState<CharacterGender>('male')

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!nickname.trim()) return
    onConfirm(nickname.trim(), gender)
  }

  return (
    <div className="character-create-screen">
      <form className="character-create-panel pixel-panel" onSubmit={handleSubmit}>
        <h2>캐릭터 만들기</h2>

        <label className="character-nickname-label">
          닉네임
          <input
            className="pixel-input"
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
            maxLength={12}
            placeholder="마을에서 불릴 이름"
            required
          />
        </label>

        <div className="character-gender-label">캐릭터</div>
        <div className="character-gender-options">
          <button
            type="button"
            className={`character-gender-card ${gender === 'male' ? 'is-selected' : ''}`}
            onClick={() => setGender('male')}
          >
            <span className="character-gender-badge character-gender-badge--male">남</span>
            남자 캐릭터
          </button>
          <button
            type="button"
            className={`character-gender-card ${gender === 'female' ? 'is-selected' : ''}`}
            onClick={() => setGender('female')}
          >
            <span className="character-gender-badge character-gender-badge--female">여</span>
            여자 캐릭터
          </button>
        </div>

        {error && <p className="pixel-error">{error}</p>}

        <div className="character-create-actions">
          <button type="submit" className="pixel-button pixel-button--accent" disabled={submitting}>
            {submitting ? '마을로 가는 중...' : '시작하기'}
          </button>
          <button type="button" className="pixel-button" onClick={onBack} disabled={submitting}>
            뒤로
          </button>
        </div>
      </form>
    </div>
  )
}
