<!--
  Messaggio d'errore di un singolo campo.

  Sta SOTTO il campo a cui si riferisce (l'`ion-note slot="error"` di Ionic non
  viene mai presentato dentro un `ion-item`) ed è un `role="alert"`, così viene
  annunciato nel momento in cui compare dopo il tentativo di invio.
-->
<template>
  <p v-if="message" class="field-error" role="alert">{{ message }}</p>
</template>

<script setup lang="ts">
defineProps<{ message?: string }>();
</script>