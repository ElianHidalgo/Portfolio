import { DOCUMENT } from '@angular/common';
import { LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { SITE_ORIGIN } from './site-origin';
import { SeoService } from './seo.service';

describe('SeoService', () => {
  let seo: SeoService;
  let doc: Document;

  const meta = (selector: string) => doc.head.querySelector(`meta[${selector}]`)?.getAttribute('content');
  const href = (selector: string) => doc.head.querySelector(`link[${selector}]`)?.getAttribute('href');

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        { provide: LOCALE_ID, useValue: 'es' },
        { provide: SITE_ORIGIN, useValue: 'https://example.dev' },
      ],
    });
    seo = TestBed.inject(SeoService);
    doc = TestBed.inject(DOCUMENT);
  });

  const page = {
    title: 'GameVault | Portafolio',
    description: 'App multiplataforma.',
    path: '/projects/gamevault',
    type: 'article' as const,
  };

  it('sets title, description and Open Graph tags', () => {
    seo.setPage(page);

    expect(doc.title).toBe('GameVault | Portafolio');
    expect(meta('name="description"')).toBe('App multiplataforma.');
    expect(meta('property="og:title"')).toBe('GameVault | Portafolio');
    expect(meta('property="og:url"')).toBe('https://example.dev/es/projects/gamevault');
    expect(meta('property="og:locale"')).toBe('es_EC');
    expect(meta('property="og:type"')).toBe('article');
  });

  it('writes canonical and hreflang alternates', () => {
    seo.setPage(page);

    expect(href('rel="canonical"')).toBe('https://example.dev/es/projects/gamevault');
    expect(href('hreflang="es"')).toBe('https://example.dev/es/projects/gamevault');
    expect(href('hreflang="en"')).toBe('https://example.dev/en/projects/gamevault');
    expect(href('hreflang="x-default"')).toBe('https://example.dev/es/projects/gamevault');
  });

  it('replaces previous tags instead of duplicating them', () => {
    seo.setPage(page);
    seo.setPage({ ...page, description: 'Otra.', path: '/' });

    expect(doc.head.querySelectorAll('link[rel="canonical"]').length).toBe(1);
    expect(doc.head.querySelectorAll('meta[name="description"]').length).toBe(1);
    expect(href('rel="canonical"')).toBe('https://example.dev/es/');
  });
});
