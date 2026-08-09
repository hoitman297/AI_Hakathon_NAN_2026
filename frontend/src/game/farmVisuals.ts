import type { FarmPlot } from '../api/farmApi'

/** 백엔드 CropMaster.name(한글) -> MainScene에 preload된 성장단계 텍스처 키 접두사. */
export const CROP_NAME_TO_KEY: Record<string, string> = {
  당근: 'carrot',
  감자: 'potato',
  딸기: 'strawberry',
  고구마: 'sweetPotato',
}

/** 백엔드 FruitMaster.name(한글) -> 맵 위 고정 나무/덤불 키. */
export const FRUIT_NAME_TO_KEY: Record<string, string> = {
  사과: 'apple',
  산딸기: 'raspberry',
  체리: 'cherry',
  감: 'persimmon',
}

export type CropStage = 1 | 2 | 3

/** 심은 날짜~수확 가능일 사이 진행도로 3단계 중 지금 보여줄 성장 단계를 근사한다. */
export function computeCropStage(plot: FarmPlot, currentDay: number): CropStage {
  if (plot.readyToHarvest || plot.harvested) return 3
  const totalDays = plot.readyDay - plot.plantedDay
  const elapsed = currentDay - plot.plantedDay
  if (totalDays <= 0 || elapsed <= 0) return 1
  return elapsed / totalDays < 0.5 ? 1 : 2
}

export function cropTextureKey(cropName: string, stage: CropStage): string | null {
  const key = CROP_NAME_TO_KEY[cropName]
  return key ? `crop-${key}-${stage}` : null
}
