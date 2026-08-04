import { GraphQLFormattedError } from 'graphql';
import { classifyGraphQlError, ErrorHandlerDeps, handleGraphQlErrors } from './error-handling';

function errorWith(classification: string | undefined, message = 'boom'): GraphQLFormattedError {
  return { message, extensions: classification === undefined ? undefined : { classification } };
}

describe('classifyGraphQlError', () => {
  it('classifies known extensions.classification values', () => {
    expect(classifyGraphQlError(errorWith('UNAUTHORIZED'))).toBe('UNAUTHORIZED');
    expect(classifyGraphQlError(errorWith('FORBIDDEN'))).toBe('FORBIDDEN');
    expect(classifyGraphQlError(errorWith('BAD_REQUEST'))).toBe('BAD_REQUEST');
    expect(classifyGraphQlError(errorWith('INTERNAL_ERROR'))).toBe('INTERNAL_ERROR');
  });

  it('falls back to UNKNOWN when extensions.classification is missing or unrecognized', () => {
    expect(classifyGraphQlError(errorWith(undefined))).toBe('UNKNOWN');
    expect(classifyGraphQlError(errorWith('SOMETHING_ELSE'))).toBe('UNKNOWN');
  });
});

describe('handleGraphQlErrors', () => {
  let deps: jasmine.SpyObj<ErrorHandlerDeps>;

  beforeEach(() => {
    deps = jasmine.createSpyObj<ErrorHandlerDeps>(['logout', 'navigateToLogin', 'showMessage']);
  });

  it('logs out, navigates to /login, and shows a message on UNAUTHORIZED', () => {
    handleGraphQlErrors([errorWith('UNAUTHORIZED', 'not authenticated')], deps);

    expect(deps.logout).toHaveBeenCalled();
    expect(deps.navigateToLogin).toHaveBeenCalled();
    expect(deps.showMessage).toHaveBeenCalledWith('Session expired — please log in again.');
  });

  it('shows a message without logging out on FORBIDDEN', () => {
    handleGraphQlErrors([errorWith('FORBIDDEN', 'not your order')], deps);

    expect(deps.logout).not.toHaveBeenCalled();
    expect(deps.navigateToLogin).not.toHaveBeenCalled();
    expect(deps.showMessage).toHaveBeenCalledWith('Not allowed: not your order');
  });

  it('shows a generic message for other classifications', () => {
    handleGraphQlErrors([errorWith('INTERNAL_ERROR', 'simulated failure')], deps);

    expect(deps.showMessage).toHaveBeenCalledWith('Error: simulated failure');
  });

  it('handles every error in the list', () => {
    handleGraphQlErrors([errorWith('FORBIDDEN', 'a'), errorWith('BAD_REQUEST', 'b')], deps);

    expect(deps.showMessage).toHaveBeenCalledTimes(2);
  });
});
