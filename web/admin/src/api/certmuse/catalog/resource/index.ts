import request from '@/utils/request';
import type {
  TextbookChunkDetailVO,
  TextbookChunkPage,
  TextbookChunkQuery,
  TextbookChunkUpdateForm,
  TextbookDetailVO,
  TextbookListPage,
  TextbookOptionsVO,
  TextbookQuery,
  TextbookUpdateForm
} from './types';

const baseUrl = '/api/admin/catalog/textbooks';

export function listTextbooks(query: TextbookQuery): Promise<TextbookListPage> {
  return request({ url: baseUrl, method: 'get', params: query }).then(response => response.data);
}
export function getTextbookOptions(): Promise<TextbookOptionsVO> {
  return request({ url: `${baseUrl}/options`, method: 'get' }).then(response => response.data);
}
export function getTextbook(textbookId: string): Promise<TextbookDetailVO> {
  return request({ url: `${baseUrl}/${textbookId}`, method: 'get' }).then(response => response.data);
}
export function updateTextbook(textbookId: string, form: TextbookUpdateForm): Promise<void> {
  return request({ url: `${baseUrl}/${textbookId}`, method: 'put', data: form }).then(() => undefined);
}
export function publishTextbook(textbookId: string): Promise<void> {
  return request({ url: `${baseUrl}/${textbookId}/publish`, method: 'post' }).then(() => undefined);
}
export function takeTextbookOffline(textbookId: string): Promise<void> {
  return request({ url: `${baseUrl}/${textbookId}/offline`, method: 'post' }).then(() => undefined);
}
export function listTextbookChunks(textbookId: string, query: TextbookChunkQuery): Promise<TextbookChunkPage> {
  return request({ url: `${baseUrl}/${textbookId}/chunks`, method: 'get', params: query }).then(
    response => response.data
  );
}
export function getTextbookChunk(textbookId: string, chunkId: string): Promise<TextbookChunkDetailVO> {
  return request({ url: `${baseUrl}/${textbookId}/chunks/${chunkId}`, method: 'get' }).then(response => response.data);
}
export function updateTextbookChunk(
  textbookId: string,
  chunkId: string,
  form: TextbookChunkUpdateForm
): Promise<TextbookChunkDetailVO> {
  return request({ url: `${baseUrl}/${textbookId}/chunks/${chunkId}`, method: 'put', data: form }).then(
    response => response.data
  );
}
export function deleteTextbook(textbookId: string): Promise<void> {
  return request({ url: `${baseUrl}/${textbookId}`, method: 'delete' }).then(() => undefined);
}
export function deleteTextbookChunks(textbookId: string, chunkIds: string[]): Promise<void> {
  return request({
    url: `${baseUrl}/${textbookId}/chunks`,
    method: 'delete',
    params: { ids: chunkIds.join(',') }
  }).then(() => undefined);
}
