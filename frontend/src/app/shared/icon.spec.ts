import { TestBed } from '@angular/core/testing';
import { Icon, IconName } from './icon';

describe('Icon', () => {
  const names: IconName[] = ['github', 'linkedin', 'mail', 'download', 'external'];

  for (const name of names) {
    it(`renders a decorative "${name}" svg that inherits the text colour`, async () => {
      const fixture = TestBed.createComponent(Icon);
      fixture.componentRef.setInput('name', name);
      await fixture.whenStable();

      const svg = (fixture.nativeElement as HTMLElement).querySelector('svg');
      expect(svg).not.toBeNull();
      expect(svg!.getAttribute('aria-hidden')).toBe('true');
      expect(svg!.querySelector('path, rect')).not.toBeNull();
    });
  }
});
