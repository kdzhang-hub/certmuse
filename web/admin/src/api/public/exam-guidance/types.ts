export type PublicQualificationLevel = 'HIGH' | 'MIDDLE' | 'LOW';

export interface PublicQualificationVO {
  certificationCode: string;
  certificationName: string;
  qualificationLevel: PublicQualificationLevel;
  nearestExamStartDate: string | null;
}

export interface PublicExamScheduleQualificationVO {
  certificationId: string;
  certificationCode: string;
  certificationName: string;
  level: PublicQualificationLevel;
  examStartDate: string;
  examEndDate: string;
  publicNote?: string | null;
}

export interface PublicQualificationContextVO {
  availability: 'OFFICIAL' | 'PENDING_OFFICIAL' | 'ENDED';
  periodCode?: string | null;
  qualifications: PublicExamScheduleQualificationVO[];
}

export interface PublicExamRegionVO {
  regionCode: string;
  regionName: string;
  localNoticeUrl: string;
}

export interface PublicExamRegionsVO {
  periodCode?: string | null;
  regions: PublicExamRegionVO[];
}
