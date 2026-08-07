<template>
  <!-- Contenuto dell'About, condiviso tra la pagina /about e il popup di
       primo avvio (AboutStartupModal): un solo posto da aggiornare quando
       cambiano persone, ruoli o contatti. -->
  <div class="about-container">

    <!-- Premessa del responsabile scientifico -->
    <ion-card class="about-card">
      <ion-card-header>
        <ion-card-title class="section-title">Foreword</ion-card-title>
      </ion-card-header>
      <ion-card-content>
        <div class="unicas-header">
          <a
              href="https://www.unicas.it"
              target="_blank"
              rel="noopener noreferrer"
              aria-label="University of Cassino and Southern Lazio website"
          >
            <img
                src="/unicas_emblem.svg"
                alt="University of Cassino and Southern Lazio emblem"
                class="unicas-logo"
            />
          </a>
          <div class="unicas-caption">
            <p class="unicas-name">
              Università degli Studi di Cassino e del Lazio Meridionale
            </p>
            <a
                class="unicas-link"
                href="https://www.unicas.it"
                target="_blank"
                rel="noopener noreferrer"
            >www.unicas.it</a>
          </div>
        </div>

        <p>
          Time is the one resource we all share in equal measure, yet how we
          allocate it across work, care, leisure and rest remains largely
          invisible — even to ourselves. Chronogram was conceived as a
          research tool to make that allocation visible: by recording daily
          activities, in real time or retrospectively, participants build
          the empirical ground on which the study of time use stands.
        </p>
        <p>
          I am grateful to everyone who contributes their time — in both
          senses — to this project.
        </p>
        <p class="signature">
          Prof. Sergio Nisticò<br />
          <span class="signature-role">
            Scientific lead — University of Cassino and Southern Lazio
          </span>
        </p>
      </ion-card-content>
    </ion-card>

    <!-- Team, nello stesso ordine del README -->
    <ion-card class="about-card">
      <ion-card-header>
        <ion-card-title class="section-title">The team</ion-card-title>
      </ion-card-header>
      <ion-card-content>
        <ion-list lines="none" class="team-list">
          <ion-item v-for="member in team" :key="member.name">
            <ion-icon slot="start" :icon="personCircleOutline" class="team-icon" aria-hidden="true" />
            <ion-label>
              <h3>{{ member.name }}</h3>
              <p>{{ member.role }}</p>
            </ion-label>
            <a
                v-if="member.github"
                slot="end"
                class="github-link"
                :href="member.github"
                target="_blank"
                rel="noopener noreferrer"
                :aria-label="`${member.name} on GitHub`"
            >
              <ion-icon :icon="logoGithub" />
            </a>
          </ion-item>
        </ion-list>
        <p class="claude-note">
          Chronogram was developed with the assistance of
          <a
              href="https://claude.com"
              target="_blank"
              rel="noopener noreferrer"
          >Claude</a>, Anthropic's AI assistant.
        </p>
      </ion-card-content>
    </ion-card>

    <!-- Contatti -->
    <ion-card class="about-card">
      <ion-card-header>
        <ion-card-title class="section-title">Contacts</ion-card-title>
      </ion-card-header>
      <ion-card-content>
        <ion-list lines="none" class="contact-list">
          <ion-item>
            <ion-icon slot="start" :icon="constructOutline" class="team-icon" aria-hidden="true" />
            <ion-label>
              <h3>Technical contact</h3>
              <p><a href="mailto:m.molinara@unicas.it">m.molinara@unicas.it</a></p>
            </ion-label>
          </ion-item>
          <ion-item>
            <ion-icon slot="start" :icon="schoolOutline" class="team-icon" aria-hidden="true" />
            <ion-label>
              <h3>Research contact</h3>
              <p><a href="mailto:nistico@unicas.it">nistico@unicas.it</a></p>
            </ion-label>
          </ion-item>
        </ion-list>
      </ion-card-content>
    </ion-card>

  </div>
</template>

<script setup lang="ts">
import {
  IonCard, IonCardHeader, IonCardTitle, IonCardContent,
  IonList, IonItem, IonLabel, IonIcon
} from '@ionic/vue';
import {
  personCircleOutline, logoGithub, constructOutline, schoolOutline
} from 'ionicons/icons';

/** Stesse persone, stessi ruoli e stesso ordine della tabella Contributors del README. */
const team = [
  {
    name: 'Prof. Sergio Nisticò',
    role: 'Scientific lead',
    github: null
  },
  {
    name: 'Prof. Mario Molinara',
    role: 'Developer and coordinator',
    github: 'https://github.com/mariomolinara'
  },
  {
    name: 'Giuseppe Alfieri',
    role: 'Designer and developer',
    github: 'https://github.com/giusalfieri'
  },
  {
    name: 'Paolo Simeone',
    role: 'Designer and developer',
    github: 'https://github.com/bonoboprog'
  },
  {
    name: 'Violeta Perez',
    role: 'Initial prototype developer',
    github: 'https://github.com/violetapd'
  }
] as const;
</script>

<style scoped>
.about-container {
  max-width: 600px;
  margin: 0 auto;
}

.about-card {
  background-color: var(--surface0);
  border-radius: var(--radius-md);
  color: var(--text);
}

.section-title {
  color: var(--mauve);
  font-size: var(--font-md);
  font-weight: var(--font-weight-bold);
}

.about-card p {
  color: var(--subtext0);
  font-size: var(--font-base);
  line-height: 1.6;
  margin-bottom: var(--space-3);
}

/* Intestazione con emblema e ateneo */
.unicas-header {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
}

/* L'emblema ufficiale è a tratto scuro: su tema mocha serve un fondo chiaro
   per restare leggibile. */
.unicas-logo {
  width: 72px;
  height: 72px;
  flex-shrink: 0;
  background: #fff;
  border-radius: var(--radius-md);
  padding: var(--space-2);
  box-sizing: border-box;
}

.unicas-name {
  margin: 0 0 var(--space-1);
  color: var(--text);
  font-weight: var(--font-weight-bold);
  font-size: var(--font-base);
}

.unicas-link {
  color: var(--peach);
  text-decoration: none;
  font-size: var(--font-sm);
}

.signature {
  color: var(--text);
  font-weight: var(--font-weight-bold);
  margin-top: var(--space-4);
}

.signature-role {
  color: var(--subtext0);
  font-weight: normal;
  font-size: var(--font-sm);
}

/* Team e contatti */
.team-list ion-item,
.contact-list ion-item {
  --background: transparent;
}

.team-list h3,
.contact-list h3 {
  color: var(--text);
  font-weight: var(--font-weight-bold);
  font-size: var(--font-base);
}

.team-list p,
.contact-list p {
  color: var(--subtext0);
  margin: 0;
}

.team-icon {
  color: var(--peach);
  font-size: var(--font-lg);
}

.github-link {
  display: flex;
  align-items: center;
  color: var(--subtext0);
  font-size: 1.4rem;
}

.contact-list a,
.claude-note a {
  color: var(--peach);
  text-decoration: none;
}

.claude-note {
  margin-top: var(--space-4);
  margin-bottom: 0;
  font-size: var(--font-sm);
}
</style>
