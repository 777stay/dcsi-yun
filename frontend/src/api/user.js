import request from '@/utils/request'

export function login(data) {
  return request({
    url: '/api/auth/login',
    method: 'post',
    data
  })
}

export function getInfo(token) {
  return request({
    url: '/user/info',
    method: 'get',
    params: { token }
  })
}
export function register(data) {
  return request({
    url: '/api/auth/register',
    method: 'post',
    data
  })
}
export function logout() {
  return request({
    url: '/api/auth/logout',
    method: 'post'
  })
}
