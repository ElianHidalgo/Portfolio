import { LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { LocalizedPipe } from './localized.pipe';

function pipeFor(locale: string): LocalizedPipe {
  TestBed.configureTestingModule({ providers: [{ provide: LOCALE_ID, useValue: locale }] });
  return TestBed.runInInjectionContext(() => new LocalizedPipe());
}

describe('LocalizedPipe', () => {
  it('picks Spanish for an es locale', () => {
    expect(pipeFor('es').transform({ es: 'Hola', en: 'Hello' })).toBe('Hola');
  });

  it('picks English for en-US', () => {
    expect(pipeFor('en-US').transform({ es: 'Hola', en: 'Hello' })).toBe('Hello');
  });

  it('returns an empty string for missing values', () => {
    const pipe = pipeFor('es');
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform({ es: null, en: null })).toBe('');
  });
});
