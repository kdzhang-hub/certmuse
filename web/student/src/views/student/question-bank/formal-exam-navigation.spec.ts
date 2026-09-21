import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const examPages = [
  resolve(process.cwd(), 'src/views/student/question-bank/past-papers/exam.vue'),
  resolve(process.cwd(), 'src/views/student/question-bank/mock-exams/exam.vue')
];

describe('formal exam navigation persistence', () => {
  it.each(examPages)('persists the destination order before loading the next question: %s', page => {
    const source = readFileSync(page, 'utf8');

    expect(source).toContain('async function flushSave(currentQuestionOrder = order.value)');
    expect(source).toContain('currentQuestionOrder\n    });');
    expect(source).toContain('if (!(await flushSave(target))) return;');
  });
});
