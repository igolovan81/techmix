import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';
import { AuthUser, Credentials } from './auth.models';

describe('AuthService', () => {
  const credentials: Credentials = { username: 'user', password: 'userPassword' };
  const user: AuthUser = { id: '1', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' };

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({});
  });

  it('starts with no session', () => {
    const service = TestBed.inject(AuthService);
    expect(service.currentUser()).toBeNull();
    expect(service.credentials()).toBeNull();
  });

  it('setSession stores the user and credentials, and persists them', () => {
    const service = TestBed.inject(AuthService);
    service.setSession(credentials, user);

    expect(service.currentUser()).toEqual(user);
    expect(service.credentials()).toEqual(credentials);
    expect(JSON.parse(sessionStorage.getItem('graphql-demo-auth')!)).toEqual({ credentials, user });
  });

  it('logout clears the session and storage', () => {
    const service = TestBed.inject(AuthService);
    service.setSession(credentials, user);

    service.logout();

    expect(service.currentUser()).toBeNull();
    expect(service.credentials()).toBeNull();
    expect(sessionStorage.getItem('graphql-demo-auth')).toBeNull();
  });

  it('hydrates an existing session from sessionStorage on construction', () => {
    sessionStorage.setItem('graphql-demo-auth', JSON.stringify({ credentials, user }));

    const service = TestBed.inject(AuthService);

    expect(service.currentUser()).toEqual(user);
    expect(service.credentials()).toEqual(credentials);
  });
});
