import {NgModule} from '@angular/core';
import {CommonModule} from '@angular/common';
import {RouterModule, Routes} from '@angular/router';
import {TranslocoModule} from '@ngneat/transloco';
import {MatIconModule} from '@angular/material/icon';
import {MatButtonModule} from '@angular/material/button';
import {MatDividerModule} from '@angular/material/divider';
import {BiitDatatableModule} from '@biit-solutions/wizardry-theme/table';
import {BiitPopupModule} from '@biit-solutions/wizardry-theme/popup';
import {HasPermissionPipe} from '../../pipes/has-permission.pipe';
import {ParticipantQrCodeModule} from '../../components/participant-qr-code/participant-qr-code.module';
import {MatSpinnerOverlayModule} from '../../components/mat-spinner-overlay/mat-spinner-overlay.module';
import {ClubMembersComponent} from './club-members.component';
import {LoggedIn} from '../../interceptors/logged-in.service';

const routes: Routes = [
  {path: '', component: ClubMembersComponent, canActivate: [LoggedIn]}
];

@NgModule({
  declarations: [ClubMembersComponent],
  imports: [
    CommonModule,
    TranslocoModule,
    MatIconModule,
    MatButtonModule,
    MatDividerModule,
    BiitDatatableModule,
    BiitPopupModule,
    HasPermissionPipe,
    ParticipantQrCodeModule,
    MatSpinnerOverlayModule,
    RouterModule.forChild(routes)
  ],
  exports: [ClubMembersComponent]
})
export class ClubMembersModule {
}
