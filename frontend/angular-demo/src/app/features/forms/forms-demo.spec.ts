import { TestBed } from '@angular/core/testing';
import { FormsDemo } from './forms-demo';

describe('FormsDemo', () => {
  it('is invalid until email and age pass validation, then submits', () => {
    const fixture = TestBed.createComponent(FormsDemo);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.signupForm.invalid).toBe(true);

    component.signupForm.controls.email.setValue('not-an-email');
    component.signupForm.controls.age.setValue(16);
    expect(component.signupForm.controls.email.invalid).toBe(true);
    expect(component.signupForm.controls.age.invalid).toBe(true);

    component.signupForm.controls.email.setValue('demo@example.com');
    component.signupForm.controls.age.setValue(21);
    expect(component.signupForm.valid).toBe(true);

    component.onSubmit();
    expect(component.submitted()).toBe(true);
  });
});
