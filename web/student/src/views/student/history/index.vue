<template>
  <div class="p-2 app-container learning-history-page">
    <section v-if="pageLoading" class="page-state"><el-skeleton :rows="8" animated /></section>
    <section v-else-if="pageError" class="page-state">
      <el-result icon="warning" title="学习记录暂不可用" :sub-title="pageError">
        <template #extra><el-button type="primary" @click="bootstrap">重新加载</el-button></template>
      </el-result>
    </section>

    <template v-else>
      <el-empty v-if="!goals.length" class="no-goal" description="还没有可查看的学习记录">
        <template #description><p>完成一次学习任务或题目练习后，记录会显示在这里。</p></template>
      </el-empty>

      <template v-else>
        <el-tabs v-model="activeTab" class="history-tabs" @tab-change="handleTabChange">
          <el-tab-pane label="每日任务" name="tasks" />
          <el-tab-pane label="练习历史" name="practices" />
          <el-tab-pane label="考试历史" name="exams" />
        </el-tabs>

        <el-card class="filter-card" shadow="never">
          <el-form
            v-if="activeTab === 'tasks'"
            :inline="true"
            label-position="top"
            class="filter-form"
            @submit.prevent="searchTasks"
          >
            <el-form-item label="任务名称或知识点">
              <el-input
                v-model="taskFilters.keyword"
                clearable
                placeholder="搜索已完成任务"
                :prefix-icon="Search"
                @keyup.enter="searchTasks"
              />
            </el-form-item>
            <el-form-item label="完成日期">
              <el-date-picker
                v-model="taskFilters.dateRange[0]"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="开始日期"
                class="date-range-input"
              />
              <span class="date-range-separator">至</span>
              <el-date-picker v-model="taskFilters.dateRange[1]" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" class="date-range-input" />
            </el-form-item>
            <el-form-item class="filter-form__actions">
              <el-button type="primary" native-type="submit">查询</el-button>
              <el-button :icon="Refresh" @click="resetTaskFilters">重置</el-button>
            </el-form-item>
          </el-form>

          <el-form
            v-else-if="activeTab === 'practices'"
            :inline="true"
            label-position="top"
            class="filter-form"
            @submit.prevent="searchPractices"
          >
            <el-form-item label="练习类型">
              <el-select v-model="practiceFilters.practiceType" clearable placeholder="全部练习">
                <el-option label="知识点练习" value="KNOWLEDGE_PRACTICE" />
                <el-option label="历年真题练习" value="PAST_PAPER_PRACTICE" />
              </el-select>
            </el-form-item>
            <el-form-item label="完成日期">
              <el-date-picker
                v-model="practiceFilters.dateRange[0]"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="开始日期"
                class="date-range-input"
              />
              <span class="date-range-separator">至</span>
              <el-date-picker v-model="practiceFilters.dateRange[1]" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" class="date-range-input" />
            </el-form-item>
            <el-form-item class="filter-form__actions">
              <el-button type="primary" native-type="submit">查询</el-button>
              <el-button :icon="Refresh" @click="resetPracticeFilters">重置</el-button>
            </el-form-item>
          </el-form>

          <el-form v-else :inline="true" label-position="top" class="filter-form" @submit.prevent="searchExams">
            <el-form-item label="考试类型">
              <el-select v-model="examFilters.examType" clearable placeholder="全部考试">
                <el-option label="首次诊断" value="INITIAL_DIAGNOSIS" />
                <el-option label="历年真题" value="PAST_PAPER" />
                <el-option label="模拟考试" value="SIMULATION" />
              </el-select>
            </el-form-item>
            <el-form-item label="完成日期">
              <el-date-picker
                v-model="examFilters.dateRange[0]"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="开始日期"
                class="date-range-input"
              />
              <span class="date-range-separator">至</span>
              <el-date-picker v-model="examFilters.dateRange[1]" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" class="date-range-input" />
            </el-form-item>
            <el-form-item class="filter-form__actions">
              <el-button type="primary" native-type="submit">查询</el-button>
              <el-button :icon="Refresh" @click="resetExamFilters">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <section class="list-heading">
          <div>
            <span class="list-heading__eyebrow">{{ heading.eyebrow }}</span>
            <h3>{{ heading.title }}</h3>
          </div>
          <span>{{ heading.count }}</span>
        </section>

        <section v-loading="listLoading" class="history-list">
          <template v-if="activeTab === 'tasks'">
            <el-table :data="taskRows" border class="history-table">
              <el-table-column label="每日任务" min-width="250">
                <template #default="{ row }">
                  <strong>每日任务</strong>
                  <span class="table-subtext">{{ taskSubject(row.subject.name) }}</span>
                  <span class="table-subtext">{{ row.knowledgePoint.name }}</span>
                </template>
              </el-table-column>
              <el-table-column label="完成情况" min-width="160" align="center">
                <template #default="{ row }">
                  <span>{{ row.correctCount }} 对 / {{ row.questionCount }} 题</span>
                  <span v-if="row.incorrectCount || row.skippedCount" class="table-subtext">
                    错 {{ row.incorrectCount }} · 跳过 {{ row.skippedCount }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="完成日期" min-width="170" align="center">
                <template #default="{ row }">{{ formatDateOnly(row.completedAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="110" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openTaskDetail(row.taskId)">查看详情</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty
              v-if="!listLoading && !taskRows.length"
              description="没有符合条件的已完成每日任务"
              :image-size="92"
            >
              <el-button @click="resetTaskFilters">清除筛选条件</el-button>
            </el-empty>
          </template>

          <template v-else-if="activeTab === 'practices'">
            <el-table :data="practiceRows" border class="history-table">
              <el-table-column label="练习" min-width="280">
                <template #default="{ row }">
                  <strong>{{ practiceTitle(row.practiceType, row.title) }}</strong>
                  <span class="table-subtext">{{ practiceSubject(row.practiceType, row.subject.name) }}</span>
                  <span class="table-subtext">{{ primaryKnowledgeName(row.knowledgePoints) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="完成情况" min-width="180" align="center">
                <template #default="{ row }">
                  <strong>{{ row.correctCount }} 对 / {{ row.answeredCount }} 已答</strong>
                  <span class="table-subtext">
                    共 {{ row.questionCount }} 题 · 错 {{ row.incorrectCount }} · 未答 {{ row.unansweredCount }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="完成日期" min-width="150" align="center">
                <template #default="{ row }">{{ formatDateOnly(row.completedAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="110" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openPracticeDetail(row.sessionId)">查看详情</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!listLoading && !practiceRows.length" description="没有符合条件的练习记录" :image-size="92">
              <el-button @click="resetPracticeFilters">清除筛选条件</el-button>
            </el-empty>
          </template>

          <template v-else>
            <el-table :data="examRows" border class="history-table">
              <el-table-column label="考试" min-width="270">
                <template #default="{ row }">
                  <strong>{{ row.title }}</strong>
                  <span class="table-subtext">
                    {{ examTypeText(row.examType) }} · {{ row.subject?.name ?? '综合考试' }}
                  </span>
                  <span class="table-subtext">{{ qualificationName() }}</span>
                </template>
              </el-table-column>
              <el-table-column label="考试结果" min-width="150" align="center">
                <template #default="{ row }">
                  <strong v-if="row.result.reportStatus === 'AVAILABLE'">
                    {{ scoreText(row.result.score, row.result.maxScore) }}
                  </strong>
                  <el-tag v-else type="warning" effect="light">{{ reportText(row.result.reportStatus) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="考试用时" min-width="145" align="center">
                <template #default="{ row }">
                  {{ durationText(row.durationSeconds, row.durationLimitSeconds, row.durationStatus) }}
                </template>
              </el-table-column>
              <el-table-column label="完成日期" min-width="150" align="center">
                <template #default="{ row }">{{ formatDateOnly(row.completedAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="110" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openExamDetail(row.sessionId)">查看详情</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!listLoading && !examRows.length" description="暂无符合条件的考试记录" :image-size="92">
              <p class="empty-hint">提交首次诊断或完成真题、模拟考试后，可在这里查看单场结果。</p>
            </el-empty>
          </template>
        </section>

        <el-pagination
          v-if="currentTotal > pageSize"
          class="history-pagination"
          background
          layout="prev, pager, next"
          :current-page="currentPage"
          :page-size="pageSize"
          :total="currentTotal"
          @current-change="changePage"
        />
      </template>
    </template>

    <el-dialog v-model="taskDialogVisible" title="每日任务详情" width="min(920px, calc(100vw - 32px))" destroy-on-close>
      <div v-if="detailLoading" class="dialog-loading"><el-skeleton :rows="8" animated /></div>
      <template v-else-if="taskDetail">
        <section class="detail-summary">
          <div>
            <span class="detail-summary__eyebrow">DAILY TASK</span>
            <h3>每日任务</h3>
            <p>{{ taskDetail.knowledgePoint.name }} · 完成于 {{ formatDateTime(taskDetail.completedAt) }}</p>
          </div>
          <div class="score-board">
            <strong>{{ taskDetail.summary.correctCount }} / {{ taskDetail.summary.questionCount }}</strong>
            <span>答对题数</span>
          </div>
        </section>
        <el-alert
          :closable="false"
          type="info"
          show-icon
          :title="`答错 ${taskDetail.summary.incorrectCount} 题，跳过 ${taskDetail.summary.skippedCount} 题`"
        />
        <section class="question-replay-list detail-question-list">
          <article v-for="question in taskDetail.questions" :key="question.questionId" class="question-replay">
            <header class="question-replay__header">
              <div>
                <strong>第 {{ question.questionOrder }} 题</strong>
                <el-tag size="small" effect="plain">选择题 · {{ difficultyText(question.difficulty) }}</el-tag>
              </div>
              <el-tag :type="taskQuestionTagType(question)">{{ taskQuestionStatusText(question) }}</el-tag>
            </header>
            <div class="question-stem" v-html="formatQuestionStem(question.stem)" />
            <div v-if="question.images.length" class="question-images">
              <el-image
                v-for="image in question.images"
                :key="`${question.questionId}-${image.sortOrder}`"
                :src="toQuestionImageUrl(image.url)"
                :alt="image.altText ?? '题目图片'"
                fit="contain"
              />
            </div>
            <div class="answer-options">
              <div
                v-for="option in question.options"
                :key="option.label"
                class="answer-option"
                :class="
                  optionClasses(
                    option.label,
                    question.submission.selectedOptionLabels,
                    question.result.correctOptionLabels
                  )
                "
              >
                <span class="answer-option__label">{{ option.label }}</span>
                <span class="answer-option__content">{{ option.content }}</span>
                <span class="answer-option__badges">
                  <el-tag
                    v-if="question.submission.selectedOptionLabels.includes(option.label)"
                    size="small"
                    type="primary"
                  >
                    你的选择
                  </el-tag>
                  <el-tag v-if="question.result.correctOptionLabels.includes(option.label)" size="small" type="success">
                    正确答案
                  </el-tag>
                </span>
              </div>
            </div>
            <div class="answer-meta">
              <span>
                你的答案：
                <strong>{{ selectedAnswerText(question.submission.selectedOptionLabels) }}</strong>
              </span>
              <span>
                正确答案：
                <strong>{{ selectedAnswerText(question.result.correctOptionLabels) }}</strong>
              </span>
              <span>
                得分：
                <strong>{{ scoreText(question.result.score, question.result.maxScore) }}</strong>
              </span>
            </div>
            <div class="analysis-block">
              <strong>题目解析</strong>
              <p>{{ question.result.analysis || '暂无解析' }}</p>
            </div>
          </article>
        </section>
      </template>
    </el-dialog>

    <el-dialog
      v-model="practiceDialogVisible"
      title="练习详情"
      width="min(1040px, calc(100vw - 24px))"
      destroy-on-close
    >
      <div v-if="detailLoading" class="dialog-loading"><el-skeleton :rows="9" animated /></div>
      <template v-else-if="practiceDetail">
        <section class="detail-summary">
          <div>
            <span class="detail-summary__eyebrow">{{ practiceTypeText(practiceDetail.practiceType) }}</span>
            <h3>{{ practiceTitle(practiceDetail.practiceType, practiceDetail.title) }}</h3>
            <p>
              {{ practiceSubject(practiceDetail.practiceType, practiceDetail.subject.name) }} ·
              {{ formatPracticePeriod(practiceDetail.startedAt, practiceDetail.completedAt) }}
            </p>
          </div>
          <div class="score-board">
            <strong>{{ practiceDetail.summary.correctCount }} / {{ practiceDetail.summary.answeredCount }}</strong>
            <span>已答题中答对</span>
          </div>
        </section>
        <div class="summary-chips">
          <el-tag effect="plain">共 {{ practiceDetail.summary.questionCount }} 题</el-tag>
          <el-tag type="success" effect="plain">答对 {{ practiceDetail.summary.correctCount }}</el-tag>
          <el-tag type="danger" effect="plain">答错 {{ practiceDetail.summary.incorrectCount }}</el-tag>
          <el-tag type="info" effect="plain">未答 {{ practiceDetail.summary.unansweredCount }}</el-tag>
        </div>
        <section class="question-replay-list">
          <article v-for="question in practiceDetail.questions" :key="question.questionId" class="question-replay">
            <header class="question-replay__header">
              <div>
                <strong>第 {{ question.questionOrder }} 题</strong>
                <el-tag size="small" effect="plain">{{ difficultyText(question.difficulty) }}</el-tag>
              </div>
              <el-tag :type="practiceQuestionTagType(question)">{{ practiceQuestionStatusText(question) }}</el-tag>
            </header>
            <div class="question-stem" v-html="formatQuestionStem(question.stem)" />
            <div v-if="question.images.length" class="question-images">
              <el-image
                v-for="image in question.images"
                :key="`${question.questionId}-${image.sortOrder}`"
                :src="toQuestionImageUrl(image.url)"
                :alt="image.altText ?? '题目图片'"
                fit="contain"
              />
            </div>
            <template v-if="question.questionType === 'CHOICE'">
              <div class="answer-options">
                <div
                  v-for="option in question.options"
                  :key="option.label"
                  class="answer-option"
                  :class="
                    optionClasses(
                      option.label,
                      question.submission?.selectedOptionLabels ?? [],
                      question.result.correctOptionLabels
                    )
                  "
                >
                  <span class="answer-option__label">{{ option.label }}</span>
                  <span class="answer-option__content">{{ option.content }}</span>
                  <span class="answer-option__badges">
                    <el-tag
                      v-if="question.submission?.selectedOptionLabels.includes(option.label)"
                      size="small"
                      type="primary"
                    >
                      你的选择
                    </el-tag>
                    <el-tag
                      v-if="question.result.correctOptionLabels.includes(option.label)"
                      size="small"
                      type="success"
                    >
                      正确答案
                    </el-tag>
                  </span>
                </div>
              </div>
              <div class="answer-meta">
                <span>
                  你的答案：
                  <strong>{{ selectedAnswerText(question.submission?.selectedOptionLabels) }}</strong>
                </span>
                <span>
                  正确答案：
                  <strong>{{ selectedAnswerText(question.result.correctOptionLabels) }}</strong>
                </span>
                <span>
                  得分：
                  <strong>
                    {{ question.submission ? scoreText(question.result.score, question.result.maxScore) : '—' }}
                  </strong>
                </span>
              </div>
            </template>
            <template v-else>
              <div class="subjective-grid">
                <div>
                  <strong>你的作答</strong>
                  <p class="long-answer">{{ question.submission?.textAnswer || '本题未作答' }}</p>
                </div>
                <div>
                  <strong>参考答案</strong>
                  <p class="long-answer">{{ question.result.referenceAnswer || '暂无参考答案' }}</p>
                </div>
              </div>
              <div class="answer-meta">
                <span>
                  评分状态：
                  <strong>{{ gradingStatusText(question.result.gradingStatus) }}</strong>
                </span>
                <span>
                  得分：
                  <strong>{{ scoreText(question.result.score, question.result.maxScore) }}</strong>
                </span>
                <span v-if="question.result.gradingSource">
                  评分方式：
                  <strong>
                    {{ question.result.gradingSource === 'AI' ? 'AI 评分' : question.result.gradingSource }}
                  </strong>
                </span>
              </div>
              <div
                v-if="question.result.gradingSource === 'AI' && question.result.gradingStatus === 'graded'"
                class="ai-feedback"
              >
                <strong>AI 评分解析</strong>
                <p>{{ question.result.aiFeedback || '暂无 AI 解析' }}</p>
              </div>
            </template>
            <div class="analysis-block">
              <strong>题目解析</strong>
              <p>{{ question.result.analysis || '暂无解析' }}</p>
            </div>
          </article>
        </section>
      </template>
    </el-dialog>

    <el-dialog v-model="examDialogVisible" title="考试详情" width="min(1000px, calc(100vw - 32px))" destroy-on-close>
      <div v-if="detailLoading" class="dialog-loading"><el-skeleton :rows="9" animated /></div>
      <template v-else-if="examDetail">
        <section class="detail-summary">
          <div>
            <span class="detail-summary__eyebrow">{{ examTypeText(examDetail.examType) }}</span>
            <h3>{{ examDetail.title }}</h3>
            <p>
              {{ qualificationName() }} · {{ examDetail.subject?.name ?? '综合考试' }} · 完成于
              {{ formatDateTime(examDetail.completedAt) }} ·
              {{ durationText(examDetail.durationSeconds, examDetail.durationLimitSeconds, examDetail.durationStatus) }}
            </p>
          </div>
          <div class="score-board">
            <strong>
              {{
                examDetail.result.reportStatus === 'AVAILABLE'
                  ? scoreText(examDetail.result.score, examDetail.result.maxScore)
                  : reportText(examDetail.result.reportStatus)
              }}
            </strong>
            <span>考试结果</span>
          </div>
        </section>
        <section class="question-replay-list">
          <article v-for="question in examDetail.questions" :key="question.questionId" class="question-replay">
            <header class="question-replay__header">
              <div>
                <strong>第 {{ question.questionOrder }} 题</strong>
                <el-tag size="small" effect="plain">
                  {{ questionTypeText(question.questionType) }} · {{ difficultyText(question.difficulty) }}
                </el-tag>
              </div>
              <el-tag :type="examQuestionTagType(question)">{{ examQuestionStatusText(question) }}</el-tag>
            </header>
            <div class="question-stem" v-html="formatQuestionStem(question.stem)" />
            <div v-if="question.images.length" class="question-images">
              <el-image
                v-for="image in question.images"
                :key="`${question.questionId}-${image.sortOrder}`"
                :src="toQuestionImageUrl(image.url)"
                :alt="image.altText ?? '题目图片'"
                fit="contain"
              />
            </div>
            <template v-if="question.questionType === 'CHOICE'">
              <div class="answer-options">
                <div
                  v-for="option in question.options"
                  :key="option.label"
                  class="answer-option"
                  :class="
                    optionClasses(
                      option.label,
                      question.submission.selectedOptionLabels,
                      question.result.correctOptionLabels
                    )
                  "
                >
                  <span class="answer-option__label">{{ option.label }}</span>
                  <span class="answer-option__content">{{ option.content }}</span>
                  <span class="answer-option__badges">
                    <el-tag
                      v-if="question.submission.selectedOptionLabels.includes(option.label)"
                      size="small"
                      type="primary"
                    >
                      你的选择
                    </el-tag>
                    <el-tag
                      v-if="question.result.correctOptionLabels.includes(option.label)"
                      size="small"
                      type="success"
                    >
                      正确答案
                    </el-tag>
                  </span>
                </div>
              </div>
              <div class="answer-meta">
                <span>
                  你的答案：
                  <strong>{{ selectedAnswerText(question.submission.selectedOptionLabels) }}</strong>
                </span>
                <span>
                  正确答案：
                  <strong>{{ selectedAnswerText(question.result.correctOptionLabels) }}</strong>
                </span>
                <span>
                  得分：
                  <strong>{{ scoreText(question.result.score, question.result.maxScore) }}</strong>
                </span>
              </div>
            </template>
            <template v-else>
              <div class="subjective-grid">
                <div>
                  <strong>你的作答</strong>
                  <p class="long-answer">{{ question.submission.textAnswer || '本题未作答' }}</p>
                </div>
                <div>
                  <strong>参考答案</strong>
                  <p class="long-answer">{{ question.result.referenceAnswer || '暂无参考答案' }}</p>
                </div>
              </div>
              <div class="answer-meta">
                <span>
                  评分状态：
                  <strong>{{ gradingStatusText(question.result.gradingStatus) }}</strong>
                </span>
                <span>
                  得分：
                  <strong>{{ scoreText(question.result.score, question.result.maxScore) }}</strong>
                </span>
                <span v-if="question.result.gradingSource">
                  评分方式：
                  <strong>
                    {{ question.result.gradingSource === 'AI' ? 'AI 评分' : question.result.gradingSource }}
                  </strong>
                </span>
              </div>
              <div
                v-if="question.result.gradingSource === 'AI' && question.result.gradingStatus === 'graded'"
                class="ai-feedback"
              >
                <strong>AI 评分解析</strong>
                <p>{{ question.result.aiFeedback || '暂无 AI 解析' }}</p>
              </div>
            </template>
            <div class="analysis-block">
              <strong>题目解析</strong>
              <p>{{ question.result.analysis || '暂无解析' }}</p>
            </div>
          </article>
        </section>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="StudentHistory" lang="ts">
import { Refresh, Search } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';
import {
  getHistoryGoals,
  getHistoryExam,
  getHistoryPractice,
  getHistoryTask,
  listHistoryExams,
  listHistoryPractices,
  listHistoryTasks,
  type HistoryExamDetail,
  type HistoryExamRow,
  type HistoryExamType,
  type HistoryGoal,
  type HistoryPracticeDetail,
  type HistoryPracticeRow,
  type HistoryPracticeType,
  type HistoryTaskDetail,
  type HistoryTaskRow
} from '@/api/certmuse/learning/history';
import { formatQuestionStem, toQuestionImageUrl } from '@/utils/question-image';
import { extractErrorMessage } from '@/utils/request';

type HistoryTab = 'tasks' | 'practices' | 'exams';
const pageSize = 10;
const activeTab = ref<HistoryTab>('tasks');
const selectedGoalId = ref('');
const goals = ref<HistoryGoal[]>([]);
const pageLoading = ref(true);
const listLoading = ref(false);
const pageError = ref('');
const taskRows = ref<HistoryTaskRow[]>([]);
const practiceRows = ref<HistoryPracticeRow[]>([]);
const examRows = ref<HistoryExamRow[]>([]);
const taskTotal = ref(0);
const practiceTotal = ref(0);
const examTotal = ref(0);
const taskPage = ref(1);
const practicePage = ref(1);
const examPage = ref(1);
const taskDialogVisible = ref(false);
const practiceDialogVisible = ref(false);
const examDialogVisible = ref(false);
const detailLoading = ref(false);
const taskDetail = ref<HistoryTaskDetail>();
const practiceDetail = ref<HistoryPracticeDetail>();
const examDetail = ref<HistoryExamDetail>();

const taskFilters = reactive({ keyword: '', dateRange: [] as string[] });
const practiceFilters = reactive({ practiceType: '' as HistoryPracticeType | '', dateRange: [] as string[] });
const examFilters = reactive({ examType: '' as HistoryExamType | '', dateRange: [] as string[] });

const currentTotal = computed(() =>
  activeTab.value === 'tasks'
    ? taskTotal.value
    : activeTab.value === 'practices'
      ? practiceTotal.value
      : examTotal.value
);
const currentPage = computed(() =>
  activeTab.value === 'tasks' ? taskPage.value : activeTab.value === 'practices' ? practicePage.value : examPage.value
);
const selectedGoal = computed(() => goals.value.find(item => item.goalId === selectedGoalId.value));
const heading = computed(() => {
  if (activeTab.value === 'tasks')
    return { eyebrow: 'DAILY TASKS', title: '已完成每日任务', count: `共 ${taskTotal.value} 项` };
  if (activeTab.value === 'practices')
    return { eyebrow: 'PRACTICE HISTORY', title: '已完成练习', count: `共 ${practiceTotal.value} 次` };
  return { eyebrow: 'EXAM HISTORY', title: '已完成考试', count: `共 ${examTotal.value} 场` };
});

onMounted(() => void bootstrap());

async function bootstrap() {
  pageLoading.value = true;
  pageError.value = '';
  try {
    const response = await getHistoryGoals();
    goals.value = response.data?.rows ?? [];
    selectedGoalId.value = goals.value.find(item => item.isCurrent)?.goalId ?? goals.value[0]?.goalId ?? '';
    if (selectedGoalId.value) await reloadCurrentTab();
  } catch (error) {
    pageError.value = await messageFor(error, '无法加载学习记录。');
  } finally {
    pageLoading.value = false;
  }
}

async function reloadCurrentTab() {
  if (!selectedGoalId.value) return;
  if (activeTab.value === 'tasks') await loadTasks();
  else if (activeTab.value === 'practices') await loadPractices();
  else await loadExams();
}

function handleTabChange(tab: string | number) {
  activeTab.value = tab as HistoryTab;
  void reloadCurrentTab();
}

function searchTasks() {
  taskPage.value = 1;
  void loadTasks();
}
function searchPractices() {
  practicePage.value = 1;
  void loadPractices();
}
function searchExams() {
  examPage.value = 1;
  void loadExams();
}
function resetTaskFilters() {
  Object.assign(taskFilters, { keyword: '', dateRange: [] });
  searchTasks();
}
function resetPracticeFilters() {
  Object.assign(practiceFilters, { practiceType: '', dateRange: [] });
  searchPractices();
}
function resetExamFilters() {
  Object.assign(examFilters, { examType: '', dateRange: [] });
  searchExams();
}

async function loadTasks() {
  listLoading.value = true;
  try {
    const [completedFrom, completedTo] = taskFilters.dateRange;
    const response = await listHistoryTasks({
      goalId: selectedGoalId.value,
      keyword: normalized(taskFilters.keyword),
      completedFrom,
      completedTo,
      pageNum: taskPage.value,
      pageSize
    });
    taskRows.value = response.data?.rows ?? [];
    taskTotal.value = response.data?.total ?? 0;
  } catch (error) {
    taskRows.value = [];
    taskTotal.value = 0;
    ElMessage.error(await messageFor(error, '无法加载每日任务历史。'));
  } finally {
    listLoading.value = false;
  }
}

async function loadPractices() {
  listLoading.value = true;
  try {
    const [completedFrom, completedTo] = practiceFilters.dateRange;
    const response = await listHistoryPractices({
      goalId: selectedGoalId.value,
      practiceType: practiceFilters.practiceType || undefined,
      completedFrom,
      completedTo,
      pageNum: practicePage.value,
      pageSize
    });
    practiceRows.value = response.data?.rows ?? [];
    practiceTotal.value = response.data?.total ?? 0;
  } catch (error) {
    practiceRows.value = [];
    practiceTotal.value = 0;
    ElMessage.error(await messageFor(error, '无法加载练习历史。'));
  } finally {
    listLoading.value = false;
  }
}

async function loadExams() {
  listLoading.value = true;
  try {
    const [completedFrom, completedTo] = examFilters.dateRange;
    const response = await listHistoryExams({
      goalId: selectedGoalId.value,
      examType: examFilters.examType || undefined,
      completedFrom,
      completedTo,
      pageNum: examPage.value,
      pageSize
    });
    examRows.value = response.data?.rows ?? [];
    examTotal.value = response.data?.total ?? 0;
  } catch (error) {
    examRows.value = [];
    examTotal.value = 0;
    ElMessage.error(await messageFor(error, '无法加载考试历史。'));
  } finally {
    listLoading.value = false;
  }
}

function changePage(nextPage: number) {
  if (activeTab.value === 'tasks') taskPage.value = nextPage;
  else if (activeTab.value === 'practices') practicePage.value = nextPage;
  else examPage.value = nextPage;
  void reloadCurrentTab();
}

async function openTaskDetail(taskId: string) {
  detailLoading.value = true;
  taskDetail.value = undefined;
  taskDialogVisible.value = true;
  try {
    const response = await getHistoryTask(taskId, selectedGoalId.value);
    taskDetail.value = response.data;
  } catch (error) {
    taskDialogVisible.value = false;
    ElMessage.error(await messageFor(error, '无法加载任务详情。'));
  } finally {
    detailLoading.value = false;
  }
}

async function openPracticeDetail(sessionId: string) {
  detailLoading.value = true;
  practiceDetail.value = undefined;
  practiceDialogVisible.value = true;
  try {
    const response = await getHistoryPractice(sessionId, selectedGoalId.value);
    practiceDetail.value = response.data;
  } catch (error) {
    practiceDialogVisible.value = false;
    ElMessage.error(await messageFor(error, '无法加载练习详情。'));
  } finally {
    detailLoading.value = false;
  }
}

async function openExamDetail(sessionId: string) {
  detailLoading.value = true;
  examDetail.value = undefined;
  examDialogVisible.value = true;
  try {
    const response = await getHistoryExam(sessionId, selectedGoalId.value);
    examDetail.value = response.data;
  } catch (error) {
    examDialogVisible.value = false;
    ElMessage.error(await messageFor(error, '无法加载考试详情。'));
  } finally {
    detailLoading.value = false;
  }
}

function normalized(value: string) {
  const next = value.trim();
  return next || undefined;
}
async function messageFor(error: unknown, fallback: string) {
  return (await extractErrorMessage(error)) ?? fallback;
}
function formatDateOnly(value?: string | null) {
  return value ? value.slice(0, 10) : '—';
}
function formatDateTime(value?: string | null) {
  return value ? value.replace('T', ' ').replace(/([+-]\d{2}:\d{2}|Z)$/, '') : '—';
}
function practiceTitle(practiceType: HistoryPracticeType, title: string) {
  return practiceType === 'KNOWLEDGE_PRACTICE' ? '知识点练习' : title;
}
function practiceSubject(practiceType: HistoryPracticeType, subjectName: string) {
  if (practiceType !== 'KNOWLEDGE_PRACTICE') return `${practiceTypeText(practiceType)} · ${subjectName}`;
  const certificationName = selectedGoal.value?.certificationName;
  return certificationName ? `${certificationName} · ${subjectName}` : subjectName;
}
function taskSubject(subjectName: string) {
  const certificationName = selectedGoal.value?.certificationName;
  return certificationName ? `${certificationName} · ${subjectName}` : subjectName;
}
function qualificationName() {
  return selectedGoal.value?.certificationName ?? '资格名称未标注';
}
function primaryKnowledgeName(items: Array<{ name: string }>) {
  return items[0]?.name || '未标注知识点';
}
function practiceTypeText(value: string) {
  return value === 'KNOWLEDGE_PRACTICE' ? '知识点练习' : '历年真题练习';
}
function questionTypeText(value: string) {
  return ({ CHOICE: '选择题', CASE: '案例题', ESSAY: '论文题' } as Record<string, string>)[value] ?? value;
}
function difficultyText(value: string) {
  return ({ EASY: '简单', MEDIUM: '中等', HARD: '困难' } as Record<string, string>)[value] ?? value;
}
function examTypeText(value: string) {
  return (
    ({ INITIAL_DIAGNOSIS: '首次诊断', PAST_PAPER: '历年真题', SIMULATION: '模拟考试' } as Record<string, string>)[
      value
    ] ?? value
  );
}
function scoreText(score: string | null, maxScore: string | null) {
  return score != null && maxScore != null ? `${score} / ${maxScore}` : '待评分';
}
function reportText(status: string) {
  return (
    ({ PROCESSING: '结果处理中', FAILED: '结果生成失败', UNAVAILABLE: '结果不可用' } as Record<string, string>)[
      status
    ] ?? status
  );
}
function durationText(seconds: number | null, limit: number | null, status: string) {
  if (status !== 'AVAILABLE' || seconds == null) return '用时不可用';
  const used = secondsToText(seconds);
  return limit == null ? used : `${used} / ${secondsToText(limit)}`;
}
function secondsToText(seconds: number) {
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const rest = seconds % 60;
  return hours ? `${hours}小时${minutes}分` : `${minutes}分${rest}秒`;
}
function selectedAnswerText(labels?: string[]) {
  return labels?.length ? labels.join('、') : '未作答';
}
function optionClasses(label: string, selected: string[], correct: string[]) {
  return { 'is-selected': selected.includes(label), 'is-correct': correct.includes(label) };
}
function formatPracticePeriod(startedAt: string | null, completedAt: string) {
  return startedAt
    ? `${formatDateTime(startedAt)} 至 ${formatDateTime(completedAt)}`
    : `完成于 ${formatDateTime(completedAt)}`;
}
function gradingStatusText(status: string | null) {
  return (
    ({ graded: '评分完成', pending: '等待评分', processing: '评分中', failed: '评分失败' } as Record<string, string>)[
      status ?? ''
    ] ?? '未评分'
  );
}
function taskQuestionStatusText(question: HistoryTaskDetail['questions'][number]) {
  if (question.submission.unanswered) return '未作答';
  return question.result.correct ? '答对' : '答错';
}
function taskQuestionTagType(question: HistoryTaskDetail['questions'][number]): 'success' | 'danger' | 'info' {
  if (question.submission.unanswered) return 'info';
  return question.result.correct ? 'success' : 'danger';
}
function practiceQuestionStatusText(question: HistoryPracticeDetail['questions'][number]) {
  if (!question.submission || question.submission.unanswered) return '未作答';
  if (question.result.gradingStatus === 'pending' || question.result.gradingStatus === 'processing') return '评分中';
  if (question.result.gradingStatus === 'failed') return '评分失败';
  if (question.questionType === 'CHOICE') return question.result.correct ? '答对' : '答错';
  return '评分完成';
}
function practiceQuestionTagType(
  question: HistoryPracticeDetail['questions'][number]
): 'success' | 'danger' | 'warning' | 'info' {
  if (!question.submission || question.submission.unanswered) return 'info';
  if (question.result.gradingStatus === 'pending' || question.result.gradingStatus === 'processing') return 'warning';
  if (question.result.gradingStatus === 'failed') return 'danger';
  if (question.questionType === 'CHOICE') return question.result.correct ? 'success' : 'danger';
  return 'success';
}
function examQuestionStatusText(question: HistoryExamDetail['questions'][number]) {
  if (question.submission.unanswered) return '未作答';
  if (question.result.gradingStatus === 'pending' || question.result.gradingStatus === 'processing') return '评分中';
  if (question.result.gradingStatus === 'failed') return '评分失败';
  if (question.questionType === 'CHOICE') return question.result.correct ? '答对' : '答错';
  return '评分完成';
}
function examQuestionTagType(
  question: HistoryExamDetail['questions'][number]
): 'success' | 'danger' | 'warning' | 'info' {
  if (question.submission.unanswered) return 'info';
  if (question.result.gradingStatus === 'pending' || question.result.gradingStatus === 'processing') return 'warning';
  if (question.result.gradingStatus === 'failed') return 'danger';
  if (question.questionType === 'CHOICE') return question.result.correct ? 'success' : 'danger';
  return 'success';
}
</script>

<style lang="scss" scoped>
.learning-history-page {
  width: 100%;
  max-width: none;
  margin: 0;
}
.list-heading,
.detail-summary {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
}
.list-heading__eyebrow,
.detail-summary__eyebrow {
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.13em;
}
.detail-summary p {
  margin: 0;
  color: var(--el-text-color-secondary);
}
.page-state,
.no-goal {
  min-height: 360px;
  display: grid;
  place-items: center;
}
.no-goal {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
}
.no-goal p,
.empty-hint {
  color: var(--el-text-color-secondary);
}
.history-tabs {
  margin-bottom: 12px;
}
.filter-card {
  margin-bottom: 24px;
  border-radius: 12px;
}
.filter-form {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 4px;
}
.filter-form :deep(.el-form-item) {
  flex: 1 1 20%;
  min-width: 0;
  margin-bottom: 0;
}
.filter-form :deep(.el-input),
.filter-form :deep(.el-select),
.filter-form :deep(.el-date-editor) {
  width: 100%;
}
.filter-form :deep(.date-range-input) {
  width: calc((100% - 28px) / 2);
}
.date-range-separator {
  width: 28px;
  color: var(--el-text-color-secondary);
  text-align: center;
}
.filter-form__actions {
  margin-left: auto;
}
.list-heading {
  margin: 0 6px 14px;
  align-items: center;
}
.list-heading h3 {
  margin: 5px 0 0;
  font-size: 20px;
}
.list-heading > span {
  color: var(--el-text-color-secondary);
}
.history-list {
  min-height: 220px;
}
.history-list :deep(.el-empty) {
  padding: 52px 0;
  border: 1px dashed var(--el-border-color);
  border-top: 0;
}
.table-subtext {
  display: block;
  margin-top: 5px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.35;
}
.history-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
.dialog-loading {
  padding: 28px 8px;
}
.detail-summary {
  align-items: center;
  margin-bottom: 18px;
}
.detail-summary h3 {
  margin: 5px 0 7px;
  font-size: 19px;
  line-height: 1.45;
}
.score-board {
  min-width: 112px;
  padding: 12px 16px;
  border-radius: 10px;
  background: var(--el-color-primary-light-9);
  text-align: center;
}
.score-board strong {
  display: block;
  color: var(--el-color-primary);
  font-size: 22px;
}
.score-board span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.detail-table {
  margin-top: 18px;
}
.summary-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 18px;
}
.question-replay-list {
  display: grid;
  gap: 16px;
}
.detail-question-list {
  margin-top: 18px;
}
.question-replay {
  padding: 20px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: var(--el-bg-color);
}
.question-replay__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.question-replay__header > div {
  display: flex;
  align-items: center;
  gap: 10px;
}
.question-stem {
  color: var(--el-text-color-primary);
  font-size: 15px;
  line-height: 1.75;
}
.question-stem :deep(p) {
  margin: 0 0 8px;
}
.question-images {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 12px;
}
.question-images :deep(.el-image) {
  width: min(100%, 420px);
  max-height: 280px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}
.answer-options {
  display: grid;
  gap: 9px;
  margin-top: 16px;
}
.answer-option {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  min-height: 46px;
  padding: 8px 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 9px;
}
.answer-option.is-selected {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}
.answer-option.is-correct {
  border-color: var(--el-color-success-light-5);
  background: var(--el-color-success-light-9);
}
.answer-option.is-selected.is-correct {
  border-color: var(--el-color-success);
}
.answer-option__label {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border-radius: 50%;
  background: var(--el-fill-color-light);
  font-weight: 700;
}
.answer-option__content {
  line-height: 1.6;
}
.answer-option__badges {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 5px;
}
.answer-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 24px;
  margin-top: 14px;
  padding: 11px 14px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-size: 13px;
}
.analysis-block,
.ai-feedback {
  margin-top: 16px;
  padding: 14px 16px;
  border-radius: 9px;
  background: var(--el-fill-color-lighter);
}
.analysis-block p,
.ai-feedback p {
  margin: 8px 0 0;
  color: var(--el-text-color-regular);
  line-height: 1.75;
  white-space: pre-wrap;
}
.ai-feedback {
  border-left: 3px solid var(--el-color-primary);
}
.subjective-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 16px;
}
.subjective-grid > div {
  min-width: 0;
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 9px;
}
.long-answer {
  margin: 10px 0 0;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}
@media (max-width: 760px) {
  .filter-form {
    align-items: stretch;
    flex-direction: column;
  }
  .filter-form :deep(.el-input),
  .filter-form :deep(.el-select),
  .filter-form :deep(.el-date-editor) {
    width: 100%;
  }
  .filter-form__actions {
    margin-left: 0;
  }
  .history-table {
    min-width: 760px;
  }
  .history-list {
    overflow-x: auto;
  }
  .detail-summary {
    align-items: flex-start;
    flex-direction: column;
  }
  .score-board {
    min-width: 0;
  }
  .question-replay {
    padding: 15px;
  }
  .answer-option {
    grid-template-columns: 32px minmax(0, 1fr);
  }
  .answer-option__badges {
    grid-column: 2;
    justify-content: flex-start;
  }
  .subjective-grid {
    grid-template-columns: 1fr;
  }
}
</style>
