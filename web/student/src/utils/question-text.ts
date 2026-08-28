import { renderToString } from 'katex';
import { formatQuestionStem } from './question-image';

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

function findOpeningDelimiter(value: string, start: number) {
  let match: { index: number; delimiter: Delimiter } | undefined;
  for (const delimiter of DELIMITERS) {
    const index = value.indexOf(delimiter.left, start);
    if (index < 0 || isEscaped(value, index) || (delimiter.left === '$' && value[index + 1] === '$')) continue;
    if (
      !match ||
      index < match.index ||
      (index === match.index && delimiter.left.length > match.delimiter.left.length)
    ) {
      match = { index, delimiter };
    }
  }
  return match;
}

function findClosingDelimiter(value: string, start: number, delimiter: Delimiter) {
  let index = value.indexOf(delimiter.right, start);
  while (index >= 0) {
    if (!isEscaped(value, index) && !(delimiter.right === '$' && value[index + 1] === '$')) return index;
    index = value.indexOf(delimiter.right, index + delimiter.right.length);
  }
  return -1;
}

/** Escapes question text, then renders only standard LaTeX math delimiters with KaTeX. */
export function renderQuestionText(content: string) {
  const text = formatQuestionStem(content ?? '');
  let output = '';
  let cursor = 0;

  while (cursor < text.length) {
    const opening = findOpeningDelimiter(text, cursor);
    if (!opening) return output + escapeHtml(text.slice(cursor));

    output += escapeHtml(text.slice(cursor, opening.index));
    const formulaStart = opening.index + opening.delimiter.left.length;
    const closingIndex = findClosingDelimiter(text, formulaStart, opening.delimiter);
    if (closingIndex < 0) return output + escapeHtml(text.slice(opening.index));

    output += renderToString(text.slice(formulaStart, closingIndex), {
      displayMode: opening.delimiter.displayMode,
      output: 'htmlAndMathml',
      throwOnError: false,
      trust: false
    });
    cursor = closingIndex + opening.delimiter.right.length;
  }

  return output;
}
