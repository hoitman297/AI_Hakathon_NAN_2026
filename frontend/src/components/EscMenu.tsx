import { Modal } from './Modal'

interface EscMenuProps {
  onResume: () => void
  onQuit: () => void
}

export function EscMenu({ onResume, onQuit }: EscMenuProps) {
  return (
    <Modal title="일시정지">
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10, minWidth: 220 }}>
        <button className="pixel-button pixel-button--accent" onClick={onResume}>
          계속하기
        </button>
        <button className="pixel-button pixel-button--danger" onClick={onQuit}>
          게임종료
        </button>
      </div>
    </Modal>
  )
}
