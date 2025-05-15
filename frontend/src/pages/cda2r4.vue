<script setup>
import { ref, computed, watch } from 'vue';
import VueJsonPretty from 'vue-json-pretty';
import 'vue-json-pretty/lib/styles.css';

const files = ref([]);
const xslFile = ref(null);
const bucketName = ref('');
const dragActive = ref(false);
const uploadStatus = ref('');
const transformedContent = ref('');
const parsedXmlFiles = ref([]);
const showSectionSelector = ref(false);
const serverResponse = ref(null);
const fhirServerUrl = ref('');
const fhirSendStatus = ref('');

const inhalte = ref();
const autoren = ref();
const patienten = ref();
const auftraege = ref();
const werte = ref();
const dokumente = ref();

const availableSections = ref([]);
const selectedSections = ref([]);
const groupedSections = ref({});

const transformationResults = ref([]);
const showPreviewButton = ref(false);

const canSubmit = computed(() => {
  return files.value.length > 0;
});

const canSelectSections = computed(() => {
  return parsedXmlFiles.value.length > 0;
});

const canSendToFhir = computed(() => {
  return serverResponse.value && fhirServerUrl.value.trim() !== '';
});

const checkForDuplicateFileName = (newFile) => {
  return files.value.some((existingFile) => existingFile.name === newFile.name);
};

const removeFile = (index) => {
  files.value.splice(index, 1);
  parseXmlFiles();
};

const onDrop = (e) => {
  e.preventDefault();
  dragActive.value = false;

  const droppedFiles = [...e.dataTransfer.files].filter((file) =>
    file.name.toLowerCase().endsWith('.xml')
  );

  if (droppedFiles.length) {
    const uniqueFiles = droppedFiles.filter((file) => {
      if (checkForDuplicateFileName(file)) {
        uploadStatus.value = `Die Datei "${file.name}" existiert bereits. Jede Datei muss einen einzigartigen Namen haben.`;
        return false;
      }
      return true;
    });
    files.value = [...files.value, ...uniqueFiles];
    parseXmlFiles();
  }
};

const handleFileInput = (e) => {
  const newFiles = [...e.target.files].filter((file) =>
    file.name.toLowerCase().endsWith('.xml')
  );

  const uniqueFiles = newFiles.filter((file) => {
    if (checkForDuplicateFileName(file)) {
      uploadStatus.value = `Die Datei "${file.name}" existiert bereits. Jede Datei muss einen einzigartigen Namen haben.`;
      return false;
    }
    return true;
  });
  files.value = [...files.value, ...uniqueFiles];
  parseXmlFiles();
};

const parseXmlFiles = async () => {
  parsedXmlFiles.value = [];
  availableSections.value = [];
  groupedSections.value = {};
  const uniqueSections = new Set();

  for (const file of files.value) {
    try {
      const xmlText = await readFileAsText(file);
      const parser = new DOMParser();
      const xmlDoc = parser.parseFromString(xmlText, 'text/xml');

      const sections = xmlDoc.querySelectorAll('section');
      sections.forEach((section, sectionIndex) => {
        let sectionId = section.getAttribute('ID');
        if (!sectionId) {
          sectionId = `section-${file.name}-${sectionIndex}`;
          section.setAttribute('ID', sectionId);
        }

        const code = section.querySelector('code');
        const title = section.querySelector('title');
        const sectionTitle = title ? title.textContent.trim() : 'Unbenannte Section';
        const sectionName = code
          ? code.getAttribute('displayName')
          : 'Unbenannte Section';
        const sectionCode = code
          ? code.getAttribute('code')
          : 'Unbenannte Section';
        const sectionSystemName = code
          ? code.getAttribute('codeSystemName')
          : 'Unbenannte Section';
        const entryCount = section.querySelectorAll('entry').length;

        if (!uniqueSections.has(sectionId)) {
          uniqueSections.add(sectionId);
          const sectionData = {
            id: sectionId,
            title: sectionTitle,
            name: sectionName,
            sectionCode: sectionCode,
            sectionSystemName: sectionSystemName,
            entryCount: entryCount,
            filename: file.name,
          };
          availableSections.value.push(sectionData);

          if (!groupedSections.value[file.name]) {
            groupedSections.value[file.name] = [];
          }
          groupedSections.value[file.name].push(sectionData);
        }
      });

      parsedXmlFiles.value.push({
        name: file.name,
        document: xmlDoc,
      });
      transformationResults.value = await transformXml();
      showPreviewButton.value = true;
    } catch (err) {
      console.error(`Fehler beim Parsen von ${file.name}:`, err);
    }
  }

  selectedSections.value = [...availableSections.value.map((section) => section.id)];
  showSectionSelector.value = availableSections.value.length > 0;
};

const filterXmlDocuments = () => {
  const filteredDocs = [];

  for (const parsedFile of parsedXmlFiles.value) {
    const clonedDoc = parsedFile.document.cloneNode(true);

    const components = Array.from(clonedDoc.querySelectorAll('component'));
    components.forEach((component) => {
      const sections = Array.from(component.querySelectorAll('section'));
      sections.forEach((section) => {
        const sectionId = section.getAttribute('ID');
        if (!selectedSections.value.includes(sectionId)) {
          section.parentNode?.removeChild(section);
        }
      });

      const remainingSections = component.querySelectorAll('section');
      if (remainingSections.length === 0) {
        component.parentNode?.removeChild(component);
      }
    });

    filteredDocs.push({
      name: parsedFile.name,
      document: clonedDoc,
    });
  }

  return filteredDocs;
};

const xmlDocToBlob = (xmlDoc) => {
  const serializer = new XMLSerializer();
  const xmlString = serializer.serializeToString(xmlDoc);
  return new Blob([xmlString], { type: 'application/xml' });
};

const uploadFiles = async () => {
  if (!files.value.length) return;

  try {
    uploadStatus.value = 'Verarbeite Dateien...';

    const filteredDocs = filterXmlDocuments();

    const formData = new FormData();
    for (const doc of filteredDocs) {
      const blob = xmlDocToBlob(doc.document);
      formData.append('files', new File([blob], doc.name, { type: 'application/xml' }));
    }

    await sendFormData(formData);

    transformedContent.value = await transformXml();
    uploadStatus.value = 'Upload erfolgreich und Transformation abgeschlossen';
  } catch (error) {
    console.error('Fehler:', error);
    uploadStatus.value = `Fehler: ${error.message}`;
  }
};

const transformXml = async () => {
  if (!files.value.length) return [];

  try {
    const xslDoc = await loadXslFile();
    if (!xslDoc) return [];

    const xsltProcessor = new XSLTProcessor();
    xsltProcessor.importStylesheet(xslDoc);

    const results = await Promise.all(
      parsedXmlFiles.value.map(async (parsedFile) => {
        const resultDoc = xsltProcessor.transformToDocument(parsedFile.document);
        const serializer = new XMLSerializer();
        return {
          filename: parsedFile.name,
          content: serializer.serializeToString(resultDoc),
        };
      })
    );
    transformationResults.value = results;
    return results;
  } catch (err) {
    throw new Error(`XSLT-Transformation fehlgeschlagen: ${err.message}`);
  }
};

const openTransformationPreview = () => {
  const features = 'width=800,height=600,resizable=yes,scrollbars=yes';
  const previewWindow = window.open('', '_blank', features);

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
        ${transformationResults.value
    .map(
      (result) => `
          <div class="document">
            <h3>${result.filename}</h3>
            <pre>${escapeHtml(result.content)}</pre>
          </div>
        `
    )
    .join('')}
      </body>
    </html>
  `);
};

const showCdaPreview = async (index) => {
  const file = files.value[index];
  const parsedFile = parsedXmlFiles.value.find((pf) => pf.name === file.name);
  if (!parsedFile) {
    uploadStatus.value = 'Fehler: Datei nicht gefunden.';
    return;
  }

  try {
    const xslDoc = await loadXslFile();
    if (!xslDoc) {
      uploadStatus.value = 'Fehler: XSL-Datei konnte nicht geladen werden.';
      return;
    }

    const xsltProcessor = new XSLTProcessor();
    xsltProcessor.importStylesheet(xslDoc);
    const resultDoc = xsltProcessor.transformToDocument(parsedFile.document);
    const serializer = new XMLSerializer();
    const transformedContent = serializer.serializeToString(resultDoc);

    const features = 'width=800,height=600,resizable=yes,scrollbars=yes';
    const previewWindow = window.open('', '_blank', features);

    previewWindow.document.write(`
      <html>
        <head>
          <title>CDA Vorschau: ${file.name}</title>
          <style>
            body { font-family: Arial, sans-serif; padding: 20px; }
            h1 { color: #2c3e50; }
          </style>
        </head>
        <body>
          <h1>CDA Vorschau: ${file.name}</h1>
          ${transformedContent}
        </body>
      </html>
    `);
    previewWindow.document.close();
  } catch (err) {
    uploadStatus.value = `Fehler bei der Transformation: ${err.message}`;
    console.error('Fehler bei der CDA-Vorschau:', err);
  }
};

const escapeHtml = (html) => {
  return html.replace(/"/g, '"').replace(/'/g, '');
};

const toggleAllSections = (selectAll) => {
  if (selectAll) {
    selectedSections.value = availableSections.value.map((section) => section.id);
  } else {
    selectedSections.value = [];
  }
};

const toggleSectionsByFile = (filename, selectAll) => {
  const sectionsOfFile = groupedSections.value[filename].map((section) => section.id);
  if (selectAll) {
    selectedSections.value = [...new Set([...selectedSections.value, ...sectionsOfFile])];
  } else {
    selectedSections.value = selectedSections.value.filter((id) => !sectionsOfFile.includes(id));
  }
};

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
    const response = await fetch('http://localhost:7777/convert/cda2r4', {
      method: 'POST',
      body: formData,
    });

    if (response.ok) {
      uploadStatus.value = 'Upload erfolgreich';
      bucketName.value = '';
      const data = await response.json();
      serverResponse.value = data; // Speichere die FHIR-Nachricht
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
    } else {
      uploadStatus.value = 'Upload fehlgeschlagen';
    }
  } catch (error) {
    console.error('Fehler beim Abrufen der Daten:', error);
    uploadStatus.value = `Fehler: ${error.message}`;
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

const sendToFhirServer = async () => {
  if (!serverResponse.value || !fhirServerUrl.value) return;

  try {
    fhirSendStatus.value = 'Sende an FHIR-Server...';

    // Prüfe, ob serverResponse.value ein Array ist, und wandle es in ein Array um, falls es nur ein einzelnes Bundle ist
    const bundles = Array.isArray(serverResponse.value) ? serverResponse.value : [serverResponse.value];
    console.log(bundles);
    // Iteriere über alle Bundles und sende sie einzeln
    for (const bundle of bundles) {
      const response = await fetch(fhirServerUrl.value, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/fhir+json',
        },
        body: JSON.stringify(bundle),
      });

      if (response.ok) {
        console.log('Bundle erfolgreich gesendet');
      } else {
        const errorData = await response.json();
        console.error(`Fehler beim Senden des Bundles: ${response.status} - ${
          errorData.issue ? errorData.issue[0].diagnostics : response.statusText
        }`);
      }
    }

    fhirSendStatus.value = 'Alle FHIR-Nachrichten wurden gesendet';
  } catch (error) {
    console.error('Fehler beim Senden an FHIR-Server:', error);
    fhirSendStatus.value = `Fehler: ${error.message}`;
  }
};

watch(files, () => {
  if (files.value.length === 0) {
    availableSections.value = [];
    groupedSections.value = {};
    selectedSections.value = [];
    showSectionSelector.value = false;
    serverResponse.value = null;
    fhirSendStatus.value = '';
  }
}, { deep: true });
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
      <svg class="upload-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="64" height="64" fill="#007bff">
        <path d="M12 4l-8 8h5v8h6v-8h5l-8-8z"></path>
      </svg>
      <p>XML-Dateien hier ablegen oder auswählen</p>
      <p class="hint">Hinweis: Jede Datei muss einen einzigartigen Namen haben</p>
      <input id="xml-upload" accept=".xml" multiple type="file" @change="handleFileInput" class="hidden-input" />
      <label for="xml-upload" class="upload-button">XML-Dateien auswählen</label>
    </div>

    <!-- Liste der ausgewählten XML-Dateien -->
    <div v-if="files.length" class="file-list">
      <h3>Ausgewählte XML-Dateien:</h3>
      <div class="file-list-container">
        <table>
          <thead>
          <tr>
            <th>Dateiname</th>
            <th>Vorschau</th>
            <th>Entfernen</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="(file, index) in files" :key="file.name">
            <td>{{ file.name }}</td>
            <td>
              <button class="preview-icon" @click="showCdaPreview(index)" title="CDA anzeigen">🔍</button>
            </td>
            <td>
              <button class="remove-button" @click="removeFile(index)" title="Datei entfernen">✕</button>
            </td>
          </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- CDA Section Selector mit Gruppierung nach Datei -->
    <div v-if="showSectionSelector" class="section-selector">
      <h3>CDA Sections auswählen:</h3>
      <div class="section-controls">
        <button class="control-button" @click="toggleAllSections(true)">Alle auswählen</button>
        <button class="control-button" @click="toggleAllSections(false)">Keine auswählen</button>
      </div>

      <div class="section-list">
        <div v-for="(sections, filename) in groupedSections" :key="filename" class="file-group">
          <div class="file-header">
            <h4>{{ filename }}</h4>
            <div class="file-controls">
              <button class="control-button small" @click="toggleSectionsByFile(filename, true)">
                Alle
              </button>
              <button class="control-button small" @click="toggleSectionsByFile(filename, false)">
                Keine
              </button>
            </div>
          </div>
          <div class="section-group">
            <div v-for="section in sections.filter(section => section.entryCount > 0)" :key="section.id" class="section-item">
              <label class="section-label">
                <input v-model="selectedSections" :value="section.id" type="checkbox" />
                <span class="section-name">{{ section.title }} ({{ section.name }}) {{section.sectionSystemName}}: {{section.sectionCode}}</span>
                <span class="entry-count">[Entries: {{ section.entryCount }}]</span>
              </label>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Aktions-Buttons -->
    <div class="action-buttons">
      <button :disabled="!canSubmit" class="upload-button" @click="uploadFiles">
        Zum Server hochladen
      </button>
    </div>

    <!-- Statusmeldung -->
    <div v-if="uploadStatus" class="status">{{ uploadStatus }}</div>

    <!-- JSON Viewer für die Serverantwort -->
    <div v-if="serverResponse" class="json-viewer">
      <h3>Serverantwort (FHIR-Nachricht)</h3>
      <vue-json-pretty :data="serverResponse" :deep="2" :showLength="true" />

      <!-- FHIR Server URL Eingabe und Senden -->
      <div class="fhir-server-section">
        <label for="fhir-url">FHIR-Server URL:</label>
        <input
          id="fhir-url"
          v-model="fhirServerUrl"
          type="url"
          placeholder="z. B. http://fhir.example.com"
          class="fhir-url-input"
        />
        <button :disabled="!canSendToFhir" class="fhir-send-button" @click="sendToFhirServer">
          An FHIR-Server senden
        </button>
      </div>
      <div v-if="fhirSendStatus" class="fhir-status">{{ fhirSendStatus }}</div>
    </div>
  </div>
</template>

<style scoped>
body {
  font-size: 16px;
}

.upload-container {
  background: #ffffff;
  padding: 2rem;
  width: 100vw;
  min-height: 100vh;
  font-family: Arial, 'Segoe UI', sans-serif;
}

.hidden-input {
  width: 0.1px;
  height: 0.1px;
  opacity: 0;
  overflow: hidden;
  position: absolute;
  z-index: -1;
}

.drop-zone {
  border: 2px dashed #007bff;
  background: #f8f9fa;
  padding: 2rem;
  text-align: center;
  border-radius: 10px;
  margin-bottom: 2rem;
  transition: background 0.3s ease;
}

.drop-zone.active {
  background: #e9ecef;
}

.drop-zone p {
  color: #212529;
  font-size: 1.2rem;
  margin: 0.5rem 0;
}

.upload-icon {
  margin-bottom: 1rem;
}

.file-list {
  background: #ffffff;
  padding: 1rem;
  border-radius: 10px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  margin-bottom: 2rem;
}

.file-list-container {
  max-height: 200px;
  overflow-y: auto;
}

.file-list table {
  width: 100%;
  border-collapse: collapse;
}

.file-list th,
.file-list td {
  padding: 0.5rem;
  text-align: left;
}

.file-list th {
  background: #e9ecef;
}

.file-list tr:nth-child(even) {
  background: #f8f9fa;
}

.preview-icon,
.remove-button {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 1.2rem;
  color: #007bff;
}

.remove-button {
  color: #dc3545;
}

.section-selector {
  background: #ffffff;
  padding: 1rem;
  border-radius: 10px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  margin-bottom: 2rem;
}

.section-controls {
  display: flex;
  gap: 1rem;
  margin-bottom: 1rem;
}

.control-button {
  background: #007bff;
  color: white;
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  transition: background 0.3s ease;
}

.control-button:hover {
  background: #0056b3;
}

.control-button.small {
  padding: 0.3rem 0.6rem;
  font-size: 0.9rem;
}

.section-list {
  max-height: 400px;
  overflow-y: auto;
}

.file-header {
  background: #e9ecef;
  padding: 0.5rem;
  border-radius: 5px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.file-header h4 {
  margin: 0;
  font-size: 1.1rem;
}

.file-controls {
  display: flex;
  gap: 0.5rem;
}

.section-item {
  padding: 0.5rem 0;
}

.section-label {
  display: flex;
  align-items: center;
  cursor: pointer;
}

.section-name {
  margin-left: 0.5rem;
  font-weight: 600;
}

.entry-count {
  margin-left: 0.5rem;
  font-size: 0.9rem;
  color: #007bff;
}

.action-buttons {
  display: flex;
  justify-content: center;
  margin: 2rem 0;
}

.upload-button {
  background: #28a745;
  color: white;
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  font-size: 1.1rem;
  transition: background 0.3s ease;
}

.upload-button:hover:not(:disabled) {
  background: #218838;
}

.upload-button:disabled {
  background: #6c757d;
  cursor: not-allowed;
}

.status {
  background: #f8f9fa;
  padding: 1rem;
  border-radius: 5px;
  text-align: center;
  color: #212529;
  font-size: 1.1rem;
  margin-top: 2rem;
}

.json-viewer {
  background: #ffffff;
  padding: 1rem;
  border-radius: 10px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  margin-top: 2rem;
  max-height: 600px;
  overflow-y: auto;
}

.json-viewer h3 {
  color: #212529;
  margin-bottom: 1rem;
}

.fhir-server-section {
  margin-top: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.fhir-server-section label {
  font-weight: 600;
  color: #212529;
}

.fhir-url-input {
  padding: 0.5rem;
  border: 1px solid #ced4da;
  border-radius: 5px;
  font-size: 1rem;
  width: 100%;
  max-width: 400px;
}

.fhir-send-button {
  background: #007bff;
  color: white;
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  transition: background 0.3s ease;
}

.fhir-send-button:hover:not(:disabled) {
  background: #0056b3;
}

.fhir-send-button:disabled {
  background: #6c757d;
  cursor: not-allowed;
}

.fhir-status {
  margin-top: 1rem;
  background: #f8f9fa;
  padding: 0.5rem;
  border-radius: 5px;
  text-align: center;
  color: #212529;
  font-size: 1rem;
}

/* Barrierefreiheit */
button,
input,
label {
  outline: none;
}

button:focus,
input:focus {
  outline: 2px solid #007bff;
}

/* Responsivität */
@media (max-width: 768px) {
  .upload-container {
    padding: 1rem;
  }

  .drop-zone {
    padding: 1.5rem;
  }

  .action-buttons {
    flex-direction: column;
  }

  .upload-button {
    width: 100%;
  }

  .file-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .file-controls {
    margin-top: 0.5rem;
  }

  .fhir-server-section {
    align-items: stretch;
  }

  .fhir-url-input {
    width: 100%;
  }
}
</style>
