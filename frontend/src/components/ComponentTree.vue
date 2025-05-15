<script setup>
import { ref, watch } from 'vue'

// Props definieren
const props = defineProps({
  components: {
    type: Array,
    required: true
  },
  level: {
    type: Number,
    default: 0
  }
})

const emit = defineEmits(['update:selectedComponents'])

// Reaktiver Zustand für den Ein-/Ausklapp-Status der Entries
const expandedEntries = ref({})
// Reaktiver Zustand für ausgewählte Komponenten
const selectedComponents = ref({})

const toggleEntries = (componentIndex) => {
  expandedEntries.value[componentIndex] = !expandedEntries.value[componentIndex]
}

const toggleComponentSelection = (componentIndex) => {
  selectedComponents.value[componentIndex] = !selectedComponents.value[componentIndex]
  emitSelectionUpdate()
}

const getColorForLevel = (level) => {
  const colors = [
    '#3498db', // Blau (Level 0)
    '#2ecc71', // Grün (Level 1)
    '#e67e22', // Orange (Level 2)
    '#e74c3c', // Rot (Level 3)
    '#9b59b6'  // Lila (Level 4 und darüber)
  ]
  return colors[Math.min(level, colors.length - 1)]
}

// Funktion zum Emitten der ausgewählten Komponenten an die übergeordnete Komponente
const emitSelectionUpdate = () => {
  const selected = Object.keys(selectedComponents.value)
    .filter(index => selectedComponents.value[index])
    .map(index => props.components[Number(index)]) // Korrigierter Zugriff auf props.components
  emit('update:selectedComponents', selected)
}

// Emit bei Änderungen überwachen
watch(selectedComponents, () => {
  emitSelectionUpdate()
}, { deep: true })
</script>

<template>
  <div class="component-tree">
    <div
      v-for="(component, index) in props.components"
      :key="index"
      class="component-item"
      :style="{
        marginLeft: `${level * 20}px`,
        borderLeft: `4px solid ${getColorForLevel(level)}`
      }"
    >
      <div class="component-header">
        <label class="checkbox-container">
          <input
            type="checkbox"
            :checked="selectedComponents[index]"
            @change="toggleComponentSelection(index)"
          >
          <span class="checkmark"></span>
        </label>
        <div class="header-content" @click="toggleEntries(index)">
          <span v-if="level > 0" class="nesting-indicator">↳</span>
          <span class="component-title">{{ component.title }}</span>
          <span v-if="component.entries?.length || component.nestedComponents?.length" class="info-badge">
            {{ (component.entries?.length || 0) + (component.nestedComponents?.length || 0) }}
          </span>
          <span v-if="component.entries?.length" class="toggle-icon">
            {{ expandedEntries[index] ? '▲' : '▼' }}
          </span>
        </div>
      </div>

      <!-- Entries für jede Component anzeigen -->
      <transition name="accordion">
        <div v-if="component.entries?.length && expandedEntries[index]" class="component-entries">
          <div class="entries-header">Entries ({{ component.entries.length }}):</div>
          <ul class="entries-list">
            <li v-for="(entry, entryIndex) in component.entries" :key="entryIndex" class="entry-item">
              {{ entry }}
            </li>
          </ul>
        </div>
      </transition>
      <div v-if="!component.entries?.length" class="no-entries">
        Keine Entries vorhanden
      </div>

      <!-- Rekursiver Aufruf für verschachtelte Components -->
      <div v-if="component.nestedComponents?.length" class="nested-components">
        <div class="nested-header">Nested Components ({{ component.nestedComponents.length }}):</div>
        <component-tree
          :components="component.nestedComponents"
          :level="level + 1"
          @update:selectedComponents="$emit('update:selectedComponents', $event)"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Styles bleiben unverändert */
.component-tree {
  margin-top: 0.5rem;
}

.component-item {
  position: relative;
  padding: 1rem;
  margin: 0.5rem 0;
  background: #f8fafc;
  border-radius: 6px;
  transition: all 0.2s ease;
}

.component-item:hover {
  background: #eef2f7;
  transform: translateX(2px);
}

.component-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.header-content {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-grow: 1;
  flex-wrap: wrap;
  cursor: pointer;
  user-select: none;
}

.component-title {
  font-weight: 500;
  color: #2c3e50;
  font-size: 1rem;
  flex-grow: 1;
}

.info-badge {
  background: #3498db;
  color: white;
  padding: 0.2rem 0.6rem;
  border-radius: 10px;
  font-size: 0.8rem;
  font-weight: 500;
  min-width: 1.5rem;
  text-align: center;
}

.toggle-icon {
  color: #7f8c8d;
  font-size: 0.9rem;
  transition: transform 0.3s ease;
}

.nesting-indicator {
  color: #7f8c8d;
  font-size: 0.9rem;
  margin-right: 0.3rem;
}

.component-entries {
  margin-top: 0.5rem;
  padding: 0.5rem 0 0 1rem;
  border-left: 2px dashed #eef2f7;
}

.entries-header {
  font-size: 0.9rem;
  color: #7f8c8d;
  font-weight: 500;
  margin-bottom: 0.3rem;
}

.entries-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.entry-item {
  font-size: 0.85rem;
  color: #34495e;
  padding: 0.4rem 0;
  border-bottom: 1px dashed #eef2f7;
}

.entry-item:last-child {
  border-bottom: none;
}

.no-entries {
  font-size: 0.85rem;
  color: #95a5a6;
  font-style: italic;
  padding: 0.5rem 0 0 1rem;
}

.nested-components {
  margin-top: 0.75rem;
  padding-left: 1rem;
}

.nested-header {
  font-size: 0.9rem;
  color: #7f8c8d;
  font-weight: 500;
  margin-bottom: 0.5rem;
  padding-bottom: 0.2rem;
  border-bottom: 1px dashed #eef2f7;
}

/* Checkbox Styles */
.checkbox-container {
  display: flex;
  align-items: center;
  cursor: pointer;
}

.checkbox-container input {
  position: absolute;
  opacity: 0;
  cursor: pointer;
  height: 0;
  width: 0;
}

.checkmark {
  display: inline-block;
  height: 18px;
  width: 18px;
  background-color: #fff;
  border: 2px solid #7f8c8d;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.checkbox-container input:checked ~ .checkmark {
  background-color: #3498db;
  border-color: #3498db;
}

.checkbox-container input:checked ~ .checkmark:after {
  display: block;
}

.checkmark:after {
  content: "✓";
  display: none;
  position: relative;
  left: 4px;
  top: -2px;
  color: white;
  font-size: 12px;
}

/* Transition für das Ein-/Ausklappen der Entries */
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
  max-height: 200px;
}
</style>
