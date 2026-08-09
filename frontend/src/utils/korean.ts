/** 한글 음절(가~힣) 하나가 받침(종성)으로 끝나는지 판별한다. */
function hasBatchim(char: string): boolean {
  const code = char.charCodeAt(0)
  if (code < 0xac00 || code > 0xd7a3) return false
  return (code - 0xac00) % 28 !== 0
}

/**
 * 명사 뒤에 서술격 조사 "이었다/였다"를 받침 유무에 맞게 붙인다.
 * 받침 있음(예: 전주인) → "전주인이었다", 받침 없음(예: 나주부) → "나주부였다".
 */
export function withWasCopula(noun: string): string {
  if (!noun) return noun
  const lastChar = noun[noun.length - 1]
  return hasBatchim(lastChar) ? `${noun}이었다` : `${noun}였다`
}
