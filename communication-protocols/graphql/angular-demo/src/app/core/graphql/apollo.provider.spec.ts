import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { InMemoryCache } from '@apollo/client';
import { createApollo } from './apollo.provider';

describe('createApollo', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideRouter([]), provideNoopAnimations()],
    });
  });

  it('returns Apollo client options with a link and an InMemoryCache', () => {
    const options = TestBed.runInInjectionContext(() => createApollo());

    expect(options.link).toBeTruthy();
    expect(options.cache instanceof InMemoryCache).toBe(true);
  });
});
