import request from '@/utils/request';

export interface LearningTextbookPdfListItemVO {
  id: string;
  title: string;
  edition: string | null;
  syllabusVersionName: string | null;
  fileName: string;
  fileSize: number;
  uploadedTime: string;
}

export interface LearningTextbookPdfReaderVO {
  textbookId: string;
  title: string;
  fileName: string;
  readUrl: string;
  expiresAt: string;
}

export function listLearningTextbookPdfs(keyword?: string): Promise<LearningTextbookPdfListItemVO[]> {
  return request({ url: '/api/learning/textbooks', method: 'get', params: keyword ? { keyword } : undefined }).then(
    response => response.data
  );
}

export function getLearningTextbookPdfReader(textbookId: string): Promise<LearningTextbookPdfReaderVO> {
  return request({ url: `/api/learning/textbooks/${textbookId}/reader`, method: 'get' }).then(response => response.data);
}
