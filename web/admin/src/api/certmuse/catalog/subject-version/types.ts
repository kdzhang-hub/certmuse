export type QualificationLevel = 'HIGH' | 'MIDDLE' | 'LOW';
export type QualificationStatus = '0' | '1';

export interface SyllabusVersionVO {
  id: string;
  certificationId: string;
  versionName: string;
  publishedDate: string | null;
  referenceCount: number;
  createTime: string;
  updateTime: string;
}

export interface QualificationVO {
  id: string;
  certificationCode: string;
  certificationName: string;
  qualificationLevel: QualificationLevel;
  status: QualificationStatus;
  sortOrder: number;
  versionCount: number;
  referenceCount: number;
  createTime: string;
  updateTime: string;
  versions: SyllabusVersionVO[];
}

export interface QualificationPageQuery {
  keyword?: string;
  qualificationLevel?: QualificationLevel | '';
  status?: QualificationStatus | '';
  pageNum?: number;
  pageSize?: number;
}

export interface QualificationForm {
  certificationCode: string;
  certificationName: string;
  qualificationLevel: QualificationLevel;
  status: QualificationStatus;
  sortOrder: number;
}

export interface SyllabusVersionForm {
  versionName: string;
  publishedDate: string | null;
}
