<template>
  <ion-page>
    <ion-header>
      <ion-toolbar>

        <!-- Botón de retroceso (con color peach y funcional) -->
        <ion-buttons slot="start">
          <ion-button @click="goToHome">
            <ion-icon slot="icon-only" :icon="arrowBackOutline" class="back-icon" />
          </ion-button>
        </ion-buttons>

        <!-- Icono calendario a la derecha -->
        <ion-buttons slot="end">
          <ion-icon :icon="calendarOutline" class="header-icon" />
        </ion-buttons>

        <ion-title>Calendar</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content class="ion-padding">
      <!-- Calendario centrado -->
      <div class="calendar-wrapper">
        <ion-datetime
            presentation="date"
            :value="selectedDate"
            @ionChange="onDateChange"
            locale="en-GB"
            class="calendar"
        ></ion-datetime>
      </div>

      <!-- Fecha seleccionada -->
      <div class="date-label gradient-text">
        <ion-icon :icon="calendarOutline" class="date-icon" />
        <h2>{{ formattedDate }}</h2>
      </div>

      <!-- Lista de actividades -->
      <div v-if="filteredEvents.length > 0">
        <div
            v-for="(event, index) in filteredEvents"
            :key="index"
            class="event-box"
        >
          <div class="event-time">{{ event.time }}</div>
          <div class="event-info">
            <div class="event-title">{{ event.title }}</div>
            <div class="event-dot" :style="{ backgroundColor: event.color }"></div>
          </div>
        </div>
      </div>
      <div v-else class="state-block">
        <ion-icon :icon="calendarClearOutline" aria-hidden="true" />
        <span>No activities for this day.</span>
      </div>
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  IonPage, IonHeader, IonToolbar, IonTitle,
  IonContent, IonDatetime, IonIcon, IonButtons, IonButton
} from '@ionic/vue'
import { calendarOutline, arrowBackOutline, calendarClearOutline } from 'ionicons/icons'
import { useRouter } from 'vue-router'

const router = useRouter()

const goToHome = () => {
  router.push({ name: 'Home' })
}

const selectedDate = ref(new Date().toISOString().substring(0, 10))

const events = [
  { date: '2025-06-20', time: '08:00', title: 'Morning Activity', color: '#f9a28f' },
  { date: '2025-06-20', time: '09:00', title: 'University', color: '#d9a4f5' },
  { date: '2025-06-21', time: '11:00', title: 'Gym Session', color: '#b4e1ff' },
]

const onDateChange = (e: CustomEvent) => {
  selectedDate.value = e.detail.value
}

const formattedDate = computed(() => {
  const date = new Date(selectedDate.value)
  return date.toLocaleDateString('en-GB', {
    weekday: 'long',
    year: 'numeric',
    month: 'long',
    day: '2-digit'
  })
})

const filteredEvents = computed(() =>
    events.filter(e => e.date === selectedDate.value)
)
</script>

<style scoped>
.calendar-wrapper {
  display: flex;
  justify-content: center;
  margin-bottom: var(--space-5);
}

.calendar {
  max-width: 320px;
  border-radius: var(--radius-lg);
  background-color: var(--base);
}

.date-label {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--font-lg);
  justify-content: center;
  margin-bottom: var(--space-5);
}

.date-icon {
  font-size: 1.4rem;
}

.back-icon {
  font-size: 1.4rem;
  color: var(--peach);
}

.header-icon {
  font-size: 1.4rem;
  color: var(--peach);
}

.event-box {
  background-color: var(--surface1);
  border: 1px solid var(--overlay1);
  border-radius: var(--radius-lg);
  padding: var(--space-3) var(--space-4);
  margin-bottom: var(--space-4);
  box-shadow: var(--shadow-sm);
}

.event-time {
  font-weight: var(--font-weight-bold);
  color: var(--subtext0);
  margin-bottom: var(--space-1);
}

.event-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.event-title {
  font-size: var(--font-md);
  color: var(--text);
}

.event-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
</style>
