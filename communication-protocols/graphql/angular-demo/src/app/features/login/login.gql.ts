import { gql } from 'apollo-angular';

export const ME_QUERY = gql`
  query Me {
    me {
      id
      username
      displayName
      role
    }
  }
`;
