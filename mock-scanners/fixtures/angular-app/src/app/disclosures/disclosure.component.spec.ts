import { DisclosureComponent } from './disclosure.component';

describe('DisclosureComponent', () => {
  it('should be excluded from the scan', () => {
    const c = new DisclosureComponent({ bypassSecurityTrustHtml: (s: string) => s } as any);
    expect(c).toBeTruthy();
  });
});
