import { TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { Router, provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Apollo } from 'apollo-angular';
import { of, throwError } from 'rxjs';
import { Login } from './login';
import { AuthService } from '../../core/auth/auth.service';

describe('Login', () => {
  let apollo: jasmine.SpyObj<Apollo>;
  let authService: AuthService;
  let router: Router;

  beforeEach(() => {
    sessionStorage.clear();
    apollo = jasmine.createSpyObj<Apollo>(['query']);
    TestBed.configureTestingModule({
      imports: [Login, ReactiveFormsModule],
      providers: [provideRouter([]), provideNoopAnimations(), { provide: Apollo, useValue: apollo }],
    });
    authService = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  it('quick-selecting a role logs in and navigates to /catalog', () => {
    const user = { id: '2', username: 'admin', displayName: 'Demo Admin', role: 'ADMIN' as const };
    apollo.query.and.returnValue(of({ data: { me: user } }) as never);
    spyOn(router, 'navigateByUrl');

    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
    fixture.componentInstance.quickSelect('admin', 'adminPassword');

    expect(apollo.query).toHaveBeenCalledWith(
      jasmine.objectContaining({
        context: { headers: { Authorization: `Basic ${btoa('admin:adminPassword')}` } },
      }),
    );
    expect(authService.currentUser()).toEqual(user);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/catalog');
  });

  it('shows an error message when the credentials are rejected', () => {
    apollo.query.and.returnValue(throwError(() => new Error('unauthorized')) as never);

    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
    fixture.componentInstance.quickSelect('user', 'wrong');
    fixture.detectChanges();

    expect(fixture.componentInstance.error()).toBe('Invalid username or password.');
    expect(authService.currentUser()).toBeNull();
  });
});
