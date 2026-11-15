import { DOCUMENT } from '@angular/common';
import { Injectable, LOCALE_ID, inject } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { SITE_ORIGIN } from './site-origin';

export interface SeoPage {
  title: string;
  description: string;
  /** Locale-free path, e.g. `/` or `/projects/gamevault`. */
  path: string;
  type?: 'website' | 'article';
}

const LOCALES = ['es', 'en'] as const;
const OG_LOCALE: Record<(typeof LOCALES)[number], string> = { es: 'es_EC', en: 'en_US' };

/** Owns the page's search and social metadata: title, description, Open Graph, canonical, hreflang. */
@Injectable({ providedIn: 'root' })
export class SeoService {
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly document = inject(DOCUMENT);
  private readonly origin = inject(SITE_ORIGIN);
  private readonly locale: (typeof LOCALES)[number] = inject(LOCALE_ID).startsWith('es') ? 'es' : 'en';

  setPage({ title, description, path, type = 'website' }: SeoPage): void {
    const urlFor = (locale: string) => `${this.origin}/${locale}${path}`;

    this.title.setTitle(title);
    this.meta.updateTag({ name: 'description', content: description });
    this.meta.updateTag({ property: 'og:title', content: title });
    this.meta.updateTag({ property: 'og:description', content: description });
    this.meta.updateTag({ property: 'og:type', content: type });
    this.meta.updateTag({ property: 'og:url', content: urlFor(this.locale) });
    this.meta.updateTag({ property: 'og:locale', content: OG_LOCALE[this.locale] });
    this.meta.updateTag({ property: 'og:site_name', content: $localize`:@@pageTitle:Portfolio` });
    this.meta.updateTag({ name: 'twitter:card', content: 'summary' });

    this.setLink('link[rel="canonical"]', { rel: 'canonical', href: urlFor(this.locale) });
    for (const locale of LOCALES) {
      this.setLink(`link[rel="alternate"][hreflang="${locale}"]`, {
        rel: 'alternate',
        hreflang: locale,
        href: urlFor(locale),
      });
    }
    this.setLink('link[rel="alternate"][hreflang="x-default"]', {
      rel: 'alternate',
      hreflang: 'x-default',
      href: urlFor('es'),
    });
  }

  /** Updates the `<link>` matching `selector`, creating it once if missing. */
  private setLink(selector: string, attributes: Record<string, string>): void {
    let link = this.document.head.querySelector<HTMLLinkElement>(selector);
    if (!link) {
      link = this.document.createElement('link');
      this.document.head.appendChild(link);
    }
    for (const [name, value] of Object.entries(attributes)) {
      link.setAttribute(name, value);
    }
  }
}
