import { Inject, Injectable, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import Keycloak from 'keycloak-js';
import { UserProfile } from './user-profile';

@Injectable({
  providedIn: 'root'
})
export class KeycloakService {

  private _keycloak: Keycloak | undefined;
  private _profile : UserProfile | undefined;

  get keycloak(): Keycloak | undefined{
    if (!this._keycloak) {
      this._keycloak = new Keycloak({
        url: 'http://localhost:8081',
        realm: 'geo-tracking-solution',
        clientId: 'angular-client'
      });   
    }
    return this._keycloak
  }


  get profile(): UserProfile | undefined {
    return this._profile;
  }

  constructor(@Inject(PLATFORM_ID) private platformId: Object) {}

  async init(): Promise<boolean> {
    if (isPlatformBrowser(this.platformId)) {
      console.log("init keycloak");
      const authenticated = await this.keycloak?.init({
        onLoad: 'login-required',
        pkceMethod: 'S256',
        flow: 'standard',
      })
      if (authenticated) {
        this._profile = (await this.keycloak?.loadUserProfile()) as UserProfile;
        this._profile.token = this._keycloak?.token;
        return true;
      }
    }
    return false;
  }

  get roles(): string[] {
    if (this.keycloak?.tokenParsed) {
      const tokenParsed = this.keycloak.tokenParsed as any;
      const realmRoles = tokenParsed.realm_access?.roles || [];
      const resourceRoles = tokenParsed.resource_access?.['angular-client']?.roles || [];
      return [...realmRoles, ...resourceRoles];
    }
    return [];
  }

  hasRole(role: string): boolean {
    return this.roles.includes(role);
  }
  isGroupMaster(): boolean {return this.hasRole('groupmaster');}
  isSquadmaster(): boolean {return this.hasRole('squadmaster');}
  isMember(): boolean {return (this.hasRole('member') || this.isSquadmaster() || this.isGroupMaster())}
  
  
  login() {
    return this.keycloak?.login();
  }

  logout() {
    return this.keycloak?.logout({
      redirectUri:'http://localhost:4200'
    });
  }
}
