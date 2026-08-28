import request from '@/utils/request';

export interface PublicTextbookCertificationVO {
  id: string;
  name: string;
}

export interface PublicTextbookPdfListItemVO {
  id: string;
  title: string;
  edition: string | null;
  syllabusVersionName: string | null;
  fileName: string;
  fileSize: number;
  uploadedTime: string;
}

export interface PublicTextbookPdfReaderVO {
  textbookId: string;
  title: string;
  fileName: string;
  readUrl: string;
  expiresAt: string;
}

export function listPublicTextbookCertifications(): Promise<PublicTextbookCertificationVO[]> {
  return request({ url: '/api/public/textbooks/certifications', method: 'get' }).then(response => response.data);
}

export function listPublicTextbookPdfs(
  certificationId: string,
  keyword?: string
): Promise<PublicTextbookPdfListItemVO[]> {
  return request({
    url: '/api/public/textbooks',
    method: 'get',
    params: { certificationId, ...(keyword ? { keyword } : {}) }
  }).then(response => response.data);
}

export function getPublicTextbookPdfReader(textbookId: string): Promise<PublicTextbookPdfReaderVO> {
  return request({ url: `/api/public/textbooks/${textbookId}/reader`, method: 'get' }).then(response => response.data);
}
