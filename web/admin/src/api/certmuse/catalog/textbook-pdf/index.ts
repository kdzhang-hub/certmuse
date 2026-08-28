import request from '@/utils/request';
import type { TextbookOriginalPdfInfoVO, TextbookPdfReaderVO } from './types';

const baseUrl = '/api/admin/catalog/textbooks';

export function getTextbookOriginalPdf(textbookId: string): Promise<TextbookOriginalPdfInfoVO> {
  return request({ url: `${baseUrl}/${textbookId}/original-pdf`, method: 'get' }).then(response => response.data);
}

export function uploadTextbookOriginalPdf(textbookId: string, file: File): Promise<TextbookOriginalPdfInfoVO> {
  const data = new FormData();
  data.append('file', file);
  return request({ url: `${baseUrl}/${textbookId}/original-pdf`, method: 'post', data }).then(response => response.data);
}

export function deleteTextbookOriginalPdf(textbookId: string): Promise<void> {
  return request({ url: `${baseUrl}/${textbookId}/original-pdf`, method: 'delete' }).then(() => undefined);
}

export function getTextbookPdfReader(textbookId: string): Promise<TextbookPdfReaderVO> {
  return request({ url: `${baseUrl}/${textbookId}/original-pdf/reader`, method: 'get' }).then(response => response.data);
}
