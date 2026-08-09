import { useEffect, useState } from 'react'
import { getNightSummary, type NightSummary } from '../api/sabotageApi'
import './NightTransitionScreen.css'

interface NightTransitionScreenProps {
  sessionId: number
  day: number
  nextDay: number
  onContinue: () => void
}

type Phase = 'loading' | 'scene' | 'popup'

/** 사보타주 유형별 뱃지 문구 + 실제 장소 데이터가 없거나 매칭 에셋이 없을 때의 대체값. */
const TYPE_META: Record<NightSummary['sabotageType'], { badge: string; fallbackLocation: string; fallbackImage: string }> = {
  theft: {
    badge: '도난 흔적 발견',
    fallbackLocation: '농산물 상점 앞',
    fallbackImage: '/assets/background-assets/buildings/public/produce-shop.png',
  },
  vandalism: {
    badge: '파손 흔적 발견',
    fallbackLocation: '수박밭',
    fallbackImage: '/assets/background-assets/facilities/watermelon-field-damaged.png',
  },
  sabotage: {
    badge: '훼손 흔적 발견',
    fallbackLocation: '양계장',
    fallbackImage: '/assets/background-assets/facilities/chicken-coop-broken.png',
  },
}

/** 백엔드가 내려주는 실제 사건 장소(CulpritProfileRegistry의 location 문자열)와 배경 에셋 매핑. */
const LOCATION_IMAGES: Record<string, string> = {
  '마을회관': '/assets/background-assets/buildings/public/village-hall.png',
  '정자': '/assets/background-assets/facilities/village-park.png',
  '마을 어귀 순찰': '/assets/background-assets/bridge-concrete.png',
  '상점': '/assets/background-assets/buildings/public/produce-shop.png',
  '상점(근무)': '/assets/background-assets/buildings/public/produce-shop.png',
  '상점(납품)': '/assets/background-assets/buildings/public/produce-shop.png',
  '양계장': '/assets/background-assets/facilities/chicken-coop-broken.png',
  '수박밭': '/assets/background-assets/facilities/watermelon-field-damaged.png',
  '자택': '/assets/background-assets/buildings/houses/house-1.png',
  '자택 인근 텃밭': '/assets/background-assets/buildings/houses/house-1.png',
}

export function NightTransitionScreen({ sessionId, day, nextDay, onContinue }: NightTransitionScreenProps) {
  const [summary, setSummary] = useState<NightSummary | null>(null)
  const [phase, setPhase] = useState<Phase>('loading')
  const [confirmed, setConfirmed] = useState(false)

  useEffect(() => {
    getNightSummary(sessionId, day)
      .then(data => {
        if (!data) {
          setPhase('popup')
          return
        }
        setSummary(data)
        setPhase('scene')
      })
      .catch(() => {
        setPhase('popup')
      })
  }, [sessionId, day])

  const handleConfirm = () => {
    setConfirmed(true)
    setTimeout(onContinue, 800)
  }

  if (phase === 'loading') {
    return (
      <div className="ns-root">
        <p className="ns-loading-text">모두가 잠드는 중...</p>
      </div>
    )
  }

  if (!summary || phase === 'popup') {
    const meta = summary ? TYPE_META[summary.sabotageType] : null
    const location = summary ? summary.location || meta!.fallbackLocation : null
    const bodyText = meta ? `${location}에서 ${meta.badge}.` : '마을은 오늘 밤도 평온했던 것 같다.'

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
  const meta = TYPE_META[type]
  const location = summary.location || meta.fallbackLocation
  const bgSrc = LOCATION_IMAGES[summary.location] ?? meta.fallbackImage

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

        <div className="ns-stage">
          <div className="ns-bg" style={{ backgroundImage: `url('${bgSrc}')` }} />

          <div className="ns-stage-tag">{location}</div>
          <div className="ns-stage-badge-bottom">{meta.badge}</div>

          {type === 'theft' && (
            <>
              {(location === '수박밭' || location === '자택 인근 텃밭') && (
                <>
                  <img className="ns-crop ns-crop--potato" src="/assets/background-assets/growth/crops/potato-stage-3.png" alt="" />
                  <img className="ns-crop ns-crop--carrot" src="/assets/background-assets/growth/crops/carrot-stage-3.png" alt="" />
                  <img className="ns-crop ns-crop--strawberry" src="/assets/background-assets/growth/crops/strawberry-stage-3.png" alt="" />
                </>
              )}
              <img className="ns-bag" src="/assets/items/bag-level-1.png" alt="" />
            </>
          )}

          {location === '양계장' && (
            <img className="ns-chicken" src="/assets/background-assets/objects/chicken-front.png" alt="" />
          )}

          {/* 마을회관/정자/상점/자택 등은 전용 파손(broken) 배경 에셋이 없어서, 부서진 울타리
              소품으로 "여기서 파손 사고가 났다"는 걸 대신 표시한다. */}
          {type === 'vandalism' && location !== '수박밭' && (
            <img className="ns-fence-broken" src="/assets/background-assets/objects/wood-fence-broken.png" alt="" />
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
        </div>

        <button className="pixel-button pixel-button--accent ns-skip-button" onClick={() => setPhase('popup')}>
          넘기기
        </button>
      </div>
    </div>
  )
}
