import {NgModule} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {RouterModule, Routes} from '@angular/router';
import {TranslocoModule} from '@ngneat/transloco';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {BiitButtonModule} from '@biit-solutions/wizardry-theme/button';
import {BiitProgressBarModule} from '@biit-solutions/wizardry-theme/info';
import {ClubManagerLoginComponent} from './club-manager-login.component';

const routes: Routes = [
  {path: '', component: ClubManagerLoginComponent}
];

@NgModule({
  declarations: [ClubManagerLoginComponent],
  imports: [
    CommonModule,
    FormsModule,
    TranslocoModule,
    MatFormFieldModule,
    MatInputModule,
    BiitButtonModule,
    BiitProgressBarModule,
    RouterModule.forChild(routes)
  ],
  exports: [ClubManagerLoginComponent]
})
export class ClubManagerLoginModule {
}
