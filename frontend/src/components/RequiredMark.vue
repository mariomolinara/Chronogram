<!--
  Marcatore di campo obbligatorio.

  L'asterisco è decorativo (`aria-hidden`) perché da solo non si legge: chi usa
  uno screen reader riceve il testo "(required)" accanto al nome del campo, chi
  guarda vede l'asterisco spiegato dalla legenda in cima al form.
-->
<template>
  <span class="required-marker" aria-hidden="true">*</span>
  <span class="sr-only"> (required)</span>
</template>