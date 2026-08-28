import type { PageResult } from '@/api/types';

export type QuestionType = 'CHOICE' | 'CASE' | 'ESSAY';
export type QuestionStatus = 'draft' | 'pending_review' | 'rejected' | 'published';

export interface QuestionListVO {
  questionId: string; revisionId: string; revisionNo: number; questionCode: string;
  syllabusVersionId: string; syllabusVersionName: string; stemSummary: string;
  examSubjectId: string; examSubjectName: string; questionType: QuestionType;
  difficulty: 'easy' | 'medium' | 'hard' | null; status: QuestionStatus; hasReviewOpinion?: boolean; updatedTime: string;
}
export interface QuestionImageVO { imageId: string; sortOrder: number; url: string; alt: string; }
export interface QuestionOptionVO { label: string; content: string; sortOrder?: number; }
/** Display label from the question-detail join; it is not part of the save payload. */
export interface KnowledgeBindingVO { knowledgePointId: string; knowledgePointLabel: string; relationRole: 'primary' | 'secondary'; sortOrder: number; }
export interface KnowledgeBindingSaveForm { knowledgePointId: string; relationRole: 'primary' | 'secondary'; sortOrder: number; }
export interface QuestionDetailVO {
  questionId: string; revisionId: string; revisionNo: number; rowVersion: string; questionCode: string;
  syllabusVersionId: string; syllabusVersionName: string; examSubjectId: string; examSubjectName: string;
  examSubjectEditable: boolean; examSubjectOptions: { id: string; label: string }[];
  questionType: QuestionType; difficulty: 'easy' | 'medium' | 'hard' | null; estimatedSeconds: number | null;
  stem: string; images: QuestionImageVO[]; options: QuestionOptionVO[];
  answer: { schemaVersion?: string; answerType: string; selectionMode?: string; value: string[] | string };
  analysis: string | null; commonMistakes: string | null; knowledgeBindings: KnowledgeBindingVO[];
  status: QuestionStatus; reviewOpinion?: string | null; editableMode: 'update_working_revision' | 'create_draft_revision' | 'readonly'; updatedTime: string;
}
export interface QuestionPreviewVO { questionId: string; revisionId: string; stem: string; images: QuestionImageVO[]; options: QuestionOptionVO[]; answer: { answerType: string; value: string[] | string }; analysis: string | null; reviewOpinion?: string | null; }
export interface QuestionSaveForm {
  baseRevisionId: string; rowVersion: string; examSubjectId: string; questionType: QuestionType;
  difficulty: 'easy' | 'medium' | 'hard' | null; estimatedSeconds?: number; stem: string;
  options: QuestionOptionVO[]; answer: { schemaVersion: '1.0'; answerType: 'option_keys' | 'reference_text'; selectionMode?: 'single'; value: string[] | string };
  analysis?: string; commonMistakes?: string; knowledgeBindings: KnowledgeBindingSaveForm[];
}
export interface QuestionSaveResultVO { questionId: string; revisionId: string; revisionNo: number; rowVersion: string; status: QuestionStatus; createdNewRevision: boolean; updatedTime: string; }
export interface QuestionReviewMutationVO { questionId: string; revisionId: string; status: 'draft' | 'rejected' | 'published'; rowVersion: string; }
/** Result of moving a draft revision into the review queue. */
export interface QuestionSubmitReviewResultVO { questionId: string; revisionId: string; status: 'pending_review'; rowVersion: string; }
export interface QuestionQuery { keyword?: string; certificationId?: string; syllabusVersionId?: string; examSubjectId?: string; knowledgePointId?: string; includeDescendants?: boolean; questionType?: QuestionType; difficulty?: string; status?: QuestionStatus; pageNum: number; pageSize: number; }
export type QuestionListPage = PageResult<QuestionListVO>;
