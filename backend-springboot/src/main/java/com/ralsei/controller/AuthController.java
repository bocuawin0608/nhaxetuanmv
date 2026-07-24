package com.ralsei.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ralsei.dto.request.auth.CustomerLoginRequest;
import com.ralsei.dto.request.auth.CustomerRegisterRequest;
import com.ralsei.dto.request.auth.RefreshTokenRequest;
import com.ralsei.dto.request.auth.StaffForgotPasswordRequest;
import com.ralsei.dto.request.auth.StaffLoginRequest;
import com.ralsei.dto.response.auth.AuthResponse;
import com.ralsei.dto.response.auth.StaffForgotPasswordResponse;
import com.ralsei.service.AuthService;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Auth", description = "Authentication and token endpoints")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
/**
 * Handles HTTP requests for auth operations.
 */
public class AuthController {
    private final AuthService authService;

    @PostMapping("/customer/login")
    public ResponseEntity<AuthResponse> customerLogin(
        @Valid @RequestBody CustomerLoginRequest request
    ) {
        return ResponseEntity.ok(authService.customerLogin(request));
    }

    @PostMapping("/customer/register")
    public ResponseEntity<AuthResponse> customerRegister(
        @Valid @RequestBody CustomerRegisterRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.customerRegister(request));
    }

    @PostMapping("/staff/login")
    public ResponseEntity<AuthResponse> staffLogin(
        @Valid @RequestBody StaffLoginRequest request
    ) {
        return ResponseEntity.ok(authService.staffLogin(request));
    }

    /**
     * Accepts a staff forgot-password request from the public staff login page.
     */
    @PostMapping("/staff/forgot-password")
    public ResponseEntity<StaffForgotPasswordResponse> staffForgotPassword(
        @Valid @RequestBody StaffForgotPasswordRequest request
    ) {
        return ResponseEntity.ok(authService.staffForgotPassword(request));
    }

    @PostMapping("/refresh-token")
    /**
     * Executes the refresh token operation.
     *
     * @param refreshToken the value supplied for this operation
     *
     * @return the operation result
     */
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest refreshToken) {
        System.out.println(".... co cai gi chay o day ko ....");
        return ResponseEntity.ok(authService.refreshToken(refreshToken));
    }

    @PostMapping("/logout")
    /**
     * Executes the logout operation.
     *
     * @param refreshToken the value supplied for this operation
     *
     * @return the operation result
     */
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent().build();
    }

    //TODO: api change password (maybe reset password bên admin) gọi revokeAllUserTokens() bên authService
}
