import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { App } from './app';
import { AuthService } from './core/auth/auth.service';

describe('App', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideNoopAnimations()],
    });
  });

  it('creates the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('hides the nav when no user is logged in', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="nav-links"]')).toBeNull();
  });

  it('shows the nav and the display name once a user is logged in', () => {
    const authService = TestBed.inject(AuthService);
    authService.setSession(
      { username: 'user', password: 'userPassword' },
      { id: '1', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' },
    );

    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-links"]')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Demo User');
  });
});
