import { request } from '../request'
import type { Result, LoginResponse } from '../../types/api'

export const authAPI = {
    // 登录：使用邮箱+密码
    login: (email: string, password: string): Promise<Result<LoginResponse>> =>
        request<LoginResponse>('/auth/login', {
            method: 'POST',
            body: JSON.stringify({ email, password }),
        }),
    // 注册：邮箱+用户名+真实姓名+密码+角色
    register: (email: string, username: string, realName: string, password: string, role: string): Promise<Result<void>> =>
        request<void>('/auth/register', {
            method: 'POST',
            body: JSON.stringify({ email, username, realName, password, role }),
        }),
    // 密码重置：邮箱+真实姓名+新密码
    resetPassword: (email: string, realName: string, newPassword: string): Promise<Result<void>> =>
        request<void>('/auth/reset-password', {
            method: 'POST',
            body: JSON.stringify({ email, realName, newPassword }),
        }),
    logout: (): Promise<Result<void>> =>
        request<void>('/auth/logout', {
            method: 'POST',
        }),
    heartbeat: (): Promise<Result<void>> =>
        request<void>('/auth/heartbeat', {
            method: 'POST',
        }),
    checkStatus: (userId: number): Promise<Result<any>> =>
        request(`/auth/check-status/${userId}`),
    validateToken: (userId: number): Promise<Result<any>> =>
        request(`/auth/validate-token/${userId}`),
    forceLogout: (userId: number): Promise<Result<void>> =>
        request<void>(`/auth/force-logout/${userId}`, {
            method: 'POST',
        }),
}
