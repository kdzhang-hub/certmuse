import { describe, expect, it } from 'vitest';
import {
  eligibleQualifications,
  examPeriods,
  formatNextOfficialExamDate,
  qualificationLevelLabels,
  qualifications,
  recommendQualification
} from './qualification-fit-config';

describe('qualification fit configuration', () => {
  it('covers the complete 27-qualification catalogue with unique codes', () => {
    expect(qualifications).toHaveLength(27);
    expect(new Set(qualifications.map(item => item.code)).size).toBe(27);
  });

  it('groups every catalogue item under exactly one visible qualification level', () => {
    expect(qualificationLevelLabels).toEqual({ LOW: '初级资格', MIDDLE: '中级资格', HIGH: '高级资格' });
    expect(qualifications.filter(item => item.level === 'LOW')).toHaveLength(7);
    expect(qualifications.filter(item => item.level === 'MIDDLE')).toHaveLength(15);
    expect(qualifications.filter(item => item.level === 'HIGH')).toHaveLength(5);
  });

  it('uses the earliest available period when the visitor is unsure', () => {
    expect(eligibleQualifications('unsure').map(item => item.code)).toEqual(
      eligibleQualifications(examPeriods[0].id).map(item => item.code)
    );
  });

  it('recommends an available software starting point for a beginner interested in development', () => {
    const result = recommendQualification({
      periodId: '2026-h2',
      stage: 'year-1',
      foundation: 'none',
      direction: 'software',
      preference: 'build',
      specialty: 'development'
    });

    expect(result.code).toBe('PROGRAMMER');
  });

  it('keeps recommendations inside the selected period candidates', () => {
    const result = recommendQualification({
      periodId: '2026-h2',
      stage: 'technical',
      foundation: 'technical',
      direction: 'infrastructure',
      preference: 'operate',
      specialty: 'network'
    });

    expect(eligibleQualifications('2026-h2').map(item => item.code)).toContain(result.code);
    expect(result.code).toBe('NETWORK_ENGINEER');
  });

  it('formats published future exam dates and falls back when none is available', () => {
    expect(formatNextOfficialExamDate('2026-11-08')).toBe('下一场官方考试：2026年11月8日');
    expect(formatNextOfficialExamDate(null)).toBe('下一场官方考试：暂未公布');
  });
});
