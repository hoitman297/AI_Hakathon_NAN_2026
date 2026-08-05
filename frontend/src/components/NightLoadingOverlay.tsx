import './NightLoadingOverlay.css'

export function NightLoadingOverlay() {
  return (
    <div className="night-loading-overlay">
      <div className="night-loading-panel pixel-panel">
        <div className="night-loading-spinner" />
        <div className="night-loading-title">밤이 깊어가고 있습니다...</div>
        <p className="night-loading-text">
          마을에 어둠이 내려앉는 사이, 오늘 밤 사건을 준비하고 있습니다.
          <br />
          잠시만 기다려주세요.
        </p>
      </div>
    </div>
  )
}
