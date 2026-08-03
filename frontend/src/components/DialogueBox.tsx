import { useState } from 'react'
import { findRosterEntry } from '../types/npc'
import './DialogueBox.css'

const DUMMY_LINES = [
  '(더미 대사) 오늘 날씨가 참 좋구먼.',
  '(더미 대사) 요즘 마을에 이상한 일이 자꾸 생긴다지?',
  '(더미 대사) 별일 없으면 다음에 또 이야기하자고.',
]

interface DialogueBoxProps {
  npcName: string
  npcRole: string
  onClose: () => void
}

export function DialogueBox({ npcName, npcRole, onClose }: DialogueBoxProps) {
  const [lineIndex, setLineIndex] = useState(0)
  const roster = findRosterEntry(npcName)
  const isLastLine = lineIndex === DUMMY_LINES.length - 1

  return (
    <div className="dialogue-overlay">
      <div className="dialogue-box pixel-panel">
        {roster && <img className="dialogue-portrait pixel-art" src={roster.portrait} alt="" />}
        <div className="dialogue-content">
          <div className="dialogue-name">
            {npcName} <span className="dialogue-role">· {npcRole}</span>
          </div>
          <p className="dialogue-text">{DUMMY_LINES[lineIndex]}</p>
          <div className="dialogue-actions">
            <button className="pixel-button" onClick={onClose}>
              닫기
            </button>
            <button
              className="pixel-button pixel-button--accent"
              onClick={() => (isLastLine ? onClose() : setLineIndex((i) => i + 1))}
            >
              {isLastLine ? '대화 종료' : '다음'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
