import { gql } from 'apollo-angular';

export const PRODUCTS_QUERY = gql`
  query Products($filter: ProductFilter, $first: Int, $after: String) {
    products(filter: $filter, first: $first, after: $after) {
      totalCount
      pageInfo {
        hasNextPage
        endCursor
      }
      edges {
        cursor
        node {
          id
          name
          priceCents
          stockQty
          categories {
            id
            name
          }
        }
      }
    }
  }
`;

export const PRODUCT_QUERY = gql`
  query Product($id: ID!) {
    product(id: $id) {
      id
      name
      priceCents
      stockQty
      imageUrl
      categories {
        id
        name
      }
    }
  }
`;

export const PRODUCT_REVIEWS_QUERY = gql`
  query ProductReviews($id: ID!, $filter: ReviewFilter, $first: Int, $after: String) {
    product(id: $id) {
      id
      reviews(filter: $filter, first: $first, after: $after) {
        totalCount
        pageInfo {
          hasNextPage
          endCursor
        }
        edges {
          cursor
          node {
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
      }
    }
  }
`;

export const ADD_REVIEW_MUTATION = gql`
  mutation AddReview($input: AddReviewInput!) {
    addReview(input: $input) {
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

export const DELETE_REVIEW_MUTATION = gql`
  mutation DeleteReview($id: ID!) {
    deleteReview(id: $id)
  }
`;
