export interface RetryOptions {
  /** Total tries. Default 12 × 15 s ≈ 3 min: Render's free instance needs ~2 min to wake. */
  attempts?: number;
  delayMs?: number;
  fetchFn?: (url: string) => Promise<Response>;
  sleep?: (ms: number) => Promise<void>;
}

const wait = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

/**
 * Build-time fetch that tolerates a sleeping backend: retries network errors and 5xx
 * answers (what a waking host returns), but not 4xx, which would not fix itself.
 */
export async function fetchWithRetry(url: string, options: RetryOptions = {}): Promise<Response> {
  const { attempts = 12, delayMs = 15_000, fetchFn = (u) => fetch(u), sleep = wait } = options;
  for (let attempt = 1; attempt <= attempts; attempt++) {
    try {
      const response = await fetchFn(url);
      if (response.status < 500) {
        return response;
      }
    } catch {
      // Network error: the host may still be starting.
    }
    if (attempt < attempts) {
      await sleep(delayMs);
    }
  }
  throw new Error(`Prerender: cannot reach ${url} after ${attempts} attempts`);
}
