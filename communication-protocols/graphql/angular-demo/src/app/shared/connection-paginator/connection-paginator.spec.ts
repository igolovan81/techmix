import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ConnectionPaginator } from './connection-paginator';

@Component({
  imports: [ConnectionPaginator],
  template: `
    <app-connection-paginator
      [hasNextPage]="hasNextPage"
      [totalCount]="10"
      [loadedCount]="3"
      (loadMore)="loadMoreCount = loadMoreCount + 1"
    />
  `,
})
class HostComponent {
  hasNextPage = true;
  loadMoreCount = 0;
}

describe('ConnectionPaginator', () => {
  it('shows the loaded/total summary', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const summary = fixture.nativeElement.querySelector('[data-testid="paginator-summary"]');
    expect(summary.textContent).toContain('3');
    expect(summary.textContent).toContain('10');
  });

  it('emits loadMore when the button is clicked and hasNextPage is true', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="load-more-button"]');

    button.click();

    expect(fixture.componentInstance.loadMoreCount).toBe(1);
  });

  it('disables the button when hasNextPage is false', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.hasNextPage = false;
    fixture.detectChanges();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="load-more-button"]');

    expect(button.disabled).toBe(true);
  });
});
