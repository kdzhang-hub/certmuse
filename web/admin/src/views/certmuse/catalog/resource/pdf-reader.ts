/** Resolves a backend reader path through the frontend's configured API gateway. */
export function resolvePdfReaderUrl(readUrl: string, apiBase = '') {
  if (!readUrl.startsWith('/api/')) return readUrl;
  return `${apiBase.replace(/\/$/, '')}${readUrl}`;
}

/** Adds a PDF destination fragment without modifying the opaque reader-ticket path. */
export function toNativePdfPreviewUrl(readUrl: string, page: number) {
  const target = Math.max(1, Math.round(page || 1));
  const hashIndex = readUrl.indexOf('#');
  return `${hashIndex >= 0 ? readUrl.slice(0, hashIndex) : readUrl}#page=${target}`;
}

/**
 * Treats a nearly-white PDF.js sample as blank. JPX decode failures can leave a few
 * stray dark pixels even though the rendered page is visually empty.
 */
export function isBlankPdfSample(pixels: Uint8ClampedArray) {
  let opaquePixels = 0;
  let visiblePixels = 0;
  for (let index = 0; index < pixels.length; index += 4) {
    if (pixels[index + 3] === 0) continue;
    opaquePixels += 1;
    if (pixels[index] < 250 || pixels[index + 1] < 250 || pixels[index + 2] < 250) visiblePixels += 1;
  }
  return visiblePixels < Math.max(4, Math.ceil(opaquePixels * 0.005));
}
