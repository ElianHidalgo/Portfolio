import { readBuildEnv } from './build-env';

describe('readBuildEnv', () => {
  it('uses local defaults', () => {
    expect(readBuildEnv({})).toEqual({
      apiUrl: 'http://localhost:8080',
      siteUrl: 'http://localhost:8080',
      prerender: true,
    });
  });

  it('trims trailing slashes so URLs never contain "//api"', () => {
    const env = readBuildEnv({ API_URL: 'https://x.onrender.com/', SITE_URL: 'https://site.pages.dev//' });
    expect(env.apiUrl).toBe('https://x.onrender.com');
    expect(env.siteUrl).toBe('https://site.pages.dev');
  });

  it('turns prerendering off only with PRERENDER=false', () => {
    expect(readBuildEnv({ PRERENDER: 'false' }).prerender).toBe(false);
    expect(readBuildEnv({ PRERENDER: 'true' }).prerender).toBe(true);
  });
});
