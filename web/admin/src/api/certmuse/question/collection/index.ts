import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  CollectionDetailVO,
  CollectionListPage,
  CollectionManagePage,
  CollectionMutationVO,
  CollectionQuery,
  CollectionRenameForm,
  CollectionRenameVO,
  CollectionRevisionDetailVO,
  CollectionSaveForm
} from './types';

const requestId = () => crypto.randomUUID();

export const listCollections = (params: CollectionQuery): AxiosPromise<CollectionListPage> =>
  request({ url: '/api/admin/collections', method: 'get', params });
export const listCollectionManagements = (params: CollectionQuery): AxiosPromise<CollectionManagePage> =>
  request({ url: '/api/admin/collections/manage', method: 'get', params });
export const getCollectionDetail = (collectionId: string): AxiosPromise<CollectionDetailVO> =>
  request({ url: `/api/admin/collections/${collectionId}`, method: 'get' });
export const getCollectionRevision = (revisionId: string): AxiosPromise<CollectionRevisionDetailVO> =>
  request({ url: `/api/admin/collection-revisions/${revisionId}`, method: 'get' });
export const createCollection = (data: CollectionSaveForm): AxiosPromise<CollectionMutationVO> =>
  request({ url: '/api/admin/collections', method: 'post', data, headers: { 'X-Request-Id': requestId() } });
export const saveCollectionRevision = (
  revisionId: string,
  data: CollectionSaveForm
): AxiosPromise<CollectionMutationVO> =>
  request({
    url: `/api/admin/collection-revisions/${revisionId}`,
    method: 'put',
    data,
    headers: { 'X-Request-Id': requestId() }
  });
export const renameCollection = (
  collectionId: string,
  data: CollectionRenameForm
): AxiosPromise<CollectionRenameVO> =>
  request({
    url: `/api/admin/collections/${collectionId}/name`,
    method: 'put',
    data,
    headers: { 'X-Request-Id': requestId() }
  });
export const createCollectionRevision = (
  collectionId: string,
  sourceRevisionId?: string
): AxiosPromise<CollectionMutationVO> =>
  request({
    url: `/api/admin/collections/${collectionId}/revisions`,
    method: 'post',
    data: sourceRevisionId ? { sourceRevisionId } : {},
    headers: { 'X-Request-Id': requestId() }
  });
export const submitCollectionReview = (revisionId: string): AxiosPromise<CollectionMutationVO> =>
  request({
    url: `/api/admin/collection-revisions/${revisionId}/submit-review`,
    method: 'post',
    headers: { 'X-Request-Id': requestId() }
  });
export const takeCollectionOffline = (revisionId: string): AxiosPromise<CollectionMutationVO> =>
  request({
    url: `/api/admin/collection-revisions/${revisionId}/offline`,
    method: 'post',
    headers: { 'X-Request-Id': requestId() }
  });
export const deleteCollectionDraft = (revisionId: string): AxiosPromise<void> =>
  request({
    url: `/api/admin/collection-revisions/${revisionId}`,
    method: 'delete',
    headers: { 'X-Request-Id': requestId() }
  });
export const approveCollectionRevision = (revisionId: string): AxiosPromise<CollectionMutationVO> =>
  request({ url: `/api/admin/collection-revisions/${revisionId}/approve`, method: 'post', headers: { 'X-Request-Id': requestId() } });
export const rejectCollectionRevision = (revisionId: string, reviewOpinion: string): AxiosPromise<CollectionMutationVO> =>
  request({ url: `/api/admin/collection-revisions/${revisionId}/reject`, method: 'post', data: { reviewOpinion }, headers: { 'X-Request-Id': requestId() } });

export type * from './types';
