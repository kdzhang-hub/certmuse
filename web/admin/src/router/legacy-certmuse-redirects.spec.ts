import { createMemoryHistory, createRouter } from 'vue-router';
import { describe, expect, it } from 'vitest';
import { constantRoutes } from './index';

const destinationRoutes = [
  { path: '/content/resources', name: 'TextbookListDestination', component: { template: '<div />' } },
  { path: '/content/questions', name: 'QuestionListDestination', component: { template: '<div />' } },
  { path: '/review-rules/content-review', name: 'ContentReviewDestination', component: { template: '<div />' } }
];

describe('legacy CertMuse content routes', () => {
  it.each([
    ['/content/resources/edit/textbook-1', 'TextbookListDestination'],
    ['/content/resources/preview/textbook-1', 'TextbookListDestination'],
    ['/content/questions/edit/question-1', 'QuestionListDestination'],
    ['/content/questions/preview/question-1', 'QuestionListDestination'],
    ['/review-rules/content-review/detail/revision-1', 'ContentReviewDestination']
  ])('redirects %s to its implemented management view', async (legacyPath, destinationName) => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [...constantRoutes, ...destinationRoutes]
    });

    await router.push(legacyPath);
    await router.isReady();

    expect(router.currentRoute.value.name).toBe(destinationName);
  });
});
