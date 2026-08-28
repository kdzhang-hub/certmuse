const PROXIED_OSS_ORIGINS = new Set(['host.docker.internal:9000', 'minio:9000', '127.0.0.1:9000']);
const QUESTION_IMAGE_TOKEN =
  /\s*(?:\[图片\s*\d+\]\s*\{\{\s*question-image\s*:\s*\d+\s*}}|\[图片\s*:\s*\{\{\s*question-image\s*:\s*\d+\s*}}[^\]\r\n]*])/gi;

/** Routes Docker-only or loopback MinIO links through the same-origin OSS proxy. */
export function toQuestionImageUrl(sourceUrl: string) {
  try {
    const url = new URL(sourceUrl);
    if (!PROXIED_OSS_ORIGINS.has(url.host)) return sourceUrl;
    return `/oss-proxy/${url.host}${url.pathname}${url.search}${url.hash}`;
  } catch {
    return sourceUrl;
  }
}

/** Hides import markers after their attached images are rendered below the stem. */
export function formatQuestionStem(stem: string) {
  return stem.replace(QUESTION_IMAGE_TOKEN, '').trim();
}
