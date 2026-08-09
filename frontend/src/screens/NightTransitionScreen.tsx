import { useEffect, useRef, useState } from 'react'
import { getNightSummary, type NightSummary } from '../api/sabotageApi'
import './NightTransitionScreen.css'

interface NightTransitionScreenProps {
  sessionId: number
  day: number
  nextDay: number
  onContinue: () => void
}

type Phase = 'loading' | 'scene' | 'popup'

const SCENE_DESCRIPTIONS: Record<NightSummary['sabotageType'], { location: string; badge: string; text: string }> = {
  theft: {
    location: '농산물 상점 앞',
    badge: '도난 흔적 발견',
    text: '농산물 상점 앞에서 진열해 둔 작물 몇 개가\n감쪽같이 사라져 있었다.',
  },
  vandalism: {
    location: '수박밭',
    badge: '파손 흔적 발견',
    text: '수박밭에서 잘 익은 수박 몇 통이\n처참히 짓이겨져 있었다.',
  },
  sabotage: {
    location: '양계장',
    badge: '우리 문이 열려있다',
    text: '양계장 문이 활짝 열려 있고,\n닭 몇 마리가 밖을 헤매고 있었다.',
  },
}

export function NightTransitionScreen({ sessionId, day, nextDay, onContinue }: NightTransitionScreenProps) {
  const [summary, setSummary] = useState<NightSummary | null>(null)
  const [phase, setPhase] = useState<Phase>('loading')
  const [confirmed, setConfirmed] = useState(false)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    getNightSummary(sessionId, day)
      .then(data => {
        if (!data) {
          setPhase('popup')
          return
        }
        setSummary(data)
        setPhase('scene')
        timerRef.current = setTimeout(() => setPhase('popup'), 8000)
      })
      .catch(() => {
        setPhase('popup')
      })
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current)
    }
  }, [sessionId, day])

  const handleConfirm = () => {
    setConfirmed(true)
    setTimeout(onContinue, 800)
  }

  if (phase === 'loading') {
    return (
      <div className="ns-root">
        <p className="ns-loading-text">지난밤 소식을 불러오는 중...</p>
      </div>
    )
  }

  if (!summary || phase === 'popup') {
    const desc = summary ? SCENE_DESCRIPTIONS[summary.sabotageType] : null
    const bodyText = summary?.summaryText ?? desc?.text ?? '마을은 오늘 밤도 평온했던 것 같다.'

    return (
      <div className="ns-root">
        <div className="ns-popup pixel-panel">
          {summary && <div className="ns-popup-stamp">사건 발생</div>}
          <div className="ns-popup-day">{day}일차, 깊은 밤</div>
          <p className="ns-popup-body">{bodyText}</p>
          <div className="ns-popup-action">
            {!confirmed ? (
              <button className="pixel-button pixel-button--accent" onClick={handleConfirm}>
                {nextDay}일차 아침으로
              </button>
            ) : (
              <div className="ns-popup-confirmed">정신을 차려보니, 어느새 아침이 밝아 있었다.</div>
            )}
          </div>
        </div>
      </div>
    )
  }

  const type = summary.sabotageType
  const desc = SCENE_DESCRIPTIONS[type]

  const bgImage: Record<NightSummary['sabotageType'], string> = {
    theft: '/assets/background-assets/buildings/public/produce-shop.png',
    vandalism: '/assets/background-assets/facilities/watermelon-field-damaged.png',
    sabotage: '/assets/background-assets/facilities/chicken-coop-broken.png',
  }

  const walkSheet: Record<NightSummary['sabotageType'], string> = {
    theft: '/assets/characters/player-male/walk/east_walk_sheet.png',
    vandalism: '/assets/characters/player-male/walk/north_walk_sheet.png',
    sabotage: '/assets/characters/player-male/walk/west_walk_sheet.png',
  }

  return (
    <div className="ns-root">
      {/* ambient sky */}
      <div className="ns-sky" aria-hidden>
        <img
          className="ns-moon"
          src="/assets/moon-pixel.png"
          alt=""
        />
        <div className="ns-star" style={{ top: '12%', left: '9%', animationDelay: '0s' }} />
        <div className="ns-star" style={{ top: '18%', left: '22%', animationDelay: '.4s' }} />
        <div className="ns-star ns-star--lg" style={{ top: '9%', left: '38%', animationDelay: '.8s' }} />
        <div className="ns-star" style={{ top: '22%', left: '55%', animationDelay: '1.1s' }} />
        <div className="ns-star" style={{ top: '14%', left: '68%', animationDelay: '.2s' }} />
        <div className="ns-star" style={{ top: '26%', left: '83%', animationDelay: '.6s' }} />
        <div className="ns-star" style={{ top: '7%', left: '80%', animationDelay: '1.4s' }} />
        <div className="ns-firefly" style={{ top: '38%', left: '16%', animationDelay: '0s' }} />
        <div className="ns-firefly" style={{ top: '64%', left: '78%', animationDelay: '1s' }} />
        <div className="ns-firefly" style={{ top: '76%', left: '30%', animationDelay: '.6s' }} />
        <div className="ns-fog ns-fog--1" />
        <div className="ns-fog ns-fog--2" />
      </div>

      {/* flash stamp */}
      <div className="ns-flash-overlay" aria-hidden />

      {/* scene stage */}
      <div className="ns-stage-wrapper">
        <div className="ns-day-badge">{day}일차 · 깊은 밤</div>

        <div className="ns-stage" onClick={() => { if (timerRef.current) clearTimeout(timerRef.current); setPhase('popup') }}>
          <div className="ns-bg" style={{ backgroundImage: `url('${bgImage[type]}')` }} />

          <div className="ns-stage-tag">{desc.location}</div>
          <div className="ns-stage-badge-bottom">{desc.badge}</div>

          {type === 'theft' && (
            <>
              <img className="ns-crop ns-crop--potato" src="/assets/background-assets/growth/crops/potato-stage-3.png" alt="" />
              <img className="ns-crop ns-crop--carrot" src="/assets/background-assets/growth/crops/carrot-stage-3.png" alt="" />
              <img className="ns-crop ns-crop--strawberry" src="/assets/background-assets/growth/crops/strawberry-stage-3.png" alt="" />
              <img className="ns-bag" src="/assets/items/bag-level-1.png" alt="" />
            </>
          )}

          {type === 'sabotage' && (
            <img className="ns-chicken" src="/assets/background-assets/objects/chicken-front.png" alt="" />
          )}

          <div className="ns-player-glow" />
          <div
            className="ns-player"
            style={{ backgroundImage: `url('${walkSheet[type]}')` }}
            aria-hidden
          />

          <div className="ns-vignette" aria-hidden />
          <div className="ns-vignette-center" aria-hidden />
          <div className="ns-fog-ground" aria-hidden />

          <div className="ns-stamp">사건 발생</div>

          <div className="ns-click-hint">클릭하여 계속</div>
        </div>
      </div>
    </div>
  )
}
