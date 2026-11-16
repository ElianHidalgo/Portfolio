/** Test helper: an unsigned JWT-shaped string whose payload expires `secondsFromNow` from now. */
export function fakeJwt(secondsFromNow: number, sub = 'admin'): string {
  const encode = (value: object) =>
    btoa(JSON.stringify(value)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  const exp = Math.floor(Date.now() / 1000) + secondsFromNow;
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub, exp })}.signature`;
}
