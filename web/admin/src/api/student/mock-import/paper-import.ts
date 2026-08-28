/** Mock-preview adapter for paper imports, selected only by an explicit alias. */
export type * from '../../certmuse/question/paper-import/types';

import {
  confirmMockPaperImport,
  createMockPaperImport,
  getMockPaperImportQualifications,
  getMockPaperImportProgress,
  listMockPaperImportIssues,
  validateMockPaperImport
} from '../../certmuse/question/paper-import/mock';

export const paperImportMockEnabled = true;
export const getPaperImportQualifications = getMockPaperImportQualifications;
export const createPaperImport = createMockPaperImport;
export const validatePaperImport = validateMockPaperImport;
export const getPaperImportProgress = getMockPaperImportProgress;
export const listPaperImportIssues = listMockPaperImportIssues;
export const confirmPaperImport = confirmMockPaperImport;
