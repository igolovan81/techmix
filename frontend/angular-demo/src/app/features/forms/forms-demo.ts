import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-forms-demo',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <form [formGroup]="signupForm" (ngSubmit)="onSubmit()">
      <mat-form-field appearance="outline">
        <mat-label>Email</mat-label>
        <input matInput formControlName="email" data-testid="email-input" />
        @if (signupForm.controls.email.invalid && signupForm.controls.email.touched) {
          <mat-error data-testid="email-error">Enter a valid email address</mat-error>
        }
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Age</mat-label>
        <input matInput type="number" formControlName="age" data-testid="age-input" />
        @if (signupForm.controls.age.invalid && signupForm.controls.age.touched) {
          <mat-error data-testid="age-error">Must be 18 or older</mat-error>
        }
      </mat-form-field>
      <button
        mat-raised-button
        color="primary"
        type="submit"
        [disabled]="signupForm.invalid"
        data-testid="submit-button"
      >
        Submit
      </button>
    </form>
    @if (submitted()) {
      <p data-testid="submit-success">Submitted: {{ signupForm.value.email }}</p>
    }
  `,
})
export class FormsDemo {
  private readonly fb = inject(FormBuilder);
  readonly submitted = signal(false);

  readonly signupForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    age: [18, [Validators.required, Validators.min(18)]],
  });

  onSubmit(): void {
    if (this.signupForm.valid) {
      this.submitted.set(true);
    }
  }
}
