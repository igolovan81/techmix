import { Component, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { HighlightDirective } from './highlight.directive';
import { RepeatDirective } from './repeat.directive';

@Component({
  selector: 'app-directives-demo',
  imports: [MatCardModule, HighlightDirective, RepeatDirective],
  template: `
    <mat-card>
      <p appHighlight="#c8e6c9" data-testid="highlight-target">
        Hover to highlight (custom attribute directive).
      </p>
      <ul data-testid="repeat-list">
        <li *appRepeat="repeatCount()">Item repeated by *appRepeat</li>
      </ul>
    </mat-card>
  `,
})
export class DirectivesDemo {
  readonly repeatCount = signal(3);
}
