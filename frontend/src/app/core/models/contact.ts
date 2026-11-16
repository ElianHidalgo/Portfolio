/** The contact form as the API receives it (`POST /api/contact`). */
export interface ContactMessage {
  name: string;
  email: string;
  message: string;
  locale: 'es' | 'en';
  /** Honeypot: hidden from people, so only bots fill it in. */
  website: string;
}
