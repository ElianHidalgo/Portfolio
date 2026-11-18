import { CanDeactivateFn } from '@angular/router';

/** A page that knows whether the user has edits that were not saved yet. */
export interface HasUnsavedChanges {
  hasUnsavedChanges(): boolean;
}

/** Asks before leaving a page with unsaved edits. */
export const unsavedChangesGuard: CanDeactivateFn<HasUnsavedChanges> = (component) =>
  !component.hasUnsavedChanges() || window.confirm('Tienes cambios sin guardar. ¿Salir de todos modos?');
