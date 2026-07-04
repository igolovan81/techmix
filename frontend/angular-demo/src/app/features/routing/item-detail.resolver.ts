import { ResolveFn } from '@angular/router';
import { DEMO_ITEMS, DemoRoutingItem } from './demo-items';

export const itemDetailResolver: ResolveFn<DemoRoutingItem | undefined> = (route) => {
  const id = Number(route.paramMap.get('id'));
  return DEMO_ITEMS.find((item) => item.id === id);
};
