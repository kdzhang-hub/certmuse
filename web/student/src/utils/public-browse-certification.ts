const STORAGE_KEY = 'certmuse:public-browse-certification-id';

/** Stores only the current tab-session qualification used by public browsing pages. */
export function getPublicBrowseCertificationId(): string {
  return window.sessionStorage.getItem(STORAGE_KEY) ?? '';
}

export function setPublicBrowseCertificationId(certificationId: string): void {
  if (certificationId) {
    window.sessionStorage.setItem(STORAGE_KEY, certificationId);
    return;
  }
  window.sessionStorage.removeItem(STORAGE_KEY);
}
