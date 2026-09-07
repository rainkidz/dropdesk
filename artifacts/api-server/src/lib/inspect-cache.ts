const inspectCache = new Map<string, { data: unknown; expiresAt: number }>();
const CACHE_TTL_MS = 5 * 60 * 1000;

export function getCachedInspect(key: string): unknown | null {
  const entry = inspectCache.get(key);
  if (!entry || Date.now() > entry.expiresAt) {
    inspectCache.delete(key);
    return null;
  }
  return entry.data;
}

export function setCachedInspect(key: string, data: unknown): void {
  inspectCache.set(key, { data, expiresAt: Date.now() + CACHE_TTL_MS });
  if (inspectCache.size > 100) {
    const now = Date.now();
    for (const [k, v] of inspectCache) {
      if (now > v.expiresAt) inspectCache.delete(k);
    }
  }
}
