import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { API_ORIGIN } from './api-origin';
import { BackendWake } from './backend-wake';

describe('BackendWake', () => {
  afterEach(() => vi.restoreAllMocks());

  it('pings the liveness endpoint once per page load', () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null));
    TestBed.configureTestingModule({ providers: [{ provide: API_ORIGIN, useValue: 'https://api.example' }] });
    const wake = TestBed.inject(BackendWake);

    wake.wake();
    wake.wake();

    expect(fetchSpy).toHaveBeenCalledTimes(1);
    expect(fetchSpy).toHaveBeenCalledWith('https://api.example/api/health/liveness',
      expect.objectContaining({ mode: 'no-cors', cache: 'no-store' }));
  });

  it('ignores a failed ping', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('offline'));
    TestBed.configureTestingModule({ providers: [{ provide: API_ORIGIN, useValue: '' }] });

    expect(() => TestBed.inject(BackendWake).wake()).not.toThrow();
    await Promise.resolve();
  });
});
