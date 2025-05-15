<template>
  <div class="page-container">
    <div class="content-wrapper">
      <!-- Hauptüberschrift mit dekorativem Element -->
      <div class="header">
        <div class="title-container">
          <h1 class="main-title">Bachelorarbeit CDA zu FHIR</h1>
          <div class="title-underline"></div>
        </div>
      </div>

      <!-- Container für die zwei Kästen -->
      <div class="cards-container">
        <!-- Erster Kasten - Link zu /own -->
        <div class="card" @click="navigateTo('/own')">
          <div class="card-accent blue-accent"></div>
          <div class="card-content">
            <div class="card-header">
              <h2 class="card-title">Eigener Implementierter Ansatz</h2>
            </div>

            <p class="card-description">
              Untersuchung der Machbarkeit des Mappings von CDA zu FHIR mit einem eigenen Ansatz. Prototypisch implementiert.
            </p>

            <div class="card-footer">
              <div class="explore-link blue-link">
                <span>Erkunden</span>
              </div>
            </div>
          </div>
        </div>

        <div class="card" @click="navigateTo('/cda2r4')">
          <div class="card-accent purple-accent"></div>
          <div class="card-content">
            <div class="card-header">
              <h2 class="card-title">Nutzung cda2r4-Bibliothek</h2>
            </div>

            <p class="card-description">
              Prototypische Implementierung des Mappings von CDA zu FHIR mittels der cda2r4-Bibliothek.
            </p>

            <div class="card-footer">
              <div class="explore-link purple-link">
                <span>Erkunden</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router';


const router = useRouter();

// Funktion zur Navigation
const navigateTo = (path) => {
  router.push(path);
};
</script>

<style scoped>
.page-container {
  min-height: 100vh;
  background: linear-gradient(to bottom right, #f9fafb, #f3f4f6);
  padding: 4rem 1rem;
}

.content-wrapper {
  max-width: 1200px;
  margin: 0 auto;
  position: relative;
}

.header {
  text-align: center;
  margin-bottom: 4rem;
}

.title-container {
  display: inline-block;
  position: relative;
}

.main-title {
  font-size: 2.5rem;
  font-weight: 700;
  color: #1f2937;
  position: relative;
  z-index: 1;
  margin: 0;
}

.title-underline {
  height: 0.75rem;
  background: #dc3545;
  border-radius: 9999px;
  margin-top: -0.75rem;
  position: relative;
  z-index: 0;
  opacity: 0.75;
}

.subtitle {
  margin-top: 1.5rem;
  color: #4b5563;
  font-size: 1.125rem;
  max-width: 36rem;
  margin-left: auto;
  margin-right: auto;
}

/* Karten-Container */
.cards-container {
  display: grid;
  grid-template-columns: 1fr;
  gap: 2.5rem;
}

/* Karten-Styling */
.card {
  background-color: white;
  border-radius: 0.75rem;
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05);
  overflow: hidden;
  transition: all 0.3s ease;
  cursor: pointer;
  border: 1px solid #f3f4f6;
  position: relative;
}

.card-accent {
  height: 0.75rem;
}

.card-content {
  padding: 2rem;
}

/* Karten-Header */
.card-header {
  display: flex;
  align-items: center;
  margin-bottom: 1.25rem;
}

.icon-container {
  padding: 0.75rem;
  border-radius: 0.5rem;
  margin-right: 1rem;
  display: flex;
  align-items: center;
  justify-content: center;
}

.blue-icon {
  background-color: #eff6ff;
}

.purple-icon {
  background-color: #f5f3ff;
}

.icon {
  height: 1.5rem;
  width: 1.5rem;
}


.card-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: #1f2937;
  margin: 0;
}

/* Karten-Beschreibung */
.card-description {
  color: #4b5563;
  margin-bottom: 1.5rem;
  line-height: 1.6;
}

/* Karten-Footer */
.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 2rem;
}

.card-tag {
  font-size: 0.875rem;
  color: #6b7280;
}

.explore-link {
  display: flex;
  align-items: center;
  font-weight: 500;
  transition: transform 0.3s ease;
}

.arrow-icon {
  height: 1rem;
  width: 1rem;
  margin-left: 0.25rem;
}

/* Responsive Design */
@media (min-width: 768px) {
  .main-title {
    font-size: 3rem;
  }

  .cards-container {
    grid-template-columns: 1fr 1fr;
  }
}


.card {
  animation: float 4s ease-in-out infinite;
}
</style>
