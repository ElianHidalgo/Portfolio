import { fetchWithRetry } from './fetch-with-retry';

const noWait = () => Promise.resolve();

describe('fetchWithRetry', () => {
  it('retries while the backend wakes up and returns the first good response', async () => {
    const answers: Array<() => Promise<Response>> = [
      () => Promise.reject(new TypeError('fetch failed')),
      () => Promise.resolve(new Response('', { status: 502 })),
      () => Promise.resolve(new Response('[]', { status: 200 })),
    ];
    let calls = 0;
    const response = await fetchWithRetry('http://api.test/x', { fetchFn: () => answers[calls++](), sleep: noWait });

    expect(calls).toBe(3);
    expect(response.status).toBe(200);
  });

  it('does not retry a client error', async () => {
    let calls = 0;
    const response = await fetchWithRetry('http://api.test/x', {
      fetchFn: () => (calls++, Promise.resolve(new Response('', { status: 404 }))),
      sleep: noWait,
    });
    expect(calls).toBe(1);
    expect(response.status).toBe(404);
  });

  it('aborts an attempt that hangs and tries again', async () => {
    let calls = 0;
    const hang = (_url: string, init?: RequestInit) =>
      new Promise<Response>((_resolve, reject) => {
        calls++;
        init?.signal?.addEventListener('abort', () => reject(new Error('aborted')));
      });
    const attempt = fetchWithRetry('http://api.test/x', { attempts: 2, timeoutMs: 10, fetchFn: hang, sleep: noWait });

    await expect(attempt).rejects.toThrow('after 2 attempts');
    expect(calls).toBe(2);
  });

  it('gives up after the last attempt with a clear message', async () => {
    let calls = 0;
    const attempt = fetchWithRetry('http://api.test/x', {
      attempts: 4,
      fetchFn: () => (calls++, Promise.reject(new TypeError('fetch failed'))),
      sleep: noWait,
    });
    await expect(attempt).rejects.toThrow('Prerender: cannot reach http://api.test/x after 4 attempts');
    expect(calls).toBe(4);
  });
});
