import type { PageResult } from '@/api/types';

export type CollectionRevisionStatus = 'draft' | 'pending_review' | 'rejected' | 'published';
export type CollectionType = 'FIRST_DIAGNOSTIC' | 'PRACTICE' | 'SIMULATION' | 'PAST_PAPER';

export interface CollectionListVO {
  collectionId: string;
  collectionCode: string;
  revisionId: string;
  revisionNo: number;
  collectionName: string;
  collectionType: CollectionType;
  certificationId: string;
  certificationName: string;
  syllabusVersionId: string;
  syllabusVersionName: string;
  status: CollectionRevisionStatus;
  currentPublished: boolean;
  hasReviewOpinion: boolean;
  rowVersion: string;
  questionCount: number;
  totalReportScore: number;
  durationMinutes: number;
  updatedTime: string;
}

/** 题集管理页的父行；审核中心仍使用 CollectionListVO 的扁平结构。 */
export interface CollectionManageVO {
  collectionId: string;
  collectionCode: string;
  collectionName: string;
  collectionType: CollectionType;
  certificationId: string;
  certificationName: string;
  syllabusVersionId: string;
  syllabusVersionName: string;
  currentPublishedRevisionId: string | null;
  currentPublishedRevisionNo: number | null;
  updatedTime: string;
  revisions: CollectionRevisionSummaryVO[];
}

export interface CollectionRevisionSummaryVO {
  revisionId: string;
  revisionNo: number;
  status: CollectionRevisionStatus;
  currentPublished: boolean;
  rowVersion: string;
  questionCount: number;
  totalReportScore: number;
  hasReviewOpinion?: boolean;
  updatedTime: string;
}

export interface CollectionDetailVO {
  collectionId: string;
  collectionCode: string;
  displayRevision: CollectionRevisionDetailVO;
  currentPublishedRevision: CollectionRevisionSummaryVO | null;
  revisions: CollectionRevisionSummaryVO[];
  allowedActions: string[];
}

export interface CollectionRevisionDetailVO {
  revisionId: string;
  collectionId: string;
  collectionCode: string;
  revisionNo: number;
  collectionName: string;
  collectionType: CollectionType;
  certificationId: string;
  certificationName: string;
  syllabusVersionId: string;
  syllabusVersionName: string;
  durationMinutes: number;
  status: CollectionRevisionStatus;
  questionCount: number;
  totalReportScore: number;
  rowVersion: string;
  reviewOpinion: string | null;
  updatedTime: string;
  items: CollectionRevisionItemVO[];
}

export interface CollectionRevisionItemVO {
  itemOrder: number;
  questionRevisionId: string;
  questionId: string;
  questionCode: string;
  stem: string;
  questionType: string;
  difficulty: string;
  questionStatus: string;
  examSubjectId: string;
  examSubjectName: string;
  knowledgePointId: string | null;
  knowledgePointLabel: string | null;
  reportScore: number;
}

export interface CollectionQuery {
  keyword?: string;
  collectionType?: CollectionType;
  certificationId?: string;
  syllabusVersionId?: string;
  status?: CollectionRevisionStatus;
  statuses?: CollectionRevisionStatus[];
  pageNum: number;
  pageSize: number;
}

export interface CollectionSaveForm {
  collectionName: string;
  collectionType: CollectionType;
  certificationId: string;
  syllabusVersionId: string;
  durationMinutes: number;
  examYear?: number;
  examMonth?: number;
  paperTypeCode?: string;
  paperTypeName?: string;
  rowVersion?: string;
  items: Array<{ itemOrder: number; questionRevisionId: string; reportScore: number }>;
}

export interface CollectionRenameForm {
  collectionName: string;
}

export interface CollectionRenameVO {
  collectionId: string;
  collectionName: string;
}

export interface CollectionMutationVO {
  collectionId: string;
  collectionCode: string;
  revisionId: string;
  revisionNo: number;
  status: CollectionRevisionStatus;
  rowVersion: string;
  createdRevision: boolean;
  sourceRevisionId: string | null;
}

export interface CollectionPublishIssueVO {
  code: string;
  message: string;
  itemOrder: number | null;
}

export interface CollectionErrorData {
  errorCode: string;
  currentRevisionId: string | null;
  currentRowVersion: string | null;
  workingRevisionId: string | null;
  blockingIssues: CollectionPublishIssueVO[] | null;
  traceId: string;
}

export type CollectionListPage = PageResult<CollectionListVO>;
export type CollectionManagePage = PageResult<CollectionManageVO>;
