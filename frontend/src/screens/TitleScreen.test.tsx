import type { ComponentProps } from 'react'
import { fireEvent, render, screen } from '@testing-library/react'
import { TitleScreen } from './TitleScreen'

// TitleScreen이 배경으로 마을 미리보기(MainScene)를 Phaser 캔버스로 마운트하는데, jsdom에는
// 실제 2D/WebGL 캔버스 컨텍스트가 없어서(canvas npm 패키지 미설치) 진짜 Phaser를 그대로 로드하면
// 모듈 로드 시점에 렌더러 기능 감지 코드가 곧바로 터진다. 이 테스트는 버튼/오버레이 UI만
// 검증하면 되므로 Phaser 자체를 가볍게 목킹한다.
vi.mock('phaser', () => {
  class MockScene {}
  class MockGame {
    destroy = vi.fn()
  }
  return {
    default: {
      AUTO: 0,
      Scale: { RESIZE: 0, CENTER_BOTH: 0 },
      Game: MockGame,
      Scene: MockScene,
    },
  }
})

function renderTitleScreen(overrides: Partial<ComponentProps<typeof TitleScreen>> = {}) {
  const props = {
    isLoggedIn: false,
    nickname: null,
    onLoginClick: vi.fn(),
    onLogoutClick: vi.fn(),
    onStartNewGame: vi.fn(),
    onContinue: vi.fn(),
    ...overrides,
  }
  render(<TitleScreen {...props} />)
  return props
}

describe('TitleScreen', () => {
  it('disables 게임시작 and hides 이어서하기 when logged out', () => {
    renderTitleScreen({ isLoggedIn: false })

    expect(screen.getByRole('button', { name: '게임시작' })).toBeDisabled()
    expect(screen.queryByRole('button', { name: '이어서하기' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '로그인' })).toBeEnabled()
  })

  it('calls onLoginClick when the login button is clicked', () => {
    const props = renderTitleScreen({ isLoggedIn: false })

    fireEvent.click(screen.getByRole('button', { name: '로그인' }))

    expect(props.onLoginClick).toHaveBeenCalledTimes(1)
  })

  it('shows the nickname and enables 게임시작/이어서하기 when logged in', () => {
    renderTitleScreen({ isLoggedIn: true, nickname: '철수' })

    expect(screen.getByRole('button', { name: '게임시작' })).toBeEnabled()
    expect(screen.getByRole('button', { name: '이어서하기' })).toBeInTheDocument()
    expect(screen.getByText(/철수님 접속 중/)).toBeInTheDocument()
  })

  it('calls onStartNewGame / onContinue when logged in', () => {
    const props = renderTitleScreen({ isLoggedIn: true, nickname: '철수' })

    fireEvent.click(screen.getByRole('button', { name: '게임시작' }))
    fireEvent.click(screen.getByRole('button', { name: '이어서하기' }))

    expect(props.onStartNewGame).toHaveBeenCalledTimes(1)
    expect(props.onContinue).toHaveBeenCalledTimes(1)
  })

  it('calls onLogoutClick from the account line when logged in', () => {
    const props = renderTitleScreen({ isLoggedIn: true, nickname: '철수' })

    fireEvent.click(screen.getByRole('button', { name: '로그아웃' }))

    expect(props.onLogoutClick).toHaveBeenCalledTimes(1)
  })
})
