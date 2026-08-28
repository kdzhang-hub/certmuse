import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { QuestionDetailVO, QuestionListPage, QuestionPreviewVO, QuestionQuery, QuestionReviewMutationVO, QuestionSaveForm, QuestionSaveResultVO, QuestionSubmitReviewResultVO } from './types';

const baseUrl = '/api/admin/questions';
const requestId = () => crypto.randomUUID();
export const listQuestions = (params: QuestionQuery): AxiosPromise<QuestionListPage> => request({ url: baseUrl, method: 'get', params });
export const getQuestionDetail = (questionId: string, revisionId?: string): AxiosPromise<QuestionDetailVO> => request({ url: `${baseUrl}/${questionId}`, method: 'get', params: revisionId ? { revisionId } : undefined });
export const saveQuestionDraft = (questionId: string, data: QuestionSaveForm): AxiosPromise<QuestionSaveResultVO> => request({ url: `${baseUrl}/${questionId}`, method: 'put', data, headers: { 'X-Request-Id': requestId() } });
export const getQuestionPreview = (questionId: string, revisionId: string): AxiosPromise<QuestionPreviewVO> => request({ url: `${baseUrl}/${questionId}/preview`, method: 'get', params: { revisionId } });
export const deleteQuestion = (questionId: string): AxiosPromise<void> => request({ url: `${baseUrl}/${questionId}`, method: 'delete', headers: { 'X-Request-Id': requestId() } });
export const submitQuestionReview = (revisionId: string): AxiosPromise<QuestionSubmitReviewResultVO> => request({ url: `/api/admin/question-revisions/${revisionId}/submit-review`, method: 'post', headers: { 'X-Request-Id': requestId() } });
export const takeQuestionOffline = (revisionId: string): AxiosPromise<QuestionReviewMutationVO> => request({ url: `/api/admin/question-revisions/${revisionId}/offline`, method: 'post', headers: { 'X-Request-Id': requestId() } });
export const approveQuestionRevision = (revisionId: string): AxiosPromise<QuestionReviewMutationVO> => request({ url: `/api/admin/question-revisions/${revisionId}/approve`, method: 'post', headers: { 'X-Request-Id': requestId() } });
export const rejectQuestionRevision = (revisionId: string, reviewOpinion: string): AxiosPromise<QuestionReviewMutationVO> => request({ url: `/api/admin/question-revisions/${revisionId}/reject`, method: 'post', data: { reviewOpinion }, headers: { 'X-Request-Id': requestId() } });
