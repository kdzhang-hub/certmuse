const PROXIED_OSS_ORIGINS = new Set(['host.docker.internal:9000', 'minio:9000', '127.0.0.1:9000']);
const QUESTION_IMAGE_TOKEN = /\s*\[图片\s*\d+\]\s*\{\{\s*question-image\s*:\s*\d+\s*}}/gi;

/**
 * Routes Docker-internal MinIO URLs through the frontend reverse proxy.
 *
 * The original host is kept in the path so nginx can forward it as the Host
 * header. MinIO validates that header as part of the pre-signed URL signature.
 */
export function toQuestionImageUrl(sourceUrl: string) {
  try {
    const url = new URL(sourceUrl);
    if (!PROXIED_OSS_ORIGINS.has(url.host)) {
      return sourceUrl;
    }
    return `/oss-proxy/${url.host}${url.pathname}${url.search}${url.hash}`;
  } catch {
    return sourceUrl;
  }
}

/** Hides import-only image placeholders once their attached images are rendered separately. */
export function formatQuestionPreviewStem(stem: string) {
  return stem.replace(QUESTION_IMAGE_TOKEN, '').trim();
}
