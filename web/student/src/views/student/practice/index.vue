<template>
  <div class="p-2 app-container">
    <el-card shadow="hover" class="intro-card">
      <template #header>
        <div class="card-header">
          <div>
            <h2>自主练习</h2>
            <p>题集仅自己可见；每次练习固定本次题目顺序。</p>
          </div>
          <el-button type="primary" icon="Plus" @click="openCreate">创建题集</el-button>
        </div>
      </template>
      <el-alert
        title="先从已有任务开始；需要针对某一主题巩固时，再创建个人题集。"
        type="info"
        :closable="false"
        show-icon
      />
    </el-card>

    <el-row v-loading="loading" :gutter="16" class="set-row">
      <el-col v-for="item in practiceSets" :key="item.id" :xs="24" :sm="12" :xl="8">
        <el-card shadow="hover" class="practice-card">
          <template #header>
            <div class="set-heading">
              <strong>{{ item.name }}</strong>
              <el-tag :type="item.status === '可练习' ? 'success' : 'info'" size="small">{{ item.status }}</el-tag>
            </div>
          </template>
          <p class="set-note">{{ item.note || '尚未补充说明。' }}</p>
          <el-descriptions :column="2" size="small">
            <el-descriptions-item label="可用题数">{{ item.questionCount }}</el-descriptions-item>
            <el-descriptions-item label="累计练习">{{ item.practiceCount }} 次</el-descriptions-item>
            <el-descriptions-item label="最近更新" :span="2">{{ item.updatedAt }}</el-descriptions-item>
          </el-descriptions>
          <div class="set-actions">
            <el-button type="primary" :disabled="item.status !== '可练习'" @click="startPractice(item)">
              开始练习
            </el-button>
            <el-button @click="openEdit(item)">编辑</el-button>
            <el-button link type="danger" @click="removeSet(item)">删除</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-empty v-if="!loading && practiceSets.length === 0" description="还没有个人题集">
      <el-button type="primary" @click="openCreate">创建题集</el-button>
    </el-empty>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑题集' : '创建题集'" width="520px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="86px">
        <el-form-item label="题集名称" prop="name">
          <el-input v-model="form.name" maxlength="30" show-word-limit placeholder="例如：架构风格易错点" />
        </el-form-item>
        <el-form-item label="备注" prop="note">
          <el-input
            v-model="form.note"
            type="textarea"
            maxlength="80"
            show-word-limit
            placeholder="可选，说明这组题要巩固什么"
          />
        </el-form-item>
        <el-form-item label="收录题数" prop="questionCount">
          <el-input-number v-model="form.questionCount" :min="1" :max="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :loading="saving" type="primary" @click="submitForm">保存</el-button>
        <el-button @click="dialogVisible = false">取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import type { StudentPracticeSetForm, StudentPracticeSetVO } from '@/api/student/types';
import { deleteStudentPracticeSet, listStudentPracticeSets, saveStudentPracticeSet } from '@/api/student';
import modal from '@/plugins/modal';

const router = useRouter();
const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const formRef = ref();
const practiceSets = ref<StudentPracticeSetVO[]>([]);
const emptyForm = (): StudentPracticeSetForm => ({ name: '', note: '', questionCount: 10 });
const form = reactive<StudentPracticeSetForm>(emptyForm());
const rules = {
  name: [{ required: true, message: '请输入题集名称', trigger: 'blur' }],
  questionCount: [{ required: true, type: 'number', message: '至少选择 1 道题', trigger: 'change' }]
};

const getList = async () => {
  loading.value = true;
  try {
    const response = await listStudentPracticeSets();
    practiceSets.value = response.data ?? [];
  } finally {
    loading.value = false;
  }
};

const resetForm = () => Object.assign(form, emptyForm());
const openCreate = () => {
  resetForm();
  dialogVisible.value = true;
};
const openEdit = (item: StudentPracticeSetVO) => {
  Object.assign(form, item);
  dialogVisible.value = true;
};
const submitForm = () => {
  formRef.value?.validate(async (valid: boolean) => {
    if (!valid) return;
    saving.value = true;
    try {
      await saveStudentPracticeSet({ ...form });
      modal.msgSuccess('题集已保存');
      dialogVisible.value = false;
      await getList();
    } finally {
      saving.value = false;
    }
  });
};
const removeSet = async (item: StudentPracticeSetVO) => {
  await modal.confirm(`确认删除题集“${item.name}”吗？历史练习记录不会被删除。`);
  await deleteStudentPracticeSet(item.id);
  modal.msgSuccess('题集已删除');
  await getList();
};
const startPractice = (item: StudentPracticeSetVO) =>
  router.push({ path: '/learning/session/practice', query: { setId: item.id } });

onMounted(getList);
</script>

<style lang="scss" scoped>
.card-header,
.set-heading,
.set-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.card-header h2,
.card-header p {
  margin: 0;
}

.card-header p,
.set-note {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.set-row {
  margin-top: 16px;
}

.practice-card {
  height: 100%;
  margin-bottom: 16px;
}

.set-note {
  min-height: 42px;
  margin-bottom: 16px;
}

.set-actions {
  justify-content: flex-start;
  flex-wrap: wrap;
  margin-top: 18px;
}
</style>
