/**
 * Mock-preview adapter for the catalog import dialog.
 *
 * Production imports resolve to the real endpoint module. Vite selects this
 * adapter only for an explicit preview build.
 */
export * from '../../certmuse/catalog/import/index';

import {
  confirmMockKnowledgePointImport,
  createMockKnowledgePointImport,
  createMockQuestionImport,
  createMockTextbookImport,
  getMockImportContextOptions,
  getMockImportProgress,
  getMockQuestionImportContextOptions,
  getMockTextbookImportPreview,
  getMockTextbookImportContextOptions,
  listMockImportIssues,
  validateMockImport
} from '../../certmuse/catalog/import/mock';

export const importMockEnabled = true;

export const getImportContextOptions = getMockImportContextOptions;
export const getQuestionImportContextOptions = getMockQuestionImportContextOptions;
export const getTextbookImportContextOptions = getMockTextbookImportContextOptions;
export const createKnowledgePointImport = createMockKnowledgePointImport;
export const createQuestionImport = createMockQuestionImport;
export const createTextbookImport = createMockTextbookImport;
export const validateImport = validateMockImport;
export const confirmKnowledgePointImport = confirmMockKnowledgePointImport;
export const confirmImport = confirmMockKnowledgePointImport;
export const getImportProgress = getMockImportProgress;
export const getTextbookImportPreview = getMockTextbookImportPreview;
export const listImportIssues = listMockImportIssues;
