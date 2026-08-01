import { gql } from 'apollo-angular';

export const CATEGORIES_QUERY = gql`
  query Categories($first: Int, $after: String) {
    categories(first: $first, after: $after) {
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
        }
      }
    }
  }
`;

export const CATEGORY_CHILDREN_QUERY = gql`
  query CategoryChildren($id: ID!, $first: Int, $after: String) {
    category(id: $id) {
      id
      children(first: $first, after: $after) {
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
          }
        }
      }
    }
  }
`;

export const CATEGORY_PRODUCTS_QUERY = gql`
  query CategoryProducts($id: ID!, $first: Int, $after: String) {
    category(id: $id) {
      id
      products(first: $first, after: $after) {
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
          }
        }
      }
    }
  }
`;
