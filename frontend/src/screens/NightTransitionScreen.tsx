import { useEffect, useRef, useState } from 'react'
import { getNightSummary, type NightSummary } from '../api/sabotageApi'
import './NightTransitionScreen.css'

// 사보타주 알림용 긴장감 BGM 에셋(04-sabotage-alert.wav)이 팀원 커밋으로 추가돼 있었는데
// 실제로 이 화면(사보타주 발생을 알리는 화면)에 연결된 적이 없었다.
const SABOTAGE_BGM_SRC = '/assets/audio/bgm-samples/04-sabotage-alert.wav'

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
  const audioRef = useRef<HTMLAudioElement>(null)
  // 아래 effect 안에서만 최신 onContinue를 읽으려고 ref로 든다 — App.tsx가 매 렌더마다 새
  // 화살표 함수를 내려주는데, 그걸 그대로 deps에 넣으면 이 effect(사보타주 여부 조회)가
  // 불필요하게 다시 실행돼 API를 중복 호출하게 된다.
  const onContinueRef = useRef(onContinue)
  useEffect(() => {
    onContinueRef.current = onContinue
  }, [onContinue])

  useEffect(() => {
    // 8·9일차로 넘어갈 때는 "잡히지 않은 범인이 사보타주를 일으켰습니다" 알림(DayScreen)이
    // 그날 진입 직후 따로 뜬다 — 여기서 "오늘 밤은 무사히 지나갔다"까지 겹쳐 보이면 같은 날
    // 두 팝업이 서로 모순돼 보인다. 그래서 이 밤에 정규 사보타주가 없었다면(대부분 그렇다,
    // 정규 사보타주는 1~5일차 밤에만 있음) 이 화면 자체를 건너뛰고 바로 낮으로 넘어간다.
    const skipNoSabotagePopup = nextDay === 8 || nextDay === 9
    getNightSummary(sessionId, day)
      .then(data => {
        if (!data) {
          if (skipNoSabotagePopup) {
            onContinueRef.current()
            return
          }
          setPhase('popup')
          return
        }
        setSummary(data)
        setPhase('scene')
      })
      .catch(() => {
        if (skipNoSabotagePopup) {
          onContinueRef.current()
          return
        }
        setPhase('popup')
      })
  }, [sessionId, day, nextDay])

  // 사건이 있었던 밤(scene/popup with summary)에만 긴장감 BGM 재생 — "평온했던 밤"(summary
  // 없음)엔 안 튼다. 브라우저 자동재생 정책 때문에 마운트 시 1차 시도 + 실패하면 첫
  // pointerdown/keydown에 이어서 재생(TitleScreen과 동일 패턴).
  useEffect(() => {
    if (phase === 'loading') return
    const audio = audioRef.current
    if (!audio || !summary) return

    const play = () => {
      audio.play()?.catch(() => undefined)
    }
    play()
    window.addEventListener('pointerdown', play, { once: true })
    window.addEventListener('keydown', play, { once: true })

    return () => {
      window.removeEventListener('pointerdown', play)
      window.removeEventListener('keydown', play)
      audio.pause()
      audio.currentTime = 0
    }
  }, [phase, summary])

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
    const bodyText = meta ? `${location}에서 ${meta.badge}.` : '오늘 밤은 무사히 지나갔다.'

    return (
      <div className="ns-root">
        {summary && <audio ref={audioRef} src={SABOTAGE_BGM_SRC} loop />}
        <div className="ns-popup pixel-panel">
          {summary && <div className="ns-popup-stamp">사건 발생</div>}
          {/* 사건이 벌어진 건 day의 밤이지만, 플레이어가 실제로 이 소식을 듣는 건 다음날
              아침(nextDay)이라 표시도 그 기준으로 맞춘다 — 첫 사보타주(1일차 밤)가
              "1일차"가 아니라 "2일차"로 보여야 한다는 피드백. */}
          <div className="ns-popup-day">{nextDay}일차, 깊은 밤</div>
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
      <audio ref={audioRef} src={SABOTAGE_BGM_SRC} loop />
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
        {/* ns-popup-day와 같은 이유로 nextDay 기준 표시 — 위 주석 참고. */}
        <div className="ns-day-badge">{nextDay}일차 · 깊은 밤</div>

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
