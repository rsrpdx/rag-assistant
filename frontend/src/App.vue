<template>
  <router-view />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'

onMounted(() => {
  const saved = localStorage.getItem('theme')
  if (saved === 'light') {
    document.documentElement.classList.add('light-theme')
  }
})
</script>