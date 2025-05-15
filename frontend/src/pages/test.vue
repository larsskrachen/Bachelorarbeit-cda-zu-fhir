<script setup>
import {ref, computed, watch} from 'vue'

const files = ref([])
const xslFile = ref(null)
const bucketName = ref('')
const dragActive = ref(false)
const uploadStatus = ref('')
const transformedContent = ref('')
const parsedXmlFiles = ref([])
const showSectionSelector = ref(false)

const inhalte = ref();
const autoren = ref();
const patienten = ref();
const auftraege = ref();
const werte = ref();
const dokumente = ref();

const availableSections = ref([])
const availableEntries = ref([]) // Array für alle gefundenen Entries
const availableTexts = ref([]) // Array für alle gefundenen Texte
const selectedSections = ref([])
const selectedEntries = ref([]) // Array für ausgewählte Entries
const selectedTexts = ref([]) // Array für ausgewählte Texte

const transformationResults = ref([])
const showPreviewButton = ref(false)

const canSubmit = computed(() => {
  return files.value.length > 0;
})

const canSelectSections = computed(() => {
  return parsedXmlFiles.value.length > 0;
})

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
    const uniqueFiles = droppedFiles.filter(file => {
      if (checkForDuplicateFileName(file)) {
        uploadStatus.value = `Die Datei "${file.name}" existiert bereits. Jede Datei muss einen einzigartigen Namen haben.`
        return false
      }
      return true
    })
    files.value = [...files.value, ...uniqueFiles]
    parseXmlFiles()
  }
}

const handleFileInput = (e) => {
  const newFiles = [...e.target.files].filter(file =>
    file.name.toLowerCase().endsWith('.xml')
  )

  const uniqueFiles = newFiles.filter(file => {
    if (checkForDuplicateFileName(file)) {
      uploadStatus.value = `Die Datei "${file.name}" existiert bereits. Jede Datei muss einen einzigartigen Namen haben.`
      return false
    }
    return true
  })
  files.value = [...files.value, ...uniqueFiles]
  parseXmlFiles()
}

const parseXmlFiles = async () => {
  parsedXmlFiles.value = []
  availableSections.value = []
  availableEntries.value = []
  availableTexts.value = []
  const uniqueSections = new Set()
  const uniqueEntries = new Set()
  const uniqueTexts = new Set()

  for (const file of files.value) {
    try {
      const xmlText = await readFileAsText(file)
      const parser = new DOMParser()
      const xmlDoc = parser.parseFromString(xmlText, 'text/xml')

      // Füge IDs zu Sections und Entries hinzu, falls nicht vorhanden
      const sections = xmlDoc.querySelectorAll('section')
      sections.forEach((section, sectionIndex) => {
        // Section-ID setzen
        let sectionId = section.getAttribute('ID')
        if (!sectionId) {
          sectionId = `section-${file.name}-${sectionIndex}`
          section.setAttribute('ID', sectionId)
        }

        const code = section.querySelector('code')
        const title = section.querySelector('title');
        const sectionTitle = title ? title.textContent.trim() : 'Unbenannte Section';
        const sectionName = code ? (code.getAttribute('displayName') || code.getAttribute('code')) : 'Unbenannte Section'

        if (!uniqueSections.has(sectionId)) {
          uniqueSections.add(sectionId)
          availableSections.value.push({
            id: sectionId,
            title: sectionTitle,
            name: sectionName,
            type: 'section',
            filenames: [file.name]
          })
        }

        // Verarbeite Entries
        const entries = section.querySelectorAll('entry')
        entries.forEach((entry, entryIndex) => {
          // Entry-ID setzen
          let entryId = entry.getAttribute('ID')
          if (!entryId) {
            entryId = `entry-${sectionId}-${entryIndex}`
            entry.setAttribute('ID', entryId)
          }

          const entryCode = entry.querySelector('code')
          const entryName = entryCode ? (entryCode.getAttribute('displayName') || entryCode.getAttribute('code')) : 'Unbenannter Entry'

          if (!uniqueEntries.has(entryId)) {
            uniqueEntries.add(entryId)
            availableEntries.value.push({
              id: entryId,
              name: entryName,
              type: 'entry',
              parentSection: sectionId,
              filenames: [file.name]
            })
          }
        })

        // Verarbeite Texte (unverändert)
        const textElements = section.querySelectorAll('text')
        textElements.forEach((text, index) => {
          const textId = `text-${sectionId}-${index}`
          if (!uniqueTexts.has(textId)) {
            uniqueTexts.add(textId)
            availableTexts.value.push({
              id: textId,
              name: `Text in ${sectionName}`,
              type: 'text',
              parentSection: sectionId,
              filenames: [file.name]
            })
          }
        })
      })

      parsedXmlFiles.value.push({
        name: file.name,
        document: xmlDoc
      })
      transformationResults.value = await transformXml()
      showPreviewButton.value = true
    } catch (err) {
      console.error(`Fehler beim Parsen von ${file.name}:`, err)
    }
  }

  selectedEntries.value = [...availableEntries.value.map(entry => entry.id)];
  selectedTexts.value = [...availableTexts.value.map(text => text.id)];
  showSectionSelector.value = availableSections.value.length > 0;
}

// Geänderte Funktion zum Filtern der XML-Dokumente
// Corrected function to properly filter XML documents based on selections
// Corrected function to properly filter XML documents based on selections
const filterXmlDocuments = () => {
  const filteredDocs = []

  for (const parsedFile of parsedXmlFiles.value) {
    // Klonen des Original-Dokuments
    const clonedDoc = parsedFile.document.cloneNode(true)

    // Durchlaufe alle Components
    const components = Array.from(clonedDoc.querySelectorAll('component'))
    components.forEach(component => {
      let componentHasContent = false
      const sections = Array.from(component.querySelectorAll('section'))

      sections.forEach(section => {
        const sectionId = section.getAttribute('ID')
        let sectionHasEntries = false

        // Verarbeite Entries
        const entries = Array.from(section.querySelectorAll('entry'))
        entries.forEach(entry => {
          const entryId = entry.getAttribute('ID')

          if (selectedEntries.value.includes(entryId)) {
            // Entry behalten
            componentHasContent = true
            sectionHasEntries = true
          } else {
            // Nicht ausgewähltes Entry entfernen
            entry.parentNode?.removeChild(entry)
          }
        })

        // Entferne leere Sections
        if (!sectionHasEntries) {
          section.parentNode?.removeChild(section)
        }
      })

      // Überprüfe ob Component noch Sections enthält
      const remainingSections = component.querySelectorAll('section')
      if (remainingSections.length === 0) {
        // Leeres Component entfernen
        component.parentNode?.removeChild(component)
      } else {
        // Aktualisiere Component-Status
        componentHasContent = true
      }
    })

    // Füge gefiltertes Dokument hinzu
    filteredDocs.push({
      name: parsedFile.name,
      document: clonedDoc
    })
  }

  return filteredDocs
}

const xmlDocToBlob = (xmlDoc) => {
  const serializer = new XMLSerializer()
  const xmlString = serializer.serializeToString(xmlDoc)
  return new Blob([xmlString], {type: 'application/xml'})
}

const uploadFiles = async () => {
  if (!files.value.length) return;

  try {
    uploadStatus.value = 'Verarbeite Dateien...';

    // Filtere XML-Dokumente
    const filteredDocs = filterXmlDocuments();

    // Erstelle FormData
    const formData = new FormData();
    for (const doc of filteredDocs) {
      const blob = xmlDocToBlob(doc.document);
      formData.append('files', new File([blob], doc.name, {type: 'application/xml'}));
    }

    // Hochladen
    await sendFormData(formData);

    // Automatische Transformation nach dem Upload
    transformedContent.value = await transformXml();
    uploadStatus.value = 'Upload erfolgreich und Transformation abgeschlossen';

  } catch (error) {
    console.error('Fehler:', error);
    uploadStatus.value = `Fehler: ${error.message}`;
  }
};

const transformXml = async () => {
  if (!files.value.length) return []

  try {
    const xslDoc = await loadXslFile()
    if (!xslDoc) return []

    const xsltProcessor = new XSLTProcessor()
    xsltProcessor.importStylesheet(xslDoc)

    return await Promise.all(
      parsedXmlFiles.value.map(async (parsedFile) => {
        const resultDoc = xsltProcessor.transformToDocument(parsedFile.document)
        const serializer = new XMLSerializer()
        return {
          filename: parsedFile.name,
          content: serializer.serializeToString(resultDoc)
        }
      })
    )
  } catch (err) {
    throw new Error(`XSLT-Transformation fehlgeschlagen: ${err.message}`)
  }
}

const openTransformationPreview = () => {
  const features = 'width=800,height=600,resizable=yes,scrollbars=yes'
  const previewWindow = window.open('', '_blank', features)

  previewWindow.document.write(`
    <html>
      <head>
        <title>Transformierte Dokumente</title>
        <style>
          body { font-family: Arial; padding: 20px; }
          .document { margin-bottom: 40px; }
          h3 { color: #2c3e50; }
          pre {
            background: #f8f9fa;
            padding: 20px;
            border-radius: 5px;
            overflow-x: auto;
            white-space: pre-wrap;
            word-wrap: break-word;
          }
        </style>
      </head>
      <body>
        <h1>Transformierte Dokumente</h1>
        ${transformationResults.value.map(result => `
          <div class="document">
            <h3>${result.filename}</h3>
            <pre>${escapeHtml(result.content)}</pre>
          </div>
        `).join('')}
      </body>
    </html>
  `)
}

// Hilfsfunktion zum Escapen von HTML für srcdoc
const escapeHtml = (html) => {
  return html.replace(/"/g, '&quot;').replace(/'/g, '&#39;');
};

const toggleAllSections = (selectAll) => {
  if (selectAll) {
    selectedSections.value = availableSections.value.map(section => section.id)
  } else {
    selectedSections.value = []
  }
}

const toggleAllEntries = (selectAll) => {
  if (selectAll) {
    selectedEntries.value = availableEntries.value.map(entry => entry.id)
  } else {
    selectedEntries.value = []
  }
}

const toggleAllTexts = (selectAll) => {
  if (selectAll) {
    selectedTexts.value = availableTexts.value.map(text => text.id)
  } else {
    selectedTexts.value = []
  }
}

const toggleSectionsByType = (type, selectAll) => {
  const sectionsOfType = availableSections.value
    .filter(section => section.type === type)
    .map(section => section.id)

  if (selectAll) {
    selectedSections.value = [...new Set([...selectedSections.value, ...sectionsOfType])]
  } else {
    selectedSections.value = selectedSections.value.filter(id => !sectionsOfType.includes(id))
  }
}

const readFileAsText = (file) => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = (e) => resolve(e.target.result);
    reader.onerror = (e) => reject(new Error('Datei konnte nicht gelesen werden'));
    reader.readAsText(file);
  });
};

async function sendFormData(formData) {
  try {
    const response = await fetch('http://localhost:7777/extract/xml', {
      method: 'POST',
      body: formData
    });

    if (response.ok) {
      uploadStatus.value = 'Upload erfolgreich'
      bucketName.value = ''
    } else {
      uploadStatus.value = 'Upload fehlgeschlagen'
    }

    const data = await response.json();

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

  } catch (error) {
    console.error('Fehler beim Abrufen der Daten:', error);
  }
}

const loadXslFile = async () => {
  try {
    const response = await fetch('/CDA.xsl');
    const xslText = await response.text();
    const parser = new DOMParser();
    return parser.parseFromString(xslText, 'text/xml');
  } catch (error) {
    console.error('Fehler beim Laden der XSL-Datei:', error);
    uploadStatus.value = 'Fehler beim Laden der XSL-Datei.';
  }
};

watch(files, () => {
  if (files.value.length === 0) {
    availableSections.value = []
    availableEntries.value = []
    availableTexts.value = []
    selectedSections.value = []
    selectedEntries.value = []
    selectedTexts.value = []
    showSectionSelector.value = false
  }
}, {deep: true})
</script>

<template>
  <div class="upload-container">
    <!-- XML-Dateien Upload-Bereich -->
    <div
      :class="{ active: dragActive }"
      class="drop-zone"
      @drop="onDrop"
      @dragenter.prevent="dragActive = true"
      @dragleave.prevent="dragActive = false"
      @dragover.prevent
    >
      <p>XML-Dateien hier ablegen oder klicken zum Auswählen</p>
      <p class="hint">Hinweis: Jede Datei muss einen einzigartigen Namen haben</p>
      <input
        id="xml-upload"
        accept=".xml"
        multiple
        type="file"
        @change="handleFileInput"
      >
      <label class="file-input-label" for="xml-upload">XML-Dateien auswählen</label>
    </div>

    <div v-if="showPreviewButton" class="preview-section">
      <button class="preview-button" @click="openTransformationPreview">
        Transformierte Dokumente anzeigen
      </button>
      <p class="status-info">{{ uploadStatus }}</p>
    </div>

    <!-- Liste der ausgewählten XML-Dateien -->
    <div v-if="files.length" class="file-list">
      <h3>Ausgewählte XML-Dateien:</h3>
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

    <!-- CDA Section, Entry und Text Selector -->
    <div v-if="showSectionSelector" class="section-selector">
      <h3>CDA Sections, Entries und Texte auswählen:</h3>
      <div class="section-controls">
        <button class="control-button" @click="toggleAllEntries(true)">Alle Entries</button>
        <button class="control-button" @click="toggleAllEntries(false)">Keine Entries</button>
      </div>

      <div class="section-list">
        <div
          v-for="section in availableSections"
          :key="section.id"
          class="section-item"
        >
          <div class="section-header">
            <span class="section-name">{{ section.title }}</span>
            <span class="section-name">({{ section.name }})</span>
            <span class="section-files">({{ section.filenames.join(', ') }})</span>
          </div>

          <!-- Entries dieser Section -->
          <div v-if="availableEntries.filter(e => e.parentSection === section.id).length > 0" class="entry-container">
            <h4 class="entries-heading">Entries:</h4>
            <div class="entry-list">
              <div
                v-for="entry in availableEntries.filter(e => e.parentSection === section.id)"
                :key="entry.id"
                class="entry-item"
              >
                <label class="entry-label">
                  <input
                    v-model="selectedEntries"
                    :value="entry.id"
                    type="checkbox"
                  >
                  <span class="entry-name">{{ entry.name }}</span>
                  <span class="entry-files">({{ entry.filenames.join(', ') }})</span>
                </label>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Aktions-Buttons -->
    <div class="action-buttons">
      <!--      <button-->
      <!--        :disabled="!files.length"-->
      <!--        class="transform-button"-->
      <!--        @click="transformXml"-->
      <!--      >-->
      <!--        Mit XSL transformieren-->
      <!--      </button>-->

      <button
        :disabled="!canSubmit"
        class="upload-button"
        @click="uploadFiles"
      >
        Zum Server hochladen
      </button>
    </div>

    <p v-if="uploadStatus" class="status">{{ uploadStatus }}</p>

    <!-- Bereich für die transformierten Inhalte -->
    <div v-if="transformedContent" class="transformed-content">
      <h3>Transformierte Inhalte:</h3>
      <div v-html="transformedContent"></div>
    </div>
  </div>
</template>

<style scoped>
.upload-container {
  max-width: 800px;
  margin: 2rem auto;
  padding: 1rem;
}

.transformed-content {
  margin-top: 2rem;
  padding: 1rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  max-height: 500px;
  overflow-y: auto;
  background: #fff;
}

.transformed-content h3 {
  margin-top: 0;
  color: #333;
}

.transformed-content hr {
  margin: 1.5rem 0;
  border: none;
  border-top: 1px dashed #ccc;
}

/* Stile für typische HTML-Elemente im transformierten Inhalt */
.transformed-content table {
  width: 100%;
  border-collapse: collapse;
  margin: 1rem 0;
}

.transformed-content th,
.transformed-content td {
  border: 1px solid #ddd;
  padding: 0.5rem;
  text-align: left;
}

.transformed-content th {
  background: #f5f5f5;
  font-weight: bold;
}

.transformed-content p {
  margin: 0.5rem 0;
}

.transformed-content ul,
.transformed-content ol {
  margin: 0.5rem 0;
  padding-left: 2rem;
}

.transformed-content li {
  margin: 0.3rem 0;
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

.drop-zone input[type="file"],
.xsl-upload-section input[type="file"] {
  display: none;
}

.file-input-label {
  display: inline-block;
  background: #4CAF50;
  color: white;
  padding: 0.5rem 1rem;
  border-radius: 4px;
  cursor: pointer;
  margin-top: 1rem;
}

.hint {
  font-size: 0.9em;
  color: #666;
  margin-top: 0.5rem;
}

.xsl-upload-section {
  margin: 1rem 0;
  padding: 1rem;
  background: #f5f5f5;
  border-radius: 4px;
}

.xsl-input-container {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.selected-xsl {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem;
  background: #e0f7fa;
  border-radius: 4px;
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

.preview-section {
  margin-top: 2rem;
  padding-top: 1rem;
  border-top: 1px solid #eee;
}

.preview-button {
  background: #2196f3;
  color: white;
  padding: 12px 24px;
  border-radius: 8px;
  font-size: 1.1rem;
  transition: background 0.3s ease;
}

.preview-button:hover {
  background: #1976d2;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.status-info {
  margin-top: 1rem;
  color: #666;
  font-size: 0.9rem;
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

.action-buttons {
  display: flex;
  gap: 1rem;
  margin: 1rem 0;
}

button {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.transform-button {
  background: #2196F3;
  color: white;
}

.upload-button {
  background: #4CAF50;
  color: white;
}

button:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.status {
  padding: 1rem;
  margin-top: 1rem;
  background: #f0f0f0;
  border-radius: 4px;
}

.transformed-content {
  margin-top: 2rem;
  padding: 1rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  max-height: 500px;
  overflow-y: auto;
}

.transformed-content hr {
  margin: 1.5rem 0;
  border: none;
  border-top: 1px dashed #ccc;
}

/* Styles für Section, Entry und Text Selector */
.section-selector {
  margin: 1.5rem 0;
  padding: 1rem;
  background: #f5f5f5;
  border-radius: 4px;
}

.section-controls {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
  flex-wrap: wrap;
}

.control-button {
  background: #2196F3;
  color: white;
  font-size: 0.8rem;
  padding: 0.3rem 0.7rem;
}

.section-list {
  max-height: 400px;
  overflow-y: auto;
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 0.5rem;
  background: white;
}

.section-item {
  margin-bottom: 1rem;
  padding: 1rem;
  border-bottom: 1px dotted #ccc;
  background-color: #f8f8f8;
  border-radius: 4px;
}

.section-label {
  display: flex;
  align-items: center;
  font-weight: bold;
  cursor: pointer;
  padding: 0.3rem;
  margin-bottom: 0.5rem;
}

.section-label:hover {
  background: #e3f2fd;
}

.section-name {
  margin-left: 0.5rem;
}

.section-files {
  margin-left: 0.5rem;
  font-size: 0.8rem;
  color: #666;
  font-weight: normal;
}

.entry-container, .text-container {
  margin-top: 0.5rem;
  margin-left: 1.5rem;
  padding: 0.5rem;
  border-left: 2px solid #2196F3;
  background-color: #f0f8ff;
  border-radius: 0 4px 4px 0;
}

.text-container {
  border-left-color: #4CAF50;
  background-color: #f0fff0;
}

.entries-heading, .texts-heading {
  font-size: 0.9rem;
  margin: 0.5rem 0;
  color: #2196F3;
}

.texts-heading {
  color: #4CAF50;
}

.entry-list, .text-list {
  margin-left: 1rem;
}

.entry-item, .text-item {
  margin-bottom: 0.3rem;
}

.entry-label, .text-label {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 0.2rem;
}

.entry-label:hover {
  background: #e3f2fd;
}

.text-label:hover {
  background: #e8f5e9;
}

.entry-name, .text-name {
  margin-left: 0.5rem;
}

.entry-files, .text-files {
  margin-left: 0.5rem;
  font-size: 0.8rem;
  color: #666;
}
</style>
