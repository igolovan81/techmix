import { gql } from 'apollo-angular';

export const REVIEW_ADDED_SUBSCRIPTION = gql`
  subscription ReviewAdded($productId: ID) {
    reviewAdded(productId: $productId) {
      id
      productId
      rating
      comment
      author {
        id
        displayName
      }
    }
  }
`;
