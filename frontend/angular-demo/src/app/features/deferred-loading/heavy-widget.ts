import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

@Component({
  selector: 'app-heavy-widget',
  imports: [MatCardModule],
  template: `
    <mat-card data-testid="heavy-widget">
      <mat-card-content>Heavy widget loaded via &#64;defer.</mat-card-content>
    </mat-card>
  `,
})
export class HeavyWidget {}
