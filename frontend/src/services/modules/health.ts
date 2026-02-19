import { request } from '../request'

export const healthAPI = {
    check: () => request('/auth/health'),
}
