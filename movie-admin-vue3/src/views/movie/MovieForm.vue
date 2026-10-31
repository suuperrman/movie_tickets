<template>
  <div class="movie-form">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <el-button @click="goBack">
            <el-icon><ArrowLeft /></el-icon>返回列表
          </el-button>
          <span class="page-title">{{ isEdit ? '编辑电影' : '新增电影' }}</span>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        class="movie-form-body"
      >
        <!-- ===== 基本信息 ===== -->
        <el-row :gutter="24">
          <el-col :span="16">
            <el-form-item label="电影名称" prop="name">
              <el-input v-model="form.name" placeholder="请输入电影名称" maxlength="100" />
            </el-form-item>

            <el-form-item label="导演" prop="director">
              <el-input v-model="form.director" placeholder="请输入导演姓名" maxlength="100" />
            </el-form-item>

            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="时长" prop="duration">
                  <el-input-number
                    v-model="form.duration"
                    :min="1"
                    :max="600"
                    controls-position="right"
                    style="width: 100%"
                  />
                  <span style="margin-left: 8px; color: #9ca3af; font-size: 13px">分钟</span>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="上映日期" prop="releaseDate">
                  <el-date-picker
                    v-model="form.releaseDate"
                    type="date"
                    value-format="YYYY-MM-DD"
                    placeholder="请选择上映日期"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">上架</el-radio>
                <el-radio :value="0">下架</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="分类" prop="categoryIds">
              <el-select
                v-model="form.categoryIds"
                multiple
                placeholder="请选择电影分类"
                style="width: 100%"
              >
                <el-option
                  v-for="cat in categoryOptions"
                  :key="cat.id"
                  :label="cat.name"
                  :value="cat.id"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="简介" prop="description">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="5"
                placeholder="请输入电影简介"
                maxlength="2000"
                show-word-limit
              />
            </el-form-item>
          </el-col>

          <!-- 海报上传 -->
          <el-col :span="8">
            <el-form-item label="海报" prop="posterUrl" label-width="80px">
              <el-upload
                class="poster-uploader"
                :show-file-list="false"
                :before-upload="beforePosterUpload"
                :http-request="handlePosterUpload"
                accept="image/*"
              >
                <div v-if="form.posterUrl" class="poster-preview">
                  <img :src="form.posterUrl" alt="海报" />
                  <div class="poster-mask">
                    <el-icon :size="24"><Edit /></el-icon>
                    <span>更换海报</span>
                  </div>
                </div>
                <div v-else class="poster-placeholder">
                  <el-icon :size="48"><Plus /></el-icon>
                  <span>上传海报</span>
                </div>
              </el-upload>
              <div class="upload-tip">支持 JPG/PNG，建议 2:3 竖版海报</div>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- ===== 演员列表 ===== -->
        <el-divider content-position="left">演员信息</el-divider>

        <div class="actors-section">
          <div class="actors-header">
            <span>演员列表</span>
            <el-button type="primary" size="small" @click="addActor">
              <el-icon><Plus /></el-icon>添加演员
            </el-button>
          </div>

          <el-table
            v-if="form.actors.length > 0"
            :data="form.actors"
            border
            style="width: 100%"
          >
            <el-table-column label="头像" width="80" align="center">
              <template #default="{ row }">
                <el-avatar :size="48" shape="square" :src="row.avatarUrl">
                  <User />
                </el-avatar>
              </template>
            </el-table-column>
            <el-table-column label="演员姓名" min-width="120">
              <template #default="{ row }">
                <el-input v-model="row.name" placeholder="姓名" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="饰演角色" min-width="120">
              <template #default="{ row }">
                <el-input v-model="row.roleName" placeholder="角色名" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="类型" width="100" align="center">
              <template #default="{ row }">
                <el-select v-model="row.isLead" size="small" style="width: 100%">
                  <el-option label="主演" :value="1" />
                  <el-option label="配角" :value="0" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="排序" width="100" align="center">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.sort"
                  :min="0"
                  :max="999"
                  size="small"
                  controls-position="right"
                />
              </template>
            </el-table-column>
            <el-table-column label="头像URL" min-width="200">
              <template #default="{ row }">
                <el-input v-model="row.avatarUrl" placeholder="头像 URL" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default="{ $index }">
                <el-button type="danger" link size="small" @click="removeActor($index)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-else description="暂无演员，点击右上角添加" />
        </div>

        <!-- 底部操作 -->
        <div class="form-footer">
          <el-button @click="goBack">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            {{ isEdit ? '保存修改' : '提交新增' }}
          </el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';

import { createMovie, getMovieDetail, updateMovie } from '@/api/movie.js';
import { getCategoryList } from '@/api/category.js';
import { uploadImage } from '@/utils/upload.js';

const route = useRoute();
const router = useRouter();

const formRef = ref(null);
const submitting = ref(false);
const uploadingPoster = ref(false);

const isEdit = computed(() => !!route.params.id);
const movieId = computed(() => Number(route.params.id));

const form = reactive({
  name: '',
  director: '',
  duration: 90,
  releaseDate: '',
  posterUrl: '',
  description: '',
  status: 0,
  categoryIds: [],
  actors: [],
});

const rules = {
  name: [{ required: true, message: '请输入电影名称', trigger: 'blur' }],
  director: [{ required: true, message: '请输入导演', trigger: 'blur' }],
  duration: [{ required: true, message: '请输入时长', trigger: 'blur' }],
  releaseDate: [{ required: true, message: '请选择上映日期', trigger: 'change' }],
  posterUrl: [{ required: true, message: '请上传海报', trigger: 'change' }],
  categoryIds: [{ required: true, message: '请选择至少一个分类', trigger: 'change' }],
};

const categoryOptions = ref([]);

async function loadCategories() {
  try {
    categoryOptions.value = await getCategoryList();
  } catch (err) {
    // 错误已在拦截器提示
  }
}

async function loadMovieDetail() {
  if (!isEdit.value) return;
  try {
    const detail = await getMovieDetail(movieId.value);
    form.name = detail.name;
    form.director = detail.director;
    form.duration = detail.duration;
    form.releaseDate = detail.releaseDate;
    form.posterUrl = detail.posterUrl;
    form.description = detail.description || '';
    form.status = detail.status;
    form.categoryIds = (detail.categories || []).map((c) => c.id);
    form.actors = (detail.actors || []).map((a, idx) => ({
      actorId: a.id,
      name: a.name,
      avatarUrl: a.avatarUrl,
      roleName: a.roleName,
      isLead: idx < 3 ? 1 : 0,
      sort: idx,
      gender: 0,
      intro: '',
    }));
  } catch (err) {
    // 错误已在拦截器提示
  }
}

function addActor() {
  form.actors.push({
    actorId: null,
    name: '',
    avatarUrl: '',
    roleName: '',
    isLead: 0,
    sort: form.actors.length,
    gender: 0,
    intro: '',
  });
}

function removeActor(index) {
  form.actors.splice(index, 1);
  // 重新排序
  form.actors.forEach((a, i) => (a.sort = i));
}

function beforePosterUpload(file) {
  const isImage = file.type.startsWith('image/');
  if (!isImage) {
    ElMessage.error('只能上传图片文件');
    return false;
  }
  const isLt5M = file.size / 1024 / 1024 < 5;
  if (!isLt5M) {
    ElMessage.error('海报大小不能超过 5MB');
    return false;
  }
  return true;
}

async function handlePosterUpload({ file }) {
  uploadingPoster.value = true;
  try {
    const result = await uploadImage(file, {
      dir: 'movie-ticket/poster/',
      maxSize: 5 * 1024 * 1024,
    });
    form.posterUrl = result.url;
    ElMessage.success('海报上传成功');
  } catch (err) {
    ElMessage.error(err.message || '上传失败');
  } finally {
    uploadingPoster.value = false;
  }
}

async function handleSubmit() {
  if (!formRef.value) return;
  try {
    await formRef.value.validate();
  } catch (err) {
    ElMessage.error('请完善表单信息');
    return;
  }

  // 校验演员
  if (form.actors.length > 0) {
    const invalid = form.actors.find((a) => !a.name?.trim());
    if (invalid) {
      ElMessage.error('请填写所有演员的姓名');
      return;
    }
  }

  submitting.value = true;
  try {
    const payload = {
      name: form.name,
      director: form.director,
      duration: form.duration,
      releaseDate: form.releaseDate,
      posterUrl: form.posterUrl,
      description: form.description,
      status: form.status,
      categoryIds: form.categoryIds,
      actors: form.actors.map((a) => ({
        actorId: a.actorId,
        name: a.name,
        avatarUrl: a.avatarUrl,
        roleName: a.roleName,
        isLead: a.isLead,
        sort: a.sort,
        gender: a.gender,
        intro: a.intro,
      })),
    };

    if (isEdit.value) {
      await updateMovie(movieId.value, payload);
      ElMessage.success('修改成功');
    } else {
      await createMovie(payload);
      ElMessage.success('新增成功');
    }

    goBack();
  } catch (err) {
    // 错误已在拦截器提示
  } finally {
    submitting.value = false;
  }
}

function goBack() {
  router.push('/movie/list');
}

onMounted(() => {
  loadCategories();
  loadMovieDetail();
});
</script>

<style scoped>
.movie-form {
  max-width: 1100px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.movie-form-body {
  padding: 10px 10px 0;
}

/* 海报上传 */
.poster-uploader {
  width: 200px;
  height: 300px;
}

.poster-preview {
  position: relative;
  width: 200px;
  height: 300px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #dcdfe6;
  cursor: pointer;
}

.poster-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.poster-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  font-size: 13px;
  opacity: 0;
  transition: opacity 0.2s;
}

.poster-preview:hover .poster-mask {
  opacity: 1;
}

.poster-placeholder {
  width: 200px;
  height: 300px;
  border: 2px dashed #dcdfe6;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #9ca3af;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.poster-placeholder:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.upload-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #9ca3af;
  text-align: center;
  width: 200px;
}

/* 演员 */
.actors-section {
  margin-top: 8px;
}

.actors-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-weight: 500;
  color: #374151;
}

/* 底部 */
.form-footer {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 32px;
  padding-top: 20px;
  border-top: 1px solid #e5e7eb;
}

.form-footer .el-button {
  min-width: 120px;
}
</style>
