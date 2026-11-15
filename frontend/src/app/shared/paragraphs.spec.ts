import { splitParagraphs } from './paragraphs';

describe('splitParagraphs', () => {
  it('splits on blank lines', () => {
    expect(splitParagraphs('A\n\nB')).toEqual(['A', 'B']);
  });

  it('handles Windows line endings', () => {
    expect(splitParagraphs('A\r\n\r\nB')).toEqual(['A', 'B']);
  });

  it('returns nothing for blank text', () => {
    expect(splitParagraphs('  ')).toEqual([]);
  });
});
