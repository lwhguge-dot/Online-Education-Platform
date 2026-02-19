import { request } from '../request'

export const fileAPI = {
    uploadVideo: (file: File) => {
        const formData = new FormData()
        formData.append('file', file)
        return request('/files/upload/video', {
            method: 'POST',
            body: formData,
        })
    },
    uploadImage: (file: File) => {
        const formData = new FormData()
        formData.append('file', file)
        return request('/files/upload/image', {
            method: 'POST',
            body: formData,
        })
    },
    uploadDocument: (file: File) => {
        const formData = new FormData()
        formData.append('file', file)
        return request('/files/upload/document', {
            method: 'POST',
            body: formData,
        })
    },
    // 删除已上传的文件
    deleteFile: (path: string) =>
        request(`/files/delete?path=${encodeURIComponent(path)}`, {
            method: 'DELETE',
        }),
}
