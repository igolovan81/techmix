import { Component } from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { HeavyWidget } from './heavy-widget';

@Component({
  selector: 'app-deferred-loading-demo',
  imports: [MatProgressSpinnerModule, HeavyWidget],
  template: `
    <div style="height: 1200px;">Scroll down to trigger &#64;defer (on viewport).</div>
    @defer (on viewport) {
      <app-heavy-widget />
    } @placeholder (minimum 200ms) {
      <p data-testid="defer-placeholder">Placeholder: scroll into view to load.</p>
    } @loading (minimum 200ms) {
      <mat-spinner data-testid="defer-loading" diameter="24"></mat-spinner>
    } @error {
      <p data-testid="defer-error">Failed to load widget.</p>
    }
  `,
})
export class DeferredLoadingDemo {}
