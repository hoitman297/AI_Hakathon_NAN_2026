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
  // Phaser 씬은 이 목록 로딩을 기다리지 않고 바로 시작돼서, 로딩이 끝나기 전에 NPC를 클릭하면
  // npcIdByNameRef가 아직 비어 있어 매핑을 못 찾는 경우가 있었다(새로고침 직후 등) — 그 요청
  // 자체를 들고 있다가, 클릭 시점에 아직 비어 있으면 이 요청이 끝나길 한 번 더 기다려서 재조회한다.
  const npcListPromiseRef = useRef<Promise<Map<string, number>> | null>(null)

  useEffect(() => {
    let cancelled = false
    const promise = listNpcsToday(session.sessionId).then((npcs) => {
      const map = new Map(npcs.map((npc) => [npc.name, npc.npcId]))
      if (!cancelled) npcIdByNameRef.current = map
      return map
    })
    npcListPromiseRef.current = promise
    promise.catch((err) => console.error('NPC 목록을 불러오지 못했습니다.', err))
    return () => {
      cancelled = true
    }
  }, [session.sessionId])

  useEffect(() => {
    if (!containerRef.current) return

    // 이동 중엔 대략 1초마다 handleMove가 호출되는데, 요청을 기다리지 않고 매번 새로 쏘면
    // 응답이 도착하는 순서가 요청 순서와 어긋날 수 있다(네트워크 지연 편차) — 그러면 나중에
    // 도착한 "더 과거" 요청의 응답이 최신 응답을 덮어써서 체력바가 95→35→95처럼 튀어 보였다.
    // 한 번에 하나의 요청만 보내고, 그사이 쌓인 이동 시간은 다음 요청에 합쳐서 보고한다 —
    // 응답을 항상 보낸 순서대로만 받게 되므로 이 역전이 원천적으로 불가능해진다.
    const pendingSecondsRef = { current: 0 }
    let moveInFlight = false

    async function handleMove(seconds: number) {
      pendingSecondsRef.current += seconds
      if (moveInFlight) return
      moveInFlight = true
      try {
        while (pendingSecondsRef.current > 0) {
          const toReport = pendingSecondsRef.current
          pendingSecondsRef.current = 0
          const updated = await moveSession(session.sessionId, toReport)
          setStamina(updated.staminaCurrent)
        }
      } catch (err) {
        console.error('이동 체력 반영에 실패했습니다.', err)
      } finally {
        moveInFlight = false
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
        const resolveNpcId = (npcId: number | undefined) => {
          if (npcId === undefined) {
            console.error(`"${name}"에 대응하는 npcId를 찾지 못했습니다.`)
            return
          }
          setDialogueNpc({ npcId, name, role })
        }

        const cachedNpcId = npcIdByNameRef.current.get(name)
        if (cachedNpcId !== undefined) {
          resolveNpcId(cachedNpcId)
          return
        }
        // 아직 NPC 목록 로딩이 안 끝났을 수 있다 — 그 요청이 끝나길 기다렸다가 한 번 더 조회.
        npcListPromiseRef.current
          ?.then((map) => resolveNpcId(map.get(name)))
          .catch(() => {})
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

  // 체력(낮 시간)이 다 떨어지면 버튼 없이 자동으로 밤으로 넘어간다. 대화창이 열려 있는 동안엔
  // 넘어가지 않고 기다린다 — 안 그러면 대화 도중에 화면이 통째로 밤으로 전환되며 대화창이
  // 갑자기 사라지는 문제가 있었다. 대화창은 체력이 바닥나면 스스로 마무리 인사를 보여주고
  // 닫히므로(DialogueBox 참고), dialogueNpc가 null이 되는 순간 이 effect가 다시 평가되어
  // 자연스럽게 밤으로 넘어간다.
  const nightTriggeredRef = useRef(false)
  useEffect(() => {
    if (stamina > 0 || advancing || nightTriggeredRef.current || dialogueNpc) return
    nightTriggeredRef.current = true
    onAdvanceDay()
  }, [stamina, advancing, onAdvanceDay, dialogueNpc])

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
          staminaCurrent={stamina}
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
