export const buildSseTicketUrl = (baseApi: string, path: string, ticket: string) => {
  const separator = path.includes('?') ? '&' : '?';
  return `${baseApi}${path}${separator}ticket=${encodeURIComponent(ticket)}`;
};
