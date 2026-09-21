import { flushPromises, mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ExamSchedulePage from './index.vue';

const guidance = vi.hoisted(() => ({
  createExamPeriod: vi.fn(), createExamPeriodRevision: vi.fn(), createExamRegionRegistration: vi.fn(),
  createExamSchedule: vi.fn(), deleteExamPeriod: vi.fn(), deleteExamRegionRegistration: vi.fn(),
  deleteExamSchedule: vi.fn(), getExamPeriod: vi.fn(), listExamPeriods: vi.fn(), listExamRegions: vi.fn(),
  publishExamPeriod: vi.fn(), updateExamPeriod: vi.fn(), updateExamRegion: vi.fn(),
  updateExamRegionRegistration: vi.fn(), updateExamSchedule: vi.fn()
}));
const importApi = vi.hoisted(() => ({ getImportContextOptions: vi.fn() }));
const qualificationApi = vi.hoisted(() => ({ listQualifications: vi.fn() }));
const messages = vi.hoisted(() => ({ confirm: vi.fn(), error: vi.fn(), success: vi.fn(), warning: vi.fn() }));

vi.mock('@/api/certmuse/catalog/exam-guidance', () => guidance);
vi.mock('@/api/certmuse/catalog/import', () => importApi);
vi.mock('@/api/certmuse/catalog/subject-version', () => qualificationApi);
vi.mock('element-plus', async importOriginal => {
  const elementPlus = await importOriginal<typeof import('element-plus')>();
  return { ...elementPlus, ElMessage: messages, ElMessageBox: { confirm: messages.confirm } };
});

const period = (overrides: Record<string, unknown> = {}) => ({
  id: 'period-1', periodCode: '2026-H1', examYear: 2026, half: 'H1', status: 'draft', revisionNo: 1,
  officialSourceUrl: 'https://example.test/notice', sourcePublishedAt: '2026-01-01T00:00:00+08:00',
  rowVersion: 2, updatedTime: '2026-01-01T00:00:00+08:00', scheduleCount: 0, registrationCount: 0,
  createdTime: '2026-01-01T00:00:00+08:00', schedules: [], registrations: [], ...overrides
});
const schedule = (overrides: Record<string, unknown> = {}) => ({
  id: 'schedule-1', certificationId: 'certification-1', certificationName: '系统架构设计师', level: 'HIGH',
  examStartDate: '2026-05-23', examEndDate: '2026-05-24', publicNote: null,
  sessions: [{ id: 'session-1', examSubjectId: 'subject-1', subjectName: '综合知识', sessionCode: 'AM', startTime: '2026-05-23T01:00:00Z', endTime: '2026-05-23T03:30:00Z', sortOrder: 1 }],
  ...overrides
});
const registration = (overrides: Record<string, unknown> = {}) => ({
  id: 'registration-1', regionId: 'region-1', regionCode: '110000', regionName: '北京', registrationStart: '2026-03-01T01:00:00Z', registrationEnd: '2026-03-20T01:00:00Z',
  reviewStart: null, reviewEnd: null, paymentStart: null, paymentEnd: null, qualificationReviewNote: null, paymentNote: null,
  officialSourceUrl: 'https://example.test/region', sourcePublishedAt: '2026-01-02T00:00:00+08:00', ...overrides
});
const region = (overrides: Record<string, unknown> = {}) => ({
  id: 'region-1', regionCode: '110000', regionName: '北京', regionType: 'MUNICIPALITY', institutionName: null,
  contactPhone: null, localNoticeUrl: 'https://example.test/region', sortOrder: 1, status: 'enabled', rowVersion: 3,
  updatedTime: '2026-01-01T00:00:00+08:00', ...overrides
});

function mountPage() {
  return mount(ExamSchedulePage, {
    attachTo: document.body,
    global: { plugins: [ElementPlus], directives: { hasPermi: () => undefined } }
  });
}

describe('exam schedule management page', () => {
  beforeEach(() => {
    Object.values(guidance).forEach(mock => mock.mockReset());
    Object.values(importApi).forEach(mock => mock.mockReset());
    Object.values(qualificationApi).forEach(mock => mock.mockReset());
    Object.values(messages).forEach(mock => mock.mockReset());
    guidance.listExamPeriods.mockResolvedValue({ data: { rows: [period()], total: 1 } });
    guidance.getExamPeriod.mockResolvedValue({ data: period() });
    guidance.createExamPeriod.mockResolvedValue({ data: period() });
    guidance.updateExamPeriod.mockResolvedValue({ data: period({ rowVersion: 3 }) });
    guidance.createExamSchedule.mockResolvedValue({ data: {} });
    guidance.updateExamSchedule.mockResolvedValue({ data: {} });
    guidance.deleteExamSchedule.mockResolvedValue({ data: {} });
    guidance.createExamRegionRegistration.mockResolvedValue({ data: {} });
    guidance.updateExamRegionRegistration.mockResolvedValue({ data: {} });
    guidance.deleteExamRegionRegistration.mockResolvedValue({ data: {} });
    guidance.updateExamRegion.mockResolvedValue({ data: {} });
    guidance.publishExamPeriod.mockResolvedValue({ data: {} });
    guidance.createExamPeriodRevision.mockResolvedValue({ data: period({ id: 'period-2', revisionNo: 2 }) });
    guidance.deleteExamPeriod.mockResolvedValue({ data: {} });
    guidance.listExamRegions.mockResolvedValue({ data: { rows: [region()], total: 1 } });
    qualificationApi.listQualifications.mockResolvedValue({ data: { rows: [{ id: 'certification-1', certificationName: '系统架构设计师' }], total: 1 } });
    importApi.getImportContextOptions.mockResolvedValue({ examSubjects: [{ id: 'subject-1', label: '综合知识' }] });
    messages.confirm.mockResolvedValue(undefined);
  });

  it('loads list data and recovers from list or detail failures without leaving a stale workspace', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    expect(vm.periodList).toHaveLength(1);
    expect(vm.statusLabel('published')).toBe('已发布');
    expect(vm.statusType('superseded')).toBe('info');
    expect(vm.formatDate(null)).toBe('—');
    expect(vm.timeRange(null, null)).toBe('未公布');

    guidance.listExamPeriods.mockRejectedValueOnce(new Error('network unavailable'));
    await vm.getList();
    expect(messages.error).toHaveBeenCalledWith('network unavailable');
    guidance.getExamPeriod.mockRejectedValueOnce(new Error('not found'));
    await vm.openWorkspace(period());
    expect(messages.error).toHaveBeenCalledWith('not found');
    await vm.openWorkspace();
    expect(vm.workspaceDrawer).toBe(true);
    expect(vm.selectedPeriod).toBeUndefined();
    wrapper.unmount();
  });

  it('creates or updates a period only after validation and keeps failure feedback actionable', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    vm.periodFormRef = { validate: vi.fn().mockResolvedValue(false) };
    await vm.savePeriod();
    expect(guidance.createExamPeriod).not.toHaveBeenCalled();

    vm.periodFormRef.validate.mockResolvedValue(true);
    vm.periodForm.officialSourceUrl = 'https://example.test/new';
    vm.periodForm.sourcePublishedAt = '2026-01-02';
    await vm.savePeriod();
    expect(guidance.createExamPeriod).toHaveBeenCalledWith(expect.objectContaining({
      periodCode: expect.stringMatching(/^\d{4}-H[12]$/), sourcePublishedAt: '2026-01-02T00:00:00+08:00'
    }));
    expect(messages.success).toHaveBeenCalledWith('考期已保存，可继续配置');

    guidance.updateExamPeriod.mockRejectedValueOnce(new Error('row version conflict'));
    vm.selectedPeriod = period();
    await vm.savePeriod();
    expect(messages.error).toHaveBeenCalledWith('row version conflict');
    vm.resetWorkspace();
    expect(vm.selectedPeriod).toBeUndefined();
    wrapper.unmount();
  });

  it('validates, saves, deletes and reloads qualification schedules', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    vm.selectedPeriod = period();
    await vm.openScheduleEditor();
    expect(qualificationApi.listQualifications).toHaveBeenCalled();
    expect(vm.subjectOptions).toEqual([]);
    await vm.loadSubjectOptions('certification-1');
    expect(importApi.getImportContextOptions).toHaveBeenCalledWith(undefined, 'certification-1');
    vm.scheduleFormRef = { validate: vi.fn().mockResolvedValue(true) };
    vm.scheduleForm.sessions[0] = { examSubjectId: '', sessionCode: '', startTime: '', endTime: '', sortOrder: 1 };
    await vm.submitSchedule();
    expect(messages.warning).toHaveBeenCalledWith('请完整填写场次，且结束时间必须晚于开始时间');

    vm.scheduleForm.sessions[0] = { examSubjectId: 'subject-1', sessionCode: ' am ', startTime: '2026-05-23T01:00:00Z', endTime: '2026-05-23T03:30:00Z', sortOrder: 1 };
    await vm.submitSchedule();
    expect(guidance.createExamSchedule).toHaveBeenCalledWith('period-1', expect.objectContaining({
      sessions: [expect.objectContaining({ sessionCode: 'AM' })]
    }));
    await vm.openScheduleEditor(schedule());
    await vm.submitSchedule();
    expect(guidance.updateExamSchedule).toHaveBeenCalledWith('schedule-1', expect.any(Object));
    vm.addSession();
    vm.removeSession(1);
    await vm.handleDeleteSchedule(schedule());
    expect(guidance.deleteExamSchedule).toHaveBeenCalledWith('schedule-1', 2);

    importApi.getImportContextOptions.mockRejectedValueOnce(new Error('forbidden'));
    await vm.loadSubjectOptions('certification-1');
    expect(vm.subjectLoadFailed).toBe(true);
    wrapper.unmount();
  });

  it('validates registration time ranges, region maintenance and publish blockers', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    vm.selectedPeriod = period();
    vm.registrationFormRef = { validate: vi.fn().mockResolvedValue(true) };
    await vm.openRegistrationEditor();
    expect(guidance.listExamRegions).toHaveBeenCalledWith(expect.objectContaining({ status: 'enabled' }));
    vm.registrationForm.reviewStart = '2026-03-03T01:00:00Z';
    vm.registrationForm.reviewEnd = null;
    await vm.submitRegistration();
    expect(messages.warning).toHaveBeenCalledWith('审核和缴费时间必须成对填写，且结束时间晚于开始时间');

    Object.assign(vm.registrationForm, {
      regionId: 'region-1', registrationStart: '2026-03-01T01:00:00Z', registrationEnd: '2026-03-20T01:00:00Z',
      reviewStart: null, reviewEnd: null, paymentStart: null, paymentEnd: null, officialSourceUrl: 'https://example.test/region', sourcePublishedAt: '2026-01-02'
    });
    await vm.submitRegistration();
    expect(guidance.createExamRegionRegistration).toHaveBeenCalledWith('period-1', expect.objectContaining({ sourcePublishedAt: '2026-01-02T00:00:00+08:00' }));
    await vm.openRegistrationEditor(registration());
    await vm.submitRegistration();
    expect(guidance.updateExamRegionRegistration).toHaveBeenCalledWith('registration-1', expect.any(Object));
    await vm.deleteRegistration(registration());
    expect(guidance.deleteExamRegionRegistration).toHaveBeenCalledWith('registration-1', 2);

    await vm.openRegionDirectory();
    vm.selectedRegionId = 'region-1';
    vm.selectRegion('region-1');
    vm.regionFormRef = { validate: vi.fn().mockResolvedValue(true) };
    await vm.submitRegion();
    expect(guidance.updateExamRegion).toHaveBeenCalledWith('region-1', expect.objectContaining({ expectedRowVersion: 3 }));
    await vm.publishWorkspace();
    expect(messages.warning).toHaveBeenCalledWith('发布前请先完成：至少一项资格安排、至少一个考试场次、至少一条考区报名信息。');
    vm.selectedPeriod = period({ schedules: [schedule()], registrations: [registration()] });
    await vm.publishWorkspace();
    expect(guidance.publishExamPeriod).toHaveBeenCalledWith('period-1', 2);
    wrapper.unmount();
  });

  it('keeps cancellation silent while reporting mutation failures and supports revision/delete actions', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    vm.selectedPeriod = period();
    messages.confirm.mockRejectedValueOnce('cancel');
    await vm.handleDeleteSchedule(schedule());
    expect(messages.error).not.toHaveBeenCalled();
    guidance.createExamPeriodRevision.mockRejectedValueOnce(new Error('conflict'));
    await vm.handleRevision(period({ status: 'published' }));
    expect(messages.error).toHaveBeenCalledWith('conflict');
    await vm.handleRevision(period({ status: 'published' }));
    expect(messages.success).toHaveBeenCalledWith('修订草稿已创建');
    guidance.deleteExamPeriod.mockRejectedValueOnce(new Error('referenced'));
    await vm.handleDeletePeriod(period());
    expect(messages.error).toHaveBeenCalledWith('referenced');
    await vm.handleDeletePeriod(period());
    expect(messages.success).toHaveBeenCalledWith('考期草稿已删除');
    wrapper.unmount();
  });
});
