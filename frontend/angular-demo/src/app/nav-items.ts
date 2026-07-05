export interface NavItem {
  path: string;
  label: string;
}

export const NAV_ITEMS: NavItem[] = [
  { path: 'signals', label: 'Signals' },
  { path: 'component-communication', label: 'Component Communication' },
  { path: 'forms', label: 'Forms' },
  { path: 'data-fetching', label: 'Data Fetching' },
  { path: 'deferred-loading', label: 'Deferred Loading' },
  { path: 'routing', label: 'Routing' },
  { path: 'pipes', label: 'Pipes' },
  { path: 'directives', label: 'Directives' },
  { path: 'animations', label: 'Animations' },
  { path: 'custom-elements', label: 'Custom Elements' },
];
