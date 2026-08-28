<template>
  <el-dialog :model-value="modelValue" title="编辑题目" width="min(820px, calc(100vw - 32px))" :close-on-click-modal="false" @update:model-value="emit('update:modelValue', $event)">
    <div v-loading="loading">
      <el-alert v-if="detail?.reviewOpinion" title="原驳回意见" :description="detail.reviewOpinion" type="warning" :closable="false" show-icon />
      <el-form v-if="detail" label-position="top" class="edit-form">
        <el-form-item label="题干" required><el-input v-model="form.stem" type="textarea" :rows="4" /></el-form-item>
        <div class="form-grid"><el-form-item label="难度"><el-select v-model="form.difficulty"><el-option label="简单" value="easy" /><el-option label="中等" value="medium" /><el-option label="困难" value="hard" /></el-select></el-form-item><el-form-item label="预计答题时间（秒）"><el-input-number v-model="form.estimatedSeconds" :min="1" controls-position="right" /></el-form-item></div>
        <template v-if="detail.questionType === 'CHOICE'"><h4>选项与答案</h4><div v-for="(option, index) in form.options" :key="option.label" class="option-row"><el-radio v-model="form.correctOptionKey" :value="option.label">{{ option.label }}</el-radio><el-input v-model="option.content" :placeholder="`选项 ${option.label}`" /><el-button text type="danger" :disabled="form.options.length <= 2" @click="removeOption(index)">删除</el-button></div><el-button text type="primary" :disabled="form.options.length >= 10" @click="addOption">新增选项</el-button></template>
        <template v-else><el-form-item label="参考答案" required><el-input v-model="form.referenceAnswer" type="textarea" :rows="4" /></el-form-item></template>
        <el-form-item label="解析"><el-input v-model="form.analysis" type="textarea" :rows="3" /></el-form-item><el-form-item label="常见错误"><el-input v-model="form.commonMistakes" type="textarea" :rows="2" /></el-form-item><el-form-item label="知识点"><div class="knowledge-copy">{{ detail.knowledgeBindings.map(item => item.knowledgePointLabel).join('；') || '未关联' }}<small>此处保留原有关联。</small></div></el-form-item>
      </el-form>
    </div>
    <template #footer><el-button @click="emit('update:modelValue', false)">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存题目</el-button></template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { reactive, ref, watch } from 'vue';
import { getQuestionDetail, saveQuestionDraft } from '@/api/certmuse/question';
import type { QuestionDetailVO, QuestionListVO, QuestionOptionVO } from '@/api/certmuse/question/types';
const props = defineProps<{ modelValue: boolean; question?: QuestionListVO }>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [] }>();
const detail = ref<QuestionDetailVO>(); const loading = ref(false); const saving = ref(false);
const form = reactive({ stem: '', difficulty: null as QuestionListVO['difficulty'], estimatedSeconds: undefined as number | undefined, options: [] as QuestionOptionVO[], correctOptionKey: '', referenceAnswer: '', analysis: '', commonMistakes: '' });
watch(() => props.modelValue, visible => { if (visible && props.question) void load(); });
async function load() { if (!props.question) return; loading.value = true; try { const value = (await getQuestionDetail(props.question.questionId, props.question.revisionId)).data; detail.value = value; const answer = value.answer.value; Object.assign(form, { stem: value.stem, difficulty: value.difficulty, estimatedSeconds: value.estimatedSeconds ?? undefined, options: value.options.map(item => ({ ...item })), correctOptionKey: Array.isArray(answer) ? answer[0] || '' : '', referenceAnswer: typeof answer === 'string' ? answer : '', analysis: value.analysis || '', commonMistakes: value.commonMistakes || '' }); } finally { loading.value = false; } }
function addOption() { form.options.push({ label: String.fromCharCode(65 + form.options.length), content: '' }); }
function removeOption(index: number) { form.options.splice(index, 1); form.options.forEach((item, position) => { item.label = String.fromCharCode(65 + position); }); if (!form.options.some(item => item.label === form.correctOptionKey)) form.correctOptionKey = ''; }
async function save() { if (!detail.value) return; if (!form.stem.trim()) { ElMessage.warning('题干不能为空'); return; } if (detail.value.questionType === 'CHOICE' && !form.correctOptionKey) { ElMessage.warning('请选择正确答案'); return; } if (detail.value.questionType !== 'CHOICE' && !form.referenceAnswer.trim()) { ElMessage.warning('请填写参考答案'); return; } saving.value = true; try { await saveQuestionDraft(detail.value.questionId, { baseRevisionId: detail.value.revisionId, rowVersion: detail.value.rowVersion, examSubjectId: detail.value.examSubjectId, questionType: detail.value.questionType, difficulty: form.difficulty, estimatedSeconds: form.estimatedSeconds, stem: form.stem.trim(), options: detail.value.questionType === 'CHOICE' ? form.options.map((item, index) => ({ ...item, sortOrder: index + 1 })) : [], answer: detail.value.questionType === 'CHOICE' ? { schemaVersion: '1.0', answerType: 'option_keys', selectionMode: 'single', value: [form.correctOptionKey] } : { schemaVersion: '1.0', answerType: 'reference_text', value: form.referenceAnswer.trim() }, analysis: form.analysis || undefined, commonMistakes: form.commonMistakes || undefined, knowledgeBindings: detail.value.knowledgeBindings.map((item, index) => ({ knowledgePointId: item.knowledgePointId, relationRole: item.relationRole, sortOrder: index })) }); ElMessage.success('题目已保存'); emit('update:modelValue', false); emit('saved'); } finally { saving.value = false; } }
</script>

<style scoped lang="scss">
.edit-form { margin-top: 16px; }.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }.option-row { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 8px; align-items: center; margin-bottom: 8px; }.knowledge-copy { color: var(--el-text-color-regular); }.knowledge-copy small { display: block; margin-top: 4px; color: var(--el-text-color-secondary); } @media (max-width: 640px) { .form-grid { grid-template-columns: 1fr; } }
</style>
