<template>
  <footer class="site-foot">
    <div class="foot-inner">
      <span class="foot-brand">
        <span class="foot-mark">
          <svg
            width="14"
            height="14"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.6"
          >
            <rect x="3" y="4" width="18" height="16" rx="1.5" />
            <path d="M7 4v16M17 4v16M3 9h4M3 15h4M17 9h4M17 15h4" />
          </svg>
        </span>
        MOVIE TICKER
      </span>
      <div class="foot-links">
        <router-link to="/">首页</router-link>
        <router-link to="/search">搜索</router-link>
        <router-link to="/orders">我的订单</router-link>
        <router-link to="/profile">个人中心</router-link>
      </div>
      <span class="foot-copy">© 2026 / MOVIE TICKER · RED &amp; WHITE</span>
    </div>
  </footer>
</template>
