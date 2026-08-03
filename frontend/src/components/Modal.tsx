import type { ReactNode } from 'react'
import './Modal.css'

interface ModalProps {
  title?: string
  children: ReactNode
  actions?: ReactNode
}

export function Modal({ title, children, actions }: ModalProps) {
  return (
    <div className="modal-overlay">
      <div className="modal-panel pixel-panel">
        {title && <h2 className="modal-title">{title}</h2>}
        <div className="modal-body">{children}</div>
        {actions && <div className="modal-actions">{actions}</div>}
      </div>
    </div>
  )
}
