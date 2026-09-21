import { describe, expect, it } from 'vitest';
import { formatQuestionPreviewStem, toQuestionImageUrl } from './image-url';

describe('question image URLs', () => {
  it('routes Docker-only MinIO links through the same-origin proxy without altering the signature', () => {
    expect(toQuestionImageUrl('http://host.docker.internal:9000/certmuse/questions/1.png?X-Amz-Signature=abc'))
      .toBe('/oss-proxy/host.docker.internal:9000/certmuse/questions/1.png?X-Amz-Signature=abc');
    expect(toQuestionImageUrl('http://minio:9000/certmuse/questions/1.png?signature=abc'))
      .toBe('/oss-proxy/minio:9000/certmuse/questions/1.png?signature=abc');
  });

  it('routes the production loopback MinIO link through the public origin', () => {
    expect(toQuestionImageUrl('http://127.0.0.1:9000/certmuse/question-imports/1.png?X-Amz-Signature=abc'))
      .toBe('/oss-proxy/127.0.0.1:9000/certmuse/question-imports/1.png?X-Amz-Signature=abc');
  });

  it('keeps public and malformed source URLs unchanged', () => {
    expect(toQuestionImageUrl('https://cdn.example.com/question.png')).toBe('https://cdn.example.com/question.png');
    expect(toQuestionImageUrl('not a url')).toBe('not a url');
  });

  it('removes import-only image tokens from the learner-facing stem', () => {
    expect(formatQuestionPreviewStem('题干内容 [图片1] {{question-image:1}}')).toBe('题干内容');
  });
});
