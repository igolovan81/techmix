import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Apollo } from 'apollo-angular';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { AuthUser } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { ME_QUERY } from './login.gql';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  templateUrl: './login.html',
})
export class Login {
  private readonly apollo = inject(Apollo);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  quickSelect(username: string, password: string): void {
    this.form.setValue({ username, password });
    this.submit();
  }

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    const { username, password } = this.form.getRawValue();
    this.loading.set(true);
    this.error.set(null);
    const token = btoa(`${username}:${password}`);

    this.apollo
      .query<{ me: AuthUser }>({
        query: ME_QUERY,
        context: { headers: { Authorization: `Basic ${token}` } },
        fetchPolicy: 'network-only',
      })
      .subscribe({
        next: (result) => {
          this.loading.set(false);
          this.authService.setSession({ username, password }, result.data!.me);
          this.router.navigateByUrl('/catalog');
        },
        error: () => {
          this.loading.set(false);
          this.error.set('Invalid username or password.');
        },
      });
  }
}
