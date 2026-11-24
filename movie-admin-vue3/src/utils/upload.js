import http from '@/utils/http';

/**
 * 上传文件（后端中转上传）
 *
 * POST /api/oss/upload
 * multipart 字段：file + bizDir
 * 返回：{ fileUrl: 'https://...' }
 *
 * @param {File} file 文件对象
 * @param {Object} options
 * @param {string} [options.bizDir] 业务目录（movie / actor / cinema / avatar）
 * @param {string} [options.dir]    兼容旧调用方，等价于 bizDir
 * @param {number} [options.maxSize] 最大字节数，默认 10MB
 * @returns {Promise<{ url: string, name: string, fileUrl: string }>}
 */
export async function uploadImage(file, options = {}) {
  const { maxSize = 10 * 1024 * 1024 } = options;
  const bizDir = options.bizDir || options.dir || 'default';

  if (!file) {
    throw new Error('请选择要上传的文件');
  }
  if (maxSize && file.size > maxSize) {
    throw new Error(`文件大小不能超过 ${Math.round(maxSize / 1024 / 1024)}MB`);
  }

  const formData = new FormData();
  formData.append('file', file);
  formData.append('bizDir', bizDir);

  const data = await http.post('/oss/upload', formData, {
    timeout: 60000,
  });
  const url = data?.fileUrl || data?.url || '';
  if (!url) {
    throw new Error('上传失败：服务端未返回文件地址');
  }

  return { url, name: file.name, fileUrl: url };
}

export default uploadImage;
