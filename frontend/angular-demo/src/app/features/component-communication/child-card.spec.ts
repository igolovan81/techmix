import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ChildCard } from './child-card';

@Component({
  imports: [ChildCard],
  template: `<app-child-card title="Test" [(liked)]="liked" (dismissed)="dismissedCount = dismissedCount + 1" />`,
})
class HostComponent {
  liked = false;
  dismissedCount = 0;
}

describe('ChildCard', () => {
  it('toggles the model() signal and emits dismissed', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const card = fixture.debugElement.children[0].componentInstance as ChildCard;

    card.toggleLiked();
    fixture.detectChanges();
    expect(fixture.componentInstance.liked).toBe(true);

    card.dismissed.emit();
    expect(fixture.componentInstance.dismissedCount).toBe(1);
  });
});
