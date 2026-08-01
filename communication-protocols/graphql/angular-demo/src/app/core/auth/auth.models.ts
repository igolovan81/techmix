import { Role } from '../graphql/graphql.models';

export interface AuthUser {
  id: string;
  username: string;
  displayName: string;
  role: Role;
}

export interface Credentials {
  username: string;
  password: string;
}
