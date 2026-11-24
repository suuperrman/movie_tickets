import { defineStore } from 'pinia'

export interface CityInfo {
  name: string
  lat: number
  lng: number
}

/** 广西 14 个地级市（用于定位与城市选择） */
export const GUANGXI_CITIES: CityInfo[] = [
  { name: '南宁', lat: 22.817, lng: 108.3665 },
  { name: '柳州', lat: 24.3264, lng: 109.4281 },
  { name: '桂林', lat: 25.2345, lng: 110.18 },
  { name: '梧州', lat: 23.4767, lng: 111.279 },
  { name: '北海', lat: 21.4733, lng: 109.1194 },
  { name: '防城港', lat: 21.6867, lng: 108.3547 },
  { name: '钦州', lat: 21.9797, lng: 108.6541 },
  { name: '贵港', lat: 23.1115, lng: 109.598 },
  { name: '玉林', lat: 22.6314, lng: 110.1812 },
  { name: '百色', lat: 23.9022, lng: 106.6168 },
  { name: '贺州', lat: 24.4035, lng: 111.5667 },
  { name: '河池', lat: 24.6929, lng: 108.0854 },
  { name: '来宾', lat: 23.7521, lng: 109.2212 },
  { name: '崇左', lat: 22.377, lng: 107.3649 },
]

const STORAGE_KEY = 'movie-ticker.city'
const DEFAULT_CITY = '南宁'
/** 超出这个距离认为不在广西，保留原城市 */
const MAX_DISTANCE_KM = 300

function readSavedCity(): string {
  try {
    return localStorage.getItem(STORAGE_KEY) ?? ''
  } catch {
    return ''
  }
}

function distanceKm(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const rad = Math.PI / 180
  const dLat = (lat2 - lat1) * rad
  const dLng = (lng2 - lng1) * rad
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLng / 2) ** 2
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

function nearestCity(lat: number, lng: number): CityInfo | null {
  let best: CityInfo | null = null
  let bestDistance = Number.POSITIVE_INFINITY
  for (const city of GUANGXI_CITIES) {
    const distance = distanceKm(lat, lng, city.lat, city.lng)
    if (distance < bestDistance) {
      best = city
      bestDistance = distance
    }
  }
  return best && bestDistance <= MAX_DISTANCE_KM ? best : null
}

export const useCityStore = defineStore('city', {
  state: () => {
    const saved = readSavedCity()
    return {
      /** 当前城市（共享给影院查询等页面） */
      city: saved || DEFAULT_CITY,
      /** 是否由定位或用户手动选择过（否则为默认城市） */
      located: Boolean(saved),
      locating: false,
    }
  },

  getters: {
    cities: () => GUANGXI_CITIES.map((item) => item.name),
  },

  actions: {
    setCity(name: string) {
      if (!name) return
      this.city = name
      this.located = true
      try {
        localStorage.setItem(STORAGE_KEY, name)
      } catch {
        // 忽略隐私模式下的写入失败
      }
    },

    /** 浏览器定位 + 就近匹配广西城市 */
    async locate(): Promise<string> {
      if (this.locating || typeof navigator === 'undefined' || !navigator.geolocation) {
        return this.city
      }
      this.locating = true
      try {
        const position = await new Promise<GeolocationPosition>((resolve, reject) => {
          navigator.geolocation.getCurrentPosition(resolve, reject, {
            enableHighAccuracy: false,
            timeout: 10000,
            maximumAge: 10 * 60 * 1000,
          })
        })
        const nearest = nearestCity(position.coords.latitude, position.coords.longitude)
        if (nearest) {
          this.setCity(nearest.name)
        }
        return this.city
      } catch {
        return this.city
      } finally {
        this.locating = false
      }
    },
  },
})
