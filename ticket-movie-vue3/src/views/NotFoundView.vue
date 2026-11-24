<template>
  <main class="page wrap">
    <section class="not-found">
      <p class="kicker">ERROR / 404</p>
      <p class="code">4<span>0</span>4</p>
      <h1>这一场已经散场</h1>
      <p class="desc">你访问的页面不存在，或者已经被移到别的放映厅。回到首页，重新挑一部电影吧。</p>
      <div class="actions">
        <router-link class="btn btn-primary" to="/">返回首页</router-link>
        <router-link class="btn btn-ghost" to="/search">搜索电影</router-link>
      </div>
      <div class="strip" aria-hidden="true">
        <span>NOW SHOWING</span><span>·</span><span>MOVIE TICKER</span><span>·</span><span>SEE YOU NEXT TIME</span>
      </div>
    </section>
  </main>
</template>

<style scoped>
.not-found {
  position: relative;
  max-width: 760px;
  margin: 40px auto 60px;
  padding: 46px 42px 0;
  background: var(--white);
  border: 2px solid var(--ink);
  box-shadow: 12px 12px 0 var(--red);
  text-align: center;
}

.code {
  margin: 14px 0 4px;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: clamp(96px, 18vw, 178px);
  line-height: 0.9;
  letter-spacing: -0.02em;
}

.code span {
  display: inline-block;
  color: var(--white);
  background: var(--red);
  border: 3px solid var(--ink);
  box-shadow: 6px 6px 0 var(--ink);
  transform: rotate(-4deg);
  padding: 0 10px;
}

.not-found h1 {
  margin-top: 14px;
  font-size: clamp(26px, 3.6vw, 38px);
  font-weight: 900;
  letter-spacing: -0.03em;
}

.desc {
  max-width: 46ch;
  margin: 12px auto 0;
  color: var(--gray);
  font-size: 13px;
  line-height: 1.9;
}

.actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin: 26px 0 34px;
}

.strip {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  margin: 0 -42px;
  padding: 12px 20px;
  color: var(--white);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.18em;
  background: var(--ink);
}

@media (max-width: 620px) {
  .not-found {
    padding: 34px 22px 0;
    box-shadow: 8px 8px 0 var(--red);
  }

  .actions {
    flex-direction: column;
  }

  .strip {
    margin: 0 -22px;
  }
}
</style>
