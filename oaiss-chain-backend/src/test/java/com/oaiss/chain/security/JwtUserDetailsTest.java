package com.oaiss.chain.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtUserDetails unit tests")
class JwtUserDetailsTest {

    private JwtUserDetails createAdminUser() {
        return JwtUserDetails.builder()
                .userId(1L)
                .username("admin")
                .password("hashedpw")
                .userType(4)
                .roles(List.of("ADMIN"))
                .enterpriseId(1L)
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
    }

    private JwtUserDetails createEnterpriseUser() {
        return JwtUserDetails.builder()
                .userId(2L)
                .username("enterprise1")
                .password("hashedpw2")
                .userType(1)
                .roles(List.of("ENTERPRISE"))
                .enterpriseId(100L)
                .enabled(true)
                .build();
    }

    private JwtUserDetails createReviewerUser() {
        return JwtUserDetails.builder()
                .userId(3L)
                .username("reviewer1")
                .password("hashedpw3")
                .userType(2)
                .roles(List.of("REVIEWER"))
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("getAuthorities should return ROLE_ prefixed authorities")
    void getAuthorities_shouldReturnRolePrefixAuthorities() {
        JwtUserDetails user = createAdminUser();
        Collection<? extends GrantedAuthority> auths = user.getAuthorities();
        assertEquals(1, auths.size());
        assertTrue(auths.contains(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    @DisplayName("getAuthorities should return empty list for null roles")
    void getAuthorities_shouldReturnEmptyForNullRoles() {
        JwtUserDetails user = JwtUserDetails.builder()
                .userId(1L)
                .username("test")
                .roles(null)
                .build();
        Collection<? extends GrantedAuthority> auths = user.getAuthorities();
        assertNotNull(auths);
        assertTrue(auths.isEmpty());
    }

    @Test
    @DisplayName("getAuthorities should return empty list for empty roles")
    void getAuthorities_shouldReturnEmptyForEmptyRoles() {
        JwtUserDetails user = JwtUserDetails.builder()
                .userId(1L)
                .username("test")
                .roles(List.of())
                .build();
        Collection<? extends GrantedAuthority> auths = user.getAuthorities();
        assertNotNull(auths);
        assertTrue(auths.isEmpty());
    }

    @Test
    @DisplayName("getAuthorities with multiple roles should return all")
    void getAuthorities_withMultipleRoles_shouldReturnAll() {
        JwtUserDetails user = JwtUserDetails.builder()
                .userId(1L)
                .username("multi")
                .roles(List.of("ADMIN", "ENTERPRISE"))
                .build();
        Collection<? extends GrantedAuthority> auths = user.getAuthorities();
        assertEquals(2, auths.size());
        assertTrue(auths.contains(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertTrue(auths.contains(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
    }

    @Test
    @DisplayName("getPassword and getUsername should return correct values")
    void getPasswordAndGetUsername_shouldReturnCorrectValues() {
        JwtUserDetails user = createAdminUser();
        assertEquals("hashedpw", user.getPassword());
        assertEquals("admin", user.getUsername());
    }

    @Test
    @DisplayName("isEnabled should return true when enabled is true")
    void isEnabled_shouldReturnTrueWhenEnabled() {
        JwtUserDetails user = createAdminUser();
        assertTrue(user.isEnabled());
    }

    @Test
    @DisplayName("isEnabled should return false when enabled is false")
    void isEnabled_shouldReturnFalseWhenDisabled() {
        JwtUserDetails user = JwtUserDetails.builder()
                .enabled(false)
                .build();
        assertFalse(user.isEnabled());
    }

    @Test
    @DisplayName("isEnabled should default to true when null")
    void isEnabled_shouldDefaultToTrueWhenNull() {
        JwtUserDetails user = JwtUserDetails.builder().build();
        assertTrue(user.isEnabled());
    }

    @Test
    @DisplayName("isAccountNonExpired should return true when null")
    void isAccountNonExpired_shouldDefaultToTrueWhenNull() {
        JwtUserDetails user = JwtUserDetails.builder().build();
        assertTrue(user.isAccountNonExpired());
    }

    @Test
    @DisplayName("isAccountNonLocked should return true when null")
    void isAccountNonLocked_shouldDefaultToTrueWhenNull() {
        JwtUserDetails user = JwtUserDetails.builder().build();
        assertTrue(user.isAccountNonLocked());
    }

    @Test
    @DisplayName("isCredentialsNonExpired should return true when null")
    void isCredentialsNonExpired_shouldDefaultToTrueWhenNull() {
        JwtUserDetails user = JwtUserDetails.builder().build();
        assertTrue(user.isCredentialsNonExpired());
    }

    @Test
    @DisplayName("isAccountNonExpired should return false when set to false")
    void isAccountNonExpired_shouldReturnFalseWhenSet() {
        JwtUserDetails user = JwtUserDetails.builder()
                .accountNonExpired(false)
                .build();
        assertFalse(user.isAccountNonExpired());
    }

    @Test
    @DisplayName("isAccountNonLocked should return false when set to false")
    void isAccountNonLocked_shouldReturnFalseWhenSet() {
        JwtUserDetails user = JwtUserDetails.builder()
                .accountNonLocked(false)
                .build();
        assertFalse(user.isAccountNonLocked());
    }

    @Test
    @DisplayName("isCredentialsNonExpired should return false when set to false")
    void isCredentialsNonExpired_shouldReturnFalseWhenSet() {
        JwtUserDetails user = JwtUserDetails.builder()
                .credentialsNonExpired(false)
                .build();
        assertFalse(user.isCredentialsNonExpired());
    }

    @Test
    @DisplayName("hasRole should return true for existing role")
    void hasRole_shouldReturnTrueForExistingRole() {
        JwtUserDetails user = createAdminUser();
        assertTrue(user.hasRole("ADMIN"));
    }

    @Test
    @DisplayName("hasRole should return false for non-existing role")
    void hasRole_shouldReturnFalseForNonExistingRole() {
        JwtUserDetails user = createAdminUser();
        assertFalse(user.hasRole("ENTERPRISE"));
    }

    @Test
    @DisplayName("hasRole should return false for null roles")
    void hasRole_shouldReturnFalseForNullRoles() {
        JwtUserDetails user = JwtUserDetails.builder()
                .roles(null)
                .build();
        assertFalse(user.hasRole("ADMIN"));
    }

    @Test
    @DisplayName("isAdmin should return true for ADMIN role")
    void isAdmin_shouldReturnTrueForAdminRole() {
        assertTrue(createAdminUser().isAdmin());
    }

    @Test
    @DisplayName("isAdmin should return false for non-ADMIN role")
    void isAdmin_shouldReturnFalseForNonAdminRole() {
        assertFalse(createEnterpriseUser().isAdmin());
    }

    @Test
    @DisplayName("isEnterprise should return true for ENTERPRISE role")
    void isEnterprise_shouldReturnTrueForEnterpriseRole() {
        assertTrue(createEnterpriseUser().isEnterprise());
    }

    @Test
    @DisplayName("isEnterprise should return false for non-ENTERPRISE role")
    void isEnterprise_shouldReturnFalseForNonEnterpriseRole() {
        assertFalse(createAdminUser().isEnterprise());
    }

    @Test
    @DisplayName("isReviewer should return true for REVIEWER role")
    void isReviewer_shouldReturnTrueForReviewerRole() {
        assertTrue(createReviewerUser().isReviewer());
    }

    @Test
    @DisplayName("isReviewer should return false for non-REVIEWER role")
    void isReviewer_shouldReturnFalseForNonReviewerRole() {
        assertFalse(createAdminUser().isReviewer());
    }

    @Test
    @DisplayName("No-arg constructor should create empty instance")
    void noArgConstructor_shouldCreateEmptyInstance() {
        JwtUserDetails user = new JwtUserDetails();
        assertNull(user.getUserId());
        assertNull(user.getUsername());
        assertNull(user.getPassword());
        assertNull(user.getRoles());
    }

    @Test
    @DisplayName("Fields should be accessible via getters")
    void fields_shouldBeAccessibleViaGetters() {
        JwtUserDetails user = createAdminUser();
        assertEquals(1L, user.getUserId());
        assertEquals("admin", user.getUsername());
        assertEquals("hashedpw", user.getPassword());
        assertEquals(4, user.getUserType());
        assertEquals(1L, user.getEnterpriseId());
        assertEquals(List.of("ADMIN"), user.getRoles());
    }
}
