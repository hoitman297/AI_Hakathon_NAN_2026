/**
 * 배포 환경(Vercel)에서는 VITE_API_BASE_URL로 Render에 올라간 backend 주소를 주입하고,
 * 로컬 개발 시엔 값이 없으면 localhost:8080으로 대체한다.
 */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
