<template>
  <div class="p-2 app-container" v-loading="loading">
    <el-row :gutter="16">
      <el-col :xs="24" :lg="15">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <div>
                <h2>学习设置</h2>
                <p>调整学习节奏和提醒方式；当前修改保存在前端 Mock 状态中。</p>
              </div>
            </div>
          </template>
          <el-form ref="formRef" :model="settings" :rules="rules" label-width="112px" class="settings-form">
            <el-form-item label="每日学习时长" prop="dailyMinutes">
              <el-input-number v-model="settings.dailyMinutes" :min="10" :max="180" />
              <span class="field-hint">分钟。建议选择能稳定完成的时长。</span>
            </el-form-item>
            <el-form-item label="学习提醒">
              <el-switch v-model="settings.reminderEnabled" active-text="开启" inactive-text="关闭" />
            </el-form-item>
            <el-form-item label="提醒时间" prop="reminderTime">
              <el-time-picker
                v-model="settings.reminderTime"
                value-format="HH:mm"
                format="HH:mm"
                :disabled="!settings.reminderEnabled"
              />
            </el-form-item>
            <el-form-item label="错题回顾频率" prop="reviewFrequency">
              <el-radio-group v-model="settings.reviewFrequency">
                <el-radio value="每天">每天</el-radio>
                <el-radio value="每两天">每两天</el-radio>
                <el-radio value="每周">每周</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="submit">保存设置</el-button>
              <el-button @click="loadSettings">恢复当前设置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="9">
        <el-card shadow="hover" class="tip-card">
          <template #header><h3>设置说明</h3></template>
          <el-alert
            title="学习计划会依据每日可用时间安排，不会因为少量未完成任务自动堆叠。"
            type="info"
            :closable="false"
            show-icon
          />
          <p>提醒只是帮助你开始学习；不需要为了打卡而压缩正常休息。</p>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import type { StudentSettings } from '@/api/student/types';
import { getStudentSettings, updateStudentSettings } from '@/api/student';
import modal from '@/plugins/modal';

const loading = ref(false);
const saving = ref(false);
const formRef = ref();
const settings = reactive<StudentSettings>({
  dailyMinutes: 30,
  reminderEnabled: true,
  reminderTime: '20:00',
  reviewFrequency: '每天'
});
const rules = { dailyMinutes: [{ required: true, type: 'number', message: '请设置每日学习时长', trigger: 'change' }] };
const loadSettings = async () => {
  loading.value = true;
  try {
    const response = await getStudentSettings();
    Object.assign(settings, response.data);
  } finally {
    loading.value = false;
  }
};
const submit = () => {
  formRef.value?.validate(async (valid: boolean) => {
    if (!valid) return;
    saving.value = true;
    try {
      await updateStudentSettings({ ...settings });
      modal.msgSuccess('学习设置已保存');
    } finally {
      saving.value = false;
    }
  });
};

onMounted(loadSettings);
</script>

<style lang="scss" scoped>
.card-header h2,
.card-header p {
  margin: 0;
}

.card-header p,
.field-hint,
.tip-card p {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.field-hint {
  margin-left: 12px;
}

.tip-card p {
  margin: 16px 0 0;
  line-height: 1.7;
}
</style>
