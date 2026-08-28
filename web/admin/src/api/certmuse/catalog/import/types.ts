import type { PageResult } from '@/api/types';

export type ImportStatus =
  | 'uploaded'
  | 'parsing'
  | 'validating'
  | 'waiting_confirm'
  | 'importing'
  | 'completed'
  | 'partial_failed'
  | 'failed'
  | 'cancelled';

export type ImportCurrentStage =
  | 'read_jsonl'
  | 'validate_schema'
  | 'validate_mapping'
  | 'validate_tree'
  | 'finalize_counts'
  | 'persist_knowledge_tree'
  | 'persist_questions'
  | 'persist_document_chunks';

export type ImportIssueSeverity = 'error' | 'warning';

export type ImportType = 'knowledge_point' | 'question' | 'textbook';

export type ImportIssueOrderColumn = 'lineNo' | 'severity' | 'issueCode' | 'createTime';

export interface SyllabusVersionOptionVO {
  id: string;
  certificationId?: string;
  label: string;
  /** 只有真实考纲已存在的版本才能作为题目、教材导入上下文。 */
  knowledgeTreeAvailable: boolean;
  knowledgePointCount: number;
}

export interface ExamSubjectOptionVO {
  id: string;
  subjectNo: 1 | 2 | 3;
  label: string;
}

export interface ImportContextOptionsVO {
  syllabusVersions: SyllabusVersionOptionVO[];
  examSubjects: ExamSubjectOptionVO[];
  certifications?: Array<{ id: string; label: string }>;
}

export interface QuestionImportContextOptionsVO {
  knowledgeSyllabusVersions: SyllabusVersionOptionVO[];
}

export interface DerivedQuestionSubjectVO {
  subjectNo: 1 | 2 | 3;
  examSubjectId: string;
  label: string;
}

export interface SubjectMappingForm {
  subjectNo: 1 | 2 | 3;
  examSubjectId: string;
}

export interface KnowledgePointImportCreateForm {
  file: File;
  importType: 'knowledge_point';
  certificationId?: string;
  syllabusVersionId?: string;
  subjectMappings: SubjectMappingForm[];
  templateVersion: 'knowledge_point/1.0';
  requestId: string;
}

export interface QuestionImportCreateForm {
  file: File;
  importType: 'question';
  knowledgeSyllabusVersionId: string;
  templateVersion: 'question-zip/1.0';
  requestId: string;
}

export type TextbookImportMode = 'create' | 'replace_draft';

export interface DefaultSubjectMappingVO {
  subjectNo: 1 | 2 | 3;
  examSubjectId: string;
}

export interface ReplaceableTextbookDraftVO {
  id: string;
  title: string;
  edition: string | null;
  syllabusVersionId: string;
}

export interface TextbookImportContextOptionsVO extends ImportContextOptionsVO {
  defaultSubjectMappings: DefaultSubjectMappingVO[];
  replaceableDrafts: ReplaceableTextbookDraftVO[];
}

export interface TextbookImportCreateForm {
  file: File;
  importType: 'document_chunk';
  templateVersion: 'document_chunk/1.0';
  mode: TextbookImportMode;
  syllabusVersionId: string;
  subjectMappings: DefaultSubjectMappingVO[];
  title?: string;
  edition?: string;
  documentId?: string;
  requestId: string;
}

export interface TextbookPreviewKnowledgePointVO {
  id: string;
  subjectNo: 1 | 2 | 3;
  code: string;
  title: string;
}

export interface TextbookImportPreviewDocumentVO {
  id: string;
  title: string;
  edition: string | null;
  syllabusVersionName: string;
}

export interface TextbookImportPreviewSummaryVO {
  totalChunks: number;
  validChunks: number;
  mappedChunks: number;
  unmappedChunks: number;
  knowledgeRelationCount: number;
  imageCount: number;
  errorCount: number;
  warningCount: number;
}

export interface TextbookImportPreviewChunkVO {
  lineNo: number;
  sourceKey: string;
  chunkOrder: number;
  heading: string | null;
  headingPath: string[];
  contentPreview: string;
  pageStart: number | null;
  pageEnd: number | null;
  knowledgePoints: TextbookPreviewKnowledgePointVO[];
  issueCount: number;
}

export interface TextbookImportPreviewVO {
  document: TextbookImportPreviewDocumentVO;
  summary: TextbookImportPreviewSummaryVO;
  directory: unknown[];
  chunks: PageResult<TextbookImportPreviewChunkVO>;
}

export interface ImportBatchVO {
  id: string;
  documentId?: string | null;
  importType: 'knowledge_point' | 'question' | 'document_chunk';
  mode?: TextbookImportMode | null;
  syllabusVersionId?: string;
  examSubjectId?: string;
  derivedSubjects?: DerivedQuestionSubjectVO[];
  knowledgeSyllabusVersionId?: string;
  templateVersion: string;
  status: ImportStatus;
  reused: boolean;
  createTime: string;
}

export interface ImportValidationAcceptedVO {
  id: string;
  status: ImportStatus;
  currentStage: ImportCurrentStage | null;
  accepted: boolean;
}

export interface ImportConfirmationAcceptedVO {
  id: string;
  status: 'importing' | 'completed';
  currentStage: ImportCurrentStage | null;
  accepted: boolean;
}

export interface ImportProgressVO {
  id: string;
  status: ImportStatus;
  currentStage: ImportCurrentStage | null;
  progressPercent: number;
  totalCount: number;
  validCount: number;
  warningCount: number;
  failedCount: number;
  startedTime: string | null;
  finishedTime: string | null;
  failureTraceId: string | null;
  knowledgeDiffRequired: boolean;
}

export type KnowledgeDiffAction = 'unchanged' | 'update' | 'move' | 'add' | 'delete';
export type KnowledgeDiffResolutionStatus = 'not_required' | 'pending' | 'manual_confirmed';
export type KnowledgeDiffDecision = 'approve' | 'reject';
export type KnowledgeDiffResolutionDecision = 'approve' | 'reject' | null;

export interface KnowledgeDiffQuery extends PageQuery {
  examSubjectId?: string;
  resolutionStatus?: KnowledgeDiffResolutionStatus;
  action?: KnowledgeDiffAction;
  excludeUnchanged?: boolean;
}

export interface KnowledgePointSummaryVO {
  id: string | null;
  syllabusNumber: string | null;
  syllabusTitle: string | null;
}

export interface KnowledgeDiffMatchEvidenceVO {
  titleScore: number | null;
  parentScore: number | null;
  descriptionScore: number | null;
  childrenScore: number | null;
  numberScore: number | null;
  totalScore: number | null;
  candidateGap: number | null;
  possibleMove: boolean;
  ambiguous: boolean;
}

export interface KnowledgeDiffRowVO {
  id: string;
  examSubjectId: string;
  subjectName: string;
  oldPoint: KnowledgePointSummaryVO | null;
  newPoint: KnowledgePointSummaryVO | null;
  suggestedPoint: KnowledgePointSummaryVO | null;
  confirmedPoint: KnowledgePointSummaryVO | null;
  action: KnowledgeDiffAction;
  resolutionStatus: KnowledgeDiffResolutionStatus;
  resolutionDecision: KnowledgeDiffResolutionDecision;
  parentDiffId: string | null;
  matchScore: number | null;
  evidence: KnowledgeDiffMatchEvidenceVO;
  changedFields: string[];
}

export interface KnowledgeDiffPageVO {
  rows: KnowledgeDiffRowVO[];
  total: number;
  unchangedCount: number;
  updateCount: number;
  moveCount: number;
  addCount: number;
  deleteCount: number;
  pendingCount: number;
  affectedQuestionCount: number;
  offlineQuestionCount: number;
}

export interface KnowledgeDiffResolutionForm {
  decision: KnowledgeDiffDecision;
  oldKnowledgePointId: string | null;
}

export interface KnowledgeDiffBatchResolutionForm extends KnowledgeDiffResolutionForm {
  diffId: string;
}

export interface KnowledgeDiffResolutionResultVO {
  batchId: string;
  resolvedCount: number;
  pendingCount: number;
  resolutionHash: string;
}

export interface ImportIssueVO {
  lineNo: number;
  sourceKey: string | null;
  fieldPath: string | null;
  severity: ImportIssueSeverity;
  issueCode: string;
  message: string;
  createTime: string;
}

export interface ImportIssueQuery extends PageQuery {
  severity?: ImportIssueSeverity;
  issueCode?: string;
  orderByColumn?: ImportIssueOrderColumn;
  isAsc?: 'asc' | 'desc' | 'ascending' | 'descending';
}

export interface ImportFieldErrorVO {
  field: string;
  code:
    | 'REQUIRED'
    | 'INVALID_FORMAT'
    | 'OUT_OF_RANGE'
    | 'DUPLICATE'
    | 'NOT_FOUND'
    | 'CERTIFICATION_MISMATCH'
    | 'UNSUPPORTED';
  message: string;
}

export interface ImportErrorVO {
  errorCode: string;
  retryable: boolean;
  traceId: string | null;
  fieldErrors: ImportFieldErrorVO[];
}

export interface ImportBatchSyllabusOptionVO {
  id: string;
  label: string;
}

export interface ImportBatchUploaderOptionVO {
  id: string;
  name: string;
}

export interface ImportBatchFilterOptionsVO {
  syllabusVersions: ImportBatchSyllabusOptionVO[];
  uploaders: ImportBatchUploaderOptionVO[];
}

export interface CompletedImportBatchQuery extends PageQuery {
  keyword?: string;
  importType?: 'all' | ImportType;
  syllabusVersionId?: string;
  uploaderId?: string;
  completedStartDate?: string;
  completedEndDate?: string;
  orderByColumn?: 'completedTime';
  isAsc?: 'asc' | 'desc';
}

export interface CompletedImportBatchVO {
  id: string;
  fileName: string;
  importType: ImportType;
  syllabusVersionId: string;
  syllabusLabel: string;
  uploaderId: string;
  uploaderName: string;
  status: 'completed';
  completedTime: string;
}

export interface CompletedImportBatchTypeCountsVO {
  all: number;
  knowledge_point: number;
  question: number;
  textbook: number;
}

export interface CompletedImportBatchPageVO {
  rows: CompletedImportBatchVO[];
  total: number;
  typeCounts: CompletedImportBatchTypeCountsVO;
}

export interface CompletedImportBatchDetailVO extends CompletedImportBatchVO {
  totalCount: number;
  validCount: number;
  warningCount: number;
  failedCount: number;
}
