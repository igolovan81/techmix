import { GraphQLFormattedError } from 'graphql';

export type ErrorClassification = 'UNAUTHORIZED' | 'FORBIDDEN' | 'BAD_REQUEST' | 'INTERNAL_ERROR' | 'UNKNOWN';

const KNOWN_CLASSIFICATIONS: readonly string[] = ['UNAUTHORIZED', 'FORBIDDEN', 'BAD_REQUEST', 'INTERNAL_ERROR'];

export function classifyGraphQlError(error: GraphQLFormattedError): ErrorClassification {
  const classification = error.extensions?.['classification'];
  return typeof classification === 'string' && KNOWN_CLASSIFICATIONS.includes(classification)
    ? (classification as ErrorClassification)
    : 'UNKNOWN';
}

export interface ErrorHandlerDeps {
  logout: () => void;
  navigateToLogin: () => void;
  showMessage: (message: string) => void;
}

export function handleGraphQlErrors(errors: readonly GraphQLFormattedError[], deps: ErrorHandlerDeps): void {
  for (const error of errors) {
    const classification = classifyGraphQlError(error);
    if (classification === 'UNAUTHORIZED') {
      deps.logout();
      deps.navigateToLogin();
      deps.showMessage('Session expired — please log in again.');
    } else if (classification === 'FORBIDDEN') {
      deps.showMessage(`Not allowed: ${error.message}`);
    } else {
      deps.showMessage(`Error: ${error.message}`);
    }
  }
}
