import { Injectable, computed, signal } from '@angular/core';
import { AuthUser, Credentials } from './auth.models';

const STORAGE_KEY = 'graphql-demo-auth';

interface StoredSession {
  credentials: Credentials;
  user: AuthUser;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly session = signal<StoredSession | null>(readStoredSession());

  readonly currentUser = computed(() => this.session()?.user ?? null);
  readonly credentials = computed(() => this.session()?.credentials ?? null);

  setSession(credentials: Credentials, user: AuthUser): void {
    const stored: StoredSession = { credentials, user };
    this.session.set(stored);
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(stored));
  }

  logout(): void {
    this.session.set(null);
    sessionStorage.removeItem(STORAGE_KEY);
  }
}

function readStoredSession(): StoredSession | null {
  const raw = sessionStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as StoredSession;
  } catch {
    return null;
  }
}
