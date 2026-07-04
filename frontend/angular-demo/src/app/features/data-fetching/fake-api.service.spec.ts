import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DemoItem, FakeApiService } from './fake-api.service';

describe('FakeApiService', () => {
  let service: FakeApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(FakeApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('fetches items from data/items.json via HttpClient', async () => {
    const expected: DemoItem[] = [{ id: 1, name: 'Signals' }];
    let received: DemoItem[] | undefined;

    service.getItems().subscribe((items) => (received = items));

    const req = httpMock.expectOne('data/items.json');
    expect(req.request.method).toBe('GET');
    req.flush(expected);

    await new Promise((resolve) => setTimeout(resolve, 450));

    expect(received).toEqual(expected);
  });
});
