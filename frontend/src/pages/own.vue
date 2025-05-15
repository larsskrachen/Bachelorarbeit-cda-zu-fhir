<script setup>
import { ref } from 'vue'
import DataDisplay from "@/components/DataDisplay.vue";

const files = ref([])
const bucketName = ref('')
const dragActive = ref(false)
const uploadStatus = ref('')
const isUploading = ref(false)
const showSuccessMessage = ref(false)

const checkForDuplicateFileName = (newFile) => {
  return files.value.some(existingFile => existingFile.name === newFile.name)
}

const onDrop = (e) => {
  e.preventDefault()
  dragActive.value = false

  const droppedFiles = [...e.dataTransfer.files].filter(file =>
    file.name.toLowerCase().endsWith('.xml')
  )

  if (droppedFiles.length) {
    // Prüfe auf Duplikate und füge nur eindeutige Dateien hinzu
    const uniqueFiles = droppedFiles.filter(file => {
      if (checkForDuplicateFileName(file)) {
        uploadStatus.value = `Die Datei "${file.name}" existiert bereits. Jede Datei muss einen einzigartigen Namen haben.`
        return false
      }
      return true
    })
    files.value = [...files.value, ...uniqueFiles]
  }
}

const removeFile = (index) => {
  files.value = files.value.filter((_, i) => i !== index)
}

const handleFileInput = (e) => {
  const newFiles = [...e.target.files].filter(file =>
    file.name.toLowerCase().endsWith('.xml')
  )

  // Prüfe auf Duplikate und füge nur eindeutige Dateien hinzu
  const uniqueFiles = newFiles.filter(file => {
    if (checkForDuplicateFileName(file)) {
      uploadStatus.value = `Die Datei "${file.name}" existiert bereits. Jede Datei muss einen einzigartigen Namen haben.`
      return false
    }
    return true
  })
  files.value = [...files.value, ...uniqueFiles]
}

const uploadFiles = async () => {
  if (!files.value.length) {
    uploadStatus.value = 'Bitte wählen Sie Dateien aus'
    return
  }

  isUploading.value = true
  uploadStatus.value = 'Upload läuft...'

  const formData = new FormData()
  files.value.forEach(file => formData.append('files', file))

  await sendFormData(formData);
}

const inhalte = ref();
const autoren = ref();
const patienten = ref();
const auftraege = ref();
const werte = ref();
const dokumente = ref();

async function sendFormData(formData) {
  try {
    // Anfrage an den Server senden
    const response = await fetch('http://localhost:7777/extract/xml', {
      method: 'POST',
      body: formData
    });

    if (response.ok) {
      uploadStatus.value = 'Upload erfolgreich'
      showSuccessMessage.value = true
      setTimeout(() => {
        showSuccessMessage.value = false
      }, 3000)
      files.value = []
      bucketName.value = ''
    } else {
      uploadStatus.value = 'Upload fehlgeschlagen'
    }

    // Antwort als JSON parsen
    const data = await response.json();

    // Das Array "inhalte" extrahieren
    inhalte.value = data.inhalte;
    autoren.value = data.autoren;
    patienten.value = data.patienten;
    auftraege.value = data.auftraege;
    werte.value = data.werte;
    dokumente.value = data.dokumente;
    console.log(inhalte.value);
    console.log(autoren.value);
    console.log(patienten.value);
    console.log(auftraege.value);
    console.log(werte.value);
    console.log(dokumente.value);

    // Hier kannst du weitere Verarbeitungen durchführen...
  } catch (error) {
    console.error('Fehler beim Abrufen der Daten:', error);
    uploadStatus.value = 'Fehler bei der Verarbeitung: ' + error.message
  } finally {
    isUploading.value = false
  }
}

</script>

<template>
  <div class="upload-container">
    <h2 class="title">XML-Daten Extraktion</h2>

    <div
      class="drop-zone"
      :class="{ active: dragActive, 'has-files': files.length > 0 }"
      @dragenter.prevent="dragActive = true"
      @dragleave.prevent="dragActive = false"
      @dragover.prevent
      @drop="onDrop"
      @click="$event.target.querySelector('input')?.click()"
    >
      <div class="drop-zone-content">
        <div class="icon">
          <svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="17 8 12 3 7 8"/>
            <line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
        </div>
        <p class="drop-text">XML-Dateien hier ablegen oder klicken zum Auswählen</p>
        <p class="hint">Nur XML-Dateien mit eindeutigen Namen werden akzeptiert</p>
        <input
          type="file"
          @change="handleFileInput"
          accept=".xml"
          multiple
        >
      </div>
    </div>

    <div v-if="files.length" class="file-list">
      <h3>Ausgewählte Dateien ({{ files.length }})</h3>
      <ul>
        <li v-for="(file, index) in files" :key="file.name">
          <div class="file-info">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
              <polyline points="8 16 12 12 16 16"/>
              <line x1="12" y1="12" x2="12" y2="21"/>
            </svg>
            <span class="file-name">{{ file.name }}</span>
            <span class="file-size">{{ (file.size / 1024).toFixed(1) }} KB</span>
          </div>
          <button
            class="remove-button"
            @click.stop="removeFile(index)"
            title="Datei entfernen"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
        </li>
      </ul>
    </div>

    <div class="upload-controls">
      <button
        class="upload-button"
        @click="uploadFiles"
        :disabled="!files.length || isUploading"
      >
        <span v-if="!isUploading">Upload starten</span>
        <span v-else class="loading">
          <svg class="spinner" viewBox="0 0 50 50">
            <circle class="path" cx="25" cy="25" r="20" fill="none" stroke-width="5"></circle>
          </svg>
          Wird hochgeladen...
        </span>
      </button>
    </div>

    <div v-if="uploadStatus" class="status" :class="{ error: uploadStatus.includes('fehlgeschlagen') || uploadStatus.includes('Fehler'), success: uploadStatus.includes('erfolgreich') }">
      <div class="status-icon" v-if="uploadStatus.includes('erfolgreich')">
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
          <polyline points="22 4 12 14.01 9 11.01"></polyline>
        </svg>
      </div>
      <div class="status-icon" v-else-if="uploadStatus.includes('fehlgeschlagen') || uploadStatus.includes('Fehler')">
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="10"></circle>
          <line x1="15" y1="9" x2="9" y2="15"></line>
          <line x1="9" y1="9" x2="15" y2="15"></line>
        </svg>
      </div>
      {{ uploadStatus }}
    </div>

    <div v-if="showSuccessMessage" class="success-notification">
      <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
        <polyline points="22 4 12 14.01 9 11.01"></polyline>
      </svg>
      Dateien wurden erfolgreich verarbeitet
    </div>
  </div>

  <DataDisplay
    :auftraege="auftraege"
    :dokumente="dokumente"
    :patienten="patienten"
    :autoren="autoren"
    :inhalte="inhalte"
    :werte="werte"
  />
</template>

<style scoped>
.upload-container {
  max-width: 800px;
  margin: 2rem auto;
  padding: 2rem;
  background-color: white;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

.title {
  text-align: center;
  color: #2C3E50;
  margin-top: 0;
  margin-bottom: 1.5rem;
  font-size: 1.8rem;
  font-weight: 600;
}

.drop-zone {
  border: 2px dashed #d1d5db;
  border-radius: 10px;
  padding: 2.5rem 2rem;
  text-align: center;
  cursor: pointer;
  margin-bottom: 1.5rem;
  transition: all 0.3s ease;
  background-color: #f9fafb;
  position: relative;
}

.drop-zone.active {
  border-color: #4f46e5;
  background: #eef2ff;
}

.drop-zone.has-files {
  border-color: #10b981;
  background: #ecfdf5;
}

.drop-zone-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.icon {
  color: #6b7280;
  margin-bottom: 1rem;
}

.drop-zone.active .icon {
  color: #4f46e5;
}

.drop-zone.has-files .icon {
  color: #10b981;
}

.drop-text {
  font-size: 1.1rem;
  color: #4b5563;
  margin-bottom: 0.5rem;
  font-weight: 500;
}

.drop-zone input[type="file"] {
  display: none;
}

.hint {
  font-size: 0.9rem;
  color: #6b7280;
  margin-top: 0.25rem;
}

.file-list {
  margin: 1.5rem 0;
  background: #f9fafb;
  border-radius: 8px;
  padding: 1rem;
}

.file-list h3 {
  margin-top: 0;
  color: #374151;
  font-size: 1.1rem;
  font-weight: 500;
  margin-bottom: 0.75rem;
}

.file-list ul {
  list-style: none;
  padding: 0;
  margin: 0;
  max-height: 300px;
  overflow-y: auto;
}

.file-list li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem 1rem;
  background: white;
  margin-bottom: 0.5rem;
  border-radius: 6px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  transition: background-color 0.2s;
}

.file-list li:hover {
  background-color: #f3f4f6;
}

.file-list li:last-child {
  margin-bottom: 0;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  overflow: hidden;
}

.file-name {
  font-weight: 500;
  color: #374151;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 400px;
}

.file-size {
  font-size: 0.8rem;
  color: #6b7280;
  margin-left: 0.5rem;
}

.remove-button {
  background: transparent;
  color: #6b7280;
  border: none;
  border-radius: 50%;
  width: 28px;
  height: 28px;
  padding: 0;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.remove-button:hover {
  background: #fee2e2;
  color: #ef4444;
}

.upload-controls {
  display: flex;
  justify-content: center;
  margin: 1.5rem 0;
}

.upload-button {
  padding: 0.75rem 2rem;
  background: #4f46e5;
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 1rem;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background-color 0.3s, transform 0.1s;
  min-width: 180px;
}

.upload-button:hover:not(:disabled) {
  background: #4338ca;
  transform: translateY(-1px);
}

.upload-button:active:not(:disabled) {
  transform: translateY(1px);
}

.upload-button:disabled {
  background: #9ca3af;
  cursor: not-allowed;
}

.loading {
  display: flex;
  align-items: center;
  gap: 8px;
}

.spinner {
  animation: rotate 2s linear infinite;
  width: 18px;
  height: 18px;
}

.path {
  stroke: white;
  stroke-linecap: round;
  animation: dash 1.5s ease-in-out infinite;
}

@keyframes rotate {
  100% {
    transform: rotate(360deg);
  }
}

@keyframes dash {
  0% {
    stroke-dasharray: 1, 150;
    stroke-dashoffset: 0;
  }
  50% {
    stroke-dasharray: 90, 150;
    stroke-dashoffset: -35;
  }
  100% {
    stroke-dasharray: 90, 150;
    stroke-dashoffset: -124;
  }
}

.status {
  padding: 1rem;
  margin-top: 1rem;
  background: #f3f4f6;
  border-radius: 6px;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: #4b5563;
}

.status.error {
  background: #fee2e2;
  color: #b91c1c;
}

.status.success {
  background: #d1fae5;
  color: #065f46;
}

.status-icon {
  display: flex;
  align-items: center;
  justify-content: center;
}

.success-notification {
  position: fixed;
  bottom: 20px;
  right: 20px;
  background: #10b981;
  color: white;
  padding: 1rem;
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  animation: slideIn 0.3s forwards, fadeOut 0.3s 2.7s forwards;
}

@keyframes slideIn {
  from {
    transform: translateX(100%);
    opacity: 0;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}

@keyframes fadeOut {
  from {
    opacity: 1;
  }
  to {
    opacity: 0;
  }
}
</style>
