import { ViewportScroller } from '@angular/common';
import { Injector, afterNextRender, effect, inject } from '@angular/core';
import { Router } from '@angular/router';

/**
 * Scrolls to the URL fragment (e.g. `/#projects`) once `isReady()` turns true.
 *
 * The router's own anchor scrolling runs when navigation ends, which is too early for
 * sections that only render after their API data arrives. Call from an injection context.
 * `fragments` limits which anchors this caller owns.
 */
export function scrollToFragmentWhenReady(isReady: () => boolean, fragments: readonly string[]): void {
  const router = inject(Router);
  const scroller = inject(ViewportScroller);
  const injector = inject(Injector);

  effect(() => {
    if (!isReady()) {
      return;
    }
    const fragment = router.parseUrl(router.url).fragment;
    if (fragment && fragments.includes(fragment)) {
      afterNextRender(() => scroller.scrollToAnchor(fragment), { injector });
    }
  });
}
