import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {LoginService} from '../../services/login.service';
import {ClubManagerService} from '../../services/club-manager.service';
import {BiitProgressBarType, BiitSnackbarService, NotificationType} from '@biit-solutions/wizardry-theme/info';
import {TranslocoService} from '@ngneat/transloco';
import {FormsModule} from '@angular/forms';
import {CommonModule} from '@angular/common';
import {TranslocoModule} from '@ngneat/transloco';
import {BiitButtonModule} from '@biit-solutions/wizardry-theme/button';
import {BiitProgressBarModule} from '@biit-solutions/wizardry-theme/info';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';

@Component({
  selector: 'app-club-manager-login',
  templateUrl: './club-manager-login.component.html',
  styleUrls: ['./club-manager-login.component.scss'],
  standalone: false
})
export class ClubManagerLoginComponent implements OnInit {
  protected email: string = '';
  protected waiting: boolean = false;
  protected emailSent: boolean = false;
  protected loginError: boolean = false;
  protected readonly BiitProgressBarType = BiitProgressBarType;

  constructor(private route: ActivatedRoute, private router: Router,
              private loginService: LoginService, private clubManagerService: ClubManagerService,
              private biitSnackbarService: BiitSnackbarService,
              private transloco: TranslocoService) {
  }

  ngOnInit(): void {
    const passcode = this.route.snapshot.queryParamMap.get('passcode');
    if (passcode) {
      this.waiting = true;
      // Auto-authenticate with passcode from QR / email link
      if (this.loginService.getJwtValue()) {
        this.loginService.logout();
      }
      this.loginService.setClubManagerUserSession(passcode, (): void => {
        this.waiting = false;
        this.router.navigate(['/club-manager/members']);
      });
    }
  }

  requestLink(): void {
    if (!this.email || !this.email.trim()) {
      return;
    }
    this.waiting = true;
    this.loginError = false;
    this.clubManagerService.requestLoginLink(this.email.trim()).subscribe({
      next: (): void => {
        this.waiting = false;
        this.emailSent = true;
        this.biitSnackbarService.showNotification(
          this.transloco.translate('clubManagerLinkSent'), NotificationType.SUCCESS);
      },
      error: (): void => {
        this.waiting = false;
        this.loginError = true;
      }
    });
  }
}
