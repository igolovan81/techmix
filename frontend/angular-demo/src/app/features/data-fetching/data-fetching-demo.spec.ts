import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DataFetchingDemo } from './data-fetching-demo';

describe('DataFetchingDemo', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('shows items once the HTTP call resolves', async () => {
    const fixture = TestBed.createComponent(DataFetchingDemo);
    fixture.detectChanges();

    expect(fixture.componentInstance.items()).toBeUndefined();

    httpMock.expectOne('data/items.json').flush([{ id: 1, name: 'Signals' }]);
    await new Promise((resolve) => setTimeout(resolve, 450));
    fixture.detectChanges();

    expect(fixture.componentInstance.items()).toEqual([{ id: 1, name: 'Signals' }]);
  });
});
