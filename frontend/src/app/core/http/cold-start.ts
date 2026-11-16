/**
 * Longer than the backend's worst cold start (~2 minutes on Render's free plan): giving up
 * earlier could make the visitor resend something the waking server still processes.
 */
export const COLD_START_TIMEOUT_MS = 130_000;

/** After this long without an answer, forms also explain that the server is waking up. */
export const SLOW_AFTER_MS = 5_000;
