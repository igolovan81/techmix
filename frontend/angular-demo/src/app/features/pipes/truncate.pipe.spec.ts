import { TruncatePipe } from './truncate.pipe';

describe('TruncatePipe', () => {
  const pipe = new TruncatePipe();

  it('returns the value unchanged when shorter than maxLength', () => {
    expect(pipe.transform('short', 20)).toBe('short');
  });

  it('truncates and appends an ellipsis when longer than maxLength', () => {
    expect(pipe.transform('this text is definitely too long', 10)).toBe('this text…');
  });

  it('defaults maxLength to 20', () => {
    expect(pipe.transform('exactly twenty chars')).toBe('exactly twenty chars');
  });
});
