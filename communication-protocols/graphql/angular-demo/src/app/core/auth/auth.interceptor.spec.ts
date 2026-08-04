import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authService: AuthService;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => httpMock.verify());

  it('adds no Authorization header when there is no session', () => {
    http.post('/graphql', {}).subscribe();
    const req = httpMock.expectOne('/graphql');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('adds a Basic Authorization header from the current session', () => {
    authService.setSession(
      { username: 'user', password: 'userPassword' },
      { id: '1', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' },
    );

    http.post('/graphql', {}).subscribe();

    const req = httpMock.expectOne('/graphql');
    expect(req.request.headers.get('Authorization')).toBe(`Basic ${btoa('user:userPassword')}`);
    req.flush({});
  });
});
