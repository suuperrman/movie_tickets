import http from '@/utils/http';

/**
 * 获取 OSS 上传凭证（STS）
 * GET /api/oss/policy
 */
export function getOssPolicy() {
  return http.get('/oss/policy');
}

/**
 * 上传文件到阿里云 OSS（直传）
 *
 * @param {File} file       文件对象
 * @param {Object} policy   后端返回的 STS 凭证
 * @param {Object} options
 * @param {string} [options.dir]        上传目录
 * @param {number} [options.maxSize]    最大字节数
 * @returns {Promise<{ url: string, name: string }>}
 */
export async function uploadToOss(file, policy, options = {}) {
  const { maxSize = 10 * 1024 * 1024 } = options;

  if (maxSize && file.size > maxSize) {
    throw new Error(`文件大小不能超过 ${Math.round(maxSize / 1024 / 1024)}MB`);
  }

  const formData = new FormData();
  formData.append('OSSAccessKeyId', policy.accessKeyId);
  formData.append('policy', policy.policy);
  formData.append('Signature', policy.signature);
  formData.append('key', policy.dir + policy.fileNamePrefix + '_' + Date.now() + '_' + file.name);
  formData.append('success_action_status', '200');
  formData.append('file', file);

  try {
    const response = await fetch(policy.host, {
      method: 'POST',
      body: formData,
    });
    if (!response.ok) {
      throw new Error(`上传失败 (${response.status})`);
    }
    const url = policy.host + '/' + formData.get('key');
    return { url, name: file.name };
  } catch (err) {
    throw new Error(err.message || '上传失败');
  }
}

/**
 * 便捷上传：先拿凭证再上传
 *
 * @param {File} file
 * @param {Object} options
 * @param {string} [options.dir]
 * @param {number} [options.maxSize]
 */
export async function uploadImage(file, options = {}) {
  const policy = await getOssPolicy();
  const mergedOptions = { ...options, dir: options.dir || policy.dir };
  return uploadToOss(file, policy, mergedOptions);
}
