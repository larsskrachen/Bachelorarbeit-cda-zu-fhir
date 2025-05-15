<script setup>
import {ref} from 'vue'
import DataDisplay from "@/components/DataDisplay.vue";

const files = ref([])
const bucketName = ref('')
const dragActive = ref(false)
const uploadStatus = ref('')

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
    uploadStatus.value = 'Bitte wählen Sie Dateien und einen Bucket-Namen aus'
    return
  }

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
  }
}

</script>

<template>
  <div class="upload-container">
    <div
      class="drop-zone"
      :class="{ active: dragActive }"
      @dragenter.prevent="dragActive = true"
      @dragleave.prevent="dragActive = false"
      @dragover.prevent
      @drop="onDrop"
    >
      <p>XML-Dateien hier ablegen oder klicken zum Auswählen</p>
      <p class="hint">Hinweis: Jede Datei muss einen einzigartigen Namen haben</p>
      <input
        type="file"
        @change="handleFileInput"
        accept=".xml"
        multiple
      >
    </div>

    <div v-if="files.length" class="file-list">
      <h3>Ausgewählte Dateien:</h3>
      <ul>
        <li v-for="(file, index) in files" :key="file.name">
          {{ file.name }}
          <button
            class="remove-button"
            @click="removeFile(index)"
          >
            ✕
          </button>
        </li>
      </ul>
    </div>

    <div class="upload-controls">
      <button
        @click="uploadFiles"
        :disabled="!files.length"
      >
        Upload starten
      </button>
    </div>

    <p v-if="uploadStatus" class="status">{{ uploadStatus }}</p>
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
  max-width: 600px;
  margin: 2rem auto;
  padding: 1rem;
}

.drop-zone {
  border: 2px dashed #ccc;
  padding: 2rem;
  text-align: center;
  cursor: pointer;
  margin-bottom: 1rem;
}

.drop-zone.active {
  border-color: #4CAF50;
  background: #E8F5E9;
}

.drop-zone input[type="file"] {
  display: none;
}

.hint {
  font-size: 0.9em;
  color: #666;
  margin-top: 0.5rem;
}

.file-list {
  margin: 1rem 0;
}

.file-list ul {
  list-style: none;
  padding: 0;
}

.file-list li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem;
  background: #f5f5f5;
  margin-bottom: 0.5rem;
  border-radius: 4px;
}

.remove-button {
  background: #ff4444;
  color: white;
  border: none;
  border-radius: 50%;
  width: 24px;
  height: 24px;
  padding: 0;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

.upload-controls {
  display: flex;
  gap: 1rem;
  margin: 1rem 0;
}

.upload-controls input {
  flex: 1;
  padding: 0.5rem;
}

button {
  padding: 0.5rem 1rem;
  background: #4CAF50;
  color: white;
  border: none;
  cursor: pointer;
}

button:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.status {
  padding: 1rem;
  margin-top: 1rem;
  background: #f0f0f0;
}
</style>
