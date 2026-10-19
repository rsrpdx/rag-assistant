import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import './assets/styles/global.css'

const app = createApp(App)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, {
  locale: {
    ...zhCn,
    el: {
      ...zhCn.el,
      pagination: {
        ...zhCn.el?.pagination,
        total: '总计 {total} 个文件',
        pagesize: '条',
      }
    }
  }
})
app.mount('#app')


// 网络状态监听
window.addEventListener("offline", () => {
  import("element-plus").then(({ ElNotification }) => {
    ElNotification({
      title: "网络已断开",
      message: "请检查网络连接，部分功能可能不可用",
      type: "warning",
      duration: 0,
      position: "top-right"
    });
  });
});

window.addEventListener("online", () => {
  import("element-plus").then(({ ElNotification }) => {
    ElNotification({
      title: "网络已恢复",
      message: "网络连接已恢复正常",
      type: "success",
      duration: 3000,
      position: "top-right"
    });
  });
});
