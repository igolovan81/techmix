import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('authGuard', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  it('allows navigation when a user is logged in', () => {
    const authService = TestBed.inject(AuthService);
    authService.setSession(
      { username: 'user', password: 'userPassword' },
      { id: '1', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' },
    );

    const result = TestBed.runInInjectionContext(() => authGuard(null as never, null as never));

    expect(result).toBe(true);
  });

  it('redirects to /login when no user is logged in', () => {
    const router = TestBed.inject(Router);

    const result = TestBed.runInInjectionContext(() => authGuard(null as never, null as never));

    expect(result).toEqual(router.parseUrl('/login'));
  });
});
