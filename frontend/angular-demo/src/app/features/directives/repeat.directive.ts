import { Directive, Input, OnChanges, TemplateRef, ViewContainerRef, inject } from '@angular/core';

@Directive({ selector: '[appRepeat]' })
export class RepeatDirective implements OnChanges {
  private readonly templateRef = inject(TemplateRef<{ $implicit: number }>);
  private readonly viewContainerRef = inject(ViewContainerRef);

  @Input({ required: true }) appRepeat = 0;

  ngOnChanges(): void {
    this.viewContainerRef.clear();
    for (let i = 0; i < this.appRepeat; i++) {
      this.viewContainerRef.createEmbeddedView(this.templateRef, { $implicit: i });
    }
  }
}
