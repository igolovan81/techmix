import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HighlightDirective } from './highlight.directive';

@Component({
  imports: [HighlightDirective],
  template: `<p appHighlight="#c8e6c9">Hover me</p>`,
})
class HostComponent {}

describe('HighlightDirective', () => {
  it('sets and clears the background color on mouse enter/leave', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement.querySelector('p');

    el.dispatchEvent(new Event('mouseenter'));
    expect(el.style.backgroundColor).toBe('rgb(200, 230, 201)');

    el.dispatchEvent(new Event('mouseleave'));
    expect(el.style.backgroundColor).toBe('');
  });
});
