import request from './request'

// 社团与成员接口
export const createClub = (data) => request.post('/clubs', data)
export const getClubs = (page = 1, size = 10) => request.get('/clubs', { params: { page, size } })
export const getClubDetail = (clubId) => request.get(`/clubs/${clubId}`)
export const applyClub = (clubId) => request.post(`/clubs/${clubId}/apply`)
export const getMembers = (clubId) => request.get(`/clubs/${clubId}/members`)
export const approveMember = (clubId, membershipId) => request.post(`/clubs/${clubId}/members/${membershipId}/approve`)
export const rejectMember = (clubId, membershipId) => request.post(`/clubs/${clubId}/members/${membershipId}/reject`)
export const appointMember = (clubId, membershipId, role) => request.post(`/clubs/${clubId}/members/${membershipId}/appoint`, { role })
export const resignClub = (clubId) => request.post(`/clubs/${clubId}/resign`)
export const getMyClubs = () => request.get('/user/clubs')
