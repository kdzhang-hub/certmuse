export type ExamGuidanceStatus = 'draft' | 'published' | 'superseded';
export type ExamHalf = 'H1' | 'H2';
export type ExamRegionStatus = 'enabled' | 'disabled';
export type ExamRegionType = 'PROVINCE' | 'MUNICIPALITY' | 'AUTONOMOUS_REGION' | 'CITY_SPECIAL' | 'CORPS';

export interface ExamPeriodQuery extends PageQuery {
  examYear?: number;
  half?: ExamHalf | '';
  status?: ExamGuidanceStatus | '';
  periodCode?: string;
}

export interface ExamPeriodVO {
  id: string;
  periodCode: string;
  examYear: number;
  half: ExamHalf;
  status: ExamGuidanceStatus;
  revisionNo: number;
  officialSourceUrl: string;
  sourcePublishedAt: string;
  rowVersion: number;
  updatedTime: string;
  scheduleCount: number;
  registrationCount: number;
}

export interface ExamPeriodDetailVO extends ExamPeriodVO {
  supersedesPeriodId?: string | null;
  createdTime: string;
  publishedBy?: string | null;
  publishedTime?: string | null;
  schedules: ExamScheduleVO[];
  registrations: ExamRegionRegistrationVO[];
}

export interface ExamPeriodCreateForm {
  periodCode: string;
  examYear: number;
  half: ExamHalf;
  officialSourceUrl: string;
  sourcePublishedAt: string;
}

export interface ExamPeriodUpdateForm {
  officialSourceUrl: string;
  sourcePublishedAt: string;
  expectedRowVersion: number;
}

export interface ExamSessionVO {
  id?: string;
  examSubjectId: string;
  subjectName?: string;
  sessionCode: string;
  startTime: string;
  endTime: string;
  sortOrder: number;
}

export interface ExamScheduleVO {
  id: string;
  certificationId: string;
  certificationName: string;
  level: 'HIGH' | 'MIDDLE' | 'LOW';
  examStartDate: string;
  examEndDate: string;
  publicNote?: string | null;
  sessions: ExamSessionVO[];
}

export interface ExamScheduleForm {
  certificationId: string;
  publicNote?: string;
  sessions: Array<Omit<ExamSessionVO, 'id' | 'subjectName'>>;
  expectedPeriodRowVersion: number;
}

export interface ExamRegionQuery extends PageQuery {
  keyword?: string;
  status?: ExamRegionStatus | '';
  regionType?: ExamRegionType | '';
}

export interface ExamRegionVO {
  id: string;
  regionCode: string;
  regionName: string;
  regionType: ExamRegionType;
  parentRegionCode?: string | null;
  institutionName?: string | null;
  contactPhone?: string | null;
  localNoticeUrl: string;
  sortOrder: number;
  status: ExamRegionStatus;
  rowVersion: number;
  updatedTime: string;
}

export interface ExamRegionUpdateForm {
  institutionName?: string;
  contactPhone?: string;
  localNoticeUrl: string;
  sortOrder: number;
  status: ExamRegionStatus;
  expectedRowVersion: number;
}

export interface ExamRegionRegistrationVO {
  id: string;
  regionId: string;
  regionCode: string;
  regionName: string;
  registrationStart?: string | null;
  registrationEnd: string;
  reviewStart?: string | null;
  reviewEnd?: string | null;
  paymentStart?: string | null;
  paymentEnd?: string | null;
  qualificationReviewNote?: string | null;
  paymentNote?: string | null;
  officialSourceUrl: string;
  sourcePublishedAt: string;
}

export interface ExamRegionRegistrationForm {
  regionId: string;
  registrationStart?: string | null;
  registrationEnd: string;
  reviewStart?: string | null;
  reviewEnd?: string | null;
  paymentStart?: string | null;
  paymentEnd?: string | null;
  qualificationReviewNote?: string;
  paymentNote?: string;
  officialSourceUrl: string;
  sourcePublishedAt: string;
  expectedPeriodRowVersion: number;
}

export interface PeriodMutationResult {
  periodId: string;
  periodRowVersion?: number;
  rowVersion?: number;
}
