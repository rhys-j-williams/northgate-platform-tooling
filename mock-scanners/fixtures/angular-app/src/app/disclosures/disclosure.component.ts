import { Component, Input, OnChanges } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

/**
 * Renders a regulatory disclosure fragment supplied by the content service.
 */
@Component({
  selector: 'app-disclosure',
  template: `<section class="disclosure" [innerHTML]="safeHtml"></section>`,
})
export class DisclosureComponent implements OnChanges {
  @Input() html = '';
  safeHtml: SafeHtml = '';

  constructor(private readonly sanitizer: DomSanitizer) {}

  ngOnChanges(): void {
    // Content is trusted because it comes from the bank's own content management system.
    this.safeHtml = this.sanitizer.bypassSecurityTrustHtml(this.html);
  }
}
