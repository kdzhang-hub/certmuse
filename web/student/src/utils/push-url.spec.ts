import { describe, expect, it } from 'vitest';
import { buildSseTicketUrl } from './push-url';

describe('buildSseTicketUrl', () => {
  it('uses only an encoded one-time ticket in the SSE query string', () => {
    const url = buildSseTicketUrl('/prod-api', '/resource/message', 'short lived/+ticket');

    expect(url).toBe('/prod-api/resource/message?ticket=short%20lived%2F%2Bticket');
    expect(url).not.toContain('Authorization');
    expect(url).not.toContain('Bearer');
  });

  it('preserves an existing query string', () => {
    expect(buildSseTicketUrl('/prod-api', '/resource/message?locale=zh-CN', 'ticket')).toBe(
      '/prod-api/resource/message?locale=zh-CN&ticket=ticket'
    );
  });
});
