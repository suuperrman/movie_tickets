<script setup lang="ts">
import { computed } from 'vue'

import type { MovieListItem } from '@/types'
import { priceText, ratingText, toNumber } from '@/utils/format'

const props = defineProps<{
  movie: MovieListItem
  index?: number
}>()

const posterVisible = computed(() => Boolean(props.movie.posterUrl))
const glyph = computed(() => props.movie.name?.slice(0, 1) ?? '影')
const genres = computed(() =>
  (props.movie.categories ?? '')
    .split(/[,，/]/)
    .map((item) => item.trim())
    .filter(Boolean)
    .join(' / '),
)
const duration = computed(() => toNumber(props.movie.duration))
const no = computed(() => String((props.index ?? 0) + 1).padStart(2, '0'))
const tag = computed(() => genres.value.split(' / ')[0] || '')
</script>

<template>
  <router-link class="movie-card" :to="`/movie/${movie.id}`">
    <div class="poster">
      <div class="poster-fallback" aria-hidden="true">
        <span class="glyph">{{ glyph }}</span>
        <span class="no-poster">NO POSTER</span>
      </div>
      <img
        v-if="posterVisible"
        :src="movie.posterUrl ?? ''"
        :alt="`${movie.name} 海报`"
        loading="lazy"
      />
      <span class="poster-num">{{ no }}</span>
      <span v-if="tag" class="poster-tag">{{ tag }}</span>
    </div>

    <div class="movie-body">
      <h3 class="movie-title">{{ movie.name }}</h3>
      <p class="movie-meta">
        <template v-if="duration">{{ duration }} 分钟 · </template>{{ genres || '类型待定' }}
      </p>
      <p class="movie-meta">导演 · {{ movie.director || '待定' }}</p>
      <div class="movie-foot">
        <span class="price">
          <template v-if="movie.minPrice !== null && movie.minPrice !== undefined">
            ¥{{ priceText(movie.minPrice) }}<small>起</small>
          </template>
          <template v-else>待定</template>
        </span>
        <span class="movie-rating" :class="{ 'is-empty': !toNumber(movie.avgRating) }">
          {{ ratingText(movie.avgRating) }}
        </span>
      </div>
    </div>
  </router-link>
</template>

<style scoped>
.movie-rating {
  display: inline-flex;
  align-items: baseline;
  gap: 3px;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 16px;
  letter-spacing: 0.02em;
}

.movie-rating::before {
  content: '★';
  color: var(--red);
  font-size: 11px;
}

.movie-rating.is-empty {
  color: var(--muted);
  font-family: var(--font-ui);
  font-size: 10px;
  font-weight: 500;
}

.movie-rating.is-empty::before {
  color: rgba(20, 17, 16, 0.25);
}
</style>
