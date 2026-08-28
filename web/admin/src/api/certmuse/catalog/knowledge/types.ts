import type { PageResult } from '@/api/types';

export interface SyllabusListVO {
  id: string;
  displayName: string;
  certificationId: string;
  certificationName: string;
  versionName: string;
  publishedDate: string | null;
  updateTime: string | null;
  latestKnowledgeImportTime: string | null;
  knowledgePointCount: number;
  status: 'available' | 'empty';
}

export interface SyllabusQuery {
  keyword?: string;
  certificationId?: string;
  versionName?: string;
  status?: 'available' | 'empty';
  pageNum?: number;
  pageSize?: number;
}

export interface SyllabusPublishedDateForm {
  publishedDate: string;
}

export interface KnowledgeTreeNodeVO {
  id: string;
  parentId: string | null;
  examSubjectId: string;
  examSubjectCode: string;
  syllabusNumber: string;
  syllabusTitle: string;
  treeDepth: number;
  sortOrder: number;
  importance: number | null;
  diagnosticEnabled: boolean;
  recommendationEnabled: boolean;
  status: string;
  hasChildren: boolean;
  createdTime: string;
  updatedTime: string;
}

export interface KnowledgeSubjectVO {
  id: string;
  subjectCode: string;
  subjectName: string;
  sortOrder: number;
  knowledgePointCount: number;
}

export interface KnowledgeTreeSummaryVO {
  syllabusVersionRecordCount: number;
  subjectCount: number;
  knowledgePointCount: number;
  directoryCount: number;
  leafCount: number;
}

export interface LatestKnowledgeImportVO {
  batchId: string;
  finishedTime: string;
  sourceFileName: string;
  generatedCount: number;
}

export interface KnowledgeTreeVO {
  syllabus: {
    id: string;
    certificationId: string;
    certificationName: string;
    versionName: string;
    publishedDate: string | null;
    createdTime: string;
    latestKnowledgeImport: LatestKnowledgeImportVO | null;
  };
  summary: KnowledgeTreeSummaryVO;
  subjects: KnowledgeSubjectVO[];
  nodes: KnowledgeTreeNodeVO[];
}

export type SyllabusListPage = PageResult<SyllabusListVO>;
