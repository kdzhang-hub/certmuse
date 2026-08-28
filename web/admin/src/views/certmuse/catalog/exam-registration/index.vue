<template><span /></template>

<script setup name="CertmuseExamRegistrationRedirect" lang="ts">
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';

const router = useRouter();
onMounted(() => void router.replace('/content/exam-schedules'));
</script>
