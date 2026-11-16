import { TestBed } from '@angular/core/testing';
import { API_ORIGIN } from './api-origin';

describe('API_ORIGIN', () => {
  afterEach(() => document.head.querySelector('meta[name="api-origin"]')?.remove());

  it('reads the API origin from the meta tag written at prerender', () => {
    const meta = document.createElement('meta');
    meta.name = 'api-origin';
    meta.content = 'https://api.example';
    document.head.append(meta);

    expect(TestBed.inject(API_ORIGIN)).toBe('https://api.example');
  });

  it('falls back to relative URLs when the page has no meta tag', () => {
    expect(TestBed.inject(API_ORIGIN)).toBe('');
  });
});
