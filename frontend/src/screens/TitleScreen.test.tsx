import type { ComponentProps } from 'react'
import { fireEvent, render, screen } from '@testing-library/react'
import { TitleScreen } from './TitleScreen'

function renderTitleScreen(overrides: Partial<ComponentProps<typeof TitleScreen>> = {}) {
  const props = {
    isLoggedIn: false,
    nickname: null,
    onLoginClick: vi.fn(),
    onLogoutClick: vi.fn(),
    onStartNewGame: vi.fn(),
    onContinue: vi.fn(),
    onVillagePreview: vi.fn(),
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

  it('calls onVillagePreview without requiring login', () => {
    const props = renderTitleScreen({ isLoggedIn: false })

    fireEvent.click(screen.getByRole('button', { name: '로그인 없이 마을 화면 보기' }))

    expect(props.onVillagePreview).toHaveBeenCalledTimes(1)
  })

  it('calls onLogoutClick from the account line when logged in', () => {
    const props = renderTitleScreen({ isLoggedIn: true, nickname: '철수' })

    fireEvent.click(screen.getByRole('button', { name: '로그아웃' }))

    expect(props.onLogoutClick).toHaveBeenCalledTimes(1)
  })
})
