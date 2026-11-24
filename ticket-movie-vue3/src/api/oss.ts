import { http } from '@/services/http'
import type { StsCredentials } from '@/types'

/** 从后端获取 OSS STS 临时凭证 */
export function getStsCredentials() {
  return http.get<StsCredentials>('/oss/sts')
}
