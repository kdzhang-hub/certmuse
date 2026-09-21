const reloadKey = "certmuse:stale-chunk-reload-at";
const lastReloadAt = Number(sessionStorage.getItem(reloadKey) || 0);

// A deployment may replace content-hashed chunks while a tab is open. Reload
// at most once per 30 seconds so a genuinely broken current release is still
// visible instead of causing an infinite refresh loop.
if (Date.now() - lastReloadAt > 30_000) {
    sessionStorage.setItem(reloadKey, String(Date.now()));
    window.location.reload();
}

export {};
