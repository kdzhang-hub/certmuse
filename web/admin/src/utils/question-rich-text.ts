import { renderToString } from 'katex';
import { sanitizeHtml } from './sanitize';

type Delimiter = { left: string; right: string; displayMode: boolean };
const DELIMITERS: Delimiter[] = [
  { left: '$$', right: '$$', displayMode: true },
  { left: '\\[', right: '\\]', displayMode: true },
  { left: '\\(', right: '\\)', displayMode: false },
  { left: '$', right: '$', displayMode: false }
];
const escapeHtml = (value: string) =>
  value.replace(
    /[&<>'"]/g,
    character => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[character]!
  );

function isEscaped(value: string, index: number) {
  let slashCount = 0;
  for (let cursor = index - 1; cursor >= 0 && value[cursor] === '\\'; cursor -= 1) slashCount += 1;
  return slashCount % 2 === 1;
}
function findDelimiter(value: string, delimiter: Delimiter, start: number, closing = false) {
  const token = closing ? delimiter.right : delimiter.left;
  let index = value.indexOf(token, start);
  while (index >= 0) {
    if (!isEscaped(value, index) && !(token === '$' && value[index + 1] === '$')) return index;
    index = value.indexOf(token, index + token.length);
  }
  return -1;
}
function findOpeningDelimiter(value: string, start: number) {
  let match: { index: number; delimiter: Delimiter } | undefined;
  for (const delimiter of DELIMITERS) {
    const index = findDelimiter(value, delimiter, start);
    if (index < 0) continue;
    if (!match || index < match.index || (index === match.index && delimiter.left.length > match.delimiter.left.length))
      match = { index, delimiter };
  }
  return match;
}
function renderLatexInText(value: string) {
  let output = '';
  let cursor = 0;
  while (cursor < value.length) {
    const opening = findOpeningDelimiter(value, cursor);
    if (!opening) return output + escapeHtml(value.slice(cursor));
    output += escapeHtml(value.slice(cursor, opening.index));
    const formulaStart = opening.index + opening.delimiter.left.length;
    const closingIndex = findDelimiter(value, opening.delimiter, formulaStart, true);
    if (closingIndex < 0) return output + escapeHtml(value.slice(opening.index));
    output += renderToString(value.slice(formulaStart, closingIndex), {
      displayMode: opening.delimiter.displayMode,
      output: 'htmlAndMathml',
      throwOnError: false,
      trust: false
    });
    cursor = closingIndex + opening.delimiter.right.length;
  }
  return output;
}

/** Safely preserves supported rich-text markup and typesets LaTeX in text nodes. */
export function renderQuestionRichText(content: string) {
  const template = document.createElement('template');
  template.innerHTML = sanitizeHtml(content);
  const walker = document.createTreeWalker(template.content, NodeFilter.SHOW_TEXT);
  const textNodes: Text[] = [];
  while (walker.nextNode()) textNodes.push(walker.currentNode as Text);
  for (const textNode of textNodes) {
    const replacement = document.createElement('template');
    replacement.innerHTML = renderLatexInText(textNode.data);
    textNode.replaceWith(replacement.content);
  }
  return template.innerHTML;
}
