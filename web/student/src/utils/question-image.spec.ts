import { describe, expect, it } from 'vitest';
import { formatQuestionStem, toQuestionImageUrl } from './question-image';

describe('question image rendering', () => {
  it('routes private MinIO URLs through the same-origin proxy without changing the signature', () => {
    expect(toQuestionImageUrl('http://127.0.0.1:9000/certmuse/questions/36.png?X-Amz-Signature=abc')).toBe(
      '/oss-proxy/127.0.0.1:9000/certmuse/questions/36.png?X-Amz-Signature=abc'
    );
  });

  it('keeps public URLs unchanged and removes import-only image markers from a stem', () => {
    expect(toQuestionImageUrl('https://cdn.example.com/question.png')).toBe('https://cdn.example.com/question.png');
    expect(formatQuestionStem('题干 [图片1] {{question-image:1}}')).toBe('题干');
    expect(formatQuestionStem('题干 [图片:{{question-image:1}}--ruankao]')).toBe('题干');
  });
});
