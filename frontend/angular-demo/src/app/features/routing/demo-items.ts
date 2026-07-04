export interface DemoRoutingItem {
  id: number;
  name: string;
  description: string;
}

export const DEMO_ITEMS: DemoRoutingItem[] = [
  { id: 1, name: 'Route Params', description: 'The :id segment is read from ActivatedRoute.' },
  { id: 2, name: 'Guards', description: 'CanActivateFn blocks direct navigation without a selection.' },
  { id: 3, name: 'Resolvers', description: 'ResolveFn preloads data before the route activates.' },
];
