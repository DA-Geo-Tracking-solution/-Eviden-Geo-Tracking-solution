package at.htlhl.geo_tracking_solution.service;

import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import at.htlhl.geo_tracking_solution.model.Role;
import at.htlhl.geo_tracking_solution.model.keycloak.User;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
//import javax.ws.rs.core.Response;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private Keycloak keycloak;

    @Value("${keycloak.realm}")
    private String realm;

    public List<UserRepresentation> getGroupMembers() {
        List<GroupRepresentation> groups = getUserGroups();

        if (!groups.isEmpty()) {
            String groupId = groups.get(0).getId();
            return keycloak.realm(realm).groups().group(groupId).members().stream()
                .filter(user -> !user.getEmail().equals(getUserEmail()))
                .collect(Collectors.toList());
        }
        return List.of();
    }


    public UserRepresentation getUserByEmail(String userEmail) {
        System.out.println(userEmail);
        List<UserRepresentation> users = keycloak.realm(realm).users().searchByEmail(userEmail, true);
        return users.stream().findFirst().orElse(null);
    }

    public String createUser(UserRepresentation user) {
        List<GroupRepresentation> groups = getUserGroups();
        if (groups.isEmpty()) {
            return "Invalid Request! User is in no group! >8[)";
        }
        List<String> groupNames = List.of(groups.get(0).getName());
        user.setGroups(groupNames);

        Response response = keycloak.realm(realm).users().create(user);
        addRolesToUser(user.getEmail(), Role.Member.getAsList());

        try {
            int status = response.getStatus();
            if (status == 400) {
                return ("Wrong User Input!");
            } else if (status == 409) {
                return ("Username or Email already in Use!");
            } else if (status == 201) {
                return "Successful created user :)";
                // String userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$",
                // "$1");
            } else {
                String errorResponse = response.readEntity(String.class);
                System.err.println("Error response from Keycloak: " + errorResponse);
                return "Keycloak: " + status + " - " + errorResponse;
            }
        } finally {
            response.close();
        }
    }

    public void addRolesToUser(String userEmail, List<String> roles) {
        RealmResource realmResource = keycloak.realm(realm);
        UserResource userResource = realmResource.users().get(getUserByEmail(userEmail).getId());
        userResource.roles().realmLevel().add(Role.getAsRoleList(roles, keycloak, realm));
    }

    public String updateUser(User userModel) throws ResponseStatusException {
        try {


            System.out.println(getUserEmail() + "---  ,  ---" + userModel.getEmail());
            if (getUserByEmail(userModel.getEmail()) != null && !getUserEmail().equals(userModel.getEmail())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email alrady exists");
            }

            // Find the user by ID
            UserRepresentation user = keycloak.realm(realm).users().get(getUserId()).toRepresentation();
    
            if (user == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "User not found");
            }
    
            String userId = user.getId();
    
            // Update user details with userModel values
            if (userModel.getFirstname() != null) user.setFirstName(userModel.getFirstname());
            if (userModel.getLastname() != null) user.setLastName(userModel.getLastname());
            if (userModel.getEmail() != null) user.setEmail(userModel.getEmail());
            if (userModel.getUsername() != null) user.setUsername(userModel.getUsername());

            // Send the update request
            keycloak.realm(realm).users().get(userId).update(user);
    
            // Return success message
            return "User details updated successfully";
    
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An error occurred while updating the user: " + e.getMessage());
        }
    }

    public String updatePassword(String password) {
        try {
            UserRepresentation user = keycloak.realm(realm).users().get(getUserId()).toRepresentation();
        
            if (user == null) {
                return "User not found";
            }

            String userId = user.getId();

            // Update the user's password
            if (password != null && !password.isEmpty()) {
                CredentialRepresentation credential = new CredentialRepresentation();
                credential.setType(CredentialRepresentation.PASSWORD);
                credential.setValue(password);
                credential.setTemporary(false);

                // Send the password reset request
                keycloak.realm(realm).users().get(userId).resetPassword(credential);
            }
            return "Password updated successfully";
        } catch (Exception e) {
            return "An error occurred while updating the user: " + e.getMessage();
        }
    }
    
    

    public String deleteUserByEmail(String userEmail) {
        try {
            String userId = getUserByEmail(userEmail).getId();
            boolean userExists = getGroupMembers().stream()
                    .anyMatch(user -> user.getId().equals(userId));

            if (!userExists) {
                return "User does not exist in the same group";
            }

            keycloak.realm(realm).users().delete(userId);
            return "User deleted successfully";
        } catch (Exception e) {
            return "Error deleting user: " + e.getMessage();
        }
    }

    private String getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return (String) jwt.getClaims().get(StandardClaimNames.SUB);
    }

    public String getUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return (String) jwt.getClaims().get("email");
    }

    public String getUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return (String) jwt.getClaims().get("preferred_username");
    }

    public List<GroupRepresentation> getUserGroups() {
        return keycloak.realm(realm).users().get(getUserId()).groups();
    }
}
