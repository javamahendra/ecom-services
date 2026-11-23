const LAST_ACTIVITY_KEY = "form-field-last-activity";
export const SESSION_IDLE_TIMEOUT_MS = 30 * 60 * 1000;

export function getLastActivity() {
  try {
    const timestamp = Number(localStorage.getItem(LAST_ACTIVITY_KEY));
    return Number.isFinite(timestamp) && timestamp > 0 ? timestamp : null;
  } catch {
    return null;
  }
}

export function markSessionActivity(timestamp = Date.now()) {
  try {
    localStorage.setItem(LAST_ACTIVITY_KEY, String(timestamp));
  } catch {
    // Keep the current tab usable if browser storage is unavailable.
  }
  return timestamp;
}

export function clearSessionActivity() {
  try {
    localStorage.removeItem(LAST_ACTIVITY_KEY);
  } catch {
    // Session state still clears in memory when browser storage is unavailable.
  }
}

export function isSessionRecentlyActive(now = Date.now()) {
  const lastActivity = getLastActivity();
  return lastActivity === null || now - lastActivity < SESSION_IDLE_TIMEOUT_MS;
}
