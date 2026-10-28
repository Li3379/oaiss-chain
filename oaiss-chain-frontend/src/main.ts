import { createApp } from 'vue'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import router from './router'
import { pinia } from './store'
import i18n from './i18n'
import './style.css'
import App from './App.vue'

createApp(App).use(pinia).use(router).use(i18n).mount('#app')
