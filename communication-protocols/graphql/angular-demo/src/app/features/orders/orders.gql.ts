import { gql } from 'apollo-angular';

export const MY_ORDERS_QUERY = gql`
  query MyOrders($first: Int, $after: String) {
    me {
      id
      orders(first: $first, after: $after) {
        totalCount
        pageInfo {
          hasNextPage
          endCursor
        }
        edges {
          cursor
          node {
            id
            status
            placedAt
            totalCents
          }
        }
      }
    }
  }
`;

export const ALL_ORDERS_QUERY = gql`
  query AllOrders($status: OrderStatus, $first: Int, $after: String) {
    orders(status: $status, first: $first, after: $after) {
      totalCount
      pageInfo {
        hasNextPage
        endCursor
      }
      edges {
        cursor
        node {
          id
          status
          placedAt
          totalCents
          user {
            id
            displayName
          }
        }
      }
    }
  }
`;

export const ORDER_QUERY = gql`
  query Order($id: ID!) {
    order(id: $id) {
      id
      status
      placedAt
      totalCents
      user {
        id
        displayName
      }
      items {
        id
        quantity
        unitPriceCents
        lineTotalCents
        product {
          id
          name
        }
      }
    }
  }
`;

export const PLACE_ORDER_MUTATION = gql`
  mutation PlaceOrder($input: PlaceOrderInput!) {
    placeOrder(input: $input) {
      id
      status
      placedAt
      totalCents
    }
  }
`;

export const UPDATE_ORDER_STATUS_MUTATION = gql`
  mutation UpdateOrderStatus($id: ID!, $status: OrderStatus!) {
    updateOrderStatus(id: $id, status: $status) {
      id
      status
    }
  }
`;
