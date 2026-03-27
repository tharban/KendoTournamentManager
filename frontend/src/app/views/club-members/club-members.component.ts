import {AfterViewInit, Component, OnInit} from '@angular/core';
import {Router} from '@angular/router';
import {Participant} from '../../models/participant';
import {ClubManagerService, ClubManagerInfo} from '../../services/club-manager.service';
import {RbacService} from '../../services/rbac/rbac.service';
import {RbacBasedComponent} from '../../components/RbacBasedComponent';
import {TranslocoService, TRANSLOCO_SCOPE} from '@ngneat/transloco';
import {DatatableColumn} from '@biit-solutions/wizardry-theme/table';
import {combineLatest} from 'rxjs';
import {SystemOverloadService} from '../../services/notifications/system-overload.service';
import {ErrorHandler} from '@biit-solutions/wizardry-theme/utils';
import {BiitSnackbarService} from '@biit-solutions/wizardry-theme/info';
import {UserSessionService} from '../../services/user-session.service';
import {LoginService} from '../../services/login.service';

@Component({
  selector: 'app-club-members',
  templateUrl: './club-members.component.html',
  styleUrls: ['./club-members.component.scss'],
  providers: [
    {
      provide: TRANSLOCO_SCOPE,
      multi: true,
      useValue: {scope: '', alias: 't'}
    }
  ]
})
export class ClubMembersComponent extends RbacBasedComponent implements AfterViewInit, OnInit {
  protected columns: DatatableColumn[] = [];
  protected pageSize: number = 10;
  protected pageSizes: number[] = [10, 25, 50, 100];
  protected participants: Participant[] = [];
  protected loading: boolean = false;
  protected showQr: boolean = false;
  protected clubInfo: ClubManagerInfo | null = null;

  protected readonly port: number = +window.location.port;

  constructor(private router: Router,
              private clubManagerService: ClubManagerService,
              rbacService: RbacService,
              private transloco: TranslocoService,
              private systemOverloadService: SystemOverloadService,
              private biitSnackbarService: BiitSnackbarService,
              private userSessionService: UserSessionService,
              private loginService: LoginService) {
    super(rbacService);
  }

  ngOnInit(): void {
    this.clubManagerService.getMyClub().subscribe({
      next: (info: ClubManagerInfo): void => {
        this.clubInfo = info;
      },
      error: (): void => {
        this.loginService.logout();
        this.router.navigate(['/club-manager/login']);
      }
    });
  }

  ngAfterViewInit(): void {
    combineLatest([
      this.transloco.selectTranslate('id'),
      this.transloco.selectTranslate('name'),
      this.transloco.selectTranslate('lastname'),
    ]).subscribe(([id, name, lastname]) => {
      this.columns = [
        new DatatableColumn(id, 'id', false, 80),
        new DatatableColumn(name, 'name'),
        new DatatableColumn(lastname, 'lastname'),
      ];
      this.loadData();
    });
  }

  loadData(): void {
    if (!this.clubInfo) {
      // Wait for clubInfo
      this.clubManagerService.getMyClub().subscribe({
        next: (info: ClubManagerInfo): void => {
          this.clubInfo = info;
          this.fetchParticipants(info.clubId);
        }
      });
    } else {
      this.fetchParticipants(this.clubInfo.clubId);
    }
  }

  private fetchParticipants(clubId: number): void {
    this.loading = true;
    this.systemOverloadService.isTransactionalBusy.next(true);
    this.clubManagerService.getClubParticipants(clubId).subscribe({
      next: (_participants: Participant[]): void => {
        this.participants = _participants.map(p => Participant.clone(p));
      },
      error: error => ErrorHandler.notify(error, this.transloco, this.biitSnackbarService)
    }).add(() => {
      this.loading = false;
      this.systemOverloadService.isTransactionalBusy.next(false);
    });
  }

  openStatistics(participant: Participant): void {
    if (participant) {
      this.userSessionService.setSelectedParticipant(participant.id + '');
      this.router.navigate(['/participants/statistics'], {state: {participantId: participant.id}});
    }
  }
}
