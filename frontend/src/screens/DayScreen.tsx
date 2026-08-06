import { useEffect, useRef, useState } from 'react'
import Phaser from 'phaser'
import { gameConfig } from '../game/config'
import { DayScene, type DaySceneInitData } from '../game/DayScene'
import { StaminaBar } from '../components/StaminaBar'
import { DialogueBox } from '../components/DialogueBox'
import { EscMenu } from '../components/EscMenu'
import { InventoryHubPanel } from '../components/InventoryHubPanel'
import { NightLoadingOverlay } from '../components/NightLoadingOverlay'
import { moveSession, type SessionResponse } from '../api/sessionApi'
import { listNpcsToday } from '../api/npcApi'
import { villageAssets } from '../assets/asset-manifest'
import './DayScreen.css'

// 백엔드 GameConstants.FIRST_ACCUSATION_DAY / LAST_ACCUSATION_DAY 와 맞춰야 한다.
// 이 값을 조회하는 API가 없어 프론트에 그대로 하드코딩했다.
const FIRST_ACCUSATION_DAY = 7
const LAST_ACCUSATION_DAY = 9

interface DayScreenProps {
  session: SessionResponse
  advancing: boolean
  advanceError: string | null
  onAdvanceDay: () => void
  onOpenAccusation: () => void
  onQuitToTitle: () => void
}

export function DayScreen({
  session,
  advancing,
  advanceError,
  onAdvanceDay,
  onOpenAccusation,
  onQuitToTitle,
}: DayScreenProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const gameRef = useRef<Phaser.Game | null>(null)
  const [stamina, setStamina] = useState(() => session.staminaCurrent)
  const [gold] = useState(() => session.gold)
  const [dialogueNpc, setDialogueNpc] = useState<{ npcId: number; name: string; role: string } | null>(null)
  const [escOpen, setEscOpen] = useState(false)
  const [inventoryOpen, setInventoryOpen] = useState(false)
  const sneakersEquipped = session.sneakersEquipped ?? false

  // NPC 이름 -> 실제 백엔드 npcId. Phaser 씬은 로컬 스프라이트 로스터(이름 기준)만 알고 있어서,
  // 대화 API를 호출하려면 이 매핑을 통해 클릭된 NPC 이름을 npcId로 바꿔줘야 한다.
  const npcIdByNameRef = useRef<Map<string, number>>(new Map())

  useEffect(() => {
    let cancelled = false
    listNpcsToday(session.sessionId)
      .then((npcs) => {
        if (cancelled) return
        npcIdByNameRef.current = new Map(npcs.map((npc) => [npc.name, npc.npcId]))
      })
      .catch((err) => console.error('NPC 목록을 불러오지 못했습니다.', err))
    return () => {
      cancelled = true
    }
  }, [session.sessionId])

  useEffect(() => {
    if (!containerRef.current) return

    // 이동으로 실제 경과한 시간(초)을 서버에 보고하고, 서버가 계산한 값(운동화 착용 시 20% 할인 반영)으로
    // 로컬 체력 표시를 다시 맞춘다 — 프레임마다 로컬로 미리 깎아둔 값은 예측치일 뿐, 서버 응답이 최종값이다.
    async function handleMove(seconds: number) {
      try {
        const updated = await moveSession(session.sessionId, seconds)
        setStamina(updated.staminaCurrent)
      } catch (err) {
        console.error('이동 체력 반영에 실패했습니다.', err)
      }
    }

    const game = new Phaser.Game({ ...gameConfig, parent: containerRef.current })
    gameRef.current = game
    game.scene.add('DayScene', DayScene, false)
    const initData: DaySceneInitData = {
      day: session.currentDay,
      staminaCurrent: session.staminaCurrent,
      staminaMax: session.staminaMax,
      sneakersEquipped: session.sneakersEquipped ?? false,
      onStaminaChange: setStamina,
      onMove: handleMove,
      onNpcClick: (name, role) => {
        const npcId = npcIdByNameRef.current.get(name)
        if (npcId === undefined) {
          console.error(`"${name}"에 대응하는 npcId를 찾지 못했습니다.`)
          return
        }
        setDialogueNpc({ npcId, name, role })
      },
    }
    game.scene.start('DayScene', initData)

    return () => {
      game.destroy(true)
      gameRef.current = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session.currentDay])

  useEffect(() => {
    const scene = gameRef.current?.scene.getScene('DayScene') as DayScene | undefined
    scene?.setInputLocked(!!dialogueNpc || escOpen || inventoryOpen || advancing)
  }, [dialogueNpc, escOpen, inventoryOpen, advancing])

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key !== 'Escape') return
      if (dialogueNpc) {
        setDialogueNpc(null)
        return
      }
      if (inventoryOpen) {
        setInventoryOpen(false)
        return
      }
      setEscOpen((open) => !open)
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [dialogueNpc, inventoryOpen])

  // 체력(낮 시간)이 다 떨어지면 버튼 없이 자동으로 밤으로 넘어간다.
  const nightTriggeredRef = useRef(false)
  useEffect(() => {
    if (stamina > 0 || advancing || nightTriggeredRef.current) return
    nightTriggeredRef.current = true
    onAdvanceDay()
  }, [stamina, advancing, onAdvanceDay])

  const canAccuse =
    session.status === 'IN_PROGRESS' &&
    session.currentDay >= FIRST_ACCUSATION_DAY &&
    session.currentDay <= LAST_ACCUSATION_DAY

  return (
    <div className="day-screen">
      <div className="day-hud">
        <div className="day-hud-left">
          <div className="day-day">{session.currentDay}일차</div>
          <div className="day-gold">골드: {gold}</div>
          {sneakersEquipped && (
            <img className="day-sneaker-icon pixel-art" src={villageAssets.items.sneakers} alt="운동화 착용 중" />
          )}
        </div>
        <div className="day-hud-actions">
          <button className="pixel-button" onClick={() => setInventoryOpen(true)}>
            인벤토리
          </button>
          {canAccuse && (
            <button className="pixel-button pixel-button--danger" onClick={onOpenAccusation}>
              범인 고발하기
            </button>
          )}
        </div>
      </div>

      {advanceError && <p className="day-advance-error pixel-error">{advanceError}</p>}

      <div className="day-canvas-wrap">
        <StaminaBar value={stamina} max={session.staminaMax} />
        <div ref={containerRef} className="day-canvas-inner" />
      </div>

      <p className="day-hint">방향키 / WASD로 이동, NPC에게 가까이 가서 클릭하면 대화하세요. ESC로 메뉴를 엽니다.</p>

      {dialogueNpc && (
        <DialogueBox
          key={dialogueNpc.npcId}
          sessionId={session.sessionId}
          npcId={dialogueNpc.npcId}
          npcName={dialogueNpc.name}
          npcRole={dialogueNpc.role}
          onStaminaChange={setStamina}
          onClose={() => setDialogueNpc(null)}
        />
      )}

      {inventoryOpen && (
        <InventoryHubPanel
          sessionId={session.sessionId}
          onStaminaChange={(restored) => setStamina((prev) => Math.min(session.staminaMax, prev + restored))}
          onClose={() => setInventoryOpen(false)}
        />
      )}

      {escOpen && <EscMenu onResume={() => setEscOpen(false)} onQuit={onQuitToTitle} />}

      {advancing && <NightLoadingOverlay />}
    </div>
  )
}
