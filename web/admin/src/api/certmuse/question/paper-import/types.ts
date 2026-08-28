import type { PageResult } from '@/api/types';
import type { CollectionType } from '../collection/types';

export type PaperImportIssueSeverity = 'error' | 'warning';

export interface PaperImportCreateForm {
  /** Original single-ZIP upload, retained for callers outside the folder picker. */
  file?: File;
  /** ZIP files selected from one paper folder. */
  files?: File[];
  collectionName: string;
  collectionType: CollectionType;
  durationMinutes: number;
  certificationId: string;
  examYear?: number;
  examMonth?: number;
  paperTypeCode?: string;
  paperTypeName?: string;
  requestId: string;
}

export interface PaperImportBatchVO {
  id: string;
  documentId: string | null;
  importType: string;
  mode: string | null;
  syllabusVersionId: string | null;
  examSubjectId: string | null;
  knowledgeSyllabusVersionId: string | null;
  derivedSubjects: Array<{ subjectNo: string; examSubjectId: string; label: string }>;
  templateVersion: string | null;
  status: string;
  reused: boolean;
  createTime: string;
}

export interface PaperImportProgressVO {
  id: string;
  status: string;
  currentStage: string | null;
  progressPercent: number;
  totalCount: number;
  validCount: number;
  warningCount: number;
  failedCount: number;
  syllabusVersionId: string | null;
  syllabusVersionName: string | null;
  collectionId: string | null;
  revisionId: string | null;
  failureTraceId: string | null;
}

export interface PaperImportIssueVO {
  lineNo: number;
  sourceKey: string | null;
  fieldPath: string | null;
  severity: PaperImportIssueSeverity;
  issueCode: string;
  message: string;
  createTime: string;
}

export interface PaperImportIssueQuery {
  pageNum: number;
  pageSize: number;
  severity?: PaperImportIssueSeverity;
  issueCode?: string;
}

export interface PaperImportAcceptedVO {
  id: string;
  status: string;
  currentStage: string | null;
  accepted: boolean;
}

export interface PaperImportErrorVO {
  errorCode: string;
  retryable: boolean;
  traceId: string | null;
  fieldErrors: Array<{ field: string; code: string; message: string }>;
}

export type PaperImportIssuePage = PageResult<PaperImportIssueVO>;
