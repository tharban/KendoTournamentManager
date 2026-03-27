import {Injectable} from '@angular/core';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {Observable} from 'rxjs';
import {catchError, tap} from 'rxjs/operators';
import {EnvironmentService} from '../environment.service';
import {MessageService} from './message.service';
import {LoggerService} from './logger.service';
import {SystemOverloadService} from './notifications/system-overload.service';
import {Participant} from '../models/participant';

export interface ClubManagerInfo {
  clubId: number;
  clubName: string;
}

@Injectable({
  providedIn: 'root'
})
export class ClubManagerService {

  private baseUrl: string = this.environmentService.getBackendUrl() + '/club-managers';

  constructor(private http: HttpClient, private environmentService: EnvironmentService,
              private messageService: MessageService, private loggerService: LoggerService,
              private systemOverloadService: SystemOverloadService) {
  }

  sendInvitationEmail(clubId: number): Observable<void> {
    const url: string = `${this.baseUrl}/${clubId}/send-email`;
    return this.http.post<void>(url, {}).pipe(
      tap({
        next: () => this.loggerService.info(`Sent club manager invitation for club ${clubId}`),
        error: () => this.systemOverloadService.isBusy.next(false),
        complete: () => this.systemOverloadService.isBusy.next(false),
      }),
      catchError(this.messageService.handleError<void>(`sendInvitationEmail club=${clubId}`))
    );
  }

  requestLoginLink(email: string): Observable<void> {
    const url: string = `${this.baseUrl}/public/request-link`;
    return this.http.post<void>(url, {email}, {
      headers: new HttpHeaders({'Content-Type': 'application/json'})
    }).pipe(
      tap({
        next: () => this.loggerService.info(`Requested club manager link for email ${email}`),
        error: () => this.systemOverloadService.isBusy.next(false),
        complete: () => this.systemOverloadService.isBusy.next(false),
      }),
      catchError(this.messageService.handleError<void>(`requestLoginLink email=${email}`))
    );
  }

  getMyClub(): Observable<ClubManagerInfo> {
    const url: string = `${this.baseUrl}/me`;
    return this.http.get<ClubManagerInfo>(url).pipe(
      tap({
        next: () => this.loggerService.info(`Fetched club manager info`),
        error: () => this.systemOverloadService.isBusy.next(false),
        complete: () => this.systemOverloadService.isBusy.next(false),
      }),
      catchError(this.messageService.handleError<ClubManagerInfo>(`getMyClub`))
    );
  }

  getClubParticipants(clubId: number): Observable<Participant[]> {
    const url: string = `${this.baseUrl}/${clubId}/participants`;
    return this.http.get<Participant[]>(url).pipe(
      tap({
        next: () => this.loggerService.info(`Fetched participants for club ${clubId}`),
        error: () => this.systemOverloadService.isBusy.next(false),
        complete: () => this.systemOverloadService.isBusy.next(false),
      }),
      catchError(this.messageService.handleError<Participant[]>(`getClubParticipants clubId=${clubId}`))
    );
  }

  getClubManagerQr(clubId: number): Observable<any> {
    const url: string = `${this.baseUrl}/${clubId}/qr`;
    return this.http.get<any>(url).pipe(
      tap({
        next: () => this.loggerService.info(`Fetched QR for club ${clubId}`),
        error: () => this.systemOverloadService.isBusy.next(false),
        complete: () => this.systemOverloadService.isBusy.next(false),
      }),
      catchError(this.messageService.handleError<any>(`getClubManagerQr clubId=${clubId}`))
    );
  }
}
