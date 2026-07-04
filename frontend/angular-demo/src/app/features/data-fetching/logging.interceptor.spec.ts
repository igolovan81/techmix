import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { loggingInterceptor } from './logging.interceptor';

describe('loggingInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([loggingInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('logs the completed request to the console', () => {
    const logSpy = spyOn(console, 'debug');

    http.get('data/items.json').subscribe();
    httpMock.expectOne('data/items.json').flush([]);

    expect(logSpy).toHaveBeenCalledWith(jasmine.stringMatching(/GET data\/items\.json completed/));
  });
});
