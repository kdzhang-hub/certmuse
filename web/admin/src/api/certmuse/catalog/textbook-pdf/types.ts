export interface TextbookOriginalPdfInfoVO {
  available: boolean;
  fileName: string | null;
  fileSize: number | null;
  fileHash: string | null;
  uploadedTime: string | null;
}

export interface TextbookPdfReaderVO {
  textbookId: string;
  title: string;
  fileName: string;
  readUrl: string;
  expiresAt: string;
}
