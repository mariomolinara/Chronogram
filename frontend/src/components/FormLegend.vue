<!--
  Legenda della convenzione "asterisco = campo obbligatorio".

  Va messa una sola volta per form, sopra il primo campo: la convenzione va
  spiegata PRIMA di incontrarla, altrimenti l'asterisco resta un simbolo da
  interpretare.

  L'asterisco è qui un `span` e non `RequiredMark`: quel componente aggiunge il
  testo "(required)" per gli screen reader, che in questa frase produrrebbe
  "Fields marked with * (required) are required".
-->
<template>
  <p class="form-legend">
    Fields marked with <span class="required-marker" aria-hidden="true">*</span> are required
  </p>
</template>
