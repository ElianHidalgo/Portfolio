import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { SITE_ORIGIN } from './site-origin';

describe('SITE_ORIGIN (browser)', () => {
  // Tests share one document: start and end without any canonical link left by other specs.
  const removeCanonicals = () =>
    TestBed.inject(DOCUMENT).head.querySelectorAll('link[rel="canonical"]').forEach((link) => link.remove());
  beforeEach(removeCanonicals);
  afterEach(removeCanonicals);

  it('keeps the public origin written at build time, not the host serving the page', () => {
    const doc = TestBed.inject(DOCUMENT);
    const link = doc.createElement('link');
    link.setAttribute('rel', 'canonical');
    link.setAttribute('href', 'https://victor.example/es/');
    doc.head.appendChild(link);

    expect(TestBed.inject(SITE_ORIGIN)).toBe('https://victor.example');
  });

  it('falls back to the current origin when the page has no canonical link', () => {
    expect(TestBed.inject(SITE_ORIGIN)).toBe(TestBed.inject(DOCUMENT).location.origin);
  });
});
