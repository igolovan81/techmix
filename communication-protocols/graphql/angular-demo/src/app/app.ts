import { Component, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from './core/auth/auth.service';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterOutlet, MatToolbarModule, MatButtonModule],
  templateUrl: './app.html',
})
export class App {
  protected readonly authService = inject(AuthService);

  logout(): void {
    this.authService.logout();
  }
}
