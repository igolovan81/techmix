import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { delay } from 'rxjs/operators';

export interface DemoItem {
  id: number;
  name: string;
}

@Injectable({ providedIn: 'root' })
export class FakeApiService {
  private readonly http = inject(HttpClient);

  getItems(): Observable<DemoItem[]> {
    return this.http.get<DemoItem[]>('data/items.json').pipe(delay(400));
  }
}
