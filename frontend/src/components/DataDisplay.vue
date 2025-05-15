<!-- DataDisplay.vue -->
<template>
  <div class="data-display">
    <!-- Ausgewählte Elemente Übersicht -->
    <div v-if="selectedItems.length > 0" class="selected-items-overview">
      <h2>Ausgewählte Elemente</h2>
      <div v-for="item in selectedItems" :key="item.inhalt_id" class="selected-item">
        <div class="selected-item-header">
          <h3>{{ item.auftrag.auftragsname }}</h3>
          <button @click="removeSelectedItem(item.inhalt_id)" class="remove-btn">✕</button>
        </div>
        <div class="selected-item-details">
          <p><strong>Dokument:</strong> {{ item.dokument.dateiname }}</p>
          <p><strong>Patient:</strong> {{ formatPatientInfo(item.patient) }}</p>
          <p><strong>Section:</strong> {{ item.inhalt.section_title }}</p>
          <p><strong>Code:</strong> {{ item.inhalt.section_code }}</p>
          <div class="selected-item-values">
            <div v-for="wert in item.werte" :key="wert.wert_id" class="selected-value">
              <span class="wert-key">{{ wert.key_path }}:</span>
              <span class="wert-value">{{ wert.value }}</span>
            </div>
          </div>
        </div>
      </div>
      <button @click="sendToApi" class="send-btn">An API senden</button>
    </div>

    <!-- Bestehende Aufträge Ansicht -->
    <div class="auftraege-list">
      <div
        v-for="auftrag in auftraege"
        :key="auftrag.auftrags_id"
        class="auftrag-container"
      >
        <div class="auftrag-header" @click="toggleAuftrag(auftrag.auftrags_id)">
          <span class="toggle-icon">
            {{ expandedAuftraege.includes(auftrag.auftrags_id) ? '▼' : '▶' }}
          </span>
          <h2>{{ auftrag.auftragsname }}</h2>
          <span class="date">{{ formatDate(auftrag.erstellungsdatum) }}</span>
        </div>

        <!-- Dokumente des Auftrags -->
        <div
          v-if="expandedAuftraege.includes(auftrag.auftrags_id)"
          class="dokumente-list"
        >
          <div
            v-for="dokument in getDokumenteForAuftrag(auftrag.auftrags_id)"
            :key="dokument.dokument_id"
            class="dokument-container"
          >
            <div class="dokument-header" @click="toggleDokument(dokument.dokument_id)">
              <span class="toggle-icon">
                {{ expandedDokumente.includes(dokument.dokument_id) ? '▼' : '▶' }}
              </span>
              <h3>{{ dokument.dateiname }}</h3>
              <span class="validation-status" :class="{ valid: dokument.validiert }">
                {{ dokument.validiert ? '✓ Validiert' : '✗ Nicht validiert' }}
              </span>
              <span class="date">{{ formatDate(dokument.erstellungsdatum) }}</span>
            </div>

            <div
              v-if="expandedDokumente.includes(dokument.dokument_id)"
              class="dokument-details"
            >
              <!-- Patient des Dokuments -->
              <div class="patient-info">
                <h4>Patient</h4>
                <div class="info-box">
                  <p v-if="getPatientForDokument(dokument.dokument_id)">
                    <span
                      v-if="
                        getPatientForDokument(dokument.dokument_id).vorname ||
                        getPatientForDokument(dokument.dokument_id).nachname
                      "
                    >
                      Name:
                      {{ getPatientForDokument(dokument.dokument_id).vorname }}
                      {{ getPatientForDokument(dokument.dokument_id).nachname }}
                    </span>
                    <span v-if="getPatientForDokument(dokument.dokument_id).geburtsdatum">
                      Geburtsdatum:
                      {{ getPatientForDokument(dokument.dokument_id).geburtsdatum }}
                    </span>
                    <span v-if="getPatientForDokument(dokument.dokument_id).geschlecht">
                      Geschlecht:
                      {{ getPatientForDokument(dokument.dokument_id).geschlecht }}
                    </span>
                    <span
                      v-if="
                        !getPatientForDokument(dokument.dokument_id).vorname &&
                        !getPatientForDokument(dokument.dokument_id).nachname &&
                        !getPatientForDokument(dokument.dokument_id).geburtsdatum
                      "
                    >
                      Keine Patientendaten verfügbar
                    </span>
                  </p>
                </div>
              </div>

              <!-- Autoren des Dokuments -->
              <div class="autoren-info">
                <h4>Autoren</h4>
                <div class="info-box">
                  <div
                    v-for="autor in getAutorenForDokument(dokument.dokument_id)"
                    :key="autor.autor_id"
                    class="autor-entry"
                  >
                    <p v-if="autor.vorname || autor.nachname || autor.organisation">
                      {{ autor.vorname }} {{ autor.nachname }}
                      <span v-if="autor.organisation">({{ autor.organisation }})</span>
                    </p>
                    <p v-else>Keine Autorendaten verfügbar</p>
                  </div>
                </div>
              </div>

              <!-- Inhalte des Dokuments -->
              <div class="inhalte-container">
                <h4>Inhalte</h4>
                <div
                  v-for="group in groupInhalteForDokument(dokument.dokument_id)"
                  :key="group.section_title"
                  class="inhalt-group"
                >
                  <div
                    class="inhalt-header"
                    @click="toggleInhaltGroup(dokument.dokument_id, group.section_title)"
                  >
                    <span class="toggle-icon">
                      {{
                        isInhaltGroupExpanded(dokument.dokument_id, group.section_title)
                          ? '▼'
                          : '▶'
                      }}
                    </span>
                    <h5>{{ group.section_title }}</h5>
                  </div>

                  <div
                    v-if="isInhaltGroupExpanded(dokument.dokument_id, group.section_title)"
                    class="werte-list"
                  >
                    <div v-for="inhalt in group.items" :key="inhalt.inhalt_id" class="inhalt-box">
                      <div class="select-container">
                        <label class="select-label">
                          <input
                            type="checkbox"
                            :checked="isItemSelected(inhalt.inhalt_id)"
                            @change="toggleSelection(auftrag, dokument, inhalt)"
                          >
                          Auswählen
                        </label>
                      </div>
                      <div class="inhalt-header" @click="toggleInhalt(inhalt.inhalt_id)">
                        <span class="toggle-icon">
                          {{ isInhaltExpanded(inhalt.inhalt_id) ? '▼' : '▶' }}
                        </span>
                        <div class="inhalt-meta">
                          <span>Code: {{ inhalt.section_code }}</span>
                          <span>System: {{ inhalt.codeSystemName }}</span>
                          <span>Class Code: {{ inhalt.section_type }}</span>
                        </div>
                      </div>

                      <div v-if="isInhaltExpanded(inhalt.inhalt_id)">

                        <div
                          v-for="wert in getWerteForInhalt(inhalt.inhalt_id)"
                          :key="wert.wert_id"
                          class="wert-box"
                        >
                          <div class="wert-content">
                            <span class="wert-key">{{ wert.key_path }}:</span>
                            <span class="wert-value">{{ wert.value }}</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <div v-if="fhirBundle" class="fhir-bundle">
    <h2>FHIR Bundle</h2>
    <pre>{{ fhirBundle }}</pre>
  </div>
</template>

<script setup>
import {ref} from 'vue'

const props = defineProps({
  auftraege: Array,
  dokumente: Array,
  patienten: Array,
  autoren: Array,
  inhalte: Array,
  werte: Array
})
const fhirBundle = ref(null)

const expandedAuftraege = ref([])
const expandedDokumente = ref([])
// Für die gruppierten Inhalte verwenden wir einen zusammengesetzten Schlüssel aus dokumentId und section_title:
const expandedInhalte = ref([])
// Zustand für die aufgeklappten Inhalte (auf Ebene einzelner "inhalt-box")
const expandedInhalt = ref([])

const toggleInhalt = (inhaltId) => {
  const index = expandedInhalt.value.indexOf(inhaltId)
  if (index === -1) {
    expandedInhalt.value.push(inhaltId)
  } else {
    expandedInhalt.value.splice(index, 1)
  }
}

const isInhaltExpanded = (inhaltId) => {
  return expandedInhalt.value.includes(inhaltId)
}


// Toggle Funktionen
const toggleAuftrag = (id) => {
  const index = expandedAuftraege.value.indexOf(id)
  if (index === -1) {
    expandedAuftraege.value.push(id)
  } else {
    expandedAuftraege.value.splice(index, 1)
  }
}

const toggleDokument = (id) => {
  const index = expandedDokumente.value.indexOf(id)
  if (index === -1) {
    expandedDokumente.value.push(id)
  } else {
    expandedDokumente.value.splice(index, 1)
  }
}

// Neue Toggle-Funktion für gruppierte Inhalte
const toggleInhaltGroup = (dokumentId, sectionTitle) => {
  const groupKey = `${dokumentId}-${sectionTitle}`
  const index = expandedInhalte.value.indexOf(groupKey)
  if (index === -1) {
    expandedInhalte.value.push(groupKey)
  } else {
    expandedInhalte.value.splice(index, 1)
  }
}

const isInhaltGroupExpanded = (dokumentId, sectionTitle) => {
  const groupKey = `${dokumentId}-${sectionTitle}`
  return expandedInhalte.value.includes(groupKey)
}

// Hilfsfunktionen für Beziehungen
const getDokumenteForAuftrag = (auftragsId) => {
  return props.dokumente.filter((dok) => dok.auftrags_id === auftragsId)
}

const getPatientForDokument = (dokumentId) => {
  return props.patienten.find((pat) => pat.dokument_id === dokumentId)
}

const getAutorenForDokument = (dokumentId) => {
  return props.autoren.filter((autor) => autor.dokument_id === dokumentId)
}

// Statt direkt alle Inhalte anzuzeigen, gruppieren wir sie nach section_title
const groupInhalteForDokument = (dokumentId) => {
  const inhalteForDokument = props.inhalte.filter(
    (inhalt) => inhalt.dokument_id === dokumentId
  )
  const groups = {}
  inhalteForDokument.forEach((inhalt) => {
    const key = inhalt.section_title || 'Ohne Titel'
    if (!groups[key]) {
      groups[key] = []
    }
    groups[key].push(inhalt)
  })
  // Rückgabe als Array von Gruppenobjekten
  return Object.keys(groups).map((key) => ({
    section_title: key,
    items: groups[key]
  }))
}

const getWerteForInhalt = (inhaltId) => {
  return props.werte.filter((wert) => wert.inhalt_id === inhaltId)
}

const formatDate = (dateArray) => {
  if (!dateArray || dateArray.length < 3) return ''
  const [year, month, day, hour, minute, second] = dateArray
  return `${day}.${month}.${year} ${hour}:${minute}:${second}`
}

const selectedItems = ref([])

// Hilfsfunktion zum Formatieren der Patienteninformation
const formatPatientInfo = (patient) => {
  if (!patient) return 'Keine Patientendaten'
  const name = [patient.vorname, patient.nachname].filter(Boolean).join(' ')
  return name || 'Unbekannter Patient'
}

// Prüft ob ein Element bereits ausgewählt ist
const isItemSelected = (inhaltId) => {
  return selectedItems.value.some(item => item.inhalt_id === inhaltId)
}

// Fügt ein Element zur Auswahl hinzu oder entfernt es
const toggleSelection = (auftrag, dokument, inhalt) => {
  const index = selectedItems.value.findIndex(item => item.inhalt_id === inhalt.inhalt_id)

  if (index === -1) {
    // Element hinzufügen
    selectedItems.value.push({
      inhalt_id: inhalt.inhalt_id,
      auftrag: {...auftrag},
      dokument: {...dokument},
      patient: getPatientForDokument(dokument.dokument_id),
      inhalt: {...inhalt},
      werte: getWerteForInhalt(inhalt.inhalt_id)
    })
  } else {
    // Element entfernen
    selectedItems.value.splice(index, 1)
  }
}

// Entfernt ein Element aus der Auswahl
const removeSelectedItem = (inhaltId) => {
  const index = selectedItems.value.findIndex(item => item.inhalt_id === inhaltId)
  if (index !== -1) {
    selectedItems.value.splice(index, 1)
  }
}

// Sendet die ausgewählten Daten an die API
const sendToApi = async () => {
  try {
    // Hier können Sie Ihre API-URL einsetzen
    const response = await fetch('http://localhost:7777/convert/cda-to-fhir', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(selectedItems.value)
    })

    if (!response.ok) {
      throw new Error('API request failed')
    }

    // Hier ist die Korrektur: await das Promise von response.json()
    const data = await response.json()
    fhirBundle.value = data

    // Optional: Auswahl zurücksetzen
    // selectedItems.value = []

  } catch (error) {
    console.error('Fehler beim Senden der Daten:', error)
    alert('Fehler beim Senden der Daten')
  }
}
</script>

<style scoped>
.data-display {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
  font-family: Arial, sans-serif;
}

.auftrag-container {
  margin-bottom: 20px;
  border: 1px solid #ddd;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.auftrag-header,
.dokument-header,
.inhalt-header {
  background-color: #f8f9fa;
  padding: 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 10px;
}

.auftrag-header:hover,
.dokument-header:hover,
.inhalt-header:hover {
  background-color: #e9ecef;
}

.dokument-container {
  margin: 10px;
  border: 1px solid #e9ecef;
  border-radius: 6px;
}

.dokument-details {
  padding: 15px;
}

.info-box {
  background-color: #fff;
  padding: 12px;
  border: 1px solid #e9ecef;
  border-radius: 6px;
  margin: 8px 0;
}

.inhalt-box {
  margin: 10px 0;
  border: 1px solid #e9ecef;
  border-radius: 6px;
}

.inhalt-group {
  margin-bottom: 10px;
  border: 1px solid #e9ecef;
  border-radius: 6px;
}

.wert-box {
  margin: 8px;
  padding: 10px;
  background-color: #fff;
  border: 1px solid #e9ecef;
  border-radius: 4px;
}

.toggle-icon {
  font-size: 12px;
  color: #6c757d;
  width: 20px;
}

.date {
  color: #6c757d;
  font-size: 0.9em;
  margin-left: auto;
}

.validation-status {
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 0.85em;
}

.validation-status.valid {
  background-color: #d4edda;
  color: #155724;
}

.inhalt-meta {
  display: flex;
  gap: 15px;
  font-size: 0.9em;
  color: #6c757d;
  justify-content: center;
}

.wert-content {
  display: flex;
  gap: 10px;
}

.wert-key {
  color: #495057;
  font-weight: 500;
}

.wert-value {
  color: #212529;
}

h2,
h3,
h4,
h5 {
  margin: 0;
  color: #212529;
}

.autor-entry {
  padding: 4px 0;
}

.autor-entry:not(:last-child) {
  border-bottom: 1px solid #e9ecef;
}

.selected-items-overview {
  margin-bottom: 30px;
  padding: 20px;
  background-color: #f8f9fa;
  border-radius: 8px;
  border: 1px solid #dee2e6;
}

.selected-item {
  margin: 10px 0;
  padding: 15px;
  background-color: white;
  border-radius: 6px;
  border: 1px solid #e9ecef;
}

.selected-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.selected-item-details {
  padding: 10px;
  background-color: #f8f9fa;
  border-radius: 4px;
}

.remove-btn {
  padding: 4px 8px;
  border: none;
  background-color: #dc3545;
  color: white;
  border-radius: 4px;
  cursor: pointer;
}

.remove-btn:hover {
  background-color: #c82333;
}

.send-btn {
  margin-top: 20px;
  padding: 10px 20px;
  background-color: #007bff;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.send-btn:hover {
  background-color: #0056b3;
}

.select-container {
  padding: 10px;
  background-color: #f8f9fa;
  border-radius: 4px;
  margin-bottom: 10px;
}

.select-label {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.selected-value {
  margin: 5px 0;
  padding: 5px;
  background-color: #f8f9fa;
  border-radius: 4px;
}

/* Verbessertes Styling für FHIR Bundle */
.fhir-bundle {
  margin-top: 30px;
  padding: 20px;
  background-color: #f8f9fa;
  border-radius: 8px;
  border: 1px solid #dee2e6;
}

.fhir-bundle pre {
  background-color: #f1f1f1;
  padding: 15px;
  border-radius: 4px;
  overflow-x: auto;
  white-space: pre-wrap;
  font-family: monospace;
  font-size: 14px;
  line-height: 1.5;
}
</style>
