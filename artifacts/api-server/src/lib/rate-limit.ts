const rateLimitWindow = new Map<string, number[]>();
const RATE_LIMIT_MAX = 10;
const RATE_LIMIT_MS = 60_000;

export function checkRateLimit(ip: string): boolean {
  const now = Date.now();
  const timestamps = rateLimitWindow.get(ip) ?? [];
  const recent = timestamps.filter((t) => now - t < RATE_LIMIT_MS);
  if (recent.length >= RATE_LIMIT_MAX) return false;
  recent.push(now);
  rateLimitWindow.set(ip, recent);
  return true;
}
