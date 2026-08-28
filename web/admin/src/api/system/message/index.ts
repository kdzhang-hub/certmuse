import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { MessageBoxVO, MessageTicketVO } from './types';

export function getMessageBox(): AxiosPromise<MessageBoxVO> {
  return request({
    url: '/resource/message/box',
    method: 'get'
  });
}

export function issueMessageTicket(): AxiosPromise<MessageTicketVO> {
  return request({ url: '/resource/message/ticket', method: 'post' });
}

export function closeMessageConnection(connectionId: string): AxiosPromise<void> {
  return request({
    url: '/resource/message/close',
    method: 'post',
    params: { connectionId }
  });
}
