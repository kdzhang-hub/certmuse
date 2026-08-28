import type { PageResult } from '@/api/types';

export type TextbookStatus = 'draft' | 'pending_review' | 'approved' | 'rejected' | 'published' | 'offline';

export interface TextbookQuery extends PageQuery {
  title?: string;
  certificationId?: string;
  syllabusVersionId?: string;
  edition?: string;
  status?: TextbookStatus;
  createBy?: string;
  beginCreateTime?: string;
  endCreateTime?: string;
  orderByColumn?: 'createTime' | 'updateTime' | 'title' | 'chunkCount';
  isAsc?: 'asc' | 'desc';
}

export interface TextbookListItemVO {
  id: string;
  certificationId: string;
  title: string;
  syllabusVersionId: string;
  syllabusVersionName: string;
  certificationName: string;
  edition: string | null;
  status: TextbookStatus;
  chunkCount: number;
  mappedChunkCount: number;
  unmappedChunkCount: number;
  knowledgePointCount: number;
  latestImportBatchId: string | null;
  latestImportStatus: string | null;
  createBy: string | null;
  createByName: string | null;
  createTime: string;
  updateTime: string;
  deletable: boolean;
  deleteDisabledReason: string | null;
}

export interface TextbookOptionsVO {
  certifications: { id: string; label: string }[];
  statuses: { value: TextbookStatus; label: string }[];
  creators: { id: string; label: string }[];
}

export interface TextbookStatisticsVO {
  chunkCount: number;
  mappedChunkCount: number;
  unmappedChunkCount: number;
  knowledgePointCount: number;
  imageCount: number;
}

export interface TextbookDetailVO {
  id: string;
  title: string;
  syllabusVersionId: string;
  syllabusVersionName: string;
  documentType: 'textbook';
  edition: string | null;
  status: TextbookStatus;
  statistics: TextbookStatisticsVO;
  latestImportBatch: {
    id: string;
    sourceFileName: string;
    sourceFileSize: number;
    sourceFileHash: string;
    status: string;
    validCount: number;
    warningCount: number;
    failedCount: number;
    createTime: string;
    finishedTime: string | null;
  } | null;
  submittedByName: string | null;
  submittedTime: string | null;
  reviewedByName: string | null;
  reviewedTime: string | null;
  reviewComment: string | null;
  publishedByName: string | null;
  publishedTime: string | null;
  createBy: string | null;
  createByName: string | null;
  createTime: string;
  updateTime: string;
  deletable: boolean;
  deleteDisabledReason: string | null;
}

export interface TextbookChunkQuery extends PageQuery {
  knowledgePointId?: string;
  examSubjectId?: string;
  includeDescendants?: boolean;
  hasKnowledgePoint?: boolean;
  keyword?: string;
}

export interface TextbookSourceLocatorVO {
  sourceType: string | null;
  sourceKey: string | null;
  lineStart: number | null;
  lineEnd: number | null;
}

export interface TextbookKnowledgePointVO {
  id: string;
  subjectNo: number | null;
  code: string;
  title: string;
}

export interface TextbookChunkListItemVO {
  id: string;
  documentId: string;
  chunkOrder: number;
  heading: string | null;
  headingPath: string[];
  contentPreview: string;
  pageStart: number | null;
  pageEnd: number | null;
  sourceLocator: TextbookSourceLocatorVO;
  knowledgePoints: TextbookKnowledgePointVO[];
  mapped: boolean;
}

export interface TextbookChunkDetailVO {
  id: string;
  documentId: string;
  chunkOrder: number;
  heading: string | null;
  headingPath: string[];
  content: string;
  contentHash: string;
  sourceLocator: TextbookSourceLocatorVO;
  knowledgePoints: TextbookKnowledgePointVO[];
  updateTime: string;
  editable: boolean;
  deletable: boolean;
  operationDisabledReason: string | null;
}

export interface TextbookChunkUpdateForm {
  heading: string | null;
  content: string;
  updateTime: string;
  knowledgePointIds: string[];
}

export interface TextbookUpdateForm {
  title: string;
  certificationId: string;
}

export type TextbookListPage = PageResult<TextbookListItemVO>;
export type TextbookChunkPage = PageResult<TextbookChunkListItemVO>;
