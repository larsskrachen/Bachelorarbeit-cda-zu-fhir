<script setup>
import { ref, computed } from 'vue'
import ComponentTree from "@/components/ComponentTree.vue";

const files = ref([])
const dragActive = ref(false)
const uploadStatus = ref('')
const showFileDetails = ref(false)
const components = ref([])
const groupedComponents = ref({})
const selectedComponents = ref([])

const canSubmit = computed(() => {
  return files.value.length > 0 && selectedComponents.value.length > 0;
})

const checkForDuplicateFileName = (newFile) => {
  return files.value.some(existingFile => existingFile.name === newFile.name)
}

const onDrop = (e) => {
  e.preventDefault()
  dragActive.value = false
  const droppedFiles = [...e.dataTransfer.files].filter(file => file.name.toLowerCase().endsWith('.xml'))
  if (droppedFiles.length) {
    const uniqueFiles = droppedFiles.filter(file => {
      if (checkForDuplicateFileName(file)) {
        uploadStatus.value = `Die Datei "${file.name}" existiert bereits. Jede Datei muss einen einzigartigen Namen haben.`
        return false
      }
      return true
    })
    files.value = [...files.value, ...uniqueFiles]
  }
  proccesFiles()
}

const handleFileInput = (e) => {
  const newFiles = [...e.target.files].filter(file => file.name.toLowerCase().endsWith('.xml'))
  const uniqueFiles = newFiles.filter(file => {
    if (checkForDuplicateFileName(file)) {
      uploadStatus.value = `Die Datei "${file.name}" existiert bereits. Jede Datei muss einen einzigartigen Namen haben.`
      return false
    }
    return true
  })
  files.value = [...files.value, ...uniqueFiles]
  proccesFiles()
}

const proccesFiles = async () => {
  console.log("Processing " + files.value.length + " files")
  if (files.value.length > 0) {
    showFileDetails.value = true
  }
  components.value = []
  groupedComponents.value = {}

  for (const file of files.value) {
    console.log('Processing file:', file.name)
    try {
      const text = await readFileAsText(file)
      const parser = new DOMParser()
      const xmlDoc = parser.parseFromString(text, "text/xml")
      if (!groupedComponents.value[file.name]) {
        groupedComponents.value[file.name] = []
      }
      const rootComponent = xmlDoc.querySelector('component')
      const structuredBody = rootComponent ? rootComponent.querySelector('structuredBody') : null

      if (structuredBody) {
        const extractedComponents = processComponentsRecursively(structuredBody, file.name)
        components.value = [...components.value, ...flattenComponents(extractedComponents)]
        groupedComponents.value[file.name] = extractedComponents
      } else {
        console.log('No structuredBody found in file:', file.name)
      }
    } catch (error) {
      console.error(`Error processing file ${file.name}:`, error)
      uploadStatus.value = `Error processing file ${file.name}: ${error.message}`
    }
  }
}

const flattenComponents = (compArray, result = []) => {
  for (const comp of compArray) {
    const compCopy = { ...comp }
    delete compCopy.nestedComponents
    result.push(compCopy)
    if (comp.nestedComponents && comp.nestedComponents.length > 0) {
      flattenComponents(comp.nestedComponents, result)
    }
  }
  return result
}

const handleSelectedComponents = (selected) => {
  selectedComponents.value = selected
}

const processComponentsRecursively = (parentElement, fileName, depth = 0) => {
  const componentElements = parentElement.querySelectorAll(':scope > component')
  console.log(`${' '.repeat(depth * 2)}Found ${componentElements.length} components at depth ${depth}`)

  const extractedComponents = []

  for (const componentElement of componentElements) {
    const sectionElement = componentElement.querySelector(':scope > section')
    if (sectionElement) {
      const titleElement = sectionElement.querySelector('title')
      const title = titleElement ? titleElement.textContent : 'Kein Titel'
      console.log(`${' '.repeat(depth * 2)}Found component: ${title}`)

      const entries = sectionElement.querySelectorAll(':scope > entry')
      const entryTexts = []

      if (entries.length > 0) {
        console.log(`${' '.repeat(depth * 2)}  Found ${entries.length} entries for "${title}"`)
        entries.forEach((entry, index) => {
          let entryText = 'Kein Text gefunden'
          const textElement = entry.querySelector('text')
          if (textElement && textElement.textContent.trim()) {
            entryText = textElement.textContent.trim()
          } else {
            const elementWithDisplayName = entry.querySelector('*[displayName]')
            if (elementWithDisplayName) {
              entryText = elementWithDisplayName.getAttribute('displayName')
            } else {
              const codeElement = entry.querySelector('code[displayName]')
              if (codeElement) {
                entryText = codeElement.getAttribute('displayName')
              } else {
                const elementWithValue = entry.querySelector('*[value]')
                if (elementWithValue) {
                  entryText = `Value: ${elementWithValue.getAttribute('value')}`
                }
              }
            }
          }
          console.log(`${' '.repeat(depth * 2)}    Entry ${index + 1}: ${entryText}`)
          entryTexts.push(entryText)
        })
      } else {
        console.log(`${' '.repeat(depth * 2)}  No entries found for "${title}"`)
      }

      const componentInfo = {
        fileName: fileName,
        title: title,
        entries: entryTexts,
        depth: depth,
        nestedComponents: []
      }

      const nestedComponents = processComponentsRecursively(sectionElement, fileName, depth + 1)
      if (nestedComponents.length > 0) {
        componentInfo.nestedComponents = nestedComponents
      }

      extractedComponents.push(componentInfo)
    }
  }

  return extractedComponents
}

const readFileAsText = (file) => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (e) => resolve(e.target.result)
    reader.onerror = (e) => reject(e)
    reader.readAsText(file)
  })
}

const toggleFileDetails = () => {
  showFileDetails.value = !showFileDetails.value
}

const removeFile = (index) => {
  const removedFileName = files.value[index].name
  files.value.splice(index, 1)
  delete groupedComponents.value[removedFileName]
  components.value = components.value.filter(comp => comp.fileName !== removedFileName)
}

const uploadFiles = async () => {
  if (!files.value.length || !selectedComponents.value.length) {
    uploadStatus.value = 'Bitte wähle mindestens eine Datei und eine Komponente aus.'
    return
  }

  try {
    uploadStatus.value = 'Dateien werden hochgeladen...';
    const formData = new FormData();

    for (const file of files.value) {
      const text = await readFileAsText(file)
      const parser = new DOMParser()
      const xmlDoc = parser.parseFromString(text, "text/xml")

      // Erstelle ein neues XML-Dokument mit nur <structuredBody>
      const newXmlDoc = parser.parseFromString('<component><structuredBody></structuredBody></component>', 'text/xml')
      const newStructuredBody = newXmlDoc.querySelector('structuredBody')

      // Filtere ausgewählte Komponenten für diese Datei
      const fileSelectedComponents = selectedComponents.value.filter(comp => comp.fileName === file.name)

      // Konvertiere die ausgewählten Komponenten zurück in XML
      fileSelectedComponents.forEach(selectedComp => {
        const componentElement = newXmlDoc.createElement('component')
        const sectionElement = newXmlDoc.createElement('section')
        const titleElement = newXmlDoc.createElement('title')
        titleElement.textContent = selectedComp.title
        sectionElement.appendChild(titleElement)

        if (selectedComp.entries && selectedComp.entries.length > 0) {
          selectedComp.entries.forEach(entryText => {
            const entryElement = newXmlDoc.createElement('entry')
            const textElement = newXmlDoc.createElement('text')
            textElement.textContent = entryText
            entryElement.appendChild(textElement)
            sectionElement.appendChild(entryElement)
          })
        }

        // Behandle verschachtelte Komponenten rekursiv
        if (selectedComp.nestedComponents && selectedComp.nestedComponents.length > 0) {
          appendNestedComponents(newXmlDoc, sectionElement, selectedComp.nestedComponents)
        }

        componentElement.appendChild(sectionElement)
        newStructuredBody.appendChild(componentElement)
      })

      const serializer = new XMLSerializer()
      const newXmlString = serializer.serializeToString(newXmlDoc)
      const blob = new Blob([newXmlString], { type: 'application/xml' })
      formData.append('files', blob, file.name)
    }

    const response = await fetch('http://localhost:7777/extract/xml', {
      method: 'POST',
      body: formData
    })

    if (response.ok) {
      uploadStatus.value = 'Upload erfolgreich'
    } else {
      uploadStatus.value = 'Upload fehlgeschlagen'
    }
  } catch (error) {
    console.error('Fehler:', error)
    uploadStatus.value = `Fehler: ${error.message}`
  }
}

// Neue Hilfsfunktion für verschachtelte Komponenten
const appendNestedComponents = (xmlDoc, parentElement, nestedComponents) => {
  nestedComponents.forEach(nestedComp => {
    const nestedComponentElement = xmlDoc.createElement('component')
    const nestedSectionElement = xmlDoc.createElement('section')
    const nestedTitleElement = xmlDoc.createElement('title')
    nestedTitleElement.textContent = nestedComp.title
    nestedSectionElement.appendChild(nestedTitleElement)

    if (nestedComp.entries && nestedComp.entries.length > 0) {
      nestedComp.entries.forEach(entryText => {
        const entryElement = xmlDoc.createElement('entry')
        const textElement = xmlDoc.createElement('text')
        textElement.textContent = entryText
        entryElement.appendChild(textElement)
        nestedSectionElement.appendChild(entryElement)
      })
    }

    if (nestedComp.nestedComponents && nestedComp.nestedComponents.length > 0) {
      appendNestedComponents(xmlDoc, nestedSectionElement, nestedComp.nestedComponents)
    }

    nestedComponentElement.appendChild(nestedSectionElement)
    parentElement.appendChild(nestedComponentElement)
  })
}

const countAllComponents = (components) => {
  let count = components.length
  for (const component of components) {
    if (component.nestedComponents && component.nestedComponents.length > 0) {
      count += countAllComponents(component.nestedComponents)
    }
  }
  return count
}

const expandedFiles = ref({})

const toggleAccordion = (fileName) => {
  expandedFiles.value[fileName] = !expandedFiles.value[fileName]
}
</script>

<template>
  <div class="upload-container">
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

    <div class="action-buttons">
      <button
        :disabled="!canSubmit"
        class="upload-button"
        @click="uploadFiles"
      >
        Zum Server hochladen
      </button>
      <button
        v-if="files.length"
        class="toggle-details-button"
        @click="toggleFileDetails"
      >
        {{ showFileDetails ? 'Dateiliste ausblenden' : 'Dateiliste anzeigen' }}
      </button>
    </div>

    <div v-if="showFileDetails && files.length" class="file-details">
      <h3>Ausgewählte XML-Dateien:</h3>
      <ul>
        <li v-for="(file, index) in files" :key="file.name">
          <div class="file-info">
            <span class="file-name">{{ file.name }}</span>
            <span class="file-size">{{ (file.size / 1024).toFixed(2) }} KB</span>
          </div>
          <button
            class="remove-button"
            @click="removeFile(index)"
          >
            ✕
          </button>
        </li>
      </ul>
    </div>

    <p v-if="uploadStatus" class="status">{{ uploadStatus }}</p>

    <div v-if="Object.keys(groupedComponents).length > 0" class="components-container">
      <h3 class="components-header">Gefundene Komponenten</h3>
      <div class="accordion">
        <div v-for="(fileComponents, fileName) in groupedComponents" :key="fileName" class="accordion-item">
          <div class="accordion-header" @click="toggleAccordion(fileName)">
            <div class="file-info">
              <span class="file-name">{{ fileName }}</span>
              <span class="component-count">{{ countAllComponents(fileComponents) }}</span>
            </div>
            <span class="accordion-icon">{{ expandedFiles[fileName] ? '▲' : '▼' }}</span>
          </div>
          <transition name="accordion">
            <div v-if="expandedFiles[fileName]" class="accordion-content">
              <component-tree
                :components="fileComponents"
                :level="0"
                @update:selectedComponents="handleSelectedComponents"
              />
            </div>
          </transition>
        </div>
      </div>
    </div>

    <div v-else class="no-components">
      Keine Komponenten in den XML-Dateien gefunden.
    </div>

    <!-- Optional: Anzeige der ausgewählten Komponenten -->
    <div v-if="selectedComponents.length" class="selected-components">
      <h4>Ausgewählte Komponenten: {{ selectedComponents.length }}</h4>
      <ul>
        <li v-for="comp in selectedComponents" :key="comp.title">{{ comp.title }}</li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.upload-container {
  max-width: 800px;
  margin: 2rem auto;
  padding: 1rem;
}

.components-container {
  margin-top: 2rem;
  padding: 1.5rem;
  background: #ffffff;
  border-radius: 8px;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.03);
}

.components-header {
  margin: 0 0 1.5rem 0;
  font-size: 1.5rem;
  font-weight: 600;
  color: #2c3e50;
  padding-bottom: 0.5rem;
  border-bottom: 2px solid #3498db;
  width: fit-content;
}

.accordion {
  border-radius: 6px;
  overflow: hidden;
}

.accordion-item {
  border-bottom: 1px solid #eef2f7;
}

.accordion-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.5rem;
  background: #f8fafc;
  cursor: pointer;
  transition: background 0.2s ease;
}

.accordion-header:hover {
  background: #eef2f7;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.file-name {
  font-weight: 500;
  color: #2c3e50;
  font-size: 1.1rem;
}

.component-count {
  background: #3498db;
  color: white;
  padding: 0.3rem 0.8rem;
  border-radius: 12px;
  font-size: 0.85rem;
  font-weight: 500;
  min-width: 2rem;
  text-align: center;
}

.accordion-icon {
  color: #7f8c8d;
  font-size: 0.9rem;
  transition: transform 0.3s ease;
}

.accordion-content {
  padding: 1rem 1.5rem;
  background: #fff;
  border-top: 1px solid #eef2f7;
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

.upload-button {
  background: #4CAF50;
  color: white;
}

.toggle-details-button {
  background: #2196F3;
  color: white;
}

button:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.file-details {
  margin-top: 1rem;
  background: #f5f5f5;
  border-radius: 4px;
  padding: 1rem;
  transition: all 0.3s ease;
  animation: slideDown 0.3s forwards;
}

@keyframes slideDown {
  from { opacity: 0; max-height: 0; }
  to { opacity: 1; max-height: 500px; }
}

.file-details h3 {
  margin-top: 0;
  color: #333;
}

.file-details ul {
  list-style: none;
  padding: 0;
}

.file-details li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.8rem;
  background: white;
  margin-bottom: 0.5rem;
  border-radius: 4px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

.file-info {
  display: flex;
  flex-direction: column;
  flex-grow: 1;
}

.file-name {
  font-weight: 500;
}

.file-size {
  font-size: 0.8rem;
  color: #666;
  margin-top: 0.3rem;
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

.status {
  padding: 1rem;
  margin-top: 1rem;
  background: #f0f0f0;
  border-radius: 4px;
}

.no-components {
  padding: 1rem;
  text-align: center;
  color: #666;
  font-style: italic;
}

.selected-components {
  margin-top: 1rem;
  padding: 1rem;
  background: #f8fafc;
  border-radius: 6px;
}

.accordion-enter-active,
.accordion-leave-active {
  transition: all 0.3s ease;
}

.accordion-enter-from,
.accordion-leave-to {
  opacity: 0;
  max-height: 0;
}

.accordion-enter-to,
.accordion-leave-from {
  opacity: 1;
  max-height: 300px;
}
</style>
