import { request } from '../request'
import type { Result, LoginResponse, PasswordResetTokenResponse } from '../../types/api'

export const authAPI = {
    login: (email: string, password: string): Promise<Result<LoginResponse>> =>
        request<LoginResponse>('/auth/login', {
            method: 'POST',
            body: JSON.stringify({ email, password }),
        }),
    register: (email: string, username: string, realName: string, password: string, role: string): Promise<Result<LoginResponse>> =>
        request<LoginResponse>('/auth/register', {
            method: 'POST',
            body: JSON.stringify({ email, username, realName, password, role }),
        }),
    requestPasswordResetToken: (email: string, realName: string): Promise<Result<PasswordResetTokenResponse>> =>
        request<PasswordResetTokenResponse>('/auth/password-reset/request', {
            method: 'POST',
            body: JSON.stringify({ email, realName }),
        }),
    confirmPasswordReset: (resetToken: string, newPassword: string): Promise<Result<boolean>> =>
        request<boolean>('/auth/password-reset/confirm', {
            method: 'POST',
            body: JSON.stringify({ resetToken, newPassword }),
        }),
    logout: (): Promise<Result<void>> =>
        request<void>('/auth/logout', {
            method: 'POST',
        }),
    heartbeat: (): Promise<Result<void>> =>
        request<void>('/auth/heartbeat', {
            method: 'POST',
        }),
    checkStatus: (userId: number): Promise<Result<{ active: boolean }>> =>
        request(`/auth/check-status/${userId}`),
    validateToken: (userId: number): Promise<Result<{ valid: boolean }>> =>
        request(`/auth/validate-token/${userId}`),
    forceLogout: (userId: number): Promise<Result<void>> =>
        request<void>(`/auth/force-logout/${userId}`, {
            method: 'POST',
        }),
}
