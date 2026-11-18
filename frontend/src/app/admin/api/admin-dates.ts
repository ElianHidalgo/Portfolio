const FORMAT = new Intl.DateTimeFormat('es-EC', { dateStyle: 'medium', timeStyle: 'short' });

/** A timestamp from the API, in Spanish and the visitor's own time zone (e.g. "18 nov 2026, 10:30"). */
export function formatAdminDate(iso: string): string {
  return FORMAT.format(new Date(iso));
}
