import { useEffect, useState } from 'react'
import {
  listCrops,
  listFarmPlots,
  plantCrop,
  harvestPlot,
  listAvailableFruits,
  forageFruit,
  type CropSummary,
  type FarmPlot,
  type AvailableFruit,
} from '../api/farmApi'
import './FarmPanel.css'

interface FarmPanelProps {
  sessionId: number
  onStaminaChange: (value: number) => void
  onClose: () => void
}

type Tab = 'plant' | 'harvest' | 'forage'

export function FarmPanel({ sessionId, onStaminaChange, onClose }: FarmPanelProps) {
  const [tab, setTab] = useState<Tab>('plant')
  const [crops, setCrops] = useState<CropSummary[] | null>(null)
  const [plots, setPlots] = useState<FarmPlot[] | null>(null)
  const [fruits, setFruits] = useState<AvailableFruit[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [busyKey, setBusyKey] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  useEffect(() => {
    listCrops(sessionId)
      .then(setCrops)
      .catch((err: unknown) => setLoadError(err instanceof Error ? err.message : '작물 목록을 불러오지 못했습니다.'))
  }, [sessionId])

  function loadPlots() {
    listFarmPlots(sessionId)
      .then(setPlots)
      .catch((err: unknown) => setLoadError(err instanceof Error ? err.message : '밭 상태를 불러오지 못했습니다.'))
  }

  function loadFruits() {
    listAvailableFruits(sessionId)
      .then(setFruits)
      .catch((err: unknown) => setLoadError(err instanceof Error ? err.message : '채집 가능 목록을 불러오지 못했습니다.'))
  }

  useEffect(() => {
    if (tab === 'harvest' && plots === null) loadPlots()
    if (tab === 'forage' && fruits === null) loadFruits()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab])

  async function handlePlant(crop: CropSummary) {
    setMessage(null)
    setLoadError(null)
    setBusyKey(`plant-${crop.cropId}`)
    try {
      const updated = await plantCrop(sessionId, crop.cropId)
      onStaminaChange(updated.staminaCurrent)
      setMessage(`${crop.name}을(를) 심었습니다. (${updated.currentDay + crop.growDays}일차 이후 수확 가능)`)
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : '파종에 실패했습니다.')
    } finally {
      setBusyKey(null)
    }
  }

  async function handleHarvest(plot: FarmPlot) {
    setMessage(null)
    setLoadError(null)
    setBusyKey(`harvest-${plot.farmPlotId}`)
    try {
      const updated = await harvestPlot(sessionId, plot.farmPlotId)
      onStaminaChange(updated.staminaCurrent)
      setMessage(`${plot.cropName}을(를) 수확했습니다.`)
      loadPlots()
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : '수확에 실패했습니다.')
    } finally {
      setBusyKey(null)
    }
  }

  async function handleForage(fruit: AvailableFruit) {
    setMessage(null)
    setLoadError(null)
    setBusyKey(`forage-${fruit.fruitId}`)
    try {
      const result = await forageFruit(sessionId, fruit.fruitId)
      onStaminaChange(result.staminaCurrent)
      setMessage(`${result.fruitName}을(를) 채집했습니다.`)
      loadFruits()
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : '채집에 실패했습니다.')
    } finally {
      setBusyKey(null)
    }
  }

  return (
    <div className="farm-overlay">
      <div className="farm-panel pixel-panel">
        <h2>농장</h2>

        <div className="farm-tabs">
          <button
            className={`pixel-button ${tab === 'plant' ? 'pixel-button--accent' : ''}`}
            onClick={() => setTab('plant')}
          >
            파종
          </button>
          <button
            className={`pixel-button ${tab === 'harvest' ? 'pixel-button--accent' : ''}`}
            onClick={() => setTab('harvest')}
          >
            수확
          </button>
          <button
            className={`pixel-button ${tab === 'forage' ? 'pixel-button--accent' : ''}`}
            onClick={() => setTab('forage')}
          >
            채집
          </button>
        </div>

        {loadError && <p className="pixel-error">{loadError}</p>}
        {message && <p className="farm-message">{message}</p>}

        <div className="farm-list">
          {tab === 'plant' &&
            (crops === null ? (
              <p className="farm-status">불러오는 중...</p>
            ) : (
              crops.map((crop) => (
                <div key={crop.cropId} className="farm-item-row">
                  <div className="farm-item-info">
                    <div className="farm-item-name">{crop.name}</div>
                    <div className="farm-item-desc">
                      성장 {crop.growDays}일 · 파종 체력 {crop.plantOrHarvestStamina} · 수확 시 판매가 {crop.sellPrice}G
                    </div>
                  </div>
                  <button
                    className="pixel-button pixel-button--accent"
                    disabled={busyKey === `plant-${crop.cropId}`}
                    onClick={() => handlePlant(crop)}
                  >
                    심기
                  </button>
                </div>
              ))
            ))}

          {tab === 'harvest' &&
            (plots === null ? (
              <p className="farm-status">불러오는 중...</p>
            ) : plots.length === 0 ? (
              <p className="farm-status">심어둔 작물이 없습니다.</p>
            ) : (
              plots.map((plot) => (
                <div key={plot.farmPlotId} className="farm-item-row">
                  <div className="farm-item-info">
                    <div className="farm-item-name">{plot.cropName}</div>
                    <div className="farm-item-desc">
                      {plot.harvested
                        ? '수확 완료'
                        : plot.readyToHarvest
                          ? '수확 가능'
                          : `${plot.readyDay}일차부터 수확 가능`}
                    </div>
                  </div>
                  <button
                    className="pixel-button pixel-button--accent"
                    disabled={!plot.readyToHarvest || plot.harvested || busyKey === `harvest-${plot.farmPlotId}`}
                    onClick={() => handleHarvest(plot)}
                  >
                    수확
                  </button>
                </div>
              ))
            ))}

          {tab === 'forage' &&
            (fruits === null ? (
              <p className="farm-status">불러오는 중...</p>
            ) : fruits.length === 0 ? (
              <p className="farm-status">오늘은 채집할 과일이 없습니다.</p>
            ) : (
              fruits.map((fruit) => (
                <div key={fruit.fruitId} className="farm-item-row">
                  <div className="farm-item-info">
                    <div className="farm-item-name">{fruit.name}</div>
                  </div>
                  <button
                    className="pixel-button pixel-button--accent"
                    disabled={busyKey === `forage-${fruit.fruitId}`}
                    onClick={() => handleForage(fruit)}
                  >
                    채집
                  </button>
                </div>
              ))
            ))}
        </div>

        <button className="pixel-button farm-close" onClick={onClose}>
          닫기
        </button>
      </div>
    </div>
  )
}
