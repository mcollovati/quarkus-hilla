// Uses the Vaadin provided login an logout helper methods
import { login as loginImpl, LoginResult, logout as logoutImpl } from '@hilla/frontend';
import * as UserInfoEndpoint from 'Frontend/generated/UserInfoEndpoint';
import UserInfo from 'Frontend/generated/com/example/application/entities/UserInfo';
import {appStore} from "Frontend/stores/app-store";

interface Authentication {
  user: UserInfo;
  timestamp: number;
}

let authentication: Authentication | undefined = undefined;

const AUTHENTICATION_KEY = 'authentication';
const THIRTY_DAYS_MS = 30 * 24 * 60 * 60 * 1000;

// Get authentication from local storage
const storedAuthenticationJson = localStorage.getItem(AUTHENTICATION_KEY);
if (storedAuthenticationJson !== null) {
  const storedAuthentication = JSON.parse(storedAuthenticationJson) as Authentication;
  // Check that the stored timestamp is not older than 30 days
  const hasRecentAuthenticationTimestamp =
    new Date().getTime() - storedAuthentication.timestamp < THIRTY_DAYS_MS;
  if (hasRecentAuthenticationTimestamp) {
    // Use loaded authentication
    authentication = storedAuthentication;
  } else {
    // Delete expired stored authentication
    setSessionExpired();
  }
}

/**
 * Forces the session to expire and removes user information stored in
 * `localStorage`.
 */
export function setSessionExpired() {
  authentication = undefined;

  // Delete the authentication from the local storage
  localStorage.removeItem(AUTHENTICATION_KEY);
}

/**
 * Login wrapper method that retrieves user information.
 *
 * Uses `localStorage` for offline support.
 */
export async function login(username: string, password: string): Promise<LoginResult> {
  const result = await loginImpl(username, password);
  if (!result.error) {
    // Get user info from endpoint
    const user = await UserInfoEndpoint.me();
    authentication = {
      user,
      timestamp: new Date().getTime(),
    };

    // Save the authentication to local storage
    localStorage.setItem(AUTHENTICATION_KEY, JSON.stringify(authentication));
  }

  return result;
}

export async function checkAuthentication() : Promise<Authentication|undefined> {
  if (!isLoggedIn()) {
    const user = await UserInfoEndpoint.me().catch( (err) => {
      console.log("======================== uffffff", err)
      return null;
    });
    if (!!user) {
      authentication = {
        user,
        timestamp: new Date().getTime(),
      };
      // Save the authentication to local storage
      localStorage.setItem(AUTHENTICATION_KEY, JSON.stringify(authentication));
    }
  }
  return authentication;
}

/**
 * Login wrapper method that retrieves user information.
 *
 * Uses `localStorage` for offline support.
 */
export async function logout() {
  setSessionExpired();
  await logoutImpl();
  appStore.clearUserInfo();
  location.href = '';
}

/**
 * Checks if the user is logged in.
 */
export function isLoggedIn() {
  return !!authentication;
}

/**
 * Checks if the user has the role.
 */
export function isUserInRole(role: string) {
  if (!authentication) {
    return false;
  }

  return authentication.user.roles.includes(`${role}`);
}

