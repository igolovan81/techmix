import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { HttpLink } from 'apollo-angular/http';
import { ApolloLink, CombinedGraphQLErrors, InMemoryCache, from } from '@apollo/client';
import { ErrorLink } from '@apollo/client/link/error';
import { GraphQLWsLink } from '@apollo/client/link/subscriptions';
import { createClient } from 'graphql-ws';
import { OperationTypeNode } from 'graphql';
import { AuthService } from '../auth/auth.service';
import { handleGraphQlErrors } from './error-handling';

export function createApollo() {
  const httpLink = inject(HttpLink);
  const authService = inject(AuthService);
  const router = inject(Router);
  const snackBar = inject(MatSnackBar);

  const http = httpLink.create({ uri: '/graphql' });

  const wsProtocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
  const wsLink = new GraphQLWsLink(createClient({ url: `${wsProtocol}//${location.host}/graphql` }));

  const errorLink = new ErrorLink(({ error }) => {
    if (CombinedGraphQLErrors.is(error)) {
      handleGraphQlErrors(error.errors, {
        logout: () => authService.logout(),
        navigateToLogin: () => router.navigateByUrl('/login'),
        showMessage: (message) => snackBar.open(message, 'Dismiss', { duration: 5000 }),
      });
    }
  });

  const link = ApolloLink.split(
    ({ operationType }) => operationType === OperationTypeNode.SUBSCRIPTION,
    wsLink,
    from([errorLink, http]),
  );

  return {
    link,
    cache: new InMemoryCache(),
  };
}
