import { useEffect, useRef } from 'react'
import Phaser from 'phaser'
import { MainScene } from '../game/MainScene'
import './VillagePreviewScreen.css'

interface VillagePreviewScreenProps {
  onBack: () => void
}

export function VillagePreviewScreen({ onBack }: VillagePreviewScreenProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const gameRef = useRef<Phaser.Game | null>(null)

  useEffect(() => {
    if (!containerRef.current || gameRef.current) return

    gameRef.current = new Phaser.Game({
      type: Phaser.AUTO,
      parent: containerRef.current,
      width: 1280,
      height: 720,
      backgroundColor: '#17201a',
      pixelArt: true,
      roundPixels: true,
      scale: {
        mode: Phaser.Scale.RESIZE,
        autoCenter: Phaser.Scale.CENTER_BOTH,
      },
      render: {
        antialias: false,
        pixelArt: true,
        roundPixels: true,
      },
      scene: [MainScene],
    })

    return () => {
      gameRef.current?.destroy(true)
      gameRef.current = null
    }
  }, [])

  return (
    <main className="village-preview">
      <div ref={containerRef} className="village-preview__canvas" />
      <button className="pixel-button village-preview__back" type="button" onClick={onBack}>
        타이틀로 돌아가기
      </button>
      <div className="village-preview__help">WASD / 방향키 이동 · 닭장 클릭</div>
    </main>
  )
}
