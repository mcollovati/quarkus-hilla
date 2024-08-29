// Uses the Vaadin provided login an logout helper methods
import { configureAuth } from '@vaadin/hilla-react-auth';
import { UserInfoEndpoint } from 'Frontend/generated/endpoints';
const auth = configureAuth(UserInfoEndpoint.me);
export const useAuth = auth.useAuth;
export const AuthProvider = auth.AuthProvider;
