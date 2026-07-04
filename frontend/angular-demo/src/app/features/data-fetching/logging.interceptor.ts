import { HttpInterceptorFn } from '@angular/common/http';
import { tap } from 'rxjs/operators';

export const loggingInterceptor: HttpInterceptorFn = (req, next) => {
  const start = Date.now();
  return next(req).pipe(
    tap({
      next: () => console.debug(`[HTTP] ${req.method} ${req.url} completed in ${Date.now() - start}ms`),
      error: (error) =>
        console.debug(`[HTTP] ${req.method} ${req.url} failed after ${Date.now() - start}ms`, error),
    }),
  );
};
